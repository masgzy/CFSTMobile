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

/**
 * 延迟六档配色（逐字对齐 CFData-WEB LATENCY_COLORS），语义色不随动态取色变化：
 * ≤50 深绿 / ≤100 绿 / ≤200 浅绿 / ≤250 黄 / ≤3000 橙 / >3000 红。
 * [latencyFactor]：httping 延迟倍率放宽（TLS 端口 4.0 / 明文 1.3，tcping 1.0），
 * 与 CFData getLatencyColor 的 adjusted = v / factor 一致——判定用放宽后的值。
 */
fun delayColor(delayMs: Double, latencyFactor: Double = 1.0): Color {
    val adjusted = if (latencyFactor > 1.0) delayMs / latencyFactor else delayMs
    return when {
        adjusted <= 50 -> Color(0xFF1F8A3B)
        adjusted <= 100 -> Color(0xFF4CAF50)
        adjusted <= 200 -> Color(0xFF9CCC65)
        adjusted <= 250 -> Color(0xFFE0C341)
        adjusted <= 3000 -> Color(0xFFE67E22)
        else -> Color(0xFFD64541)
    }
}

/**
 * 测速配色（对齐 CFData speedColor：SPEED_GREEN = 10）：
 * >10MB/s 绿 / ≤10 橙 / 失败（0 或负值）红；未测速传 null 返回 onSurface。
 */
fun speedColor(speedMbps: Double?): Color? = when {
    speedMbps == null -> null
    speedMbps > SPEED_GREEN -> Color(0xFF10B981)
    speedMbps > 0 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

const val SPEED_GREEN = 10.0

/**
 * 丢包率配色：0 正常色（不强调）、≤10% 橙、>10% 红。
 */
fun lossColor(lossRate: Double): Color = when {
    lossRate <= 0.0 -> Color(0xFF9CCC65)
    lossRate <= 0.10 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

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
