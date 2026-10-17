package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masgzy.cfstmobile.data.BUILT_IN_IPS_V6
import com.masgzy.cfstmobile.data.SpeedUrlPresets
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.ui.dialogs.SegmentsDialog
import com.masgzy.cfstmobile.viewmodel.TestViewModel

/**
 * 参数区（对齐 CFData-WEB .controls 官方参数/非标参数卡）：
 * 官方 = IP 类型/内置段/端口/并发/延迟/速度/结果数量/自动测速/数据中心/扫描方式/测速网址/高级；
 * 非标 = 自定义目标列表 + 获取按钮 + 镜像 + 共用参数。
 * 端口下拉按 HTTPS/HTTP 分组（对齐官方optgroup）。
 */

private val HTTPS_PORTS = listOf("443", "2053", "2083", "2087", "2096", "8443")
private val HTTP_PORTS = listOf("80", "8080", "8880", "2052", "2082", "2086", "2095")

@Composable
fun ParamsSection(
    settings: UiSettings,
    official: Boolean,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
    onFetchIpList: (String) -> Unit,
) {
    ParamsCard(
        title = if (official) "官方参数" else "非标参数",
        hint = if (official) "端口可选" else "每行一个目标：CIDR / IP[:端口] / 域名[:端口]",
    ) {
        if (official) {
            OfficialParams(settings, onUpdate)
        } else {
            NonStdParams(settings, onUpdate, onFetchIpList)
        }
        OptionGap()
        SharedParams(settings, official, onUpdate)
    }
}

// ---------------------------------------------------------------- 官方参数

@Composable
private fun OfficialParams(
    settings: UiSettings,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
) {
    var showSegments by rememberSaveable { mutableStateOf(false) }

    OptionItem(label = "IP 类型") {
        CfDropdown(
            label = null,
            selected = if (settings.ipType == UiSettings.IP_V6) "IPv6" else "IPv4",
            options = listOf(
                CfOption("IPv4", UiSettings.IP_V4),
                CfOption("IPv6", UiSettings.IP_V6),
            ),
            onSelect = { o -> onUpdate { it.copy(ipType = o.value) } },
        )
    }
    OptionGap()

    if (settings.ipType == UiSettings.IP_V4) {
        OptionItem(label = "IP 段") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已选 ${settings.selectedSegments.size} / 15 段",
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showSegments = true }) { Text("选择") }
            }
        }
        OptionGap()
    } else {
        Text(
            text = "IPv6：使用官方全部 ${BUILT_IN_IPS_V6.size} 个地址段",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OptionGap()
    }

    OptionItem(label = "测试端口") {
        CfDropdown(
            label = null,
            selected = settings.tcpPort.toString(),
            options = buildList {
                add(CfOption("HTTPS", "", header = true))
                HTTPS_PORTS.forEach { add(CfOption(it, it)) }
                add(CfOption("HTTP", "", header = true))
                HTTP_PORTS.forEach { add(CfOption(it, it)) }
            },
            onSelect = { o -> o.value.toIntOrNull()?.let { v -> onUpdate { s -> s.copy(tcpPort = v) } } },
        )
    }
    OptionGap()

    OptionItem(label = "数据中心") {
        CfTextField(
            value = settings.targetDc,
            onValueChange = { v -> onUpdate { it.copy(targetDc = v) } },
            placeholder = if (settings.pingMode == UiSettings.PING_HTTP) "留空不筛选，如 NRT" else "仅 httping 模式可用",
            enabled = settings.pingMode == UiSettings.PING_HTTP,
        )
    }

    if (showSegments) {
        SegmentsDialog(
            selected = settings.selectedSegments,
            onConfirm = { segments ->
                onUpdate { it.copy(selectedSegments = segments) }
                showSegments = false
            },
            onDismiss = { showSegments = false },
        )
    }
}

// ---------------------------------------------------------------- 非标参数

@Composable
private fun NonStdParams(
    settings: UiSettings,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
    onFetchIpList: (String) -> Unit,
) {
    var showMirrorMenu by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "自定义目标列表",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        CfTextField(
            value = settings.customIpText,
            onValueChange = { v -> onUpdate { it.copy(customIpText = v) } },
            placeholder = "每行一个：CIDR、IP[:端口]、域名[:端口]",
            singleLine = false,
            minLines = 3,
        )
    }
    OptionGap()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onFetchIpList(TestViewModel.SOURCE_CLOUDFLARE) }) {
            Text("获取 Cloudflare 段", maxLines = 1)
        }
        TextButton(onClick = { onFetchIpList(TestViewModel.SOURCE_GITHUB) }) {
            Text("从 GitHub 获取", maxLines = 1)
        }
    }
    OptionGap()

    Row(verticalAlignment = Alignment.CenterVertically) {
        CfTextField(
            value = settings.mirrorUrl,
            onValueChange = { v -> onUpdate { it.copy(mirrorUrl = v) } },
            placeholder = "GitHub 镜像前缀（可选）",
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { showMirrorMenu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ExpandMore, contentDescription = "选择镜像", modifier = Modifier.size(18.dp))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showMirrorMenu,
                onDismissRequest = { showMirrorMenu = false },
            ) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("不使用镜像") },
                    onClick = {
                        onUpdate { it.copy(mirrorUrl = "") }
                        showMirrorMenu = false
                    },
                )
                com.masgzy.cfstmobile.data.CloudflareIpSources.MIRROR_PRESETS.forEach { preset ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(preset, fontSize = 12.sp, maxLines = 1) },
                        onClick = {
                            onUpdate { it.copy(mirrorUrl = preset) }
                            showMirrorMenu = false
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 共用参数

@Composable
private fun SharedParams(
    settings: UiSettings,
    official: Boolean,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
) {
    OptionItem(label = "并发数量") {
        IntControl(value = settings.maxThreads, placeholder = "默认 100") { v ->
            onUpdate { it.copy(maxThreads = v) }
        }
    }
    OptionGap()

    OptionItem(label = "扫描合格延迟") {
        IntControl(value = settings.maxDelayMs, placeholder = "默认 2000ms") { v ->
            onUpdate { it.copy(maxDelayMs = v) }
        }
    }
    OptionGap()

    OptionItem(label = "测试合格速度") {
        DoubleControl(value = settings.minSpeedMbps, placeholder = "MB/s，0 不限") { v ->
            onUpdate { it.copy(minSpeedMbps = v) }
        }
    }
    OptionGap()

    OptionItem(label = "测速结果数量") {
        IntControl(value = settings.testCount, placeholder = "0 关闭测速") { v ->
            onUpdate { it.copy(testCount = v) }
        }
    }
    OptionGap()

    OptionItem(label = "自动测速") {
        CfDropdown(
            label = null,
            selected = if (settings.enableDownload) "开启" else "关闭",
            options = listOf(CfOption("关闭", "off"), CfOption("开启", "on")),
            onSelect = { o -> onUpdate { it.copy(enableDownload = o.value == "on") } },
        )
    }
    OptionGap()

    OptionItem(label = "扫描方式") {
        Column {
            CfDropdown(
                label = null,
                selected = if (settings.pingMode == UiSettings.PING_HTTP) "HTTPing" else "TCPing（默认）",
                options = listOf(CfOption("TCPing（默认）", UiSettings.PING_TCP), CfOption("HTTPing", UiSettings.PING_HTTP)),
                onSelect = { o -> onUpdate { it.copy(pingMode = o.value) } },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (settings.pingMode == UiSettings.PING_HTTP) {
                    "测量 HTTP 请求延迟，可识别数据中心"
                } else {
                    "仅测量 TCP 握手延迟，不同模式数据不可互相比较"
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    OptionGap()

    if (settings.pingMode == UiSettings.PING_HTTP) {
        OptionItem(label = "探测地址") {
            CfTextField(
                value = settings.httpingUrl,
                onValueChange = { v -> onUpdate { it.copy(httpingUrl = v) } },
                placeholder = "留空用官方默认",
            )
        }
        OptionGap()
    }

    OptionItem(label = "测速网址") {
        Column {
            CfDropdown(
                label = null,
                selected = presetLabel(settings.speedUrlPreset),
                options = listOf(
                    CfOption("自动选择", SpeedUrlPresets.AUTO),
                    CfOption("Cloudflare", SpeedUrlPresets.CLOUDFLARE),
                    CfOption("CM 提供", SpeedUrlPresets.CM),
                    CfOption("移动专属", SpeedUrlPresets.MOBILE),
                    CfOption("手动输入", SpeedUrlPresets.CUSTOM),
                ),
                onSelect = { o ->
                    onUpdate {
                        it.copy(
                            speedUrlPreset = o.value,
                            downloadUrl = SpeedUrlPresets.resolve(o.value, it.downloadUrl),
                        )
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CfTextField(
                    value = settings.downloadUrl,
                    onValueChange = { v -> onUpdate { it.copy(downloadUrl = v, speedUrlPreset = SpeedUrlPresets.CUSTOM) } },
                    placeholder = "例如 speed.cloudflare.com/__down?bytes=99999999",
                    enabled = settings.enableDownload,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onUpdate { it.copy(speedUrlPreset = SpeedUrlPresets.AUTO, downloadUrl = SpeedUrlPresets.resolve(SpeedUrlPresets.AUTO, "")) } },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "恢复自动选择", modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    AdvancedParams(settings, official, onUpdate)
}

@Composable
private fun presetLabel(preset: String): String = when (preset) {
    SpeedUrlPresets.CLOUDFLARE -> "Cloudflare"
    SpeedUrlPresets.CM -> "CM 提供"
    SpeedUrlPresets.MOBILE -> "移动专属"
    SpeedUrlPresets.CUSTOM -> "手动输入"
    else -> "自动选择"
}

// ---------------------------------------------------------------- 高级参数

@Composable
private fun AdvancedParams(
    settings: UiSettings,
    official: Boolean,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "高级参数",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (expanded) {
        OptionGap()
        OptionItem(label = "每 IP 测速次数") {
            IntControl(value = settings.pingTimes, placeholder = "默认 4") { v ->
                onUpdate { it.copy(pingTimes = v) }
            }
        }
        OptionGap()
        OptionItem(label = "结果保留上限") {
            IntControl(value = settings.printNum, placeholder = "默认 10") { v ->
                onUpdate { it.copy(printNum = v) }
            }
        }
        OptionGap()
        OptionItem(label = "下载时长") {
            IntControl(value = settings.downloadTimeSecs, placeholder = "秒，默认 10") { v ->
                onUpdate { it.copy(downloadTimeSecs = v) }
            }
        }
        OptionGap()
        OptionItem(label = "丢包率上限") {
            DoubleControl(value = settings.maxLossRate, placeholder = "0~1，默认 1") { v ->
                onUpdate { it.copy(maxLossRate = v) }
            }
        }
        OptionGap()
        OptionItem(label = "全局超时") {
            IntControl(value = settings.globalTimeoutSecs, placeholder = "秒，0 不限") { v ->
                onUpdate { it.copy(globalTimeoutSecs = v) }
            }
        }
        OptionGap()
        SwitchItem(
            label = "测试 CIDR 全部 IPv4",
            hint = "关闭则按前缀采样，速度更快",
            checked = settings.testAllIpv4,
            onChecked = { checked -> onUpdate { it.copy(testAllIpv4 = checked) } },
        )
    }
}

@Composable
private fun SwitchItem(label: String, hint: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 13.sp)
            Text(
                text = hint,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

// ---------------------------------------------------------------- 数字输入控件

/** 整数输入（控件区无标签紧凑版：非法输入不回写） */
@Composable
private fun IntControl(value: Int, placeholder: String, enabled: Boolean = true, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    CfTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toIntOrNull()?.let(onValue)
        },
        placeholder = placeholder,
        enabled = enabled,
        number = true,
    )
}

/** 小数输入（控件区无标签紧凑版） */
@Composable
private fun DoubleControl(value: Double, placeholder: String, enabled: Boolean = true, onValue: (Double) -> Unit) {
    var text by remember(value) {
        mutableStateOf(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
    }
    CfTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toDoubleOrNull()?.let(onValue)
        },
        placeholder = placeholder,
        enabled = enabled,
    )
}
