package com.masgzy.cfstmobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 主题：默认 Material You（Android 12+ 从壁纸动态取色），
 * 低版本或用户关闭动态取色时回退 Cloudflare 蓝品牌色。
 * 延迟/丢包三档配色为语义色，不随主题变化（与 CFSTAPP 一致）。
 */
val CfBlue = Color(0xFF1976D2)
val CfBlueDark = Color(0xFF1565C0)
val CfBlueLight = Color(0xFFE3F2FD)
val CfOrange = Color(0xFFFF5722)
val OkGreen = Color(0xFF4CAF50)
val WarnOrange = Color(0xFFFF9800)
val BadRed = Color(0xFFF44336)

private val LightColors = lightColorScheme(
    primary = CfBlue,
    onPrimary = Color.White,
    primaryContainer = CfBlueLight,
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = CfOrange,
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1E4B8F),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFFF8A65),
    onSecondary = Color(0xFF3E2723),
)

@Composable
fun CfstTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Material You 动态取色开关（Android 12+ 生效，其余版本恒为品牌色） */
    dynamicColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

/** 延迟三档配色（<100 绿 / <200 橙 / 其余红），语义色不随动态取色变化 */
fun delayColor(delayMs: Double): Color = when {
    delayMs < 100 -> OkGreen
    delayMs < 200 -> WarnOrange
    else -> BadRed
}

/** 丢包配色：0 丢包绿色，否则橙色 */
fun lossColor(lossRate: Double): Color = if (lossRate <= 0.0) OkGreen else WarnOrange
