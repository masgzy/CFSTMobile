//! JNI 桥接层（仅 `target_os = "android"` 编译进 cdylib）。
//!
//! 供 Kotlin 侧 `com.masgzy.cfstmobile.ffi.CfstFfi` 的 `external fun` 直接绑定。
//! 字符串策略采用 engine/README.md 的**策略 1**：
//! - 配置以 UTF-8 `ByteArray` 传入（绕开 JNI Modified-UTF-8 与标准 UTF-8 的差异）；
//! - 进度/结果以 UTF-8 `ByteArray` 返回：JNI 层拷贝进 Java 数组后立即
//!   `cfst_string_free`，不存在悬垂指针；
//! - `engineCreate` 失败返回 0，错误消息原位写回调用方提供的 `errOut` 缓冲。
//!
//! 依赖仅 `jni-sys`（jni.h 的零传递依赖类型绑定），不引入 `jni` crate。
//!
//! ⚠️ 符号名与 Kotlin 类路径强耦合：`com.masgzy.cfstmobile.ffi.CfstFfi`
//! 若要改名，必须同步修改本文件全部导出函数名，否则运行时
//! `UnsatisfiedLinkError`。

use std::ffi::{c_char, CStr, CString};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

use jni_sys as jni;

use crate::engine::Engine;
use crate::ffi::{
    cfst_abi_version, cfst_engine_create, cfst_engine_destroy, cfst_engine_phase,
    cfst_engine_poll, cfst_engine_start, cfst_engine_stop, cfst_engine_take_result,
    cfst_string_free,
};

type VTable = jni::JNINativeInterface_;

/// 取 JNI 函数表。`env` 语义为 C 的 `JNIEnv*`（二级指针：指向持有 vtable
/// 指针的 JNIEnv_ 结构），jni-sys 将 `JNIEnv` 建模为裸 vtable 指针别名，
/// 故 `*env` 即 vtable 指针。
#[inline]
fn functions(env: *mut jni::JNIEnv) -> &'static VTable {
    // SAFETY: env 由 JVM 传入，指向的函数表由 JVM 全程持有，静态有效
    unsafe { &*(*env) }
}

/// 读取 Java `ByteArray` 全部内容（GetArrayLength + GetByteArrayRegion）
fn jni_read_array(env: *mut jni::JNIEnv, array: jni::jbyteArray) -> Vec<u8> {
    if array.is_null() {
        return Vec::new();
    }
    let Some(len_fn) = functions(env).GetArrayLength else {
        return Vec::new();
    };
    let len = {
        // SAFETY: array 为 JVM 传入的有效局部引用
        unsafe { len_fn(env, array as jni::jarray).max(0) as usize }
    };
    let mut buf = vec![0u8; len];
    if len > 0 {
        let Some(region_fn) = functions(env).GetByteArrayRegion else {
            return Vec::new();
        };
        // SAFETY: buf 长度与请求区间一致，不会越界
        unsafe {
            region_fn(env, array, 0, len as jni::jsize, buf.as_mut_ptr() as *mut jni::jbyte);
        }
    }
    buf
}

/// 新建 Java `ByteArray` 并写入内容；JVM 异常（如 OOM）或函数表缺失时返回 None。
fn jni_write_array(env: *mut jni::JNIEnv, bytes: &[u8]) -> Option<jni::jbyteArray> {
    let Some(new_fn) = functions(env).NewByteArray else {
        return None;
    };
    // SAFETY: 长度取自字节切片长度
    let arr = unsafe { new_fn(env, bytes.len() as jni::jsize) };
    if arr.is_null() {
        return None;
    }
    if !bytes.is_empty() {
        let Some(region_fn) = functions(env).SetByteArrayRegion else {
            return None;
        };
        // SAFETY: 写入区间 [0, len) 恰为数组容量
        unsafe {
            region_fn(env, arr, 0, bytes.len() as jni::jsize, bytes.as_ptr() as *const jni::jbyte);
        }
    }
    if let Some(exc_fn) = functions(env).ExceptionCheck {
        // SAFETY: env 有效
        if unsafe { exc_fn(env) } != 0 {
            return None;
        }
    }
    Some(arr)
}

// ---------------------------------------------------------------- abiVersion

/// Kotlin: `external fun abiVersion(): Int`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_abiVersion(
    _env: *mut jni::JNIEnv,
    _class: jni::jclass,
) -> jni::jint {
    catch_unwind(|| cfst_abi_version()).unwrap_or(0) as jni::jint
}

// -------------------------------------------------------------- engineCreate

/// Kotlin: `external fun engineCreate(configJson: ByteArray, errOut: ByteArray): Long`
///
/// 失败返回 0，并把错误消息（UTF-8，不含 NUL）写回 `errOut` 原位缓冲。
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_engineCreate(
    env: *mut jni::JNIEnv,
    _class: jni::jclass,
    config_json: jni::jbyteArray,
    err_out: jni::jbyteArray,
) -> jni::jlong {
    catch_unwind(AssertUnwindSafe(|| engine_create_inner(env, config_json, err_out)))
        .unwrap_or(0)
}

fn engine_create_inner(
    env: *mut jni::JNIEnv,
    config_json: jni::jbyteArray,
    err_out: jni::jbyteArray,
) -> jni::jlong {
    if config_json.is_null() {
        return 0;
    }
    // 配置字节 → C 字符串。容忍尾部 NUL（截断到首个 NUL）；
    // JSON 中途含 NUL 属非法输入，同样按截断处理（后续 JSON 解析会报错）。
    let config = jni_read_array(env, config_json);
    let trimmed = match config.iter().position(|&b| b == 0) {
        Some(pos) => &config[..pos],
        None => &config[..],
    };
    let c_config = match CString::new(trimmed) {
        Ok(s) => s,
        Err(_) => return 0,
    };

    // 错误缓冲：Kotlin 侧建议传 512 字节；空数组时退化为 NULL（错误只吞不外带）
    let cap = if err_out.is_null() {
        0usize
    } else {
        let Some(len_fn) = functions(env).GetArrayLength else {
            return 0;
        };
        // SAFETY: err_out 为 JVM 传入的有效局部引用
        unsafe { len_fn(env, err_out as jni::jarray).max(0) as usize }
    };
    let mut err_buf: Vec<c_char> = vec![0 as c_char; cap];
    let (err_ptr, err_cap) = if cap > 0 {
        (err_buf.as_mut_ptr(), cap)
    } else {
        (ptr::null_mut(), 0usize)
    };

    let engine = cfst_engine_create(c_config.as_ptr(), err_ptr, err_cap as i32);
    if engine.is_null() && !err_ptr.is_null() {
        // 错误消息原位写回 errOut（截到首个 NUL，即消息本身）
        let bytes =
            unsafe { std::slice::from_raw_parts(err_ptr as *const u8, err_cap) };
        if let Some(n) = bytes.iter().position(|&b| b == 0) {
            if let Some(region_fn) = functions(env).SetByteArrayRegion {
                // SAFETY: 写入区间 n 不超过 err_out 容量 err_cap
                unsafe {
                    region_fn(env, err_out, 0, n as jni::jsize, bytes.as_ptr() as *const jni::jbyte);
                }
            }
        }
    }
    engine as jni::jlong
}

// --------------------------------------------------------------- start/stop

/// Kotlin: `external fun engineStart(ptr: Long): Boolean`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_engineStart(
    _env: *mut jni::JNIEnv,
    _class: jni::jclass,
    engine: jni::jlong,
) -> jni::jboolean {
    if engine == 0 {
        return 0;
    }
    let started = catch_unwind(|| cfst_engine_start(engine as *mut Engine));
    u8::from(started.unwrap_or(false))
}

/// Kotlin: `external fun engineStop(ptr: Long)`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_engineStop(
    _env: *mut jni::JNIEnv,
    _class: jni::jclass,
    engine: jni::jlong,
) {
    if engine == 0 {
        return;
    }
    let _ = catch_unwind(|| cfst_engine_stop(engine as *mut Engine));
}

// ------------------------------------------------------------------- phase

/// Kotlin: `external fun enginePhase(ptr: Long): Int`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_enginePhase(
    _env: *mut jni::JNIEnv,
    _class: jni::jclass,
    engine: jni::jlong,
) -> jni::jint {
    if engine == 0 {
        return 0;
    }
    catch_unwind(|| cfst_engine_phase(engine as *mut Engine))
        .unwrap_or(crate::engine::PHASE_ERROR) as jni::jint
}

// ------------------------------------------------------- poll / take_result

/// poll / take_result 共用主体：调 C 函数拿 C 字符串 → 拷贝进 Java ByteArray
/// → 立即 `cfst_string_free` → 返回数组指针（失败/空返回 NULL）。
fn take_string_as_array(
    env: *mut jni::JNIEnv,
    engine: jni::jlong,
    f: extern "C" fn(*mut Engine) -> *mut c_char,
) -> jni::jobject {
    if engine == 0 {
        return ptr::null_mut();
    }
    let out = match catch_unwind(|| f(engine as *mut Engine)) {
        Ok(out) if !out.is_null() => out,
        _ => return ptr::null_mut(),
    };
    let bytes = unsafe {
        // SAFETY: out 由引擎分配且以 NUL 结尾（FFI 契约）
        CStr::from_ptr(out).to_bytes()
    };
    let arr = jni_write_array(env, bytes);
    // 无论拷贝成败都立即释放 C 字符串（策略 1：无悬垂）
    cfst_string_free(out);
    match arr {
        Some(arr) => arr as jni::jobject,
        None => ptr::null_mut(),
    }
}

/// Kotlin: `external fun enginePoll(ptr: Long): ByteArray?`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_enginePoll(
    env: *mut jni::JNIEnv,
    _class: jni::jclass,
    engine: jni::jlong,
) -> jni::jobject {
    catch_unwind(AssertUnwindSafe(|| take_string_as_array(env, engine, cfst_engine_poll)))
        .unwrap_or(ptr::null_mut())
}

/// Kotlin: `external fun engineTakeResult(ptr: Long): ByteArray?`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_engineTakeResult(
    env: *mut jni::JNIEnv,
    _class: jni::jclass,
    engine: jni::jlong,
) -> jni::jobject {
    catch_unwind(AssertUnwindSafe(|| {
        take_string_as_array(env, engine, cfst_engine_take_result)
    }))
    .unwrap_or(ptr::null_mut())
}

// ------------------------------------------------------------------ destroy

/// Kotlin: `external fun engineDestroy(ptr: Long)`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masgzy_cfstmobile_ffi_CfstFfi_engineDestroy(
    _env: *mut jni::JNIEnv,
    _class: jni::jclass,
    engine: jni::jlong,
) {
    if engine == 0 {
        return;
    }
    let _ = catch_unwind(|| cfst_engine_destroy(engine as *mut Engine));
}
