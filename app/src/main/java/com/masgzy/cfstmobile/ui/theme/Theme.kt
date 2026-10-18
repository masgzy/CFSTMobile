package com.masgzy.cfstmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.masgzy.cfstmobile.data.SeedColors
import com.masgzy.cfstmobile.data.UiSettings

/**
 * 主题（对齐 Material You "Full" 标准：MD3 组件全覆盖 + Dynamic Color + Expressive 动效）：
 * - Android 12+ 动态取色（dynamicLight/DarkColorScheme，AOSP 65 色调色板）
 * - 关闭动态取色时走 ImageToolbox 式调色引擎（DynamicTheme.kt）：
 *   种子色 -> HCT TonalPalette -> DynamicScheme（九种 PaletteStyle + 对比度）
 *   -> 错误色/expressive on 色/浅色表面重推 -> AMOLED 纯黑
 * - AMOLED 纯黑：独立开关（浅色模式也可开启），夜间模式下表面转纯黑（ImageToolbox 语义）
 * - 方案切换逐色 400ms 渐变（animateAllColors，ImageToolbox 同款）
 * - Expressive 动效：CfMotion 官方 spring 参数（见 Motion.kt，随各组件接入）
 * 颜色模式（自动/浅色/深色）对齐 CFData-WEB 设置菜单；
 * 延迟/丢包三档配色与绿色操作按钮为语义色，不随主题变化。
 */

/** CFData-WEB 语义色：绿色操作按钮（一键复制/结果操作/上传） */
val CfGreen = Color(0xFF10B981)

/** 延迟三档/状态横幅语义色（固定，不随主题变化） */
val OkGreen = Color(0xFF4CAF50)
val WarnOrange = Color(0xFFFF9800)
val BadRed = Color(0xFFF44336)

/** CFData-WEB 终端日志面板配色（固定深底绿字，不随主题变化） */
val LogBg = Color(0xFF0F172A)
val LogBorder = Color(0xFF1E293B)
val LogGreen = Color(0xFF34D399)

/** 延迟三档配色（<100 绿 / <200 橙 / 其余红），语义色不随动态取色变化 */
fun delayColor(delayMs: Double): Color = when {
    delayMs < 100 -> OkGreen
    delayMs < 200 -> WarnOrange
    else -> BadRed
}

/** 丢包配色：0 丢包绿色，否则橙色 */
fun lossColor(lossRate: Double): Color = if (lossRate <= 0.0) OkGreen else WarnOrange

/** SeedColors（持久化模型）-> ColorTuple（调色引擎种子） */
private fun SeedColors.toTuple(): ColorTuple = ColorTuple(
    primary = Color(primary.toInt()),
    secondary = secondary?.let { Color(it.toInt()) },
    tertiary = tertiary?.let { Color(it.toInt()) },
    surface = surface?.let { Color(it.toInt()) },
    neutralVariant = neutralVariant?.let { Color(it.toInt()) },
    error = error?.let { Color(it.toInt()) },
)

@Composable
fun CfstTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You 动态取色开关（Android 12+ 生效，其余恒走调色引擎） */
    dynamicColors: Boolean = true,
    /** 颜色模式：auto 跟随系统 / light / dark（对齐 CFData 颜色模式菜单） */
    themeMode: String = UiSettings.THEME_AUTO,
    /** AMOLED 纯黑深色（浅色模式下也可开启，夜间模式下生效；ImageToolbox 语义） */
    amoled: Boolean = false,
    /** 主题配色种子（动态取色关闭时生效） */
    colorTuple: SeedColors = SeedColors(),
    /** 调色板风格（PaletteStyle 枚举名） */
    paletteStyle: String = UiSettings.STYLE_DEFAULT,
    /** 对比度 -1..1（DynamicScheme contrastLevel） */
    themeContrast: Float = 0f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        UiSettings.THEME_LIGHT -> false
        UiSettings.THEME_DARK -> true
        else -> darkTheme
    }
    val style = runCatching { PaletteStyle.valueOf(paletteStyle) }
        .getOrDefault(PaletteStyle.TonalSpot)
    val colorScheme = rememberColorScheme(
        isDarkTheme = dark,
        amoledMode = amoled,
        colorTuple = colorTuple.toTuple(),
        style = style,
        contrastLevel = themeContrast.toDouble(),
        dynamicColor = dynamicColors,
    ).animateAllColors()
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
