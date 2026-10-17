package com.masgzy.cfstmobile.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masgzy.cfstmobile.BuildConfig
import com.masgzy.cfstmobile.data.BUILT_IN_IPS
import com.masgzy.cfstmobile.data.CloudflareIpSources
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.ui.components.DoubleField
import com.masgzy.cfstmobile.ui.components.IntField
import com.masgzy.cfstmobile.ui.components.SectionLabel
import com.masgzy.cfstmobile.viewmodel.TestViewModel

/**
 * 设置对话框（CFSTAPP EnhancedSettingsDialog 同款交互，字段对齐引擎配置）：
 * IP 来源（内置段勾选 / 自定义 + 在线获取 + 镜像）、测速参数、过滤与高级、关于。
 * 所有修改即时持久化（与 CFSTAPP 的 updateXXX 模式一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(viewModel: TestViewModel, onDismiss: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSegments by remember { mutableStateOf(false) }
    var showMirrorMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---------------- IP 来源 ----------------
                SectionLabel("IP 来源（IPv4）")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.ipSource == UiSettings.SOURCE_BUILTIN,
                        onClick = { viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_BUILTIN) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("内置 IP 段") },
                    )
                    SegmentedButton(
                        selected = settings.ipSource == UiSettings.SOURCE_CUSTOM,
                        onClick = { viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_CUSTOM) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("自定义 IP") },
                    )
                }

                if (settings.ipSource == UiSettings.SOURCE_BUILTIN) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "已选 ${settings.selectedSegments.size} / ${BUILT_IN_IPS.size} 个 Cloudflare 官方 IP 段",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Button(
                                onClick = { showSegments = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(text = "选择 IP 段") }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = settings.customIpText,
                        onValueChange = { text -> viewModel.updateSettings { it.copy(customIpText = text) } },
                        label = { Text("自定义 IP / CIDR（每行一个，支持 1.2.3.4/24=50 定量采样）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        textStyle = TextStyle(fontSize = 13.sp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.fetchIpList(TestViewModel.SOURCE_CLOUDFLARE) },
                            modifier = Modifier.weight(1f),
                        ) { Text(text = "从 Cloudflare 获取", maxLines = 1) }
                        OutlinedButton(
                            onClick = { viewModel.fetchIpList(TestViewModel.SOURCE_GITHUB) },
                            modifier = Modifier.weight(1f),
                        ) { Text(text = "从 GitHub 获取", maxLines = 1) }
                    }
                    // GitHub 镜像前缀（带内置候选下拉）
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = settings.mirrorUrl,
                            onValueChange = { text -> viewModel.updateSettings { it.copy(mirrorUrl = text) } },
                            label = { Text("GitHub 镜像前缀（可选）") },
                            placeholder = { Text("例如：https://gh-proxy.com/", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp),
                            trailingIcon = {
                                IconButton(onClick = { showMirrorMenu = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择镜像")
                                }
                            },
                        )
                        DropdownMenu(
                            expanded = showMirrorMenu,
                            onDismissRequest = { showMirrorMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("不使用镜像") },
                                onClick = {
                                    viewModel.updateSettings { it.copy(mirrorUrl = "") }
                                    showMirrorMenu = false
                                },
                            )
                            CloudflareIpSources.MIRROR_PRESETS.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset, fontSize = 12.sp) },
                                    onClick = {
                                        viewModel.updateSettings { it.copy(mirrorUrl = preset) }
                                        showMirrorMenu = false
                                    },
                                )
                            }
                        }
                    }
                }

                // ---------------- 测速参数 ----------------
                SectionLabel("测速参数")
                IntField(label = "测速端口", value = settings.tcpPort) { v ->
                    viewModel.updateSettings { it.copy(tcpPort = v) }
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.pingMode == UiSettings.PING_TCP,
                        onClick = { viewModel.updateSettings { it.copy(pingMode = UiSettings.PING_TCP) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("TCP 延迟") },
                    )
                    SegmentedButton(
                        selected = settings.pingMode == UiSettings.PING_HTTP,
                        onClick = { viewModel.updateSettings { it.copy(pingMode = UiSettings.PING_HTTP) } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("HTTP 延迟") },
                    )
                }
                if (settings.pingMode == UiSettings.PING_HTTP) {
                    OutlinedTextField(
                        value = settings.httpingUrl,
                        onValueChange = { text -> viewModel.updateSettings { it.copy(httpingUrl = text) } },
                        label = { Text("HTTP 探测地址（留空用官方默认）") },
                        placeholder = { Text("http://cp.cloudflare.com/cdn-cgi/trace", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "下载测速")
                    Switch(
                        checked = settings.enableDownload,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(enableDownload = checked) } },
                    )
                }
                if (settings.enableDownload) {
                    OutlinedTextField(
                        value = settings.downloadUrl,
                        onValueChange = { text -> viewModel.updateSettings { it.copy(downloadUrl = text) } },
                        label = { Text("下载测速 URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp),
                    )
                    IntField(label = "单 IP 下载时长（秒，1~120）", value = settings.downloadTimeSecs) { v ->
                        viewModel.updateSettings { it.copy(downloadTimeSecs = v) }
                    }
                }
                IntField(label = "需要达标的结果数量", value = settings.testCount) { v ->
                    viewModel.updateSettings { it.copy(testCount = v) }
                }
                IntField(label = "结果保留数量上限", value = settings.printNum) { v ->
                    viewModel.updateSettings { it.copy(printNum = v) }
                }
                IntField(label = "每 IP 延迟测速次数", value = settings.pingTimes) { v ->
                    viewModel.updateSettings { it.copy(pingTimes = v) }
                }
                IntField(label = "并发上限（建议 64~256）", value = settings.maxThreads) { v ->
                    viewModel.updateSettings { it.copy(maxThreads = v) }
                }

                // ---------------- 过滤与高级 ----------------
                SectionLabel("过滤与高级")
                IntField(label = "延迟上限（ms）", value = settings.maxDelayMs) { v ->
                    viewModel.updateSettings { it.copy(maxDelayMs = v) }
                }
                DoubleField(label = "丢包率上限（0~1）", value = settings.maxLossRate) { v ->
                    viewModel.updateSettings { it.copy(maxLossRate = v) }
                }
                DoubleField(label = "速度下限（MB/s，0 不限）", value = settings.minSpeedMbps) { v ->
                    viewModel.updateSettings { it.copy(minSpeedMbps = v) }
                }
                IntField(label = "全局超时（秒，0 不限）", value = settings.globalTimeoutSecs) { v ->
                    viewModel.updateSettings { it.copy(globalTimeoutSecs = v) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "测试 CIDR 全部 IPv4")
                        Text(
                            text = "关闭则按前缀采样，速度更快",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.testAllIpv4,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(testAllIpv4 = checked) } },
                    )
                }

                // ---------------- 外观 ----------------
                SectionLabel("外观")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "动态取色（Material You）")
                        Text(
                            text = "Android 12+ 跟随系统壁纸取色；关闭使用 Cloudflare 蓝",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = settings.dynamicColors,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(dynamicColors = checked) } },
                    )
                }

                // ---------------- 关于 ----------------
                SectionLabel("关于")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "CFSTMobile v${BuildConfig.VERSION_NAME}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "引擎：Rust cdylib（改造自 CloudflareST-Rust，GPL-3.0）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "UI 参照：Hsia97/CFSTAPP",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )

    if (showSegments) {
        SegmentsDialog(
            selected = settings.selectedSegments,
            onConfirm = { segments ->
                viewModel.updateSettings { it.copy(selectedSegments = segments) }
            },
            onDismiss = { showSegments = false },
        )
    }
}

