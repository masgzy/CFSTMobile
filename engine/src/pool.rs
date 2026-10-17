//! 并发限流器。
//!
//! 上游实现为全局 `OnceLock` 单例：引擎只能初始化一次并发上限，
//! 第二次 init 被静默忽略，且多实例会共享同一把信号量。
//! 库化后改为每会话独立 `Limiter`（挂在 BasePing 上随会话传递），
//! 允许同时运行多个互不干扰的测速实例。

use std::sync::Arc;
use tokio::sync::{OwnedSemaphorePermit, Semaphore};

#[derive(Debug, Clone)]
pub(crate) struct Limiter {
    semaphore: Arc<Semaphore>,
    max_concurrent: usize,
}

impl Limiter {
    pub(crate) fn new(max_concurrent: usize) -> Self {
        Self {
            semaphore: Arc::new(Semaphore::new(max_concurrent)),
            max_concurrent,
        }
    }

    pub(crate) async fn acquire(&self) -> OwnedSemaphorePermit {
        // 信号量永不 close，acquire_owned 不会返回 Err
        self.semaphore
            .clone()
            .acquire_owned()
            .await
            .expect("limiter semaphore closed unexpectedly")
    }

    pub(crate) fn max_concurrent(&self) -> usize {
        self.max_concurrent
    }
}
