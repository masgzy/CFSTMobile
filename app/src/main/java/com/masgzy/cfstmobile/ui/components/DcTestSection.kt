package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masgzy.cfstmobile.R
import com.masgzy.cfstmobile.data.DcSortKey
import com.masgzy.cfstmobile.data.DcStep
import com.masgzy.cfstmobile.data.DcTestItem
import com.masgzy.cfstmobile.data.DcTestUiState
import com.masgzy.cfstmobile.viewmodel.TestViewModel

/**
 * 详细测试结果区（对齐 CFData-WEB #test tab 官方结构）：
 * 标题「详细测试结果」+ 徽章 + 合格筛选下拉 + 动作按钮组
 * （一键测速/继续测速/一键排序/一键复制/结果操作，Material Symbols 图标）+
 * 12 列宽表：IP 地址/端口/数据中心/落地区域/地区/城市/扫描方式/丢包率/
 * 最小延迟/最大延迟/平均延迟/测速。
 * 点选 DC 后一条龙：延迟精测（tcping×10/httping×3，min/avg/max/丢包）→
 * 自动对通过 IP 做下载测速回填「测速」列。
 */

/** 合格筛选（对齐 officialTestQualifiedFilter） */
private const val FILTER_ALL = "all"
private const val FILTER_QUALIFIED = "qualified"
private const val FILTER_UNQUALIFIED = "unqualified"

@Composable
fun DcTestSection(
    viewModel: TestViewModel,
    state: DcTestUiState,
    hasResults: Boolean,
    speedMinMbps: Double,
    onExportClick: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        when (state.step) {
            DcStep.IDLE -> DcIdle(hasResults = hasResults, onStart = viewModel::startDcTest)
            DcStep.PROBING_COLO -> DcProbing(state = state, onCancel = viewModel::cancelDcTest)
            DcStep.ERROR -> DcError(state = state, onRetry = viewModel::startDcTest)
            else -> DcResults(
                viewModel = viewModel,
                state = state,
                speedMinMbps = speedMinMbps,
                onCopy = { copyToClipboard(context, it) },
                onExportClick = onExportClick,
            )
        }
    }
}

// ---------------------------------------------------------------- 空闲 / 探测中 / 错误

@Composable
private fun DcIdle(hasResults: Boolean, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "对本次测速结果按数据中心聚合，点选 DC 后对其全部 IP 做二次详细测试\n" +
                "（tcping×10 / httping×3 统计 min/avg/max/丢包，随后自动下载测速）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "开始探测数据中心",
            onClick = onStart,
            enabled = hasResults,
            icon = R.drawable.ic_sym_travel_explore,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        if (!hasResults) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "暂无测速结果，先完成一次扫描",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DcProbing(state: DcTestUiState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "正在识别数据中心归属（${state.probeDone}/${state.probeTotal}）",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "通过 speed.cloudflare.com/cdn-cgi/trace 获取每个 IP 所在的数据中心",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun DcError(state: DcTestUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.message ?: "数据中心测试失败",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.TextButton(onClick = onRetry) { Text("重试") }
    }
}

// ---------------------------------------------------------------- 结果区（DC_LIST / TESTING / DONE）

@Composable
private fun DcResults(
    viewModel: TestViewModel,
    state: DcTestUiState,
    speedMinMbps: Double,
    onCopy: (String) -> Unit,
    onExportClick: () -> Unit,
) {
    var filter by remember { mutableStateOf(FILTER_ALL) }
    var showSortMenu by remember { mutableStateOf(false) }

    val qualified: (DcTestItem) -> Boolean = { item ->
        item.speedMbps?.let { it >= speedMinMbps } == true
    }
    val visible = state.results.filter {
        when (filter) {
            FILTER_QUALIFIED -> qualified(it)
            FILTER_UNQUALIFIED -> !qualified(it)
            else -> true
        }
    }
    val scanModeLabel =
        if (viewModel.settings.value.pingMode == com.masgzy.cfstmobile.data.UiSettings.PING_HTTP) "HTTPing" else "TCPing"

    Column(modifier = Modifier.fillMaxWidth()) {
        // ---------------- 标题行（对齐 h3：标题 + badge + currentTestDC） ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("详细测试结果", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            CountBadge(visible.size)
            Spacer(Modifier.weight(1f))
            if (state.step == DcStep.TESTING) {
                Text(
                    text = "测试中: ${state.selectedDc ?: "-"}（${state.testDone}/${state.testTotal}）",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            } else if (state.selectedDc != null) {
                Text(
                    text = state.message ?: "数据中心 ${state.selectedDc}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (state.step == DcStep.TESTING) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = {
                    if (state.testTotal > 0) state.testDone.toFloat() / state.testTotal else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.speedBatchRunning) {
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = {
                    if (state.speedBatchTotal > 0) state.speedBatchDone.toFloat() / state.speedBatchTotal else 0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "下载测速中 ${state.speedBatchDone}/${state.speedBatchTotal}（自动阶段）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))

        // ---------------- 合格筛选（对齐 officialTestQualifiedFilter） ----------------
        CfDropdown(
            label = null,
            selected = when (filter) {
                FILTER_QUALIFIED -> "仅展示合格"
                FILTER_UNQUALIFIED -> "仅展示不合格"
                else -> "展示全部结果"
            },
            options = listOf(
                CfOption("展示全部结果", FILTER_ALL),
                CfOption("仅展示合格", FILTER_QUALIFIED),
                CfOption("仅展示不合格", FILTER_UNQUALIFIED),
            ),
            onSelect = { o -> filter = o.value },
        )
        Spacer(Modifier.height(8.dp))

        // ---------------- 动作按钮组（对齐 .table-actions 五钮；双列网格 + Material Symbols 图标） ----------------
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GreenButton(
                    text = if (state.speedBatchRunning) "测速中..." else "一键测速",
                    icon = R.drawable.ic_sym_rocket_launch,
                    enabled = !state.speedBatchRunning && visible.isNotEmpty(),
                    onClick = { viewModel.batchSpeedTest(continued = false) },
                    modifier = Modifier.weight(1f),
                )
                GreenButton(
                    text = "继续测速",
                    icon = R.drawable.ic_sym_skip_next,
                    enabled = !state.speedBatchRunning && visible.any { it.speedMbps == null },
                    onClick = { viewModel.batchSpeedTest(continued = true) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    GreenButton(
                        text = "一键排序",
                        icon = R.drawable.ic_sym_swap_vert,
                        enabled = visible.isNotEmpty(),
                        onClick = { showSortMenu = true },
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                    ) {
                        listOf(
                            "丢包率" to DcSortKey.LOSS,
                            "最低延迟" to DcSortKey.MIN,
                            "平均延迟" to DcSortKey.AVG,
                            "最大延迟" to DcSortKey.MAX,
                            "下载速度" to DcSortKey.SPEED,
                        ).forEach { (label, key) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.sortDcResults(key)
                                    showSortMenu = false
                                },
                            )
                        }
                    }
                }
                GreenButton(
                    text = "一键复制",
                    icon = R.drawable.ic_sym_content_copy,
                    enabled = visible.isNotEmpty(),
                    onClick = { onCopy(visible.joinToString("\n") { it.address() }) },
                    modifier = Modifier.weight(1f),
                )
            }
            GreenButton(
                text = "结果操作",
                icon = R.drawable.ic_sym_download,
                enabled = visible.isNotEmpty(),
                onClick = onExportClick,
            )
        }
        Spacer(Modifier.height(8.dp))

        // ---------------- 12 列宽表（对齐 #testTable） ----------------
        ScrollTable(
            columns = listOf(
                "IP 地址", "端口", "数据中心", "落地区域", "地区", "城市",
                "扫描方式", "丢包率", "最小延迟", "最大延迟", "平均延迟", "测速",
            ),
            columnWidths = listOf(
                120.dp, 52.dp, 68.dp, 68.dp, 68.dp, 88.dp, 76.dp, 56.dp, 72.dp, 72.dp, 72.dp, 84.dp,
            ),
            rows = visible.map { item ->
                listOf(
                    item.ip,
                    item.port.toString(),
                    item.colo.ifEmpty { "-" },
                    item.country.ifEmpty { "-" },
                    item.region.ifEmpty { "-" },
                    item.city.ifEmpty { "-" },
                    scanModeLabel,
                    "${fmt0(item.lossRate * 100)}%",
                    "${item.minMs}ms",
                    "${item.maxMs}ms",
                    "${fmt0(item.avgMs)}ms",
                    item.speedMbps?.let { "${fmt2(it)}MB/s" } ?: "未测速",
                )
            },
            onRowClick = { index -> onCopy(visible[index].address()) },
        )
    }
}
