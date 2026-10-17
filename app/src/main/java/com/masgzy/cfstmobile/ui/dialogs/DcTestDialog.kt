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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masgzy.cfstmobile.data.DcInfo
import com.masgzy.cfstmobile.data.DcStep
import com.masgzy.cfstmobile.data.DcTestItem
import com.masgzy.cfstmobile.data.DcTestUiState
import com.masgzy.cfstmobile.ui.components.copyToClipboard
import com.masgzy.cfstmobile.ui.components.fmt1
import com.masgzy.cfstmobile.ui.components.fmt2
import com.masgzy.cfstmobile.ui.theme.delayColor
import com.masgzy.cfstmobile.ui.theme.lossColor
import com.masgzy.cfstmobile.viewmodel.TestViewModel
import kotlin.math.roundToInt

/**
 * 数据中心二次详细测试全屏对话框（对齐 CFData-WEB 官方模式交互）：
 * trace 探测归属 → 数据中心列表（按最低延迟排序）→ 选择后二次详细测试（min/max/avg/loss）。
 */
@Composable
fun DcTestDialog(viewModel: TestViewModel, onDismiss: () -> Unit) {
    val dcState by viewModel.dcState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val close: () -> Unit = {
        if (dcState.busy) viewModel.cancelDcTest()
        onDismiss()
    }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 头部
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = close) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                    }
                    Text(text = "数据中心测试", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                when (dcState.step) {
                    DcStep.IDLE -> DcCentered(text = "正在准备…")
                    DcStep.PROBING_COLO -> DcProbing(state = dcState, onCancel = close)
                    DcStep.DC_LIST -> DcList(state = dcState, onSelect = viewModel::testDc)
                    DcStep.TESTING -> DcTesting(state = dcState, onCancel = close)
                    DcStep.DONE -> DcDone(
                        state = dcState,
                        onBack = viewModel::backToDcList,
                        onCopy = { copyToClipboard(context, it) },
                    )
                    DcStep.ERROR -> DcError(state = dcState, onDismiss = close, onBack = viewModel::backToDcList)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 探测中

@Composable
private fun DcProbing(state: DcTestUiState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "正在识别数据中心归属（${state.probeDone}/${state.probeTotal}）",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "通过 speed.cloudflare.com/cdn-cgi/trace 获取每个 IP 所在的数据中心",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

// ---------------------------------------------------------------- 数据中心列表

@Composable
private fun DcList(state: DcTestUiState, onSelect: (String?) -> Unit) {
    val totalIps = state.dcList.sumOf { it.ipCount }
    val minLatency = state.dcList.minOfOrNull { it.minLatencyMs } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "基于本次测速结果按数据中心聚合；点选后对该数据中心的全部 IP 做二次详细测试" +
                "（tcping 10 次 / httping 3 次，统计 min/avg/max/丢包）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "__all__") {
                DcCard(
                    title = "全部数据中心",
                    subtitle = "重新测试全部 ${state.dcList.size} 个数据中心的 IP",
                    ipCount = totalIps,
                    minLatencyMs = minLatency,
                    onClick = { onSelect(null) },
                )
            }
            items(state.dcList, key = { it.colo }) { dc ->
                DcCard(
                    title = dc.colo,
                    subtitle = dc.displayLocation(),
                    ipCount = dc.ipCount,
                    minLatencyMs = dc.minLatencyMs,
                    onClick = { onSelect(dc.colo) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DcCard(title: String, subtitle: String, ipCount: Int, minLatencyMs: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "$ipCount 个 IP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "$minLatencyMs ms",
                    fontSize = 12.sp,
                    color = delayColor(minLatencyMs.toDouble()),
                )
            }
        }
    }
}

private fun DcInfo.displayLocation(): String = when {
    city.isNotEmpty() && country.isNotEmpty() -> "$city · $country"
    city.isNotEmpty() -> city
    country.isNotEmpty() -> country
    else -> "位置未知"
}

// ---------------------------------------------------------------- 测试中 / 完成

@Composable
private fun DcTesting(state: DcTestUiState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val target = state.selectedDc?.let { "数据中心 $it" } ?: "全部数据中心"
        Text(
            text = "正在二次详细测试：$target（${state.testDone}/${state.testTotal}）",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = {
                if (state.testTotal > 0) state.testDone.toFloat() / state.testTotal else 0f
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "实时结果按 丢包率 → 最低延迟 → 最高延迟 → 平均延迟 排序",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        DcResultList(items = state.results, modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("终止并保留结果")
        }
    }
}

@Composable
private fun DcDone(
    state: DcTestUiState,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${state.results.size} 个 IP 通过详细测试",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.selectedDc?.let { "数据中心 $it" } ?: "全部数据中心",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.message?.let {
                    Text(text = it, fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            TextButton(onClick = onBack) { Text("返回选择") }
        }
        Spacer(Modifier.height(8.dp))
        DcResultList(items = state.results, modifier = Modifier.weight(1f), onCopy = onCopy)
    }
}

@Composable
private fun DcResultList(
    items: List<DcTestItem>,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "暂无结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = { it.address() }) { item ->
            DcResultRow(item = item, onCopy = onCopy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DcResultRow(item: DcTestItem, onCopy: ((String) -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick = { onCopy?.invoke(item.address()) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.address(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    text = buildString {
                        if (item.colo.isNotEmpty()) append(item.colo)
                        if (item.city.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(item.city)
                        }
                        if (item.country.isNotEmpty()) append(" ${item.country}")
                        val failed = (item.lossRate * item.attempts).roundToInt()
                        if (item.attempts > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("丢包 ${fmt1(item.lossRate * 100)}%（$failed/${item.attempts}）")
                        }
                    },
                    fontSize = 11.sp,
                    color = lossColor(item.lossRate),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${fmt2(item.avgMs)} ms",
                    fontSize = 14.sp,
                    color = delayColor(item.avgMs),
                )
                Text(
                    text = "min ${item.minMs} · max ${item.maxMs}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 错误 / 占位

@Composable
private fun DcCentered(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DcError(state: DcTestUiState, onDismiss: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.message ?: "数据中心测试失败",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (state.dcList.isNotEmpty()) {
            TextButton(onClick = onBack) { Text("返回数据中心列表") }
        } else {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}
