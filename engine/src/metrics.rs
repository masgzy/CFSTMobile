//! 下载测速阶段的结构化进度指标。
//!
//! CLI 模式不注入（进度走终端 Bar）；库模式由引擎持有 Arc，
//! FFI poll 时读取并序列化为 JSON。

use std::sync::atomic::{AtomicU32, AtomicUsize};

#[derive(Debug, Default)]
pub(crate) struct DownloadMetrics {
    /// 已完成下载测速的 IP 数
    pub(crate) tested: AtomicUsize,
    /// 达标（速度/地区符合要求）的 IP 数
    pub(crate) qualified: AtomicUsize,
    /// 本轮参与下载测速的队列总长
    pub(crate) total: AtomicUsize,
    /// 当前窗口速度（f32 的 bits，单位 B/s）；0 表示暂无有效采样
    pub(crate) speed_bps_bits: AtomicU32,
}

impl DownloadMetrics {
    pub(crate) fn set_speed_bps(&self, speed_bps: f32) {
        self.speed_bps_bits
            .store(speed_bps.to_bits(), std::sync::atomic::Ordering::Relaxed);
    }

    // 仅库模式（FFI poll）读取；CLI 不注入 metrics
    #[allow(dead_code)]
    pub(crate) fn speed_bps(&self) -> f32 {
        f32::from_bits(self.speed_bps_bits.load(std::sync::atomic::Ordering::Relaxed))
    }
}
