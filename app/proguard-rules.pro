# JNI 边界：libcfst.so 按完整类名查找 Java_com_masgzy_cfstmobile_ffi_CfstFfi_*
# 符号，类名/方法名混淆 = UnsatisfiedLinkError，必须整体保留。
-keep class com.masgzy.cfstmobile.ffi.CfstFfi { *; }

# 任何 JNI native 方法名与签名不可混淆（通用兜底）
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# kotlinx.serialization：生成的 serializer 与 Companion 查找路径不可混淆
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.masgzy.cfstmobile.**$$serializer { *; }
-keepclassmembers class com.masgzy.cfstmobile.** {
    *** Companion;
}
-keepclasseswithmembers class com.masgzy.cfstmobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 崩溃堆栈保留行号（体积代价极小，配合 mapping.txt 可还原）
-keepattributes SourceFile,LineNumberTable

# kotlinx-coroutines 可选传递依赖不在类路径，静默警告
-dontwarn org.reactivestreams.**
