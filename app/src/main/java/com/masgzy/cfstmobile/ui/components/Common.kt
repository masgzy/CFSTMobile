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
