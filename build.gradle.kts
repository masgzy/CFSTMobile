// CFSTMobile 根构建脚本
// 插件版本统一在此声明，子模块引用时不带版本号。
// 工具链与 masgzy/AnythingMobile 保持一致（AGP 9.x 内置 Kotlin）：
//   - com.android.application 启用内置 Kotlin，无需 org.jetbrains.kotlin.android；
//   - 仅额外挂 Compose 编译器插件与 kotlinx-serialization 插件（版本 = Kotlin 版本）。
plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
