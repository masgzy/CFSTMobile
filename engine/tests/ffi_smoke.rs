//! FFI 端到端冒烟测试（真实网络）。
//!
//! 默认忽略（CI 无网/离线环境自动跳过）：
//!     cargo test --test ffi_smoke -- --ignored --nocapture
//!
//! 流程：create → start → poll 循环 → take_result → destroy，
//! 直接走 C ABI 导出函数（与 Kotlin/JNI 调用路径完全一致）。

use std::ffi::{c_char, CStr};
use std::time::{Duration, Instant};

fn err_str(buf: &[c_char]) -> String {
    unsafe { CStr::from_ptr(buf.as_ptr()) }.to_string_lossy().into_owned()
}

fn poll_and_print(engine: *mut cfst::engine::Engine) -> Option<String> {
    let ptr = cfst::ffi::cfst_engine_poll(engine);
    if ptr.is_null() {
        return None;
    }
    let s = unsafe { CStr::from_ptr(ptr) }.to_string_lossy().into_owned();
    cfst::ffi::cfst_string_free(ptr);
    Some(s)
}

#[test]
#[ignore = "需要真实网络"]
fn ffi_end_to_end_tcp_ping() {
    let config = concat!(
        r#"{
        "ip_text": "1.1.1.1,1.0.0.1,8.8.8.8,9.9.9.9",
        "tcp_port": 443,
        "ping_times": 2,
        "target_num": 4,
        "disable_download": true,
        "max_threads": 32,
        "global_timeout_secs": 60
    }"#,
        "\0"
    );

    let mut err = [0 as c_char; 512];
    let engine = cfst::ffi::cfst_engine_create(config.as_ptr() as *const c_char, err.as_mut_ptr(), 512);
    assert!(!engine.is_null(), "create 失败: {}", err_str(&err));

    assert!(cfst::ffi::cfst_engine_start(engine), "start 应在 idle 状态成功");
    assert!(!cfst::ffi::cfst_engine_start(engine), "重复 start 应返回 false");

    let deadline = Instant::now() + Duration::from_secs(90);
    loop {
        assert!(Instant::now() < deadline, "引擎 90 秒内未结束");
        if let Some(json) = poll_and_print(engine) {
            println!("poll: {json}");
        }
        match cfst::ffi::cfst_engine_phase(engine) {
            cfst::engine::PHASE_DONE | cfst::engine::PHASE_ERROR => break,
            _ => std::thread::sleep(Duration::from_millis(250)),
        }
    }

    assert_eq!(cfst::ffi::cfst_engine_phase(engine), cfst::engine::PHASE_DONE, "phase 应为 done");

    let result_ptr = cfst::ffi::cfst_engine_take_result(engine);
    assert!(!result_ptr.is_null(), "done 后 take_result 应非空");
    let result = unsafe { CStr::from_ptr(result_ptr) }.to_string_lossy().into_owned();
    cfst::ffi::cfst_string_free(result_ptr);
    println!("result: {result}");

    let v: serde_json::Value = serde_json::from_str(&result).expect("结果应为合法 JSON");
    assert!(v["count"].as_u64().unwrap_or(0) >= 1, "应至少有 1 个可用结果");
    assert!(v["items"].as_array().map_or(false, |a| !a.is_empty()));

    assert!(cfst::ffi::cfst_engine_take_result(engine).is_null(), "take_result 为一次性语义");

    // 完成后 poll 依然可用（应显示 done 阶段）
    assert!(poll_and_print(engine).is_some());

    cfst::ffi::cfst_engine_destroy(engine);
}

#[test]
#[ignore = "需要真实网络"]
fn ffi_stop_takes_effect() {
    let config = concat!(
        r#"{
        "ip_text": "1.0.0.0/24",
        "tcp_port": 443,
        "disable_download": true,
        "max_threads": 16,
        "global_timeout_secs": 120
    }"#,
        "\0"
    );

    let mut err = [0 as c_char; 512];
    let engine = cfst::ffi::cfst_engine_create(config.as_ptr() as *const c_char, err.as_mut_ptr(), 512);
    assert!(!engine.is_null(), "create 失败: {}", err_str(&err));

    assert!(cfst::ffi::cfst_engine_start(engine));
    std::thread::sleep(Duration::from_millis(500));
    cfst::ffi::cfst_engine_stop(engine);

    let deadline = Instant::now() + Duration::from_secs(30);
    loop {
        let phase = cfst::ffi::cfst_engine_phase(engine);
        if phase == cfst::engine::PHASE_DONE || phase == cfst::engine::PHASE_ERROR {
            break;
        }
        assert!(Instant::now() < deadline, "stop 后 30 秒内未收尾");
        std::thread::sleep(Duration::from_millis(200));
    }

    assert_eq!(cfst::ffi::cfst_engine_phase(engine), cfst::engine::PHASE_DONE);
    println!("stop 后 poll: {}", poll_and_print(engine).unwrap_or_default());

    cfst::ffi::cfst_engine_destroy(engine);
    // destroy 后再访问同一指针属于未定义行为，此处不测试（契约禁止）
}
