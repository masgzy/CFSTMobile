//! C ABI 导出层（Kotlin 通过 JNI 直接调用的稳定边界）。
//!
//! 约定：
//! - 所有函数可在任意线程调用，均不 panic（panic 被 catch_unwind 拦截）；
//! - 返回 `*mut c_char` 的函数，其字符串由调用方调用 `cfst_string_free` 释放；
//! - `cfst_engine_create` 失败时返回 NULL，并尽力将错误消息写入 err_out
//!   （NUL 结尾、按 err_cap 截断；err_out 可为 NULL 表示不关心错误）；
//! - engine 指针只能来自 `cfst_engine_create`，且 destroy 后不得再使用；
//! - `cfst_engine_destroy` 不会阻塞：若仍在测速，先请求停止再直接返回，
//!   运行线程持有共享状态自然续命至收尾完成。
//!
//! ABI 版本：`cfst_abi_version()` 返回 1。破坏性变更时递增。

use std::ffi::{c_char, CStr, CString};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

use crate::compact::{CompactSession, COMPACT_ERROR};
use crate::engine::{panic_message, Engine, PHASE_ERROR};

/// 当前 ABI 版本
pub const CFST_ABI_VERSION: u32 = 1;

/// 把错误消息写入调用方缓冲区（UTF-8 安全截断 + NUL 结尾）
fn write_err(err_out: *mut c_char, err_cap: i32, msg: &str) {
    if err_out.is_null() || err_cap <= 0 {
        return;
    }
    let cap = (err_cap - 1) as usize; // 预留 NUL
    let bytes = msg.as_bytes();
    let mut n = bytes.len().min(cap);
    // 避免切在 UTF-8 多字节序列中间
    while n > 0 && std::str::from_utf8(&bytes[..n]).is_err() {
        n -= 1;
    }
    // SAFETY: err_out 非空且容量 err_cap >= n + 1（FFI 契约由调用方保证）
    unsafe {
        ptr::copy_nonoverlapping(bytes.as_ptr(), err_out as *mut u8, n);
        *err_out.add(n) = 0;
    }
}

/// 在引擎指针上执行操作；空指针 / panic 统一吞掉返回 None
fn with_engine<T>(engine: *mut Engine, f: impl FnOnce(&Engine) -> T) -> Option<T> {
    if engine.is_null() {
        return None;
    }
    // SAFETY: 调用方必须传回 create 返回且尚未 destroy 的指针（FFI 契约）
    let engine = unsafe { &*engine };
    catch_unwind(AssertUnwindSafe(|| f(engine))).ok()
}

#[unsafe(no_mangle)]
pub extern "C" fn cfst_abi_version() -> u32 {
    CFST_ABI_VERSION
}

/// 创建引擎。config_json 为 UTF-8 JSON；失败返回 NULL 并写 err_out。
///
/// #[no_mangle] pub extern "C" fn
#[unsafe(no_mangle)]
pub extern "C" fn cfst_engine_create(
    config_json: *const c_char,
    err_out: *mut c_char,
    err_cap: i32,
) -> *mut Engine {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if config_json.is_null() {
            return Err("config_json 为空".to_string());
        }
        // SAFETY: config_json 非空，指向 NUL 结尾的 UTF-8 C 字符串（FFI 契约）
        let cstr = unsafe { CStr::from_ptr(config_json) };
        Engine::create(&cstr.to_string_lossy())
    }));

    match result {
        Ok(Ok(engine)) => Box::into_raw(Box::new(engine)),
        Ok(Err(e)) => {
            write_err(err_out, err_cap, &e);
            ptr::null_mut()
        }
        Err(panic) => {
            write_err(err_out, err_cap, &format!("create panic: {}", panic_message(&panic)));
            ptr::null_mut()
        }
    }
}

/// 启动测速。仅 idle 引擎可启动；已在运行/已完成返回 false。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_engine_start(engine: *mut Engine) -> bool {
    with_engine(engine, |e| e.start()).unwrap_or(false)
}

/// 当前阶段（0=idle 1=ping 2=download 3=done 4=error；空指针按 error 处理）
#[unsafe(no_mangle)]
pub extern "C" fn cfst_engine_phase(engine: *mut Engine) -> u32 {
    with_engine(engine, |e| e.phase()).unwrap_or(PHASE_ERROR)
}

/// 请求停止（协作式，立即返回；结果仍可通过 take_result 获取）
#[unsafe(no_mangle)]
pub extern "C" fn cfst_engine_stop(engine: *mut Engine) {
    let _ = with_engine(engine, |e| e.stop());
}

/// 轮询进度快照。返回 JSON 字符串（调用方负责 cfst_string_free），
/// 空指针 / panic / 分配失败返回 NULL。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_engine_poll(engine: *mut Engine) -> *mut c_char {
    with_engine(engine, |e| e.progress_json())
        .and_then(|s| CString::new(s).ok())
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// 取走最终结果（一次性）。仅 done 阶段首次调用返回 JSON，其余情况 NULL。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_engine_take_result(engine: *mut Engine) -> *mut c_char {
    with_engine(engine, |e| e.result_json())
        .and_then(|one_shot| one_shot.and_then(|s| CString::new(s).ok()))
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// 销毁引擎：请求停止并释放句柄，永不阻塞（运行线程自然收尾）。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_engine_destroy(engine: *mut Engine) {
    if engine.is_null() {
        return;
    }
    // SAFETY: 指针来自 cfst_engine_create 且此前未 destroy（FFI 契约）
    let engine = unsafe { Box::from_raw(engine) };
    engine.stop();
}

/// 释放本库返回的字符串
#[unsafe(no_mangle)]
pub extern "C" fn cfst_string_free(s: *mut c_char) {
    if s.is_null() {
        return;
    }
    // SAFETY: 指针来自本库 into_raw 且此前未释放（FFI 契约）
    drop(unsafe { CString::from_raw(s) });
}

// ------------------------------------------------------------ compact（地址库精简）

/// 创建精简会话。config_json 为 UTF-8 JSON（compact.rs 的 CompactConfig）；
/// 失败返回 NULL 并写 err_out。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_compact_create(
    config_json: *const c_char,
    err_out: *mut c_char,
    err_cap: i32,
) -> *mut CompactSession {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if config_json.is_null() {
            return Err("config_json 为空".to_string());
        }
        // SAFETY: config_json 非空，指向 NUL 结尾的 UTF-8 C 字符串（FFI 契约）
        let cstr = unsafe { CStr::from_ptr(config_json) };
        CompactSession::create(&cstr.to_string_lossy())
    }));

    match result {
        Ok(Ok(session)) => Box::into_raw(Box::new(session)),
        Ok(Err(e)) => {
            write_err(err_out, err_cap, &e);
            ptr::null_mut()
        }
        Err(panic) => {
            write_err(err_out, err_cap, &format!("create panic: {}", panic_message(&panic)));
            ptr::null_mut()
        }
    }
}

/// 启动精简。仅 idle 会话可启动；已在运行/已完成返回 false。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_compact_start(session: *mut CompactSession) -> bool {
    with_session(session, |s| s.start()).unwrap_or(false)
}

/// 当前阶段（0=idle 1=running 2=done 3=error；空指针按 error 处理）
#[unsafe(no_mangle)]
pub extern "C" fn cfst_compact_phase(session: *mut CompactSession) -> u32 {
    with_session(session, |s| s.phase()).unwrap_or(COMPACT_ERROR)
}

/// 请求停止（协作式，立即返回；已保留子网仍可经 take_result 获取）
#[unsafe(no_mangle)]
pub extern "C" fn cfst_compact_stop(session: *mut CompactSession) {
    let _ = with_session(session, |s| s.stop());
}

/// 轮询进度快照（JSON，含 pass/current/total/found/percent/status/logs）。
/// 返回值由调用方 cfst_string_free 释放。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_compact_poll(session: *mut CompactSession) -> *mut c_char {
    with_session(session, |s| s.progress_json())
        .and_then(|s| CString::new(s).ok())
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// 取走精简结果（一次性）：done 阶段首次调用返回 {count, content, file} JSON。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_compact_take_result(session: *mut CompactSession) -> *mut c_char {
    with_session(session, |s| s.result_json())
        .and_then(|one_shot| one_shot.and_then(|s| CString::new(s).ok()))
        .map(|s| s.into_raw())
        .unwrap_or(ptr::null_mut())
}

/// 销毁精简会话：请求停止并释放句柄，永不阻塞（运行线程自然收尾）。
#[unsafe(no_mangle)]
pub extern "C" fn cfst_compact_destroy(session: *mut CompactSession) {
    if session.is_null() {
        return;
    }
    // SAFETY: 指针来自 cfst_compact_create 且此前未 destroy（FFI 契约）
    let session = unsafe { Box::from_raw(session) };
    session.stop();
}

/// 在精简会话指针上执行操作；空指针 / panic 统一吞掉返回 None
fn with_session<T>(session: *mut CompactSession, f: impl FnOnce(&CompactSession) -> T) -> Option<T> {
    if session.is_null() {
        return None;
    }
    // SAFETY: 调用方必须传回 create 返回且尚未 destroy 的指针（FFI 契约）
    let session = unsafe { &*session };
    catch_unwind(AssertUnwindSafe(|| f(session))).ok()
}
