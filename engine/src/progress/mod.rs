//! 进度展示的双模式模块。
//!
//! - `feature = "cli"`：`terminal::Bar`，终端 ANSI 彩色进度条（独立渲染线程）。
//! - 默认（库模式）：`headless::Bar`，同名 API 的纯内存实现，仅记录状态，
//!   不启动任何线程、不触碰 stdout——Android .so 的正确形态。

#[cfg(feature = "cli")]
mod terminal;
#[cfg(not(feature = "cli"))]
mod headless;

#[cfg(feature = "cli")]
pub(crate) use terminal::Bar;
#[cfg(not(feature = "cli"))]
pub(crate) use headless::Bar;
