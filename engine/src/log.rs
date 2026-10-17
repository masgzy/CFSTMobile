/// 共享日志助手：CLI 直接打印；库模式下 stdout/stderr 输出在 Android 上
/// 不可见（无害），结构化信息统一走 FFI 的 poll/take_result JSON。

// 以下函数在库模式下可能整条调用链都不触达（CLI 专属路径），属预期
#[allow(dead_code)]
pub(crate) fn error_println(args: std::fmt::Arguments<'_>) {
    eprintln!("\x1b[31;1m[错误]\x1b[0m {args}");
}

/// 仅 CLI 路径可调用（args::parse / parse_args 校验失败）。
/// 库模式下所有可预期的失败都应返回 Result，此函数仅为让共享代码
/// 通过编译而存在——若被触达说明库路径漏改，直接 panic 暴露问题。
#[allow(dead_code)]
pub(crate) fn error_and_exit(args: std::fmt::Arguments<'_>) -> ! {
    error_println(args);
    #[cfg(feature = "cli")]
    std::process::exit(1);
    #[cfg(not(feature = "cli"))]
    panic!("cfst-engine 内部错误（不该在库模式触达 error_and_exit）: {args}");
}

#[allow(dead_code)]
pub(crate) fn info_println(args: std::fmt::Arguments<'_>) {
    println!("\x1b[36;1m[信息]\x1b[0m {args}");
}

#[allow(dead_code)]
pub(crate) fn warning_println(args: std::fmt::Arguments<'_>) {
    println!("\x1b[33;1m[警告]\x1b[0m {args}");
}
