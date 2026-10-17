package com.masgzy.cfstmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// CFSTAPP 同款 Cloudflare 蓝主色 + 橙色强调；延迟/丢包沿用其三档配色
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
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** 延迟三档配色（<100 绿 / <200 橙 / 其余红），与 CFSTAPP 一致 */
fun delayColor(delayMs: Double): Color = when {
    delayMs < 100 -> OkGreen
    delayMs < 200 -> WarnOrange
    else -> BadRed
}

/** 丢包配色：0 丢包绿色，否则橙色 */
fun lossColor(lossRate: Double): Color = if (lossRate <= 0.0) OkGreen else WarnOrange
