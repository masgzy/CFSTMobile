//! CFSTMobile 桌面 CLI 入口（与上游 CloudflareST-Rust 交互形态一致）。
//!
//! 仅在 `--features cli` 下构建；Android 端只使用 cdylib 库（src/lib.rs）。
//! 所有模块通过 #[path] 直接并入 bin crate，与 lib crate 平行编译。

use mimalloc::MiMalloc;

#[global_allocator]
static GLOBAL: MiMalloc = MiMalloc;

use crate::args::Args;
use crate::common::{Ping as CommonPing, PingData, BasePing};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

mod args;
mod common;
mod csv;
mod download;
mod hyper;
#[cfg(feature = "icmp")]
mod icmp;
mod interface;
mod ip;
mod log;
mod metrics;
mod pool;
mod progress;
mod tcping;
mod httping;

use crate::log::{error_and_exit, info_println};

/// 构建 ping 实例（CLI 用）：BasePing 在此统一创建，便于以后接进度指标
async fn create_ping(args: Arc<Args>, sources: Vec<String>, timeout_flag: Arc<AtomicBool>) -> Option<(CommonPing, BasePing)> {
    let base = common::create_base_ping(args.clone(), sources, timeout_flag).await;
    let ping = if let Some(_) = args.httping {
        httping::new(base.clone())?
    } else {
        #[cfg(feature = "icmp")]
        if args.icmp_ping {
            icmp::new(base.clone())?
        } else {
            tcping::new(base.clone())
        }
        #[cfg(not(feature = "icmp"))]
        tcping::new(base.clone())
    };
    Some((ping, base))
}

#[tokio::main]
async fn main() {
    println!("\x1b[34;1m# CloudflareST-Rust (CFSTMobile)\x1b[0m");

    let args = Arc::new(args::parse_args());

    let sources = match ip::collect_ip_sources(&args.ip_text, &args.ip_file) {
        Ok(s) => s,
        Err(e) => error_and_exit(format_args!("{e}")),
    };

    let timeout_flag = Arc::new(AtomicBool::new(false));

    if let Some(timeout) = args.global_timeout_duration {
        info_println(format_args!(
            "程序执行时间超过 {timeout:?} 后，将提前结算结果并退出"
        ));
        let timeout_flag_clone = timeout_flag.clone();
        // fire-and-forget：超时后置标志位，主循环自行检测退出
        tokio::spawn(async move {
            tokio::time::sleep(timeout).await;
            timeout_flag_clone.store(true, Ordering::SeqCst);
        });
    }

    let Some((ping, _base)) = create_ping(args.clone(), sources, timeout_flag.clone()).await else {
        error_and_exit(format_args!("创建测速实例失败"));
    };
    let ping_result: Vec<PingData> = ping.run().await;

    let ping_interrupted = timeout_flag.load(Ordering::SeqCst);

    let ping_data = if args.disable_download || ping_result.is_empty() || ping_interrupted {
        let reason = if args.disable_download {
            "已禁用下载测速"
        } else if ping_interrupted {
            "由于全局超时，跳过下载测速"
        } else {
            "延迟测速结果为空，跳过下载测速"
        };
        info_println(format_args!("{reason}"));
        ping_result
    } else {
        let mut download_test = download::DownloadTest::new(&args, ping_result, timeout_flag.clone(), None).await;
        download_test.test_download_speed().await
    };

    csv::print_results(&ping_data, &args);

    if let Some(output_file) = &args.output && !ping_data.is_empty() {
        match csv::export_csv(&ping_data, &args) {
            Ok(_) => info_println(format_args!("测速结果已写入 {output_file} 文件，可使用记事本/表格软件查看")),
            Err(e) => info_println(format_args!("导出 CSV 失败: {e:?}")),
        }
    }

    info_println(format_args!("CloudflareST-Rust 执行完毕"));
}
