use std::collections::VecDeque;
use std::net::SocketAddr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use http_body::Body;

// 统一的速度更新间隔（毫秒）
const SPEED_UPDATE_INTERVAL_MS: u64 = 500;
// 单次 body 读取的停滞超时：服务端停止发数据时避免永久挂起
//（库模式下引擎需要保证 stop 后能在有界时间内结束）
const BODY_STALL_TIMEOUT_MS: u64 = 5000;

use crate::args::Args;
use crate::common::{self, PingData};
use crate::metrics::DownloadMetrics;
use crate::progress::Bar;
use crate::log::warning_println;
use crate::hyper::{parse_url_to_uri, RequestContext};

struct SpeedSampler {
    cumulative: u64,
    last_update: Instant,
    bar: Arc<Bar>,
    metrics: Option<Arc<DownloadMetrics>>,
    speed_samples: VecDeque<(Instant, u64)>,
    intervals: Vec<f32>,
}

impl SpeedSampler {
    fn new(bar: Arc<Bar>, metrics: Option<Arc<DownloadMetrics>>) -> Self {
        let now = Instant::now();
        Self {
            cumulative: 0,
            last_update: now,
            bar,
            metrics,
            speed_samples: VecDeque::new(),
            intervals: Vec::new(),
        }
    }

    fn record_chunk(&mut self, size: u64) {
        self.cumulative += size;
        self.speed_samples.push_back((Instant::now(), self.cumulative));
    }

    fn cleanup_old_samples(&mut self, window_start: Instant) {
        while self.speed_samples.front().is_some_and(|(time, _)| *time < window_start) {
            self.speed_samples.pop_front();
        }
    }

    fn calculate_speed(&self) -> f32 {
        self.speed_samples
            .front()
            .zip(self.speed_samples.back())
            .and_then(|(first, last)| {
                let bytes_diff = last.1 - first.1;
                let time_diff = last.0.duration_since(first.0).as_secs_f32();
                if bytes_diff == 0 || time_diff <= 0.0 {
                    None
                } else {
                    Some(bytes_diff as f32 / time_diff)
                }
            })
            .unwrap_or(0.0)
    }

    fn should_update_display(&self) -> bool {
        let now = Instant::now();
        now.duration_since(self.last_update).as_millis() >= SPEED_UPDATE_INTERVAL_MS as u128
    }

    fn update_display(&mut self) {
        if self.should_update_display() {
            let window_start = self.last_update;
            self.cleanup_old_samples(window_start);

            let speed = self.calculate_speed();
            if speed > 0.0 {
                self.intervals.push(speed);
            }
            if let Some(m) = &self.metrics {
                m.set_speed_bps(speed);
            }
            self.bar.set_suffix(format!("{:.2} MB/s", speed / crate::common::BYTES_PER_MB));
            self.last_update = Instant::now();
        }
    }

    fn update_data_received(&mut self, size: u64) {
        self.record_chunk(size);
        self.update_display();
    }

    // 中位数-MAD 高斯加权：每个区间速度相对于全体中位数的偏离决定其权重，
    // 不依赖时间顺序，无硬编码（1.4826 是 MAD→σ 的理论校准常数）
    fn weighted_speed(&self) -> Option<f32> {
        let v = &self.intervals;
        let n = v.len();
        if n == 0 {
            return None;
        }
        if n == 1 {
            return Some(v[0]);
        }

        let mut sorted = v.to_vec();
        let (_, median, _) = sorted.select_nth_unstable_by(n / 2, |a, b| a.total_cmp(b));
        let med = *median;

        // MAD: median(|v_i - median|)
        let mut devs: Vec<f32> = v.iter().map(|x| (x - med).abs()).collect();
        let (_, mad_median, _) = devs.select_nth_unstable_by(n / 2, |a, b| a.total_cmp(b));
        let mad = (*mad_median).max((med.abs() * f32::EPSILON).max(f32::EPSILON));

        let sigma = 1.4826 * mad;
        let weights: Vec<f32> = v.iter().map(|x| {
            let z = (x - med) / sigma;
            (-z * z / 2.0).exp()
        }).collect();

        let total_w: f32 = weights.iter().sum();
        let weighted_sum: f32 = v.iter().zip(weights.iter()).map(|(x, w)| x * w).sum();
        Some(weighted_sum / total_w)
    }

    // 兜底：将最后一个不完整窗口的数据补录进 intervals
    fn flush_last_interval(&mut self) {
        // 只保留最后一次 update_display 之后的采样
        let cutoff = self.last_update;
        while self.speed_samples.front().is_some_and(|(t, _)| *t < cutoff) {
            self.speed_samples.pop_front();
        }

        if self.speed_samples.len() >= 2 {
            let speed = self.calculate_speed();
            if speed > 0.0 {
                self.intervals.push(speed);
            }
        }
    }
}

pub(crate) struct DownloadTest<'a> {
    args: &'a Args,
    uri: http::Uri,
    host: String,
    bar: Arc<Bar>,
    metrics: Option<Arc<DownloadMetrics>>,
    colo_filter: Arc<Vec<String>>,
    ping_results: Vec<PingData>,
    timeout_flag: Arc<AtomicBool>,
    request_context: Arc<RequestContext>,
}

/// 单 IP 下载测速的共享上下文：并发任务以 `'static` 持有（全部字段为克隆/Arc），
/// 由 tokio::spawn 派发，结果经 mpsc 回传主循环统一判定与计数。
struct DownloadTaskCtx {
    uri: http::Uri,
    host: String,
    request_context: Arc<RequestContext>,
    bar: Arc<Bar>,
    metrics: Option<Arc<DownloadMetrics>>,
    timeout_flag: Arc<AtomicBool>,
    download_duration: Duration,
    colo_filter: Arc<Vec<String>>,
}

impl DownloadTaskCtx {
    async fn download_one(
        ctx: Arc<DownloadTaskCtx>,
        addr: SocketAddr,
        need_colo: bool,
    ) -> (Option<f32>, Option<[u8; 3]>) {
        let mut data_center = None;
        let mut handler = SpeedSampler::new(ctx.bar.clone(), ctx.metrics.clone());

        let Some(resp) = ctx.request_context.send_request(
            &ctx.host,
            &ctx.uri,
            addr,
            &http::Method::GET,
        ).await else {
            return (None, None);
        };

        let avg_speed = {
            if need_colo {
                data_center = common::extract_data_center(resp.headers());
                if data_center.is_none() {
                    return (None, None);
                }
                if !ctx.colo_filter.is_empty() && !common::is_colo_matched(common::colo_opt_to_str(data_center.as_ref()), &ctx.colo_filter) {
                    return (None, data_center);
                }
            }

            let time_start = Instant::now();

            let mut body = resp.into_body();
            let mut body_pin = std::pin::Pin::new(&mut body);

            loop {
                let elapsed = time_start.elapsed();
                if elapsed >= ctx.download_duration || ctx.timeout_flag.load(Ordering::SeqCst) {
                    break;
                }

                // 每帧读取加停滞超时：服务端停止发送时保证有界时间内退出
                let frame = tokio::time::timeout(
                    std::time::Duration::from_millis(BODY_STALL_TIMEOUT_MS),
                    std::future::poll_fn(|cx| body_pin.as_mut().poll_frame(cx)),
                ).await;

                match frame {
                    Ok(Some(Ok(frame))) => {
                        if let Some(data) = frame.data_ref() {
                            handler.update_data_received(data.len() as u64);
                        }
                    }
                    Ok(Some(Err(_))) => return (None, data_center),
                    Ok(None) => break,
                    // 超过 5 秒无新数据：视为流停滞，结束本 IP 的测速
                    Err(_) => break,
                }
            }

            // 补录最后一个不完整窗口，再计算加权速度
            handler.flush_last_interval();
            handler.weighted_speed().or_else(|| {
                let elapsed = time_start.elapsed().as_secs_f32();
                if elapsed > 0.0 && handler.cumulative > 0 {
                    Some(handler.cumulative as f32 / elapsed)
                } else {
                    None
                }
            })
        };

        (avg_speed, data_center)
    }
}

impl<'a> DownloadTest<'a> {
    pub(crate) async fn new(
        args: &'a Args,
        ping_results: Vec<PingData>,
        timeout_flag: Arc<AtomicBool>,
        metrics: Option<Arc<DownloadMetrics>>,
    ) -> Self {
        let (uri, host) = parse_url_to_uri(&args.url).unwrap();

        if args.test_count > ping_results.len() {
            warning_println(format_args!("队列的 IP 数量不足，可能需要降低延迟测速筛选条件！"));
        }

        println!(
            "开始下载测速（下限：{:.2} MB/s, 所需：{}, 队列：{}）",
            args.min_speed_bps / crate::common::BYTES_PER_MB,
            args.test_count,
            ping_results.len()
        );

        let tls_connector = crate::hyper::build_tls_connector().unwrap();
        let request_context = Arc::new(RequestContext {
            interface_config: args.interface_config.clone(),
            tls_connector,
            connect_timeout_ms: crate::common::CONNECT_TIMEOUT_MS,
            ttfb_timeout_ms: crate::common::TTFB_TIMEOUT_MS,
        });

        if let Some(m) = &metrics {
            m.total.store(ping_results.len(), Ordering::Relaxed);
        }

        Self {
            args,
            uri,
            host,
            bar: Arc::new(Bar::new(ping_results.len(), "", "")),
            metrics,
            colo_filter: Arc::new(common::parse_colo_filters(&args.httping_cf_colo)),
            ping_results,
            timeout_flag,
            request_context,
        }
    }

    pub(crate) async fn test_download_speed(&mut self) -> Vec<PingData> {
        let colo_filters = self.colo_filter.clone();

        let ctx = Arc::new(DownloadTaskCtx {
            uri: self.uri.clone(),
            host: self.host.clone(),
            request_context: self.request_context.clone(),
            bar: self.bar.clone(),
            metrics: self.metrics.clone(),
            timeout_flag: self.timeout_flag.clone(),
            download_duration: self.args.timeout_duration.unwrap(),
            colo_filter: self.colo_filter.clone(),
        });

        let mut ping_queue = std::mem::take(&mut self.ping_results).into_iter().collect::<VecDeque<_>>();
        let mut qualified_results = Vec::with_capacity(self.args.test_count);
        let mut tested_count = 0;

        // 测速并发：同时测速的 IP 数量。1 = 串行（与上游行为一致、结果最准），
        // >1 时多 IP 并发下载（吞吐更高，单 IP 速度被摊薄，对齐 CFData nsbspeedtest）。
        // 结果经 mpsc 回传主循环统一判定/计数/画进度，派发窗口随完成数滚动补位。
        let concurrency = self.args.speed_threads.clamp(1, 64);
        let (result_tx, mut result_rx) =
            tokio::sync::mpsc::unbounded_channel::<(PingData, Option<f32>, Option<[u8; 3]>)>();

        self.bar.update(qualified_results.len(), format!("{}|{}", qualified_results.len(), tested_count), "");

        let mut inflight = 0usize;
        loop {
            // 派发：有空位、有队列、未达标、未请求停止时继续补位
            while inflight < concurrency
                && qualified_results.len() < self.args.test_count
                && !common::check_timeout_signal(&self.timeout_flag)
            {
                let Some(ping_result) = ping_queue.pop_front() else { break };
                let need_colo = ping_result.data_center.is_none();
                let task_ctx = ctx.clone();
                let tx = result_tx.clone();
                tokio::spawn(async move {
                    let (speed, colo) =
                        DownloadTaskCtx::download_one(task_ctx, ping_result.addr, need_colo).await;
                    let _ = tx.send((ping_result, speed, colo));
                });
                inflight += 1;
            }

            if inflight == 0 {
                break;
            }

            let Some((mut ping_result, speed, maybe_colo)) = result_rx.recv().await else { break };
            inflight -= 1;

            ping_result.download_speed = speed;

            if ping_result.data_center.is_none()
                && let Some(colo) = maybe_colo {
                ping_result.data_center = Some(colo);
            }

            let speed_match = match speed {
                Some(s) => s >= self.args.min_speed_bps,
                None => false,
            };

            let colo_match = colo_filters.is_empty() || common::is_colo_matched(ping_result.colo_str(), &colo_filters);

            tested_count += 1;

            if let Some(m) = &self.metrics {
                m.tested.store(tested_count, Ordering::Relaxed);
                m.qualified.store(qualified_results.len(), Ordering::Relaxed);
            }

            let bar = self.bar.as_ref();
            let mut qualified_len = qualified_results.len();

            let is_qualified = speed_match && colo_match;

            if is_qualified {
                qualified_results.push(ping_result);
                qualified_len += 1;
            }

            let message = format!("{qualified_len}|{tested_count}");
            bar.update(tested_count, message, "");
        }

        self.bar.set_suffix("Done!");

        // 完成进度条但保持当前进度
        self.bar.done();

        if qualified_results.len() < self.args.test_count {
            warning_println(format_args!("下载测速符合要求的 IP 数量不足！"));
        }

        common::sort_results(&mut qualified_results[..]);

        qualified_results
    }
}
