package com.masgzy.cfstmobile.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import java.util.Locale

/** 复制到剪贴板并 Toast 提示 */
fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("CFSTMobile", text))
    Toast.makeText(context, "已复制：$text", Toast.LENGTH_SHORT).show()
}

/** 固定美式小数点格式（避免区域设置输出逗号小数） */
fun fmt2(value: Double): String = String.format(Locale.US, "%.2f", value)

/** 固定美式小数点格式（1 位小数） */
fun fmt1(value: Double): String = String.format(Locale.US, "%.1f", value)

/** 对话框内的小节标题 */
@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 单行整数输入框（非法输入不回写，保留原始文本便于继续编辑） */
@Composable
fun IntField(
    label: String,
    value: Int,
    enabled: Boolean = true,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toIntOrNull()?.let(onValue)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 单行小数输入框 */
@Composable
fun DoubleField(
    label: String,
    value: Double,
    enabled: Boolean = true,
    onValue: (Double) -> Unit,
) {
    var text by remember(value) { mutableStateOf(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toDoubleOrNull()?.let(onValue)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = TextStyle(fontSize = 14.sp),
        modifier = Modifier.fillMaxWidth(),
    )
}
