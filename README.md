# CFSTMobile

Android 原生 Cloudflare 优选测速工具。Kotlin + Jetpack Compose UI，Rust 核心引擎。非官方工具，与 Cloudflare, Inc. 无关联。GPL-3.0。

## 项目结构

```
CFSTMobile/
├── engine/                      # Rust 测速引擎（libcfst.so，C ABI FFI）
│   ├── src/ffi.rs               #   cfst_* 导出函数（纯 C ABI）
│   ├── src/jni_bridge.rs        #   JNI 桥接（Java_* 符号，仅 Android 编译，ByteArray 字符串策略）
│   ├── src/engine.rs            #   会话状态机（create → start → poll → take_result）
│   ├── README.md                #   ★ FFI 契约：配置/进度/结果 JSON 协议
│   └── build-so.sh              #   一键构建三 ABI .so（16KB 页对齐）→ app/src/main/jniLibs
├── app/                         # Kotlin + Jetpack Compose App
│   ├── src/main/java/com/masgzy/cfstmobile/
│   │   ├── ffi/CfstFfi.kt       #   external fun 声明（与 jni_bridge.rs 符号一一对应）
│   │   ├── engine/              #   CfstSession（原生/Mock 双实现）、工厂与 ABI 检查
│   │   ├── model/Protocol.kt    #   配置/进度/结果 JSON 模型（kotlinx-serialization）
│   │   ├── data/                #   设置持久化、内置 IP 段与在线获取、历史记录
│   │   ├── viewmodel/           #   测速会话轮询（250ms）、状态与设置管理
│   │   └── ui/                  #   Compose UI（状态卡/结果列表/设置/IP段/历史/导出）
│   └── src/main/jniLibs/        #   build-so.sh 产物目录（gitignore，CI 注入）
└── .github/workflows/
    ├── ci.yml                   # 引擎单测 + 交叉编译冒烟 + 16KB 对齐 + App 单测/APK
    └── release.yml              # v* 标签 → .so 挂 Release + 签名 APK（多 ABI + universal）
```

## 状态

- [x] **Rust 引擎**（`engine/`，改造自 [GuangYu-yu/CloudflareST-Rust](https://github.com/GuangYu-yu/CloudflareST-Rust)）：
      cdylib 库化、C ABI FFI、协作式停止、进度/结果 JSON 协议；单测与真实网络
      FFI 端到端验证通过，Android 交叉编译由 CI 把关。
- [x] **JNI 桥接**（`engine/src/jni_bridge.rs`）：`Java_com_masgzy_cfstmobile_ffi_CfstFfi_*`
      直连符号；配置/进度/结果走 UTF-8 ByteArray（拷贝即释放，无悬垂）；
      仅依赖 `jni-sys`（零传递依赖）。
- [x] **Kotlin App**（`app/`）：Compose UI 参照 [Hsia97/CFSTAPP](https://github.com/Hsia97/CFSTAPP)
      的界面结构与交互（状态卡 / 结果列表点击复制 / 设置对话框 / IP 段勾选 /
      历史记录 / CSV 导出）；250ms 轮询驱动测速进度；未检测到 `libcfst.so`
      时自动进入演示模式（Mock 引擎），便于无 NDK 环境开发 UI。
      协议模型有 JVM 单测回归网（`ProtocolJsonTest`，样例报文与
      [engine/README.md](engine/README.md) 逐字对齐）。
- [ ] 后续：模拟器仪器化测试（JNI 加载 / Activity 冒烟）、更多结果可视化。

## 构建

```bash
# 引擎 .so（依赖 rustup + Android NDK + cargo-ndk，脚本自动装 cargo-ndk）
./engine/build-so.sh

# App（debug 直接跑，无 .so 时为演示模式；release 需 keystore.properties）
./gradlew :app:assembleDebug
```

推送 `v*` 标签即触发 Release 流程（引擎 .so + 签名 APK）；签名沿用 repo Secrets
（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），
流程参照 [masgzy/AnythingMobile](https://github.com/masgzy/AnythingMobile)。

## License

GPL-3.0。引擎部分衍生自 CloudflareST-Rust（GPL-3.0），UI 参照 CFSTAPP；
版权归各自作者所有。
