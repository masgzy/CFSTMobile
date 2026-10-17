//! 库模式进度条：与 terminal::Bar 同 API 的纯内存实现。
//!
//! 引擎线程写、FFI poll 读（跨线程安全，用 Mutex 而非 UnsafeCell 槽位——
//! Android 上更新频率低，锁开销可忽略）。

use std::sync::Mutex;

#[derive(Debug, Default)]
struct BarState {
    pos: usize,
    msg: String,
    prefix: String,
    done: bool,
}

pub(crate) struct Bar {
    state: Mutex<BarState>,
    #[allow(dead_code)]
    total: usize,
}

impl Bar {
    pub(crate) fn new(count: usize, start_str: &str, end_str: &str) -> Self {
        let _ = (start_str, end_str);
        Self {
            state: Mutex::new(BarState::default()),
            total: count.max(1),
        }
    }

    pub(crate) fn update(&self, pos: usize, msg: impl AsRef<str>, suffix: impl AsRef<str>) {
        if let Ok(mut s) = self.state.lock() {
            s.pos = pos;
            s.msg = msg.as_ref().to_string();
            s.prefix = suffix.as_ref().to_string();
        }
    }

    pub(crate) fn set_suffix(&self, suffix: impl AsRef<str>) {
        if let Ok(mut s) = self.state.lock() {
            s.prefix = suffix.as_ref().to_string();
        }
    }

    pub(crate) fn done(&self) {
        if let Ok(mut s) = self.state.lock() {
            s.done = true;
        }
    }
}
