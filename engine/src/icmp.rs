use std::net::{IpAddr, SocketAddr};
use std::sync::Arc;
use std::future::Future;
use std::pin::Pin;
use std::sync::atomic::{AtomicU16, Ordering};
use surge_ping::{Client, Config, PingIdentifier, PingSequence, ICMP};

use crate::common::{self, PingData, BasePing, Ping as CommonPing, PingMode};
use crate::pool::Limiter;

static PING_IDENTIFIER_COUNTER: AtomicU16 = AtomicU16::new(0);

#[derive(Clone)]
pub(crate) struct IcmpingFactoryData {
    client_v4: Arc<Client>,
    client_v6: Arc<Client>,
    limiter: Arc<Limiter>,
}

impl PingMode for IcmpingFactoryData {
    fn run_test(
        &self,
        base: BasePing,
        addr: SocketAddr,
    ) -> Pin<Box<dyn Future<Output = Option<PingData>> + Send>> {
        let args = base.args.clone();
        let ip = addr.ip();
        let client = match ip {
            IpAddr::V4(_) => self.client_v4.clone(),
            IpAddr::V6(_) => self.client_v6.clone(),
        };
        let limiter = self.limiter.clone();
        let args_for_closure = args.clone();

        Box::pin(common::ping_one(args, addr, 0, move || {
            let client = client.clone();
            let limiter = limiter.clone();
            let args = args_for_closure.clone();
            async move {
                let _permit = limiter.acquire().await;
                icmp_ping(addr, &args, &client).await
            }
        }))
    }

    fn clone_box(&self) -> Box<dyn PingMode> {
        Box::new(self.clone())
    }
}

/// 由调用方预构建 BasePing 后传入。
pub(crate) fn new(base: BasePing) -> Option<CommonPing> {
    common::print_speed_test_info("ICMP-Ping", &base.args);

    let client_v4 = Arc::new(Client::new(&Config::default()).ok()?);
    let client_v6 = Arc::new(Client::new(&Config::builder().kind(ICMP::V6).build()).ok()?);

    let factory_data = IcmpingFactoryData {
        client_v4,
        client_v6,
        limiter: base.limiter.clone(),
    };

    Some(CommonPing::new(base, factory_data))
}

async fn icmp_ping(addr: SocketAddr, args: &Arc<crate::args::Args>, client: &Arc<Client>) -> Option<f32> {
    let ip = addr.ip();
    let payload = [0; 56];
    let identifier = PingIdentifier(PING_IDENTIFIER_COUNTER.fetch_add(1, Ordering::Relaxed));

    let mut pinger = client.pinger(ip, identifier).await;
    pinger.timeout(args.max_delay);

    pinger.ping(PingSequence(0), &payload).await
        .ok()
        .map(|(_, dur)| dur.as_secs_f32() * 1000.0)
}
