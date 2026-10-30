package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.masgzy.cfstmobile.R
import com.masgzy.cfstmobile.data.DcInfo
import com.masgzy.cfstmobile.data.DcStep
import com.masgzy.cfstmobile.data.DcTestUiState
import com.masgzy.cfstmobile.data.ScanRow
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.ui.theme.delayColor
import com.masgzy.cfstmobile.ui.theme.lossColor
import com.masgzy.cfstmobile.ui.theme.speedColor
import com.masgzy.cfstmobile.viewmodel.TestViewModel

/**
 * 扫描结果区（对齐 CFData-WEB #scan tab）：
 * 数据中心汇总表（dcList 就绪后显示，操作列「选择测试/重新测试/测试中」）+ 扫描结果宽表。
 * 官方 8 列 = IP/端口/数据中心/落地区域/地区/城市/扫描方式/延迟；
 * 非标 8 列 = IP地址/端口号/丢包率/扫描方式/网络延迟/下载速度/OriginalInput/数据中心。
 * 颜色对齐 CFData：延迟六档（httping 除以倍率）、丢包率/测速分级色；
 * 仅 IP/端口单元格可点复制（对齐 CFData copy-ip）。
 */
@Composable
fun ResultsSection(
    viewModel: TestViewModel,
    scanRows: List<ScanRow>,
    dcState: DcTestUiState,
    official: Boolean,
    onCopyClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    val context = LocalContext.current
    val s = viewModel.settings.value
    val isHttping = s.pingMode == UiSettings.PING_HTTP
    val scanModeLabel = if (isHttping) "HTTPing" else "TCPing"

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---------------- 数据中心汇总（对齐 #dcSection：dcList 非空才显示；渐进聚合时随进度出现） ----------------
        if (official && dcState.dcList.isNotEmpty()) {
            DcSummaryTable(
                dcList = dcState.dcList,
                testedDcs = dcState.testedDcs,
                testingDc = dcState.selectedDc.takeIf { dcState.step == DcStep.TESTING },
                currentHint = dcState.message,
                onSelect = { viewModel.testDc(it) },
            )
            Spacer(Modifier.height(10.dp))
        }

        // ---------------- 扫描结果表 ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("扫描结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            CountBadge(scanRows.size)
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GreenButton(
                text = "一键复制",
                icon = R.drawable.ic_sym_content_copy,
                enabled = scanRows.isNotEmpty(),
                onClick = onCopyClick,
                modifier = Modifier.weight(1f),
            )
            GreenButton(
                text = "结果操作",
                icon = R.drawable.ic_sym_download,
                enabled = scanRows.isNotEmpty(),
                onClick = onExportClick,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // 复制回调：仅 IP 列复制 IP、端口列复制端口（对齐 CFData copy-ip 单元格语义）
        val onIpPortCell: (Int, Int) -> Unit = { row, col ->
            when (col) {
                0 -> scanRows.getOrNull(row)?.let { copyToClipboard(context, it.ip) }
                1 -> scanRows.getOrNull(row)?.let { copyToClipboard(context, it.port.toString()) }
            }
        }

        if (official) {
            // 行/色列表用 remember 缓存：数千行下每次重组重建两个 O(n) 列表是主要开销
            // （扫描行内容未变时直接复用）
            val officialRows = remember(scanRows, scanModeLabel) {
                scanRows.map { row ->
                    listOf(
                        row.ip,
                        row.port.toString(),
                        row.colo,
                        row.country.ifEmpty { "-" },
                        row.region.ifEmpty { "-" },
                        row.city.ifEmpty { "-" },
                        scanModeLabel,
                        "${fmt0(row.delayMs)}ms",
                    )
                }
            }
            val officialColors = remember(scanRows, isHttping) {
                scanRows.map { row ->
                    listOf(null, null, null, null, null, null, null, delayColor(row.delayMs, latencyColorFactor(isHttping, row.port, isOfficial = true, nsbTls = true)))
                }
            }
            ScrollTable(
                columns = listOf("IP", "端口", "数据中心", "落地区域", "地区", "城市", "扫描方式", "延迟"),
                rows = officialRows,
                cellColors = officialColors,
                onCellClick = onIpPortCell,
            )
        } else {
            // 精简/完整表格视图（对齐 CFData nsbCompactView，默认精简）
            val compactView = s.nsbCompactView
            val columns = buildList {
                addAll(listOf("IP地址", "端口号", "丢包率", "扫描方式", "网络延迟", "下载速度", "OriginalInput", "数据中心"))
                if (!compactView) addAll(listOf("源IP位置", "地区", "城市"))
            }
            val widths = buildList {
                addAll(listOf(120.dp, 56.dp, 64.dp, 80.dp, 80.dp, 80.dp, 150.dp, 72.dp))
                if (!compactView) addAll(listOf(88.dp, 88.dp, 96.dp))
            }
            val nsbRows = remember(scanRows, scanModeLabel, compactView) {
                scanRows.map { row ->
                    val base = listOf(
                        row.ip,
                        row.port.toString(),
                        "${fmt1(row.lossRate * 100)}%",
                        scanModeLabel,
                        "${fmt0(row.delayMs)}ms",
                        row.speedMbps?.let { "${fmt2(it)}MB/s" } ?: "-",
                        row.origin ?: "-",
                        row.colo,
                    )
                    if (compactView) base else base + listOf(
                        row.country.ifEmpty { "-" },
                        row.region.ifEmpty { "-" },
                        row.city.ifEmpty { "-" },
                    )
                }
            }
            val nsbColors = remember(scanRows, isHttping, compactView, s.nsbTls) {
                scanRows.map { row ->
                    val colors = mutableListOf<Color?>(
                        null, null,
                        lossColor(row.lossRate),
                        null,
                        delayColor(row.delayMs, latencyColorFactor(isHttping, row.port, isOfficial = false, nsbTls = s.nsbTls)),
                        row.speedMbps?.let { speedColor(it) },
                        null, null,
                    )
                    if (!compactView) { colors.addAll(listOf(null, null, null)) }
                    colors
                }
            }
            ScrollTable(
                columns = columns,
                columnWidths = widths,
                rows = nsbRows,
                cellColors = nsbColors,
                onCellClick = onIpPortCell,
            )
        }
    }
}

/**
 * 数据中心汇总表（对齐 #dcTable：数据中心/落地区域/城市/IP数量/最低延迟/操作）。
 * 操作列按钮三态：测试中...（禁用）/ 重新测试（已测过）/ 选择测试。
 */
@Composable
private fun DcSummaryTable(
    dcList: List<DcInfo>,
    testedDcs: Set<String>,
    testingDc: String?,
    currentHint: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_sym_dns),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text("数据中心汇总", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            CountBadge(dcList.size)
            Spacer(Modifier.weight(1f))
            if (testingDc != null) {
                Text(
                    text = "测试中: $testingDc",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            } else if (currentHint != null && testedDcs.isNotEmpty()) {
                Text(
                    text = currentHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 汇总表体：ScrollTable 自动列宽（城市等长文本不再被 weight 均分截断），
        // 操作列单元格点击 = 选择测试（对齐 CFData dcTable 自然宽度）
        val summaryRows = dcList.map { dc ->
            listOf(
                dc.colo,
                dc.country.ifEmpty { "-" },
                dc.city.ifEmpty { "-" },
                dc.ipCount.toString(),
                "${dc.minLatencyMs}ms",
                if (testingDc != null && testingDc.equals(dc.colo, ignoreCase = true)) {
                    "测试中..."
                } else if (dc.colo in testedDcs) {
                    "重新测试"
                } else {
                    "选择测试"
                },
            )
        }
        val summaryColors = remember(dcList, testedDcs, testingDc) {
            dcList.map { dc ->
                val testing = testingDc != null && testingDc.equals(dc.colo, ignoreCase = true)
                listOf(
                    null, null, null, null,
                    delayColor(dc.minLatencyMs.toDouble()),
                    if (testing) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
        ScrollTable(
            columns = listOf("数据中心", "落地区域", "城市", "IP数量", "最低延迟", "操作"),
            rows = summaryRows,
            maxHeight = 260.dp,
            cellColors = summaryColors,
            onCellClick = { row, col ->
                if (col != 5) return@ScrollTable
                val dc = dcList.getOrNull(row) ?: return@ScrollTable
                val testing = testingDc != null && testingDc.equals(dc.colo, ignoreCase = true)
                if (!testing) onSelect(dc.colo)
            },
        )
    }
}

/** 整数毫秒显示 */
internal fun fmt0(value: Double): String = if (value == value.toLong().toDouble()) {
    value.toLong().toString()
} else {
    String.format(java.util.Locale.US, "%.0f", value)
}
