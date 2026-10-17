//! 引擎核心：JSON 配置 → Args → 会话状态机 → runner 线程。
//!
//! 设计要点：
//! - 单个引擎实例 = 一次测速会话（创建 → start → poll 轮询 → take_result → destroy）；
//! - 运行在自己的 std::thread + 独立 tokio runtime 中，与调用方线程完全隔离；
//! - 停止是协作式的：stop() 置位共享的 timeout_flag，ping/download 循环
//!   在现有检查点快速收尾并输出已完成的部分结果；
//! - FFI 边界不 join 运行线程：destroy 时请求停止后直接释放句柄，
//!   运行线程持有 Arc<EngineShared> 自然续命到运行结束，绝不阻塞调用方。

use serde::Deserialize;
use std::any::Any;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use crate::args::Args;
use crate::common::PingData;
use crate::interface::process_interface_param;
use crate::metrics::DownloadMetrics;

/// 会话阶段（poll JSON 的 phase 字段；cfst_engine_phase 的返回值）
pub const PHASE_IDLE: u32 = 0; // idle
pub const PHASE_PING: u32 = 1; // ping
pub const PHASE_DOWNLOAD: u32 = 2; // download
pub const PHASE_DONE: u32 = 3; // done
pub const PHASE_ERROR: u32 = 4; // error

pub fn phase_name(phase: u32) -> &'static str {
    match phase {
        PHASE_IDLE => "idle",
        PHASE_PING => "ping",
        PHASE_DOWNLOAD => "download",
        PHASE_DONE => "done",
        _ => "error",
    }
}

/// 测速配置（对应上游 CLI 参数；snake_case JSON 字段）。
/// 所有数值字段可缺省，缺省值与上游 CLI 默认一致。
#[derive(Debug, Clone, Deserialize)]
#[serde(default)]
pub struct EngineConfig {
    /// 直接指定 IP / CIDR，逗号分隔（与 -ip 等价）
    pub ip_text: String,
    /// IP 列表文件路径（与 -f 等价；Android 沙箱内路径）
    pub ip_file: String,
    /// 测速端口（默认 443）
    pub tcp_port: u16,
    /// "tcp"（默认）| "http" | "icmp"
    pub ping_mode: Option<String>,
    /// ping_mode = "http" 时的探测 URL（缺省 http://cp.cloudflare.com/cdn-cgi/trace）
    pub httping_url: Option<String>,
    /// HTTPing 要求的状态码，如 "200,301"
    pub httping_code: String,
    /// 数据中心过滤，如 "HKG,SJC"
    pub httping_colo: String,
    /// 下载测速 URL（与 -url 等价；disable_download = true 时可缺省）
    pub url: String,
    /// 禁用下载测速（与 -dd 等价）
    pub disable_download: bool,
    /// 单 IP 下载测速时长（秒，1~120，默认 10）
    pub download_time: Option<u64>,
    /// 下载速度下限（MB/s）
    pub min_speed_mb_s: Option<f32>,
    /// 需要达标的结果数量（与 -dn 等价，默认 10）
    pub test_count: Option<usize>,
    /// 达到可用数量后提前结束 Ping（与 -tn 等价）
    pub target_num: Option<usize>,
    /// 结果保留数量上限（默认 10）
    pub print_num: Option<u16>,
    /// 每 IP 延迟测速次数（默认 4）
    pub ping_times: Option<u16>,
    /// 并发上限（1~1024，默认 256；Android 建议 64~256）
    pub max_threads: Option<usize>,
    /// 延迟下限（毫秒，默认 0）
    pub min_delay_ms: Option<u64>,
    /// 延迟上限（毫秒，默认 2000）
    pub max_delay_ms: Option<u64>,
    /// 丢包率上限（0.0~1.0，默认 1.0）
    pub max_loss_rate: Option<f32>,
    /// 全局超时（秒，1~36000；超时后结算已有结果并结束）
    pub global_timeout_secs: Option<u64>,
    /// 测试 CIDR 内全部 IPv4（默认按前缀采样）
    pub test_all_ipv4: bool,
    /// 结果中保留端口（仅影响 CSV/终端输出；JSON 结果始终分离返回 ip/port）
    pub show_port: bool,
    /// 绑定源 IP（等价 -intf <IP>；与 bind_interface 二选一）
    pub bind_ip: Option<String>,
    /// 绑定网络接口名（等价 -intf <名称>；Android 上需要 root 权限，普通 App 仅可用 bind_ip）
    pub bind_interface: Option<String>,
    /// CSV 输出路径（可选；Android 一般不设）
    pub output_csv: Option<String>,
}

impl Default for EngineConfig {
    /// 缺省值与上游 CLI 默认一致（tcp_port 443、disable_download false 等）
    fn default() -> Self {
        Self {
            ip_text: String::new(),
            ip_file: String::new(),
            tcp_port: 443,
            ping_mode: None,
            httping_url: None,
            httping_code: String::new(),
            httping_colo: String::new(),
            url: String::new(),
            disable_download: false,
            download_time: None,
            min_speed_mb_s: None,
            test_count: None,
            target_num: None,
            print_num: None,
            ping_times: None,
            max_threads: None,
            min_delay_ms: None,
            max_delay_ms: None,
            max_loss_rate: None,
            global_timeout_secs: None,
            test_all_ipv4: false,
            show_port: false,
            bind_ip: None,
            bind_interface: None,
            output_csv: None,
        }
    }
}

impl EngineConfig {
    /// 映射为内部 Args 并做校验（与 CLI parse_args 共用 validate）
    pub(crate) fn to_args(&self) -> Result<Args, String> {
        let mut args = Args::new();

        args.ip_text = self.ip_text.trim().to_string();
        args.ip_file = self.ip_file.trim().to_string();
        args.tcp_port = self.tcp_port;
        args.url = self.url.trim().to_string();
        args.disable_download = self.disable_download;
        args.show_port = self.show_port;
        args.test_all_ipv4 = self.test_all_ipv4;
        args.httping_code = self.httping_code.trim().to_string();
        args.httping_cf_colo = self.httping_colo.trim().to_string();

        match self.ping_mode.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
            None | Some("tcp") => {}
            Some("http") => {
                let url = self
                    .httping_url
                    .as_deref()
                    .map(str::trim)
                    .filter(|s| !s.is_empty())
                    .unwrap_or("http://cp.cloudflare.com/cdn-cgi/trace")
                    .to_string();
                args.httping = Some(url);
            }
            #[cfg(feature = "icmp")]
            Some("icmp") => args.icmp_ping = true,
            #[cfg(not(feature = "icmp"))]
            Some("icmp") => {
                return Err("当前编译的引擎未包含 ICMP 支持（需启用 icmp feature，且运行环境具备相应权限）".into())
            }
            Some(other) => return Err(format!("未知 ping_mode: {other}（可选 tcp / http / icmp）")),
        }

        if let Some(secs) = self.download_time {
            args.timeout_duration = Some(Duration::from_secs(secs.clamp(1, 120)));
        }
        if let Some(mb) = self.min_speed_mb_s {
            if !mb.is_finite() || mb < 0.0 {
                return Err("min_speed_mb_s 必须为非负有限数".into());
            }
            args.min_speed_bps = mb * crate::common::BYTES_PER_MB;
        }
        if let Some(v) = self.test_count {
            args.test_count = v.max(1);
        }
        if let Some(v) = self.target_num {
            args.target_num = Some(v.max(1));
        }
        if let Some(v) = self.print_num {
            args.print_num = v.max(1);
        }
        if let Some(v) = self.ping_times {
            args.ping_times = v.max(1);
        }
        if let Some(v) = self.max_threads {
            args.max_threads = v.clamp(1, 1024);
        }
        if let Some(v) = self.min_delay_ms {
            if v > 2000 {
                return Err("min_delay_ms 上限为 2000".into());
            }
            args.min_delay = Duration::from_millis(v);
        }
        if let Some(v) = self.max_delay_ms {
            if v > 2000 {
                return Err("max_delay_ms 上限为 2000".into());
            }
            args.max_delay = Duration::from_millis(v);
        }
        if args.min_delay > args.max_delay {
            return Err("min_delay_ms 不能大于 max_delay_ms".into());
        }
        if let Some(v) = self.max_loss_rate {
            if !(0.0..=1.0).contains(&v) {
                return Err("max_loss_rate 取值范围 0.0 ~ 1.0".into());
            }
            args.max_loss_rate = v;
        }
        if let Some(secs) = self.global_timeout_secs {
            args.global_timeout_duration = Some(Duration::from_secs(secs.clamp(1, 36000)));
        }

        // 网络绑定：bind_ip 与 bind_interface 二选一
        let bind_ip = self.bind_ip.as_deref().map(str::trim).filter(|s| !s.is_empty());
        let bind_if = self.bind_interface.as_deref().map(str::trim).filter(|s| !s.is_empty());
        if bind_ip.is_some() && bind_if.is_some() {
            return Err("bind_ip 与 bind_interface 只能二选一".into());
        }
        if let Some(ip) = bind_ip {
            let cfg = process_interface_param(ip);
            if !cfg.is_valid_interface {
                return Err(format!("无效的 bind_ip: {ip}"));
            }
            args.interface_config = Arc::new(cfg);
        }
        if let Some(name) = bind_if {
            let cfg = process_interface_param(name);
            if !cfg.is_valid_interface {
                return Err(format!("无效的 bind_interface: {name}（Android 上接口名绑定需要 root）"));
            }
            args.interface_config = Arc::new(cfg);
        }

        if let Some(path) = &self.output_csv {
            let path = path.trim();
            if !path.is_empty() {
                args.output = Some(path.to_string());
            }
        }

        args.validate()?;
        Ok(args)
    }
}

/// Ping 阶段进度快照（引用 BasePing 内的原子计数）
pub(crate) struct PingProgress {
    pub(crate) tested: Arc<AtomicUsize>,
    pub(crate) success: Arc<AtomicUsize>,
    pub(crate) total: usize,
}

pub(crate) struct EngineShared {
    pub(crate) args: Args,
    pub(crate) sources: Vec<String>,
    pub(crate) phase: AtomicU32,
    pub(crate) stop: Arc<AtomicBool>,
    pub(crate) error: Mutex<Option<String>>,
    pub(crate) result: Mutex<Option<Vec<PingData>>>,
    #[allow(dead_code)]
    pub(crate) thread: Mutex<Option<std::thread::JoinHandle<()>>>,
    pub(crate) ping: Mutex<Option<PingProgress>>,
    pub(crate) download: Mutex<Option<Arc<DownloadMetrics>>>,
}

/// 一次测速会话。句柄式使用：create → start → poll / take_result → destroy。
pub struct Engine {
    shared: Arc<EngineShared>,
}

impl Engine {
    /// 解析并校验配置，收集 IP 来源。同步、快速（可能读一次 ip_file）。
    pub fn create(config_json: &str) -> Result<Engine, String> {
        let config: EngineConfig = serde_json::from_str(config_json)
            .map_err(|e| format!("配置 JSON 解析失败: {e}"))?;
        let args = config.to_args()?;
        let sources = crate::ip::collect_ip_sources(&args.ip_text, &args.ip_file)?;

        Ok(Engine {
            shared: Arc::new(EngineShared {
                args,
                sources,
                phase: AtomicU32::new(PHASE_IDLE),
                stop: Arc::new(AtomicBool::new(false)),
                error: Mutex::new(None),
                result: Mutex::new(None),
                thread: Mutex::new(None),
                ping: Mutex::new(None),
                download: Mutex::new(None),
            }),
        })
    }

    /// 启动测速。仅 idle 状态可启动（一个引擎实例只跑一次）；成功返回 true。
    pub fn start(&self) -> bool {
        if self
            .shared
            .phase
            .compare_exchange(PHASE_IDLE, PHASE_PING, Ordering::SeqCst, Ordering::SeqCst)
            .is_err()
        {
            return false;
        }

        let s = self.shared.clone();
        let spawned = std::thread::Builder::new()
            .name("cfst-engine".to_string())
            .spawn(move || {
                let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                    run_engine(s.clone())
                }));
                if let Err(panic) = result {
                    s.phase.store(PHASE_ERROR, Ordering::SeqCst);
                    *s.error.lock().unwrap() = Some(format!("引擎线程 panic: {}", panic_message(&panic)));
                }
            });

        match spawned {
            Ok(handle) => {
                *self.shared.thread.lock().unwrap() = Some(handle);
                true
            }
            Err(e) => {
                self.shared.phase.store(PHASE_ERROR, Ordering::SeqCst);
                *self.shared.error.lock().unwrap() = Some(format!("无法启动引擎线程: {e}"));
                false
            }
        }
    }

    /// 请求停止（协作式）：ping/download 循环在下一个检查点收尾，
    /// 已完成的部分结果仍会通过 take_result 返回。
    pub fn stop(&self) {
        self.shared.stop.store(true, Ordering::SeqCst);
    }

    /// 当前阶段（PHASE_* 常量）
    pub fn phase(&self) -> u32 {
        self.shared.phase.load(Ordering::SeqCst)
    }

    /// 进度 JSON（供 poll；结构见 README）
    pub fn progress_json(&self) -> String {
        let shared = &self.shared;
        let mut obj = serde_json::json!({
            "phase": phase_name(shared.phase.load(Ordering::SeqCst)),
            "stop_requested": shared.stop.load(Ordering::SeqCst),
        });

        if let Some(p) = &*shared.ping.lock().unwrap() {
            obj["ping"] = serde_json::json!({
                "tested": p.tested.load(Ordering::Relaxed),
                "success": p.success.load(Ordering::Relaxed),
                "total": p.total,
            });
        }

        if let Some(m) = &*shared.download.lock().unwrap() {
            let speed = m.speed_bps();
            obj["download"] = serde_json::json!({
                "tested": m.tested.load(Ordering::Relaxed),
                "qualified": m.qualified.load(Ordering::Relaxed),
                "total": m.total.load(Ordering::Relaxed),
                "speed_mbps": (speed / crate::common::BYTES_PER_MB) as f64,
            });
        }

        if let Some(e) = &*shared.error.lock().unwrap() {
            obj["message"] = serde_json::json!(e);
        }

        obj.to_string()
    }

    /// 结果 JSON（一次性）：仅 done 阶段首次调用返回，之后返回 None。
    pub fn result_json(&self) -> Option<String> {
        if self.shared.phase.load(Ordering::SeqCst) != PHASE_DONE {
            return None;
        }

        let mut guard = self.shared.result.lock().unwrap();
        let items = guard.take()?;
        let arr: Vec<serde_json::Value> = items
            .iter()
            .map(|d| {
                serde_json::json!({
                    "ip": d.addr.ip().to_string(),
                    "port": d.addr.port(),
                    "sent": d.sent,
                    "received": d.received,
                    "loss_rate": d.loss_rate() as f64,
                    "delay_ms": d.delay as f64,
                    "download_speed_mbps": d
                        .download_speed
                        .map(|s| (s / crate::common::BYTES_PER_MB) as f64),
                    "colo": d
                        .data_center
                        .as_ref()
                        .map(|c| crate::common::colo_bytes_to_str(c).to_string()),
                })
            })
            .collect();

        Some(serde_json::json!({ "count": arr.len(), "items": arr }).to_string())
    }
}

/// 提取 panic 载荷信息
pub(crate) fn panic_message(p: &Box<dyn Any + Send>) -> String {
    if let Some(s) = p.downcast_ref::<&str>() {
        (*s).to_string()
    } else if let Some(s) = p.downcast_ref::<String>() {
        s.clone()
    } else {
        "unknown panic".to_string()
    }
}

fn signal_error(shared: &EngineShared, msg: String) {
    shared.phase.store(PHASE_ERROR, Ordering::SeqCst);
    *shared.error.lock().unwrap() = Some(msg);
}

/// 引擎主流程（在专属线程的专属 tokio runtime 上执行）
fn run_engine(shared: Arc<EngineShared>) {
    let args = Arc::new(shared.args.clone());
    let stop = shared.stop.clone();

    let rt = match tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
    {
        Ok(rt) => rt,
        Err(e) => {
            signal_error(&shared, format!("创建 tokio runtime 失败: {e}"));
            return;
        }
    };

    let results = rt.block_on(run_async(&args, &shared, stop));
    // block_on 返回后 runtime 随之丢弃（后台残留任务被取消）

    if shared.phase.load(Ordering::SeqCst) != PHASE_ERROR {
        // 可选 CSV 导出（output_csv 配置；Android 上一般不设）
        if !results.is_empty() && let Some(output) = &shared.args.output {
            if let Err(e) = crate::csv::export_csv(&results, &shared.args) {
                eprintln!("\x1b[33;1m[警告]\x1b[0m 导出 CSV 失败: {e:?}");
            } else {
                println!("\x1b[36;1m[信息]\x1b[0m 测速结果已写入 {output}");
            }
        }

        *shared.result.lock().unwrap() = Some(results);
        shared.phase.store(PHASE_DONE, Ordering::SeqCst);
    }
}

async fn run_async(
    args: &Arc<Args>,
    shared: &Arc<EngineShared>,
    stop: Arc<AtomicBool>,
) -> Vec<PingData> {
    // 全局超时：到时置位 stop，主流程自行结算退出
    if let Some(timeout) = args.global_timeout_duration {
        let stop2 = stop.clone();
        tokio::spawn(async move {
            tokio::time::sleep(timeout).await;
            stop2.store(true, Ordering::SeqCst);
        });
    }

    let base = crate::common::create_base_ping(args.clone(), shared.sources.clone(), stop.clone());
    {
        let mut slot = shared.ping.lock().unwrap();
        *slot = Some(PingProgress {
            tested: base.tested_count.clone(),
            success: base.success_count.clone(),
            total: base.ip_buffer.total_expected(),
        });
    }

    let ping = if args.httping.is_some() {
        crate::httping::new(base)
    } else {
        #[cfg(feature = "icmp")]
        if args.icmp_ping {
            crate::icmp::new(base)
        } else {
            Some(crate::tcping::new(base))
        }
        #[cfg(not(feature = "icmp"))]
        Some(crate::tcping::new(base))
    };

    let Some(ping) = ping else {
        signal_error(shared, "创建测速实例失败".to_string());
        return Vec::new();
    };

    let ping_result: Vec<PingData> = ping.run().await;
    let ping_interrupted = stop.load(Ordering::SeqCst);

    if args.disable_download || ping_result.is_empty() || ping_interrupted {
        return ping_result;
    }

    shared.phase.store(PHASE_DOWNLOAD, Ordering::SeqCst);
    let metrics = Arc::new(DownloadMetrics::default());
    {
        let mut slot = shared.download.lock().unwrap();
        *slot = Some(metrics.clone());
    }
    let mut test = crate::download::DownloadTest::new(args, ping_result, stop.clone(), Some(metrics)).await;
    test.test_download_speed().await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn config_minimal_ok() {
        let cfg = r#"{"ip_text":"1.1.1.1/32","disable_download":true}"#;
        let args = serde_json::from_str::<EngineConfig>(cfg)
            .unwrap()
            .to_args()
            .unwrap();
        assert_eq!(args.tcp_port, 443);
        assert_eq!(args.ping_times, 4);
        assert_eq!(args.max_threads, 256);
        assert!(args.disable_download);
        assert!(args.output.is_none(), "库模式默认不写 CSV");
    }

    #[test]
    fn config_missing_ip_source_rejected() {
        let cfg = r#"{"disable_download":true}"#;
        let err = serde_json::from_str::<EngineConfig>(cfg)
            .unwrap()
            .to_args()
            .unwrap_err();
        assert!(err.contains("IP 来源"), "actual: {err}");
    }

    #[test]
    fn config_download_requires_url() {
        let cfg = r#"{"ip_text":"1.1.1.1"}"#;
        let err = serde_json::from_str::<EngineConfig>(cfg)
            .unwrap()
            .to_args()
            .unwrap_err();
        assert!(err.contains("url"), "actual: {err}");
    }

    #[test]
    fn config_unknown_ping_mode_rejected() {
        let cfg = r#"{"ip_text":"1.1.1.1","disable_download":true,"ping_mode":"udp"}"#;
        let err = serde_json::from_str::<EngineConfig>(cfg)
            .unwrap()
            .to_args()
            .unwrap_err();
        assert!(err.contains("ping_mode"), "actual: {err}");
    }

    #[test]
    fn config_clamps_and_converts() {
        let cfg = r#"{
            "ip_text":"1.1.1.1","disable_download":true,
            "max_threads":99999,"download_time":999,"min_speed_mb_s":2.5,
            "min_delay_ms":300,"max_delay_ms":200,"max_loss_rate":2.0
        }"#;
        let err = serde_json::from_str::<EngineConfig>(cfg)
            .unwrap()
            .to_args()
            .unwrap_err();
        // min > max 直接报错，而不是静默钳制
        assert!(err.contains("min_delay_ms"), "actual: {err}");

        let cfg_ok = r#"{
            "ip_text":"1.1.1.1","disable_download":true,
            "max_threads":99999,"download_time":999,"min_speed_mb_s":2.5,
            "min_delay_ms":10,"max_delay_ms":300,"max_loss_rate":0.5
        }"#;
        let args = serde_json::from_str::<EngineConfig>(cfg_ok)
            .unwrap()
            .to_args()
            .unwrap();
        assert_eq!(args.max_threads, 1024, "max_threads 钳制到 1024");
        assert_eq!(args.timeout_duration, Some(Duration::from_secs(120)));
        assert!((args.min_speed_bps - 2.5 * crate::common::BYTES_PER_MB).abs() < 1.0);
        assert_eq!(args.max_delay, Duration::from_millis(300));
        assert_eq!(args.max_loss_rate, 0.5);
    }

    #[cfg(not(feature = "icmp"))]
    #[test]
    fn config_icmp_without_feature_rejected() {
        let cfg = r#"{"ip_text":"1.1.1.1","disable_download":true,"ping_mode":"icmp"}"#;
        let err = serde_json::from_str::<EngineConfig>(cfg)
            .unwrap()
            .to_args()
            .unwrap_err();
        assert!(err.contains("ICMP"), "actual: {err}");
    }
}
