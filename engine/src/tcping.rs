use std::future::Future;
use std::net::SocketAddr;
use std::pin::Pin;
use std::sync::Arc;
use std::time::Instant;

use crate::common::{self, PingData, Target, BasePing, Ping as CommonPing, PingMode};
use crate::interface::{InterfaceParamResult, bind_socket_to_interface};
use crate::pool::Limiter;

const TCPING_TIMEOUT_MS: u64 = 1000;

#[derive(Clone)]
pub(crate) struct TcpingFactoryData {
    interface_config: Arc<InterfaceParamResult>,
    limiter: Arc<Limiter>,
}

impl PingMode for TcpingFactoryData {
    fn run_test(
        &self,
        base: BasePing,
        target: Target,
    ) -> Pin<Box<dyn Future<Output = Option<PingData>> + Send>> {
        let args = base.args.clone();
        let interface_config = self.interface_config.clone();
        let limiter = self.limiter.clone();

        Box::pin(common::ping_one(args, target.clone(), common::PING_INTERVAL_MS, move || {
            let interface_config = interface_config.clone();
            let limiter = limiter.clone();
            async move {
                let _permit = limiter.acquire().await;
                tcping(target.addr, &interface_config).await
            }
        }))
    }

    fn clone_box(&self) -> Box<dyn PingMode> {
        Box::new(self.clone())
    }
}

/// 由调用方（CLI main / 库引擎）预构建 BasePing 后传入，
/// 便于引擎持有 base 内的进度原子量供 FFI poll 读取。
pub(crate) fn new(base: BasePing) -> CommonPing {
    common::print_speed_test_info("Tcping", &base.args);

    let factory_data = TcpingFactoryData {
        interface_config: base.args.interface_config.clone(),
        limiter: base.limiter.clone(),
    };

    CommonPing::new(base, factory_data)
}

// TCP连接测试函数
pub(crate) async fn tcping(
    addr: SocketAddr,
    interface_config: &Arc<InterfaceParamResult>,
) -> Option<f32> {
    let start_time = Instant::now();

    let socket = bind_socket_to_interface(addr, interface_config).await?;

    // 跳过 TIME_WAIT：tcping 只测 RTT，不交换数据，发 RST 安全
    // linger 设置在 socket 上，connect 后 stream 自动继承
    #[allow(deprecated)]
    let _ = socket.set_linger(Some(std::time::Duration::ZERO));

    match tokio::time::timeout(std::time::Duration::from_millis(TCPING_TIMEOUT_MS), socket.connect(addr)).await {
        Ok(Ok(stream)) => {
            drop(stream);
            Some(start_time.elapsed().as_secs_f32() * 1000.0)
        }
        _ => None,
    }
}
