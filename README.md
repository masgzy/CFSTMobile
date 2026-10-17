# CFSTMobile

Android 原生 Cloudflare 优选测速工具。Kotlin + Jetpack Compose UI，Rust 核心引擎。非官方工具，与 Cloudflare, Inc. 无关联。GPL-3.0。

## 项目结构

```
CFSTMobile/
├── engine/                      # Rust 测速引擎（libcfst.so，C ABI FFI）
│   ├── src/ffi.rs               #   cfst_* 导出函数（Kotlin/JNI 直接调用）
│   ├── src/engine.rs            #   会话状态机（create → start → poll → take_result）
│   ├── README.md                #   ★ FFI 契约：配置/进度/结果 JSON 协议 + Kotlin 声明示例
│   └── build-so.sh              #   一键构建三 ABI .so（16KB 页对齐）
├── app/                         # Kotlin + Jetpack Compose App（待落地）
│   └── src/main/jniLibs/        #   engine/build-so.sh 的产物目录
└── .github/workflows/
    ├── ci.yml                   # 引擎单测 + Android 交叉编译冒烟 + 16KB 对齐校验
    └── release.yml              # v* 标签 → .so 挂 Release；app/ 存在时自动签名 APK
```

## 状态

- [x] **Rust 引擎**（`engine/`，改造自 [GuangYu-yu/CloudflareST-Rust](https://github.com/GuangYu-yu/CloudflareST-Rust)）：
      cdylib 库化、C ABI FFI、协作式停止、进度/结果 JSON 协议；单测与真实网络
      FFI 端到端验证通过，Android 交叉编译由 CI 把关。
- [ ] **Kotlin App**（`app/`）：待实现。FFI 契约与 Kotlin/JNI 声明示例见
      [engine/README.md](engine/README.md)；签名/发布沿用 repo Secrets
      （`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），
      流程参照 [masgzy/AnythingMobile](https://github.com/masgzy/AnythingMobile)。

## 构建

```bash
# 引擎 .so（依赖 rustup + Android NDK + cargo-ndk，脚本自动装 cargo-ndk）
./engine/build-so.sh

# 桌面 CLI（调试）
cd engine && cargo run --release --features cli -- -ip 1.1.1.1 -dd
```

推送 `v*` 标签即触发 Release 流程；CI 会在 app/ 工程出现后自动追加 APK 构建任务。

## License

GPL-3.0。引擎部分衍生自 CloudflareST-Rust（GPL-3.0），版权归各自作者所有。
