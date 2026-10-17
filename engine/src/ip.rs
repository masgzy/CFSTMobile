use std::fs::File;
use std::io::{self, BufRead};
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
use std::str::FromStr;
use std::sync::{
    Arc,
    atomic::{AtomicBool, AtomicUsize, Ordering},
};
use std::time::Duration;

use crate::args::Args;
use crate::common::Target;
use crate::log::warning_println;

/// 单个域名条目最多展开的解析结果数（与 CFData-WEB 非标模式一致）
const MAX_IPS_PER_HOST: usize = 50;
/// 单个域名解析超时（秒）
const RESOLVE_TIMEOUT_SECS: u64 = 5;

#[derive(Clone, Copy)]
pub(crate) enum IpCidr {
    V4(Ipv4Addr, u8),
    V6(Ipv6Addr, u8),
}

impl IpCidr {
    fn parts(&self) -> (u128, u8, u8, u128) {
        match self {
            IpCidr::V4(ip, len) => (u32::from(*ip) as u128, *len, 32, u32::MAX as u128),
            IpCidr::V6(ip, len) => (u128::from(*ip), *len, 128, u128::MAX),
        }
    }

    pub(crate) fn range_u128(&self) -> (u128, u128) {
        let (val, len, max_bits, full_mask) = self.parts();
        let host_bits = max_bits - len;

        if host_bits >= max_bits {
            return (0, full_mask);
        }

        let mask = full_mask << host_bits & full_mask;
        let start = val & mask;
        let end = start | (!mask & full_mask);
        
        (start, end)
    }

    pub(crate) fn prefix_len(&self) -> u8 {
        match self {
            IpCidr::V4(_, len) | IpCidr::V6(_, len) => *len,
        }
    }

    pub(crate) fn is_single_host(&self) -> bool {
        matches!(self, IpCidr::V4(_, 32) | IpCidr::V6(_, 128))
    }

    pub(crate) fn to_ipaddr(self) -> IpAddr {
        let (start, _) = self.range_u128();
        match self {
            IpCidr::V4(..) => IpAddr::V4(Ipv4Addr::from(start as u32)),
            IpCidr::V6(..) => IpAddr::V6(Ipv6Addr::from(start)),
        }
    }

    pub(crate) fn parse(s: &str) -> Option<Self> {
        let (ip_part, prefix_part) = s.split_once('/')?;

        let ip = IpAddr::from_str(ip_part).ok()?;
        let prefix = prefix_part.parse::<u8>().ok()?;

        match ip {
            IpAddr::V4(v4) if prefix <= 32 => Some(IpCidr::V4(v4, prefix)),
            IpAddr::V6(v6) if prefix <= 128 => Some(IpCidr::V6(v6, prefix)),
            _ => None,
        }
    }
}

pub(crate) struct IpBuffer {
    total_expected: usize,
    segments: Arc<Vec<Arc<IpSegment>>>,
    cursor: AtomicUsize,
    active_count: AtomicUsize,
    tcp_port: u16,
}

pub(crate) enum IpSegment {
    Static {
        targets: Vec<Target>,
        cursor: AtomicUsize,
        exhausted_notified: AtomicBool,
    },
    Generator {
        cidr: CidrState,
        exhausted_notified: AtomicBool,
    },
}

impl IpSegment {
    fn next_target(&self, tcp_port: u16) -> Option<Target> {
        match self {
            IpSegment::Static { targets, cursor, .. } => {
                let idx = cursor.fetch_add(1, Ordering::Relaxed);
                targets.get(idx).cloned()
            }
            IpSegment::Generator { cidr, .. } => cidr.next_ip(tcp_port).map(|addr| Target { addr, origin: None }),
        }
    }

    fn mark_dead_once(&self) -> bool {
        match self {
            IpSegment::Static { exhausted_notified, .. } | 
            IpSegment::Generator { exhausted_notified, .. } => {
                exhausted_notified.compare_exchange(false, true, Ordering::SeqCst, Ordering::Relaxed).is_ok()
            }
        }
    }
}

pub(crate) struct CidrState {
    network: IpCidr,
    total_count: usize,
    interval_size: u128,
    start: u128,
    last_size: u128,
    index_counter: AtomicUsize,
}

impl CidrState {
    pub(crate) fn new(network: IpCidr, count: usize, start: u128, interval_size: u128) -> Self {
        let last_size = if count > 0 {
            let last_start = start + (count as u128 - 1) * interval_size;
            let (_, end) = network.range_u128();
            (end - last_start).saturating_add(1)
        } else {
            interval_size
        };
        
        Self {
            network,
            total_count: count,
            interval_size,
            start,
            last_size,
            index_counter: AtomicUsize::new(0),
        }
    }

    fn next_ip(&self, tcp_port: u16) -> Option<SocketAddr> {
        let current_index = self.index_counter.fetch_add(1, Ordering::Relaxed);

        if current_index >= self.total_count {
            return None;
        }

        let interval_start = self.start + (current_index as u128 * self.interval_size);

        let actual_interval_size = if current_index == self.total_count - 1 {
            self.last_size
        } else {
            self.interval_size
        };

        let random_offset = if actual_interval_size <= 1 {
            0
        } else {
            generate_refined_random(self as *const Self as usize) % actual_interval_size
        };

        let random_ip = interval_start + random_offset;

        let ip_addr = match self.network {
            IpCidr::V4(..) => IpAddr::V4(Ipv4Addr::from(random_ip as u32)),
            IpCidr::V6(..) => IpAddr::V6(Ipv6Addr::from(random_ip)),
        };

        Some(SocketAddr::new(ip_addr, tcp_port))
    }

}

impl IpBuffer {
    pub(crate) fn new(
        targets: Vec<Target>,
        cidr_states: Vec<CidrState>,
        total_expected: usize,
        tcp_port: u16,
    ) -> Self {
        let mut segments: Vec<Arc<IpSegment>> = Vec::new();

        if !targets.is_empty() {
            segments.push(Arc::new(IpSegment::Static {
                targets,
                cursor: AtomicUsize::new(0),
                exhausted_notified: AtomicBool::new(false),
            }));
        }

        for cidr in cidr_states {
            segments.push(Arc::new(IpSegment::Generator {
                cidr,
                exhausted_notified: AtomicBool::new(false),
            }));
        }

        let initial_len = segments.len();

        Self {
            total_expected,
            segments: Arc::new(segments),
            cursor: AtomicUsize::new(0),
            active_count: AtomicUsize::new(initial_len),
            tcp_port,
        }
    }

    pub(crate) fn pop(&self) -> Option<Target> {
        loop {
            if self.active_count.load(Ordering::Acquire) == 0 {
                return None;
            }

            let current_vec = self.segments.clone();
            let segments_len = current_vec.len();

            if segments_len == 0 {
                return None;
            }

            let start_idx = self.cursor.fetch_add(1, Ordering::Relaxed);

            for i in 0..segments_len {
                let idx = (start_idx + i) % segments_len;
                let segment = &current_vec[idx];

                if let Some(target) = segment.next_target(self.tcp_port) {
                    return Some(target);
                }

                if segment.mark_dead_once() {
                    self.active_count.fetch_sub(1, Ordering::SeqCst);
                }
            }

            if self.active_count.load(Ordering::Acquire) == 0 {
                return None;
            }
        }
    }

    pub(crate) fn total_expected(&self) -> usize {
        self.total_expected
    }
}

fn generate_refined_random(obj_addr: usize) -> u128 {
    static SHARED_STATE: AtomicUsize = AtomicUsize::new(0);
    let hasher_seed = generate_refined_random as *const () as usize;

    let s = SHARED_STATE.fetch_add(1, Ordering::Relaxed);
    let t = &s as *const _ as usize;

    let mut lo = s ^ obj_addr ^ t;
    lo = lo.wrapping_mul(hasher_seed | 1);
    lo = lo.rotate_left(usize::BITS / 2);
    lo = lo.swap_bytes();

    // 黄金比例常量按 u64 书写再 cast：32 位目标（armv7）的 usize 放不下
    // 64 位字面量会触发 overflowing_literals 硬错误；cast 截断仅影响
    // 32 位平台的散列质量（此处只是 IP 采样偏移的扰码器，可接受）
    let mut hi = lo.wrapping_mul(0x9E3779B97F4A7C15_u64 as usize);
    hi = hi.rotate_left(usize::BITS / 4);
    hi = hi.swap_bytes();
    hi ^= s;

    (hi as u128) << 64 | (lo as u128)
}

pub(crate) fn collect_ip_sources(ip_text: &str, ip_file: &str) -> Result<Vec<String>, String> {
    let clean = |s: &str| {
        let s = s.trim();
        (!s.is_empty() && !s.starts_with('#') && !s.starts_with("//")).then(|| s.to_string())
    };

    let mut sources: Vec<_> = ip_text.split(',').filter_map(clean).collect();

    if !ip_file.is_empty() && let Ok(file) = File::open(ip_file) {
        sources.extend(io::BufReader::new(file).lines().map_while(Result::ok).filter_map(|l| clean(&l)));
    }

    sources.sort_unstable();
    sources.dedup();

    if sources.is_empty() {
        return Err("未获取到任何 IP、CIDR 或域名".to_string());
    }

    Ok(sources)
}

#[derive(Clone)]
struct ParsedIpInfo {
    result: IpParseResult,
    custom_count: Option<u128>,
}

fn parse_ip_info(ip_range: &str) -> ParsedIpInfo {
    let (ip_part, custom_count) = if let Some((ip_part, count_str)) = ip_range.split_once('=') {
        let count = count_str.trim().parse::<u128>().ok().filter(|&n| n > 0);
        (ip_part.trim(), count)
    } else {
        (ip_range, None)
    };
    
    ParsedIpInfo {
        result: parse_ip_with_port(ip_part),
        custom_count,
    }
}

#[derive(Clone)]
enum IpParseResult {
    SocketAddr(SocketAddr),
    Network(IpCidr),
    Invalid,
}

fn parse_ip_with_port(ip_str: &str) -> IpParseResult {
    if let Ok(socket_addr) = SocketAddr::from_str(ip_str) {
        return IpParseResult::SocketAddr(socket_addr);
    }

    if let Ok(ip_addr) = IpAddr::from_str(ip_str) {
        let network = match ip_addr {
            IpAddr::V4(v4) => IpCidr::V4(v4, 32),
            IpAddr::V6(v6) => IpCidr::V6(v6, 128),
        };
        return IpParseResult::Network(network);
    }

    if let Some(network) = IpCidr::parse(ip_str) {
        return IpParseResult::Network(network);
    }

    IpParseResult::Invalid
}

pub(crate) fn process_ip_sources(ip_sources: Vec<String>, config: &Args) -> (Vec<Target>, Vec<CidrState>, usize) {
    let mut single_targets: Vec<Target> = Vec::new();
    let mut cidr_info = Vec::new();
    let mut total_expected = 0;

    for ip_range in ip_sources {
        let ip_info = parse_ip_info(&ip_range);

        match &ip_info.result {
            IpParseResult::SocketAddr(socket_addr) => {
                single_targets.push(Target { addr: *socket_addr, origin: None });
                total_expected += 1;
            }
            IpParseResult::Network(network) => {
                if network.is_single_host() {
                    single_targets.push(Target { addr: SocketAddr::new(network.to_ipaddr(), config.tcp_port), origin: None });
                    total_expected += 1;
                } else {
                    let count = calculate_ip_count(&ip_info.result, ip_info.custom_count, config.test_all_ipv4);
                    let (start, end) = network.range_u128();

                    let range_size = (end - start).saturating_add(1);

                    let adjusted_count = count.min(range_size) as usize;

                    let interval_size = if adjusted_count > 0 {
                        (range_size / adjusted_count as u128).max(1)
                    } else {
                        1
                    };

                    total_expected += adjusted_count;
                    cidr_info.push((*network, adjusted_count, start, interval_size));
                }
            }
            IpParseResult::Invalid => {}
        }
    }

    let cidr_states: Vec<_> = cidr_info
        .into_iter()
        .map(|(net, count, start, size)| CidrState::new(net, count, start, size))
        .collect();

    (single_targets, cidr_states, total_expected)
}

fn calculate_ip_count(parsed_result: &IpParseResult, custom_count: Option<u128>, test_all_ipv4: bool) -> u128 {
    match parsed_result {
        IpParseResult::SocketAddr(_) => {
            1
        }
        IpParseResult::Network(network) => {
            if network.is_single_host() {
                return 1;
            }

            let prefix = network.prefix_len();
            let is_ipv4 = matches!(network, IpCidr::V4(_, _));

            if let Some(count) = custom_count {
                return count;
            }

            if is_ipv4 && test_all_ipv4 {
                return 1u128 << (32 - prefix);
            }

            calculate_sample_count(prefix, is_ipv4)
        }
        IpParseResult::Invalid => {
            0
        }
    }
}

const MAX_SAMPLE_EXP: u8 = 18;
const MIN_SAMPLE_EXP_OFFSET: u8 = 2;

pub(crate) fn calculate_sample_count(prefix: u8, is_ipv4: bool) -> u128 {
    let max_bits: u8 = if is_ipv4 { 31 } else { 127 };
    let host_bits = max_bits.saturating_sub(prefix);
    let sample_exp = host_bits.min(MAX_SAMPLE_EXP).saturating_sub(MIN_SAMPLE_EXP_OFFSET);
    1u128 << sample_exp
}

// ---------------------------------------------------------------------------
// 非标优选：域名条目（与 CFData-WEB 非标模式语义一致）
//
// 输入条目语法：`域名` 或 `域名:端口`（缺省端口回退到 tcp_port）。
// 语义：解析域名得到 A/AAAA 记录，展开为多个测试目标（IPv4 优先，
// 单域名上限 MAX_IPS_PER_HOST），结果行保留原始输入（origin）。
// ---------------------------------------------------------------------------

/// RFC-1123 风格主机名校验：标签 1~63 字符 [a-zA-Z0-9-]，不以连字符开头/结尾，
/// 总长 ≤253，且至少含一个字母（排除纯数字串，避免与 IP 分支语义混淆）。
pub(crate) fn is_valid_hostname(host: &str) -> bool {
    if host.is_empty() || host.len() > 253 || !host.chars().any(|c| c.is_ascii_alphabetic()) {
        return false;
    }
    host.split('.').all(|label| {
        !label.is_empty()
            && label.len() <= 63
            && label.chars().all(|c| c.is_ascii_alphanumeric() || c == '-')
            && !label.starts_with('-')
            && !label.ends_with('-')
    })
}

/// 解析单条域名条目 `域名[:端口]` → (主机名, 显式端口)。
/// 仅在 IP/CIDR 分支全部失败后调用，因此纯数字/点分形式不会进入本函数。
pub(crate) fn parse_host_entry(s: &str) -> Option<(String, Option<u16>)> {
    let s = s.trim();
    // URL / CIDR / 定量采样后缀都不是域名条目
    if s.is_empty() || s.contains("://") || s.contains('/') || s.contains('=') {
        return None;
    }

    let (host_part, explicit_port) = match s.rsplit_once(':') {
        Some((h, p)) => {
            // 主机段为空或仍含冒号（裸 IPv6 / 畸形输入）一律拒绝
            if h.is_empty() || h.contains(':') {
                return None;
            }
            let port = p.parse::<u16>().ok().filter(|&p| p > 0)?;
            (h, Some(port))
        }
        None => (s, None),
    };

    let host = host_part.strip_suffix('.').unwrap_or(host_part).to_ascii_lowercase();
    is_valid_hostname(&host).then_some((host, explicit_port))
}

/// 从来源列表中拆出域名条目与 IP/CIDR 条目（保持原顺序）
pub(crate) fn split_host_entries(sources: &[String]) -> (Vec<(String, Option<u16>)>, Vec<String>) {
    let mut hosts = Vec::new();
    let mut ips = Vec::new();
    for src in sources {
        match parse_host_entry(src) {
            Some(entry) => hosts.push(entry),
            None => ips.push(src.clone()),
        }
    }
    (hosts, ips)
}

/// 解析域名条目为测试目标（IPv4 优先，单域名上限 50 条记录）。
/// 返回 (目标列表, 解析失败的主机名列表)。
pub(crate) async fn resolve_host_entries(
    entries: Vec<(String, Option<u16>)>,
    fallback_port: u16,
) -> (Vec<Target>, Vec<String>) {
    let mut targets = Vec::new();
    let mut failed = Vec::new();

    for (host, explicit_port) in entries {
        let port = explicit_port.unwrap_or(fallback_port);
        // future 在 match 表达式内完成 await 并丢弃，随后才能 move host
        let mut resolved: Vec<SocketAddr> = match tokio::time::timeout(
            Duration::from_secs(RESOLVE_TIMEOUT_SECS),
            tokio::net::lookup_host((host.as_str(), port)),
        )
        .await
        {
            Ok(Ok(addrs)) => addrs.collect(),
            _ => Vec::new(),
        };

        if resolved.is_empty() {
            failed.push(host);
            continue;
        }

        // IPv4 优先：多数移动网络环境 v6 连通性差，避免浪费测试槽位
        resolved.sort_by_key(|sa| !sa.is_ipv4());
        resolved.truncate(MAX_IPS_PER_HOST);

        let origin: Arc<str> = Arc::from(host.as_str());
        for addr in resolved {
            targets.push(Target { addr, origin: Some(origin.clone()) });
        }
    }

    if !failed.is_empty() {
        warning_println(format_args!(
            "以下域名解析失败，已跳过：{}",
            failed.join(", ")
        ));
    }

    (targets, failed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn host_entry_valid_forms() {
        assert_eq!(
            parse_host_entry("Example.COM"),
            Some(("example.com".to_string(), None))
        );
        assert_eq!(
            parse_host_entry("vip.example.com:2083"),
            Some(("vip.example.com".to_string(), Some(2083)))
        );
        // 尾点容忍 + 归一化小写
        assert_eq!(
            parse_host_entry("Cloudflare.COM."),
            Some(("cloudflare.com".to_string(), None))
        );
        // 子域名多级 + 连字符
        assert_eq!(
            parse_host_entry("my-cdn.xn--bcher-kva.example:8443"),
            Some(("my-cdn.xn--bcher-kva.example".to_string(), Some(8443)))
        );
    }

    #[test]
    fn host_entry_rejects_non_hosts() {
        // URL / CIDR / 采样后缀 / 空串
        assert!(parse_host_entry("https://example.com").is_none());
        assert!(parse_host_entry("1.2.3.0/24").is_none());
        assert!(parse_host_entry("1.2.3.4=50").is_none());
        assert!(parse_host_entry("").is_none());
        // 端口非法
        assert!(parse_host_entry("example.com:0").is_none());
        assert!(parse_host_entry("example.com:99999").is_none());
        assert!(parse_host_entry("example.com:abc").is_none());
        // 裸 IPv6 / 畸形冒号形式
        assert!(parse_host_entry("::1").is_none());
        assert!(parse_host_entry("2001:db8::1").is_none());
        assert!(parse_host_entry("example.com:").is_none());
        // 纯数字/点分（IP 分支语义）与非法字符
        assert!(parse_host_entry("999.1.1.1").is_none());
        assert!(parse_host_entry("*.example.com").is_none());
        assert!(parse_host_entry("exa mple.com").is_none());
        // 标签以连字符开头/结尾
        assert!(parse_host_entry("-abc.example.com").is_none());
        assert!(parse_host_entry("abc-.example.com").is_none());
        // 标签超长（64 字符）
        let long_label = "a".repeat(64);
        assert!(parse_host_entry(&format!("{long_label}.com")).is_none());
        // 总长超限（>253 字符）
        let mut host = String::new();
        for i in 0..6 {
            host.push_str(&"a".repeat(50));
            host.push_str(&i.to_string());
            host.push('.');
        }
        host.push_str("com");
        assert!(host.len() > 253);
        assert!(parse_host_entry(&host).is_none());
    }

    #[test]
    fn split_entries_mixed_sources() {
        let sources = vec![
            "103.21.244.0/22".to_string(),
            "speed.example.com:2053".to_string(),
            "1.2.3.4:8443".to_string(),
            "cache.example.com".to_string(),
        ];
        let (hosts, ips) = split_host_entries(&sources);
        assert_eq!(hosts.len(), 2);
        assert_eq!(hosts[0], ("speed.example.com".to_string(), Some(2053)));
        assert_eq!(hosts[1], ("cache.example.com".to_string(), None));
        assert_eq!(ips, vec!["103.21.244.0/22".to_string(), "1.2.3.4:8443".to_string()]);
    }

    #[test]
    fn process_ip_sources_keeps_ports_and_builds_targets() {
        let mut args = Args::new();
        args.tcp_port = 443;
        let (targets, cidrs, total) = process_ip_sources(
            vec!["1.2.3.4:8443".to_string(), "5.6.7.8".to_string()],
            &args,
        );
        assert_eq!(cidrs.len(), 0);
        assert_eq!(total, 2);
        assert_eq!(targets[0].addr.to_string(), "1.2.3.4:8443");
        assert!(targets[0].origin.is_none());
        assert_eq!(targets[1].addr.to_string(), "5.6.7.8:443", "缺省端口回退 tcp_port");
    }

    #[tokio::test]
    async fn resolve_localhost_expands_with_origin() {
        let (targets, failed) =
            resolve_host_entries(vec![("localhost".to_string(), Some(2053))], 443).await;
        assert!(failed.is_empty(), "localhost 应可在离线环境解析（/etc/hosts）");
        assert!(!targets.is_empty());
        assert!(targets.iter().all(|t| t.addr.port() == 2053));
        assert!(targets.iter().all(|t| t.origin.as_deref() == Some("localhost")));
        // IPv4 优先：第一个目标应为 v4
        assert!(targets[0].addr.is_ipv4());
    }

    #[tokio::test]
    async fn resolve_unreachable_host_reports_failure() {
        // .invalid TLD 由 RFC 2606 保留，任何环境都不应解析成功
        let (_, failed) =
            resolve_host_entries(vec![("nonexistent.invalid".to_string(), None)], 443).await;
        assert_eq!(failed, vec!["nonexistent.invalid".to_string()]);
    }
}