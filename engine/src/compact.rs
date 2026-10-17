//! IPv4 地址库精简（对齐 CFData-WEB `tasks_compact.go` 的行为）。
//!
//! 算法：
//! 1. 解析地址库内容（CIDR / 裸 IPv4，`#` 注释跳过），展开为唯一 `/24` 子网集合；
//! 2. 每轮为每个未确认子网抽取一个未用过的末八位组生成探测 IP，
//!    以 TCP 连接（默认 `:80`、1s 超时）验证子网活跃性；
//! 3. 连续 `max_empty_rounds` 轮（默认 2）无新发现即提前收敛；
//! 4. 保留全部确认活跃的 `/24` 子网，按数值序输出（一行一个 `a.b.c.0/24`）。
//!
//! 会话形态与 [`crate::engine::Engine`] 一致：create → start → poll →
//! take_result → destroy，运行在专属 std::thread（纯阻塞 IO，无需 tokio）。

use serde::Deserialize;
use std::collections::{BTreeSet, VecDeque};
use std::net::{Ipv4Addr, SocketAddr, TcpStream};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

/// 会话阶段（poll JSON 的 phase 字段；cfst_compact_phase 的返回值）
pub const COMPACT_IDLE: u32 = 0;
pub const COMPACT_RUNNING: u32 = 1;
pub const COMPACT_DONE: u32 = 2;
pub const COMPACT_ERROR: u32 = 3;

fn phase_name(phase: u32) -> &'static str {
    match phase {
        COMPACT_IDLE => "idle",
        COMPACT_RUNNING => "running",
        COMPACT_DONE => "done",
        _ => "error",
    }
}

/// 单条 CIDR 最多展开的 /24 子网数（对齐 CFData maxIPv4SubnetExpansion）
const MAX_SUBNET_EXPANSION: u32 = 1 << 16;
/// poll 携带的日志上限（近期优先）
const LOG_TAIL: usize = 40;
/// 日志环形缓冲上限
const LOG_CAP: usize = 500;

/// 精简配置（JSON 载荷；snake_case，全部可缺省）
#[derive(Debug, Clone, Deserialize)]
#[serde(default)]
pub struct CompactConfig {
    /// 地址库内容（每行一个 CIDR 或裸 IPv4；`#` 注释行与空行跳过）
    pub ip_text: String,
    /// 并发探测线程数（1~512，默认 100）
    pub threads: Option<usize>,
    /// 单次 TCP 连接超时（毫秒，100~10000，默认 1000）
    pub connect_timeout_ms: Option<u64>,
    /// 探测端口（默认 80）
    pub port: Option<u16>,
    /// 连续无新发现的最大轮数（默认 2，至少 1）
    pub max_empty_rounds: Option<usize>,
}

impl Default for CompactConfig {
    fn default() -> Self {
        Self {
            ip_text: String::new(),
            threads: None,
            connect_timeout_ms: None,
            port: None,
            max_empty_rounds: None,
        }
    }
}

#[derive(Debug, Clone)]
struct CompactParams {
    threads: usize,
    timeout: Duration,
    port: u16,
    max_empty_rounds: usize,
}

impl CompactParams {
    fn from_config(cfg: CompactConfig) -> Result<Self, String> {
        if cfg.ip_text.trim().is_empty() {
            return Err("ip_text 为空，无法精简".to_string());
        }
        let port = cfg.port.unwrap_or(80);
        if port == 0 {
            return Err("port 不能为 0".to_string());
        }
        Ok(Self {
            threads: cfg.threads.unwrap_or(100).clamp(1, 512),
            timeout: Duration::from_millis(cfg.connect_timeout_ms.unwrap_or(1000).clamp(100, 10_000)),
            port,
            max_empty_rounds: cfg.max_empty_rounds.unwrap_or(2).max(1),
        })
    }
}

/// 单个 /24 子网的探测状态
struct SubnetState {
    /// /24 网络号（主机位为 0 的 u32）
    net: u32,
    found: bool,
    /// 已试过的末八位组（跨轮不重复，试满 256 个即放弃该子网）
    used: Box<[bool; 256]>,
    /// xorshift32 状态：起点随子网与轮次变化，模拟 CFData 的随机抽样
    rng: u32,
}

impl SubnetState {
    fn new(net: u32) -> Self {
        Self {
            net,
            found: false,
            used: Box::new([false; 256]),
            rng: net ^ 0x9E37_79B9,
        }
    }

    fn exhausted(&self) -> bool {
        self.used.iter().all(|&u| u)
    }

    /// 抽取本子网本轮的探测 IP（返回完整 u32 地址）
    fn next_ip(&mut self, round: usize) -> u32 {
        let mut x = self.rng ^ (round as u32).wrapping_mul(0x85EB_CA6B);
        x ^= x << 13;
        x ^= x >> 17;
        x ^= x << 5;
        self.rng = x;
        let mut octet = (x & 0xFF) as usize;
        while self.used[octet] {
            octet = (octet + 1) & 0xFF;
        }
        self.used[octet] = true;
        self.net | octet as u32
    }
}

#[derive(Default, Clone)]
struct ProgressSnapshot {
    pass: usize,
    current: usize,
    total: usize,
    found: usize,
    status: String,
}

struct CompactShared {
    params: CompactParams,
    /// 全部候选 /24 网络号（升序）
    subnets: Vec<u32>,
    phase: AtomicU32,
    stop: Arc<AtomicBool>,
    progress: Mutex<ProgressSnapshot>,
    logs: Mutex<VecDeque<String>>,
    /// (保留子网数, 精简内容)
    result: Mutex<Option<(usize, String)>>,
    error: Mutex<Option<String>>,
    thread: Mutex<Option<std::thread::JoinHandle<()>>>,
}

/// 一次地址库精简会话。句柄式使用：create → start → poll / take_result → destroy。
pub struct CompactSession {
    shared: Arc<CompactShared>,
}

impl std::fmt::Debug for CompactSession {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("CompactSession")
            .field("phase", &self.phase())
            .finish_non_exhaustive()
    }
}

impl CompactSession {
    /// 解析配置并展开 /24 子网集合。同步、快速（纯 CPU 解析）。
    pub fn create(config_json: &str) -> Result<CompactSession, String> {
        let config: CompactConfig = serde_json::from_str(config_json)
            .map_err(|e| format!("精简配置 JSON 解析失败: {e}"))?;
        let params = CompactParams::from_config(config.clone())?;

        let mut subnets: BTreeSet<u32> = BTreeSet::new();
        expand_all_lines(&config.ip_text, &mut subnets);
        if subnets.is_empty() {
            return Err("未识别到可精简的 /24 子网条目".to_string());
        }

        Ok(CompactSession {
            shared: Arc::new(CompactShared {
                params,
                subnets: subnets.into_iter().collect(),
                phase: AtomicU32::new(COMPACT_IDLE),
                stop: Arc::new(AtomicBool::new(false)),
                progress: Mutex::new(ProgressSnapshot::default()),
                logs: Mutex::new(VecDeque::new()),
                result: Mutex::new(None),
                error: Mutex::new(None),
                thread: Mutex::new(None),
            }),
        })
    }

    /// 候选子网总数（UI 展示用）
    pub fn subnet_count(&self) -> usize {
        self.shared.subnets.len()
    }

    /// 启动精简。仅 idle 状态可启动；成功返回 true。
    pub fn start(&self) -> bool {
        if self
            .shared
            .phase
            .compare_exchange(COMPACT_IDLE, COMPACT_RUNNING, Ordering::SeqCst, Ordering::SeqCst)
            .is_err()
        {
            return false;
        }

        let s = self.shared.clone();
        let spawned = std::thread::Builder::new()
            .name("cfst-compact".to_string())
            .spawn(move || {
                let result = catch_unwind(AssertUnwindSafe(|| run_compact(s.clone())));
                if let Err(panic) = result {
                    s.phase.store(COMPACT_ERROR, Ordering::SeqCst);
                    *s.error.lock().unwrap() =
                        Some(format!("精简线程 panic: {}", crate::engine::panic_message(&panic)));
                }
            });

        match spawned {
            Ok(handle) => {
                *self.shared.thread.lock().unwrap() = Some(handle);
                true
            }
            Err(e) => {
                self.shared.phase.store(COMPACT_ERROR, Ordering::SeqCst);
                *self.shared.error.lock().unwrap() = Some(format!("无法启动精简线程: {e}"));
                false
            }
        }
    }

    /// 请求停止（协作式）：探测线程与轮间检查点收尾。
    /// 已保留的子网仍作为结果返回（与 CFData「精简已终止」行为一致）。
    pub fn stop(&self) {
        self.shared.stop.store(true, Ordering::SeqCst);
    }

    pub fn phase(&self) -> u32 {
        self.shared.phase.load(Ordering::SeqCst)
    }

    /// 进度 JSON（供 poll；字段对齐 CFData compact_ipv4_progress）
    pub fn progress_json(&self) -> String {
        let shared = &self.shared;
        let snap = shared.progress.lock().unwrap().clone();
        let percent = if snap.total > 0 {
            snap.current as f64 / snap.total as f64 * 100.0
        } else {
            0.0
        };

        let mut obj = serde_json::json!({
            "phase": phase_name(shared.phase.load(Ordering::SeqCst)),
            "stop_requested": shared.stop.load(Ordering::SeqCst),
            "pass": snap.pass,
            "current": snap.current,
            "total": snap.total,
            "found": snap.found,
            "percent": format!("{:.2}", percent),
            "status": snap.status,
            "logs": logs_tail(shared),
        });

        if let Some(e) = &*shared.error.lock().unwrap() {
            obj["message"] = serde_json::json!(e);
        }

        obj.to_string()
    }

    /// 结果 JSON（一次性）：仅 done 阶段首次调用返回，之后返回 None。
    pub fn result_json(&self) -> Option<String> {
        if self.shared.phase.load(Ordering::SeqCst) != COMPACT_DONE {
            return None;
        }
        let mut guard = self.shared.result.lock().unwrap();
        let (count, content) = guard.take()?;
        Some(
            serde_json::json!({
                "count": count,
                "content": content,
                "file": "ips-v4.txt",
            })
            .to_string(),
        )
    }
}

/// 展开全部输入行为 /24 网络号集合
fn expand_all_lines(content: &str, out: &mut BTreeSet<u32>) {
    for line in content.lines() {
        expand_line_to_24s(line, out);
    }
}

/// 单行展开：
/// - `a.b.c.d/p`：p >= 24 → 包含它的唯一 /24；p < 24 → 展开全部 /24（上限 65536）；
/// - 裸 IPv4 → 其所在 /24；
/// - 域名 / 非法 / 注释行忽略；容忍 `IP:端口`、`IP 端口` 后缀与行内注释。
fn expand_line_to_24s(line: &str, out: &mut BTreeSet<u32>) {
    let raw = line.trim();
    if raw.is_empty() || raw.starts_with('#') {
        return;
    }
    // 取首个空白分隔 token（容忍 "1.2.3.0/24 443" 类端口后缀），再去行内注释
    let token = raw.split_whitespace().next().unwrap_or("");
    let token = token.split('#').next().unwrap_or("").trim();
    if token.is_empty() {
        return;
    }
    // 剥离裸 IPv4 的 ":端口" 后缀（CIDR 不可能带冒号）
    let (ip_part, prefix) = match token.split_once('/') {
        Some((ip, p)) => match p.trim().parse::<u8>() {
            Ok(p) => (ip, p),
            Err(_) => return,
        },
        None => {
            let bare = token.split(':').next().unwrap_or(token);
            (bare, 32u8)
        }
    };
    let Ok(ip) = ip_part.parse::<Ipv4Addr>() else {
        return;
    };
    let bits = u32::from(ip);

    if prefix >= 24 {
        // /24~/32：包含它的唯一 /24（对齐 Go expandCIDRTo24s 的 ones>=24 分支）
        out.insert(bits & 0xFFFF_FF00);
        return;
    }

    let shift = (24 - prefix as u32).min(16);
    let host_bits = 32 - prefix as u32;
    let mask = if host_bits >= 32 { 0 } else { !((1u32 << host_bits) - 1) };
    let network = bits & mask;
    let count = 1u32 << shift;
    for i in 0..count.min(MAX_SUBNET_EXPANSION) {
        out.insert(network + i * 256);
    }
}

/// 数值序输出（对齐 Go sortedCompactSubnets 的按八位组数值比较）
fn subnet_line(net: u32) -> String {
    let ip = Ipv4Addr::from(net & 0xFFFF_FF00);
    format!("{ip}/24")
}

fn push_log(shared: &CompactShared, msg: impl Into<String>) {
    let mut logs = shared.logs.lock().unwrap();
    if logs.len() >= LOG_CAP {
        logs.pop_front();
    }
    logs.push_back(msg.into());
}

fn logs_tail(shared: &CompactShared) -> Vec<String> {
    let logs = shared.logs.lock().unwrap();
    let skip = logs.len().saturating_sub(LOG_TAIL);
    logs.iter().skip(skip).cloned().collect()
}

fn set_progress(shared: &CompactShared, pass: usize, current: usize, total: usize, found: usize, status: &str) {
    let mut p = shared.progress.lock().unwrap();
    p.pass = pass;
    p.current = current;
    p.total = total;
    p.found = found;
    p.status = status.to_string();
}

/// 单轮扫描：为每个待测子网各派生一个探测 IP，按并发上限做 TCP 连通性测试。
/// 返回本轮确认活跃的 /24 网络号（升序去重）。
fn scan_round(shared: &Arc<CompactShared>, pass: usize, ips: &[(u32, usize)]) -> Vec<u32> {
    let total = ips.len();
    let next = Arc::new(AtomicUsize::new(0));
    let done = Arc::new(AtomicUsize::new(0));
    let hits: Mutex<Vec<u32>> = Mutex::new(Vec::new());
    let found_base = shared.progress.lock().unwrap().found;
    let status = format!("精简第 {pass} 轮");
    let workers = shared.params.threads.min(total).max(1);

    std::thread::scope(|scope| {
        for _ in 0..workers {
            let next = next.clone();
            let done = done.clone();
            let hits = &hits;
            let shared = shared.clone();
            let status = status.clone();
            scope.spawn(move || loop {
                if shared.stop.load(Ordering::SeqCst) {
                    return;
                }
                let i = next.fetch_add(1, Ordering::SeqCst);
                if i >= total {
                    return;
                }
                let (ip, _) = ips[i];
                let addr = SocketAddr::from((Ipv4Addr::from(ip), shared.params.port));
                if TcpStream::connect_timeout(&addr, shared.params.timeout).is_ok() {
                    hits.lock().unwrap().push(ip);
                }
                let current = done.fetch_add(1, Ordering::SeqCst) + 1;
                let found_now = found_base + hits.lock().unwrap().len();
                set_progress(&shared, pass, current, total, found_now, &status);
            });
        }
    });

    let mut v = hits.into_inner().unwrap_or_default();
    v.sort_unstable();
    v.dedup();
    v
}

/// 精简主流程（在专属线程上执行）
fn run_compact(shared: Arc<CompactShared>) {
    push_log(&shared, "开始精简本地 IPv4 地址库...");
    push_log(&shared, format!("候选 /24 子网共 {} 个", shared.subnets.len()));

    let mut states: Vec<SubnetState> = shared.subnets.iter().map(|&net| SubnetState::new(net)).collect();
    let mut valid: BTreeSet<u32> = BTreeSet::new();
    let mut empty_rounds = 0usize;
    let mut pass = 0usize;
    let mut canceled = false;

    loop {
        if shared.stop.load(Ordering::SeqCst) {
            canceled = true;
            break;
        }

        let pending: Vec<usize> = states
            .iter()
            .enumerate()
            .filter(|(_, s)| !s.found && !s.exhausted())
            .map(|(i, _)| i)
            .collect();
        if pending.is_empty() {
            break;
        }

        pass += 1;
        let ips: Vec<(u32, usize)> = pending.iter().map(|&i| (states[i].next_ip(pass), i)).collect();
        push_log(&shared, format!("精简第 {pass} 轮开始，本轮待测 {} 个子网", ips.len()));
        set_progress(&shared, pass, 0, ips.len(), valid.len(), &format!("精简第 {pass} 轮"));

        let found_ips = scan_round(&shared, pass, &ips);
        if shared.stop.load(Ordering::SeqCst) {
            canceled = true;
        }

        if found_ips.is_empty() {
            empty_rounds += 1;
            push_log(&shared, format!("第 {pass} 轮未发现有效子网"));
            if empty_rounds >= shared.params.max_empty_rounds {
                push_log(
                    &shared,
                    format!("连续 {} 轮未发现新子网，停止精简", shared.params.max_empty_rounds),
                );
                break;
            }
        } else {
            empty_rounds = 0;
            for net in found_ips {
                let subnet = net & 0xFFFF_FF00;
                // states 与 shared.subnets 同序（升序），二分定位
                if let Ok(idx) = states.binary_search_by_key(&subnet, |s| s.net) {
                    states[idx].found = true;
                }
                valid.insert(subnet);
            }
            push_log(&shared, format!("第 {pass} 轮发现 {} 个有效子网", valid.len()));
        }
        push_log(&shared, format!("第 {pass} 轮结束，累计保留 {} 个子网", valid.len()));
    }

    let status = if canceled { "精简已终止" } else { "精简完成" };
    set_progress(&shared, pass, valid.len(), valid.len().max(1), valid.len(), status);
    push_log(
        &shared,
        format!("精简完成：保留 {} 个 /24 子网", valid.len()),
    );

    if valid.is_empty() {
        shared.phase.store(COMPACT_ERROR, Ordering::SeqCst);
        *shared.error.lock().unwrap() =
            Some("未发现任何可保留的 IPv4 子网，已保留原始地址库".to_string());
        return;
    }

    let content: String = valid
        .iter()
        .map(|&net| subnet_line(net))
        .collect::<Vec<_>>()
        .join("\n")
        + "\n";

    *shared.result.lock().unwrap() = Some((valid.len(), content));
    shared.phase.store(COMPACT_DONE, Ordering::SeqCst);
}

#[cfg(test)]
mod tests {
    use super::*;

    fn expand(lines: &[&str]) -> Vec<u32> {
        let mut set = BTreeSet::new();
        for l in lines {
            expand_line_to_24s(l, &mut set);
        }
        set.into_iter().collect()
    }

    #[test]
    fn expand_bare_ip_to_containing_24() {
        assert_eq!(expand(&["1.2.3.4"]), vec![u32::from(Ipv4Addr::new(1, 2, 3, 0))]);
    }

    #[test]
    fn expand_exact_24_stays_single() {
        let nets = expand(&["104.16.0.0/24"]);
        assert_eq!(nets.len(), 1);
        assert_eq!(nets[0], u32::from(Ipv4Addr::new(104, 16, 0, 0)));
    }

    #[test]
    fn expand_supernet_to_all_24s() {
        // /22 → 4 个 /24
        let nets = expand(&["103.21.244.0/22"]);
        assert_eq!(nets.len(), 4);
        assert_eq!(nets[0], u32::from(Ipv4Addr::new(103, 21, 244, 0)));
        assert_eq!(nets[3], u32::from(Ipv4Addr::new(103, 21, 247, 0)));
    }

    #[test]
    fn expand_dedups_across_entries() {
        let nets = expand(&["1.2.3.0/24", "1.2.3.4", "1.2.3.200/25"]);
        assert_eq!(nets.len(), 1, "同一 /24 只保留一份");
    }

    #[test]
    fn expand_ignores_invalid_and_comments() {
        assert!(expand(&["# comment", "", "example.com", "999.1.2.3"]).is_empty());
    }

    #[test]
    fn expand_tolerates_port_suffix_and_inline_comment() {
        let nets = expand(&["1.2.3.4:443", "5.6.7.8 8080", "9.10.11.12/24 # tail"]);
        assert_eq!(nets.len(), 3);
    }

    #[test]
    fn expand_prefix_above_24_single_subnet() {
        let nets = expand(&["1.2.3.128/25"]);
        assert_eq!(nets.len(), 1);
        assert_eq!(nets[0], u32::from(Ipv4Addr::new(1, 2, 3, 0)));
    }

    #[test]
    fn subnet_line_numeric_format() {
        assert_eq!(subnet_line(u32::from(Ipv4Addr::new(8, 8, 8, 8))), "8.8.8.0/24");
    }

    #[test]
    fn create_rejects_empty_content() {
        let err = CompactSession::create(r#"{"ip_text":""}"#).unwrap_err();
        assert!(err.contains("ip_text"), "actual: {err}");
    }

    #[test]
    fn create_rejects_entries_without_subnet() {
        let err = CompactSession::create(r#"{"ip_text":"example.com\n#x"}"#).unwrap_err();
        assert!(err.contains("未识别到"), "actual: {err}");
    }

    #[test]
    fn subnet_state_never_repeats_octet() {
        let mut s = SubnetState::new(u32::from(Ipv4Addr::new(1, 2, 3, 0)));
        let mut seen = std::collections::HashSet::new();
        for round in 1..=256usize {
            let ip = s.next_ip(round);
            let octet = (ip & 0xFF) as usize;
            assert!(seen.insert(octet), "八位组 {octet} 重复");
            assert_eq!(ip & 0xFFFF_FF00, u32::from(Ipv4Addr::new(1, 2, 3, 0)));
        }
        assert!(s.exhausted());
    }

    #[test]
    fn params_clamped() {
        let cfg = serde_json::from_str::<CompactConfig>(r#"{"ip_text":"1.2.3.4","threads":99999,"connect_timeout_ms":1,"port":443,"max_empty_rounds":0}"#).unwrap();
        let p = CompactParams::from_config(cfg).unwrap();
        assert_eq!(p.threads, 512);
        assert_eq!(p.timeout, Duration::from_millis(100));
        assert_eq!(p.port, 443);
        assert_eq!(p.max_empty_rounds, 1);
    }
}
