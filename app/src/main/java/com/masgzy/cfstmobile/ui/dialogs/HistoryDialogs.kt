package com.masgzy.cfstmobile.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masgzy.cfstmobile.R
import com.masgzy.cfstmobile.data.HistoryRecord
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.ui.components.copyToClipboard
import com.masgzy.cfstmobile.ui.components.fmt2
import com.masgzy.cfstmobile.ui.theme.BadRed
import com.masgzy.cfstmobile.viewmodel.TestViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录对话框（CFSTAPP 同款：记录列表 + top3 预览 + 删除/清空 + 详情）。
 */
@Composable
fun HistoryDialog(viewModel: TestViewModel, onDismiss: () -> Unit) {
    val records by viewModel.history.collectAsStateWithLifecycle()
    var detailIndex by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "历史记录") },
        text = {
            Column(modifier = Modifier.height(400.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "共 ${records.size} 条记录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = { viewModel.clearHistory() },
                        colors = ButtonDefaults.buttonColors(containerColor = BadRed),
                    ) { Text("清空全部") }
                }
                Spacer(Modifier.height(8.dp))

                if (records.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "暂无历史记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(records) { index, record ->
                            HistoryRecordCard(
                                index = index,
                                record = record,
                                onDelete = { viewModel.deleteHistory(index) },
                                onShowDetail = { detailIndex = index },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )

    detailIndex?.let { index ->
        records.getOrNull(index)?.let { record ->
            HistoryDetailDialog(record = record, onDismiss = { detailIndex = null })
        }
    }
}

@Composable
private fun HistoryRecordCard(
    index: Int,
    record: HistoryRecord,
    onDelete: () -> Unit,
    onShowDetail: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "记录 ${index + 1}（${record.items.size} 个结果）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = "${record.summary} · ${formatTime(record.timestamp)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sym_delete),
                        contentDescription = "删除记录",
                        tint = BadRed,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            record.items.take(3).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "${item.ip}:${item.port}", fontSize = 12.sp)
                    Row {
                        Text(
                            text = "${fmt2(item.delayMs)}ms",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.downloadSpeedMbps?.let { "${fmt2(it)}Mbps" } ?: "-",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (record.items.size > 3) {
                Text(
                    text = "… 还有 ${record.items.size - 3} 项",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onShowDetail) { Text("查看详情", fontSize = 12.sp) }
            }
        }
    }
}

/** 历史详情：全部结果列表，点击复制 ip:port */
@Composable
fun HistoryDetailDialog(record: HistoryRecord, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "历史记录详情") },
        text = {
            Column {
                Text(
                    text = "${record.summary} · ${formatTime(record.timestamp)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(record.items) { index, item ->
                        ResultLineCard(rank = index + 1, item = item) {
                            copyToClipboard(context, item.address())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultLineCard(rank: Int, item: CfstResultItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(text = "#$rank  ${item.ip}:${item.port}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "延迟：${fmt2(item.delayMs)} ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = item.downloadSpeedMbps?.let { "下载：${fmt2(it)} Mbps" } ?: "未测速",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "丢包：${fmt2(item.lossRate * 100)}%",
                fontSize = 12.sp,
                color = if (item.lossRate <= 0.0) {
                    com.masgzy.cfstmobile.ui.theme.OkGreen
                } else {
                    com.masgzy.cfstmobile.ui.theme.WarnOrange
                },
            )
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
