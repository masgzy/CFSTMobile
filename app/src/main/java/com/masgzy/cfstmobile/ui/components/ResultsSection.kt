package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
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
import com.masgzy.cfstmobile.viewmodel.TestUiState

/**
 * 扫描结果卡（对齐 CFData-WEB #scan section-box）：
 * 标题 + 计数徽章 + 绿色操作钮（一键复制/结果操作）+ 排序筛选 + 表格。
 * 行点击复制 ip:port；表格列 IP/端口/数据中心/延迟/丢包/速度。
 */
@Composable
fun ResultsSection(
    state: TestUiState,
    sortBy: String,
    onSortChange: (String) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sorted = remember(state.results, sortBy) { state.results.sortedByMode(sortBy) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 标题 + 徽章 + 排序（对齐 section-box h3 + badge + table-filter-select）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("扫描结果", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                CountBadge(sorted.size)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SortChip("综合", sortBy == SortMode.SCORE) { onSortChange(SortMode.SCORE) }
                Spacer(Modifier.width(4.dp))
                SortChip("延迟", sortBy == SortMode.DELAY) { onSortChange(SortMode.DELAY) }
                Spacer(Modifier.width(4.dp))
                SortChip("速度", sortBy == SortMode.SPEED) { onSortChange(SortMode.SPEED) }
            }
        }
        Spacer(Modifier.height(10.dp))

        // 绿色操作钮（对齐 .export-btn：一键复制 / 结果操作）
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GreenButton(
                text = "一键复制",
                enabled = sorted.isNotEmpty(),
                onClick = {
                    val text = com.masgzy.cfstmobile.model.ExportFormatter.toTxt(sorted)
                    copyToClipboard(context, text)
                },
            )
            GreenButton(
                text = "结果操作（导出 / 上传）",
                enabled = sorted.isNotEmpty(),
                onClick = onExportClick,
            )
        }
        Spacer(Modifier.height(10.dp))

        CfTable(
            columns = listOf("IP", "端口", "数据中心", "延迟", "丢包", "速度"),
            rows = sorted.map { item ->
                listOf(
                    item.ip,
                    item.port.toString(),
                    item.colo ?: "-",
                    "${fmt0(item.delayMs)}ms",
                    "${fmt1(item.lossRate * 100)}%",
                    item.downloadSpeedMbps?.let { "${fmt2(it)}M" } ?: "-",
                )
            },
            onRowClick = { index -> copyToClipboard(context, sorted[index].address()) },
        )
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, maxLines = 1) },
        modifier = Modifier.height(30.dp),
    )
}

/** 整数毫秒显示（延迟不保留小数，表格更紧凑） */
private fun fmt0(value: Double): String = if (value == value.toLong().toDouble()) {
    value.toLong().toString()
} else {
    String.format(java.util.Locale.US, "%.0f", value)
}
