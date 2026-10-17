package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.ui.theme.BadRed
import com.masgzy.cfstmobile.ui.theme.CfBlueLight
import com.masgzy.cfstmobile.ui.theme.OkGreen
import com.masgzy.cfstmobile.viewmodel.TestUiState

/**
 * 顶部状态卡（CFSTAPP 同款布局）：空闲云图标 / 测速进度环 / 完成与错误态。
 */
@Composable
fun StatusCard(
    state: TestUiState,
    settings: UiSettings,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isRunning) CfBlueLight else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.isRunning && state.phase == "ping" -> {
                    val ping = state.pingProgress
                    val frac = if (ping != null && ping.total > 0) {
                        (ping.tested.toFloat() / ping.total.toFloat()).coerceIn(0f, 1f)
                    } else null
                    if (frac != null) {
                        CircularProgressIndicator(progress = { frac }, modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (ping != null) {
                        Text("延迟测试 ${ping.tested} / ${ping.total}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text("存活 ${ping.success}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StopHint(state)
                }

                state.isRunning && state.phase == "download" -> {
                    val dl = state.downloadProgress
                    val frac = if (dl != null && dl.total > 0) {
                        (dl.tested.toFloat() / dl.total.toFloat()).coerceIn(0f, 1f)
                    } else null
                    if (frac != null) {
                        CircularProgressIndicator(progress = { frac }, modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (dl != null) {
                        Text("下载测速 ${dl.tested} / ${dl.total}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "已达标 ${dl.qualified} · 实时 ${fmt2(dl.speedMbps)} Mbps",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    StopHint(state)
                }

                state.isRunning -> {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("正在初始化引擎…", fontSize = 14.sp)
                }

                state.phase == "error" -> {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = BadRed,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.message ?: "测速失败",
                        fontSize = 14.sp,
                        color = BadRed,
                        textAlign = TextAlign.Center,
                    )
                }

                state.results.isNotEmpty() -> {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = OkGreen,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("测速完成，共 ${state.results.size} 个结果", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    StopHint(state)
                }

                else -> {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("点击「开始测速」", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        settingsSummary(settings),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopHint(state: TestUiState) {
    if (state.isRunning && state.stopRequested) {
        Spacer(Modifier.height(4.dp))
        Text("正在停止，等待引擎收尾…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun settingsSummary(s: UiSettings): String = buildString {
    append("${s.tcpPort} 端口 · ")
    append(if (s.enableDownload) "延迟+下载" else "仅延迟")
    append(" · ")
    if (s.ipSource == UiSettings.SOURCE_BUILTIN) {
        append("内置 ${s.selectedSegments.size} 段")
    } else {
        append("自定义 IP")
    }
}
