//! CFSTMobile 测速引擎（Rust 端）。
//!
//! 改造自 [GuangYu-yu/CloudflareST-Rust](https://github.com/GuangYu-yu/CloudflareST-Rust)
//! （其本身是 XIU2/CloudflareSpeedTest 的 Rust 重写），GPL-3.0 授权。
//!
//! ## 面向 Android（Kotlin）
//! 构建 `cdylib`（默认 feature 即可），产出 `libcfst.so`，Kotlin 侧通过
//! `cfst_*` C ABI 函数族驱动一次测速会话（见 `ffi` 模块与 README）。
//!
//! ## 面向桌面 CLI
//! `cargo build --features cli` 额外产出与上游交互形态一致的 `cfst` 命令行。
//!
//! ## 模块划分
//! - `ffi` / `engine`：库模式入口（本crate新增）
//! - `args` / `main`：配置与 CLI（上游逻辑，CLI 部分按 feature 门控）
//! - 其余模块为上游核心算法（IP 生成 / ping / 下载 / 排序），仅做库化解耦

pub mod engine;
pub mod ffi;

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
