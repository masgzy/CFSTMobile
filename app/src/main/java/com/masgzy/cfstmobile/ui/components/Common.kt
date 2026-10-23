package com.masgzy.cfstmobile.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.util.Locale

/** 复制到剪贴板并 Toast 提示 */
fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("CFSTMobile", text))
    Toast.makeText(context, "已复制：${if (text.length > 60) text.take(60) + "…" else text}", Toast.LENGTH_SHORT).show()
}

/** 固定美式小数点格式（避免区域设置输出逗号小数） */
fun fmt2(value: Double): String = String.format(Locale.US, "%.2f", value)

/** 固定美式小数点格式（1 位小数） */
fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)

/** CFData TLS 端口集合（httping 延迟倍率 4.0 的判定依据） */
private val TLS_PORTS = setOf(443, 2053, 2083, 2087, 2096, 8443)

/**
 * 延迟着色判定倍率（对齐 CFData getLatencyColorFactor）：
 * tcping 恒 1.0；httping 官方按端口（TLS 端口集合 4.0 / 其余 1.3），
 * 非标按 TLS 开关（开 4.0 / 关 1.3）。
 */
fun latencyColorFactor(isHttping: Boolean, port: Int, isOfficial: Boolean, nsbTls: Boolean): Double = when {
    !isHttping -> 1.0
    isOfficial -> if (port in TLS_PORTS) 4.0 else 1.3
    else -> if (nsbTls) 4.0 else 1.3
}
