package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.SortMode
import com.masgzy.cfstmobile.model.sortedByMode
import com.masgzy.cfstmobile.ui.theme.delayColor
import com.masgzy.cfstmobile.ui.theme.lossColor
import com.masgzy.cfstmobile.viewmodel.TestUiState

/**
 * 结果区（CFSTAPP 同款）：标题 + 计数、排序筛选 Chips、定高 LazyColumn，
 * 点击条目复制 ip:port。
 */
@Composable
fun ResultsSection(
    state: TestUiState,
    sortBy: String,
    onSortChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sorted = remember(state.results, sortBy) { state.results.sortedByMode(sortBy) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("测速结果", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("共 ${sorted.size} 个", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = sortBy == SortMode.SCORE,
                    onClick = { onSortChange(SortMode.SCORE) },
                    label = { Text("综合") },
                )
                FilterChip(
                    selected = sortBy == SortMode.DELAY,
                    onClick = { onSortChange(SortMode.DELAY) },
                    label = { Text("延迟优先") },
                )
                FilterChip(
                    selected = sortBy == SortMode.SPEED,
                    onClick = { onSortChange(SortMode.SPEED) },
                    label = { Text("速度优先") },
                )
            }

            Spacer(Modifier.height(8.dp))

            if (sorted.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无测速结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(sorted) { index, item ->
                        ResultItem(rank = index + 1, item = item)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultItem(rank: Int, item: CfstResultItem) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick = { copyToClipboard(context, item.address()) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#$rank  ${item.ip}:${item.port}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                if (!item.origin.isNullOrBlank()) {
                    Text(
                        text = "原始输入：${item.origin}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = buildString {
                        append("${fmt1(item.lossRate * 100)}% 丢包")
                        item.colo?.let { append(" · $it") }
                        if (item.sent > 0) append("（${item.received}/${item.sent}）")
                    },
                    fontSize = 12.sp,
                    color = lossColor(item.lossRate),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${fmt2(item.delayMs)} ms",
                    fontSize = 14.sp,
                    color = delayColor(item.delayMs),
                )
                Text(
                    text = item.downloadSpeedMbps?.let { "${fmt2(it)} Mbps" } ?: "未测速",
                    fontSize = 12.sp,
                    color = if ((item.downloadSpeedMbps ?: 0.0) > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
