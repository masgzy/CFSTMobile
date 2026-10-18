package com.masgzy.cfstmobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.masgzy.cfstmobile.data.UiSettings

/**
 * 主题（对齐 Material You "Full" 标准：MD3 组件全覆盖 + Dynamic Color + Expressive 动效）：
 * - Android 12+ 默认从壁纸动态取色（dynamicLight/DarkColorScheme，AOSP 65 色调色板）
 * - 低版本或关闭动态取色时回退 Cloudflare 蓝品牌色（完整 M3 tonal 方案，明暗全槽位）
 * - AMOLED 纯黑：深色模式下表面/容器全部转纯黑系（ImageToolbox 等标杆应用的标配选项）
 * - Expressive 动效：CfMotion 官方 spring 参数（见 Motion.kt，随各组件接入）
 * 颜色模式（自动/浅色/深色）对齐 CFData-WEB 设置菜单；
 * 延迟/丢包三档配色与绿色操作按钮为语义色，不随主题变化。
 */
val CfBlue = Color(0xFF0B57D0)
val OkGreen = Color(0xFF4CAF50)
val WarnOrange = Color(0xFFFF9800)
val BadRed = Color(0xFFF44336)

/** CFData-WEB 语义色：绿色操作按钮（一键复制/结果操作/上传） */
val CfGreen = Color(0xFF10B981)

/** CFData-WEB 终端日志面板配色（固定深底绿字，不随主题变化） */
val LogBg = Color(0xFF0F172A)
val LogBorder = Color(0xFF1E293B)
val LogGreen = Color(0xFF34D399)

/**
 * Cloudflare 蓝品牌回退方案（动态取色不可用时）。
 * 完整 M3 tonal 槽位：强调色做前景、中性色做背景（source.android.com 官方规则），
 * secondary/tertiary 为同种子调和色，surfaceContainer 五档提供层级。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    inversePrimary = Color(0xFFA8C7FA),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF715573),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCD7FA),
    onTertiaryContainer = Color(0xFF29132D),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceTint = Color(0xFF0B57D0),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFF0F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceDim = Color(0xFFD9DAE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    inversePrimary = Color(0xFF0B57D0),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293141),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDFBBDE),
    onTertiary = Color(0xFF402843),
    tertiaryContainer = Color(0xFF583E5A),
    onTertiaryContainer = Color(0xFFFCD7FA),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    surfaceTint = Color(0xFFA8C7FA),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3135),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B3B43),
    surfaceDim = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)

/** AMOLED 纯黑：深色方案的所有表面/容器槽位转纯黑系（仅背景层，前景/强调色不动） */
private val DarkColorsAmoled = DarkColors.copy(
    background = Color(0xFF000000),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF1C1F24),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0C10),
    surfaceContainer = Color(0xFF0E1116),
    surfaceContainerHigh = Color(0xFF15181D),
    surfaceContainerHighest = Color(0xFF1C1F24),
)

@Composable
fun CfstTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You 动态取色开关（Android 12+ 生效，其余版本恒为品牌色） */
    dynamicColors: Boolean = true,
    /** 颜色模式：auto 跟随系统 / light / dark（对齐 CFData 颜色模式菜单） */
    themeMode: String = UiSettings.THEME_AUTO,
    /** AMOLED 纯黑深色（仅深色模式下生效；动态取色时对动态方案做同样处理） */
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        UiSettings.THEME_LIGHT -> false
        UiSettings.THEME_DARK -> true
        else -> darkTheme
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (dark && amoled) dynamic.amoled() else dynamic
        }
        dark -> if (amoled) DarkColorsAmoled else DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** 动态取色方案转 AMOLED：所有表面/背景槽位压到纯黑系，容器按层级保留微弱灰度 */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0C10),
    surfaceContainer = Color(0xFF0E1116),
    surfaceContainerHigh = Color(0xFF15181D),
    surfaceContainerHighest = Color(0xFF1C1F24),
)

/** 延迟三档配色（<100 绿 / <200 橙 / 其余红），语义色不随动态取色变化 */
fun delayColor(delayMs: Double): Color = when {
    delayMs < 100 -> OkGreen
    delayMs < 200 -> WarnOrange
    else -> BadRed
}

/** 丢包配色：0 丢包绿色，否则橙色 */
fun lossColor(lossRate: Double): Color = if (lossRate <= 0.0) OkGreen else WarnOrange
