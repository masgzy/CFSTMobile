<div align="center">

# CFSTMobile

**Android 原生 Cloudflare 优选测速工具 — Rust 引擎 + Jetpack Compose**

[![Release](https://img.shields.io/github/v/release/masgzy/CFSTMobile?include_prereleases&label=Release&logo=github)](https://github.com/masgzy/CFSTMobile/releases/latest)
[![CI](https://github.com/masgzy/CFSTMobile/actions/workflows/ci.yml/badge.svg)](https://github.com/masgzy/CFSTMobile/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/masgzy/CFSTMobile?label=License)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Rust](https://img.shields.io/badge/Rust-Engine-DEA584?logo=rust&logoColor=white)](https://www.rust-lang.org)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2024)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![ABI](https://img.shields.io/badge/ABI-arm64%20%7C%20armv7%20%7C%20x86__64-lightgray)](#构建)

*Kotlin + Jetpack Compose UI · Rust 原生测速引擎 · 直接 C ABI / JNI · Material You*

</div>

CFSTMobile 是一款 Android 原生的 Cloudflare IP 优选与测速应用。它不做 WebView 壳、不依赖 gomobile —— 测速核心是 Rust 编译的 `libcfst.so`,通过 JNI 直连符号供 Kotlin 调用;界面为纯 Kotlin + Jetpack Compose(Masonry 级 Material 3,支持 Material You 动态取色)。导出与上传协议与 [PoemMisty/CFData-WEB](https://github.com/PoemMisty/CFData-WEB) 保持兼容,CFData 用户可以无缝迁移。

> ⚠️ 非官方工具,与 Cloudflare, Inc. 无关联。仅供网络质量研究,请遵守当地法律法规。

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🔍 **延迟测速** | TCPing / HTTPing 双模式,每 IP 多次采样取平均;数据中心(Colo)识别 |
| 🚀 **下载测速** | 官方大流量端点,单 IP 定时采样,中位数-MAD 高斯加权算法 |
| 🎯 **智能筛选** | z-score 三维(速度/延迟/丢包)综合评分排序;延迟/丢包/速度阈值过滤 |
| 🌐 **IP 来源** | 内置 Cloudflare 官方段勾选、自定义 CIDR(支持 `1.2.3.0/24=50` 定量采样)、在线获取(可配 GitHub 镜像) |
| 📤 **结果导出** | TXT(CFData-WEB 兼容 `IP:端口#数据中心`)/ CSV(XIU2 CloudflareST 兼容列序),实时预览 + 剪贴板 |
| ☁️ **edgetunnel 上传** | 结果直接写入 edgetunnel(CM 系 Worker)订阅 `ADD.txt`,覆盖/追加双模式,多账户管理;协议与 CFData-WEB 一致 |
| 🐙 **GitHub 上传** | 优选结果提交到 GitHub 仓库(Contents API,新建/更新自动识别),raw 直链可分享/订阅;与 CFData-WEB 同一 API 行为 |
| 🧩 **Material You** | Material 3 组件 + Android 12+ 动态取色(可关闭回退品牌蓝);深色模式自适应 |
| 🗄️ **历史记录** | 测速结果本地留档,随时回看、再次导出 |
| 🔌 **演示模式** | 未检测到引擎 `.so` 时自动降级为 Mock 数据,UI 开发/体验不受阻 |

## 📲 下载安装

从 [Releases](https://github.com/masgzy/CFSTMobile/releases/latest) 下载 APK:

- **arm64-v8a** —— 2016 年后绝大多数手机,推荐
- **armeabi-v7a** —— 老旧 32 位设备
- **x86_64** —— 模拟器 / ChromeOS
- **universal** —— 全架构通吃包

Release 产物由 GitHub Actions 自动构建并以仓库 Secrets 证书签名,后续版本可直接覆盖安装升级。

## 🚀 快速上手

1. 首页点右上 ⚙️ 进入设置,选择 IP 来源(默认内置全部官方段)并按需调整并发/端口/阈值;
2. 回到首页点击「开始测速」,实时查看延迟阶段与下载阶段进度;
3. 结束后在结果列表点击条目可复制 `IP:端口`;
4. 点「导出」打开底部抽屉:选格式复制,或选择 edgetunnel 账户一键上传。

## 🔄 从 CFData-WEB 迁移

CFSTMobile 在导出与上传两条链路上与 CFData-WEB 对齐,**无需重新适应**:

| 能力 | CFData-WEB | CFSTMobile |
|------|-----------|------------|
| TXT 导出 | `IP:端口#数据中心`,compact 字段、`-` 分隔 | ✅ 同一格式(`ExportFormatter.toTxt`) |
| CSV 导出 | 表头 + 自定义字段 | ✅ XIU2 CloudflareST 列序互通 |
| edgetunnel 上传 | 登录 `/login` → 读写 `/admin/ADD.txt`,覆盖/追加 | ✅ 完全相同的协议与语义 |
| GitHub 上传 | Contents API:取 SHA → PUT,默认 `main` 分支 | ✅ 同一 API 与默认值(`GithubClient`) |
| 多账户管理 | 浏览器 localStorage,可选加密保存密码 | ✅ 应用私有存储,可选保存密码 |
| 浏览器内测速 | ✅ Web 版 | ➖ 移动端走原生引擎,速度更准 |

测得的优选结果格式一致,贴回 CFData-WEB、edgetunnel 或任意代理软件的优选列表都可直接使用。

## 🛠️ 构建指南

```bash
# 1) 引擎 .so(依赖 rustup + Android NDK;脚本自动安装 cargo-ndk,输出三 ABI)
./engine/build-so.sh

# 2) App(debug 可直接跑:无 .so 时自动进入演示模式)
./gradlew :app:assembleDebug
```

Release 签名:工程根目录放 `keystore.properties`(`storeFile` / `storePassword` / `keyAlias` / `keyPassword`),CI 会从仓库 Secrets(`KEYSTORE_BASE64` 等)自动生成。推送 `v*` 标签即触发 Release 流程(引擎 jniLibs 包 + 签名 APK + R8 mapping)。

### 技术栈

| 层 | 技术 |
|----|------|
| 引擎 | Rust cdylib(`tokio` + `hyper` + `rustls`),16KB 页对齐,`cargo-ndk` 交叉编译 |
| FFI | 纯 C ABI(`cfst_*`)+ `Java_com_masgzy_cfstmobile_ffi_CfstFfi_*` JNI 直连符号,JSON 字符串协议 |
| App | Kotlin 2.4 + Jetpack Compose(BOM 2026.08.00,Material 3)、kotlinx-serialization、ViewMode + 250ms 轮询 |
| CI/CD | GitHub Actions:引擎单测 → Android 交叉编译冒烟 → 16KB 对齐校验 → App 单测 → 签名 Release |

## 🤝 贡献

欢迎 Issue 与 PR:功能建议(尤其是与 CFData-WEB 的能力对齐)、引擎与 ABI 层的健壮性改进、多语言翻译。提交前请确保 `./gradlew :app:testDebugUnitTest` 通过。

## 🙏 致谢

- [GuangYu-yu/CloudflareST-Rust](https://github.com/GuangYu-yu/CloudflareST-Rust) —— Rust 测速引擎基座(GPL-3.0)
- [Hsia97/CFSTAPP](https://github.com/Hsia97/CFSTAPP) —— UI 结构与交互参照
- [PoemMisty/CFData-WEB](https://github.com/PoemMisty/CFData-WEB) —— 导出格式与 edgetunnel 上传协议参照
- [XIU2/CloudflareSpeedTest](https://github.com/XIU2/CloudflareSpeedTest) —— CSV 列序与项目生态的源头

## 📄 License

[GPL-3.0](LICENSE)。引擎部分衍生自 CloudflareST-Rust(GPL-3.0),动态链接构成合并作品,本项目整体以 GPL-3.0 发布;UI 参照 CFSTAPP、协议参照 CFData-WEB,版权归各自作者所有。
