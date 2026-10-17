import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 签名信息（keystore.properties 不入库，由 CI 从 Secrets 生成，模式同 AnythingMobile）：
//   storeFile / storePassword / keyAlias / keyPassword
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.masgzy.cfstmobile"
    // 37 = Android 17：与 Compose BOM 2026.08.00（ui 1.12.0）的 AAR 元数据要求一致
    compileSdk = 37

    defaultConfig {
        applicationId = "com.masgzy.cfstmobile"
        // 引擎 .so 以 --platform 24 交叉编译，二者保持一致
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 按 ABI 拆分产物：三种单架构 APK + 一个 universal 通吃包
    // （.so 由 engine/build-so.sh 或 CI 注入 app/src/main/jniLibs，Gradle 原样打包）
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // 签名配置必须先于 buildTypes 声明：buildTypes.release 里 getByName("release") 是立即求值
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 深度收缩：大头是 material-icons-extended（仅引用少量图标），
            // 开启后按实际引用裁剪；mapping.txt 由 release workflow 归档。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // CI 从 Secrets 解码证书并生成 keystore.properties 后自动签名；
            // 本地无该文件时退回未签名 release（不影响 debug 开发）。
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP 9 内置 Kotlin：android.kotlinOptions{} 已移除，
    // 按官方迁移指南改用 kotlin.compilerOptions{}（jvmTarget 需与 Java 一致）。
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        // 生成 BuildConfig：关于页版本号用 BuildConfig.VERSION_NAME
        buildConfig = true
    }

    testOptions {
        // 协议模型（kotlinx-serialization）为纯 JVM 逻辑，单测直接跑；
        // 若有代码触到 android.util.Log 等桩实现，返回默认值而非抛错。
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose（BOM 2026.08.00：ui/foundation 1.12.0、material3 1.4.0、icons 1.7.8）
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // 扩展图标集（CloudQueue/Stop/Share 等）；release 经 R8 裁剪只保留实际引用
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // FFI 协议 JSON（配置/进度/结果）+ 设置持久化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // JVM 单测：FFI JSON 协议回归网（对齐 engine/README.md 的样例报文）
    testImplementation("junit:junit:4.13.2")

    // 仪器化测试（后续接 emulator workflow：JNI 加载 / Activity 冒烟）
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
