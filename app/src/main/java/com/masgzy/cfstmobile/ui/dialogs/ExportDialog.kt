package com.masgzy.cfstmobile.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.ui.components.copyToClipboard
import com.masgzy.cfstmobile.ui.components.fmt2

/**
 * 导出对话框：把当前结果组装成 CSV（列序对齐 XIU2 CloudflareST 的 result.csv），
 * 复制到剪贴板。
 */
@Composable
fun ExportDialog(results: List<CfstResultItem>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val csv = remember(results) {
        buildString {
            appendLine("IP 地址,端口,已发送,已接收,丢包率(%),平均延迟(ms),下载速度(Mbps),数据中心")
            results.forEach { r ->
                appendLine(
                    listOf(
                        r.ip,
                        r.port.toString(),
                        r.sent.toString(),
                        r.received.toString(),
                        fmt2(r.lossRate * 100),
                        fmt2(r.delayMs),
                        r.downloadSpeedMbps?.let { fmt2(it) } ?: "",
                        r.colo ?: "",
                    ).joinToString(","),
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "导出结果") },
        text = {
            Column {
                Text(text = "共 ${results.size} 条结果", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(text = "点击「复制到剪贴板」将按 CSV 格式复制全部结果", fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                copyToClipboard(context, csv)
                onDismiss()
            }) { Text("复制到剪贴板") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
