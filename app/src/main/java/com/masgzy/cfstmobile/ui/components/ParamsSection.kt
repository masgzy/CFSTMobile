package com.masgzy.cfstmobile.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
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
 * - 官方：IP 类型/测试端口/并发/合格延迟/合格速度/结果数量/自动测速/数据中心(联动)/扫描方式/测速网址 + 高级折叠；
 * - 非标：IP 文件/网络 URL/手动列表/备用端口/并发/合格延迟/合格数量/合格速度/结果数量/自动测速/
 *   数据中心(筛选)/TLS 模式/输出文件名/扫描方式/测速网址 + 高级折叠；
 * - option-item 双列网格 + full-width 独占行，label 在上控件在下（对齐 Web 版 CSS）。
 */

private val HTTPS_PORTS = listOf("443", "2053", "2083", "2087", "2096", "8443")
private val HTTP_PORTS = listOf("80", "8080", "8880", "2052", "2082", "2086", "2095")

private const val SCAN_HINT_TCP = "仅测量 TCP 握手延迟，不同模式数据不可互相比较"
private const val SCAN_HINT_HTTP =
    "测量 HTTP GET 请求的完整 TTFB（含 TLS 握手），HTTPing 延迟比 TCPing 高属正常现象"

@Composable
fun ParamsSection(
    settings: UiSettings,
    official: Boolean,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
) {
    ParamsCard(
        title = if (official) "官方参数" else "非标参数",
        hint = if (official) {
            "端口可选"
        } else {
            "支持上传 txt/csv 文件或填写网络 URL（二选一）；若识别失败，请整理为每行一个目标，格式：IP 空格 端口，例如 1.2.3.4 443"
        },
    ) {
        if (official) {
            OfficialParams(settings, onUpdate)
        } else {
            NonStdParams(settings, onUpdate)
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
    OptionGrid(
        left = {
            OptionItem(label = "🌐 IP 类型") {
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
        },
        right = {
            OptionItem(label = "🚪 测试端口") {
                PortDropdown(value = settings.officialPort) { v -> onUpdate { it.copy(officialPort = v) } }
            }
        },
    )
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "🚀 并发数量") {
                IntControl(value = settings.officialThreads, placeholder = "默认 100") { v ->
                    onUpdate { it.copy(officialThreads = v) }
                }
            }
        },
        right = {
            OptionItem(label = "⏱️ 扫描合格延迟") {
                IntControl(value = settings.officialDelayMs, placeholder = "默认 500ms") { v ->
                    onUpdate { it.copy(officialDelayMs = v) }
                }
            }
        },
    )
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "📉 测试合格速度") {
                DoubleControl(value = settings.officialSpeedMin, placeholder = "默认 0.1MB/s") { v ->
                    onUpdate { it.copy(officialSpeedMin = v) }
                }
            }
        },
        right = {
            OptionItem(label = "🏁 测速结果数量") {
                IntControl(value = settings.officialSpeedLimit, placeholder = "0 关闭测速") { v ->
                    onUpdate { it.copy(officialSpeedLimit = v) }
                }
            }
        },
    )
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "🧪 自动测速") {
                CfDropdown(
                    label = null,
                    selected = if (settings.officialAutoSpeed) "开启" else "关闭",
                    options = listOf(CfOption("关闭", "off"), CfOption("开启", "on")),
                    onSelect = { o ->
                        onUpdate {
                            it.copy(
                                officialAutoSpeed = o.value == "on",
                                // 关闭自动测速时清空目标 DC（对齐 updateOfficialAutoSpeedControls）
                                officialTargetDc = if (o.value == "on") it.officialTargetDc else "",
                            )
                        }
                    },
                )
            }
        },
        right = {
            OptionItem(label = "🏢 数据中心") {
                CfTextField(
                    value = settings.officialTargetDc,
                    onValueChange = { v -> onUpdate { it.copy(officialTargetDc = v) } },
                    placeholder = "留空自动选择",
                    enabled = settings.officialAutoSpeed,
                )
            }
        },
    )
}

// ---------------------------------------------------------------- 非标参数

@Composable
private fun NonStdParams(
    settings: UiSettings,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
) {
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.readBytes().decodeToString()
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "已选文件"
                    onUpdate { it.copy(nsbFileText = text, nsbFileName = name) }
                }
            }
        }
    }

    OptionItem(label = "📄 IP 文件") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = settings.nsbFileName.ifEmpty { "未选择文件（txt/csv）" },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { filePicker.launch(arrayOf("text/plain", "text/csv")) }) {
                Text(if (settings.nsbFileName.isEmpty()) "选择文件" else "重新选择")
            }
            if (settings.nsbFileName.isNotEmpty()) {
                TextButton(onClick = { onUpdate { it.copy(nsbFileText = "", nsbFileName = "") } }) {
                    Text("清除")
                }
            }
        }
    }
    OptionGap()

    OptionItem(label = "🌐 网络 URL") {
        CfTextField(
            value = settings.nsbSourceUrl,
            onValueChange = { v -> onUpdate { it.copy(nsbSourceUrl = v) } },
            placeholder = "https://example.com/ip-list.txt",
        )
    }
    OptionGap()

    OptionItem(label = "📝 手动列表（可选）") {
        CfTextField(
            value = settings.customIpText,
            onValueChange = { v -> onUpdate { it.copy(customIpText = v) } },
            placeholder = "每行一个：CIDR、IP[:端口]、域名[:端口]",
            singleLine = false,
            minLines = 2,
        )
    }
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "🚪 备用端口") {
                IntControl(
                    value = settings.nsbFallbackPort,
                    placeholder = if (settings.nsbTls) "TLS 默认 443" else "TLS 关闭默认 80",
                ) { v -> onUpdate { it.copy(nsbFallbackPort = v) } }
            }
        },
        right = {
            OptionItem(label = "🚀 并发数量") {
                IntControl(value = settings.nsbThreads, placeholder = "默认 100") { v ->
                    onUpdate { it.copy(nsbThreads = v) }
                }
            }
        },
    )
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "⏱️ 扫描合格延迟") {
                IntControl(value = settings.nsbDelayMs, placeholder = "默认 0ms") { v ->
                    onUpdate { it.copy(nsbDelayMs = v) }
                }
            }
        },
        right = {
            OptionItem(label = "🎯 扫描合格数量") {
                IntControl(value = settings.nsbResultLimit, placeholder = "非 0 正整数") { v ->
                    onUpdate { it.copy(nsbResultLimit = v) }
                }
            }
        },
    )
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "📉 测试合格速度") {
                DoubleControl(value = settings.nsbSpeedMin, placeholder = "默认 0.1MB/s") { v ->
                    onUpdate { it.copy(nsbSpeedMin = v) }
                }
            }
        },
        right = {
            OptionItem(label = "🏁 测速结果数量") {
                IntControl(value = settings.nsbSpeedLimit, placeholder = "0 关闭测速") { v ->
                    onUpdate { it.copy(nsbSpeedLimit = v) }
                }
            }
        },
    )
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "🧪 自动测速") {
                CfDropdown(
                    label = null,
                    selected = if (settings.nsbAutoSpeed) "开启" else "关闭",
                    options = listOf(CfOption("开启", "on"), CfOption("关闭", "off")),
                    onSelect = { o -> onUpdate { it.copy(nsbAutoSpeed = o.value == "on") } },
                )
            }
        },
        right = {
            OptionItem(label = "🏢 数据中心") {
                CfTextField(
                    value = settings.nsbTargetDc,
                    onValueChange = { v -> onUpdate { it.copy(nsbTargetDc = v) } },
                    placeholder = "留空不限制，如 NRT",
                )
            }
        },
    )
    OptionGap()

    OptionGrid(
        left = {
            OptionItem(label = "🔗 TLS 模式") {
                CfDropdown(
                    label = null,
                    selected = if (settings.nsbTls) "开启" else "关闭",
                    options = listOf(CfOption("开启", "on"), CfOption("关闭", "off")),
                    onSelect = { o ->
                        onUpdate {
                            val tlsOn = o.value == "on"
                            // 对齐 updateNSBFallbackPortDefault：TLS 关闭默认端口 80
                            it.copy(
                                nsbTls = tlsOn,
                                nsbFallbackPort = when {
                                    tlsOn && it.nsbFallbackPort == 80 -> 443
                                    !tlsOn && it.nsbFallbackPort == 443 -> 80
                                    else -> it.nsbFallbackPort
                                },
                            )
                        }
                    },
                )
            }
        },
        right = {
            OptionItem(label = "🛠️ 输出文件名") {
                CfTextField(
                    value = settings.nsbOutFile,
                    onValueChange = { v -> onUpdate { it.copy(nsbOutFile = v) } },
                    placeholder = "ip.csv",
                )
            }
        },
    )
}

// ---------------------------------------------------------------- 共用参数

@Composable
private fun SharedParams(
    settings: UiSettings,
    official: Boolean,
    onUpdate: (((UiSettings) -> UiSettings)) -> Unit,
) {
    OptionItem(label = "📡 扫描方式") {
        Column {
            CfDropdown(
                label = null,
                selected = if (settings.pingMode == UiSettings.PING_HTTP) "HTTPing" else "TCPing（默认）",
                options = listOf(
                    CfOption("TCPing（默认）", UiSettings.PING_TCP),
                    CfOption("HTTPing", UiSettings.PING_HTTP),
                ),
                onSelect = { o -> onUpdate { it.copy(pingMode = o.value) } },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (settings.pingMode == UiSettings.PING_HTTP) SCAN_HINT_HTTP else SCAN_HINT_TCP,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    OptionGap()

    OptionItem(label = "🔗 测速网址") {
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
                    enabled = settings.downloadEnabled,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        onUpdate {
                            it.copy(
                                speedUrlPreset = SpeedUrlPresets.AUTO,
                                downloadUrl = SpeedUrlPresets.resolve(SpeedUrlPresets.AUTO, ""),
                            )
                        }
                    },
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
    var showSegments by rememberSaveable { mutableStateOf(false) }

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
        if (official) {
            OptionItem(label = "🧩 IP 段") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (settings.ipType == UiSettings.IP_V6) {
                            "IPv6 使用官方全部 ${BUILT_IN_IPS_V6.size} 个地址段"
                        } else {
                            "已选 ${settings.selectedSegments.size} / 15 段"
                        },
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (settings.ipType == UiSettings.IP_V4) {
                        TextButton(onClick = { showSegments = true }) { Text("选择") }
                    }
                }
            }
            OptionGap()
        }
        OptionGrid(
            left = {
                OptionItem(label = "每 IP 测速次数") {
                    IntControl(value = settings.pingTimes, placeholder = "默认 4") { v ->
                        onUpdate { it.copy(pingTimes = v) }
                    }
                }
            },
            right = {
                OptionItem(label = "结果保留上限") {
                    IntControl(value = settings.printNum, placeholder = "默认 20") { v ->
                        onUpdate { it.copy(printNum = v) }
                    }
                }
            },
        )
        OptionGap()
        OptionGrid(
            left = {
                OptionItem(label = "下载时长") {
                    IntControl(value = settings.downloadTimeSecs, placeholder = "秒，默认 10") { v ->
                        onUpdate { it.copy(downloadTimeSecs = v) }
                    }
                }
            },
            right = {
                OptionItem(label = "丢包率上限") {
                    DoubleControl(value = settings.maxLossRate, placeholder = "0~1，默认 1") { v ->
                        onUpdate { it.copy(maxLossRate = v) }
                    }
                }
            },
        )
        OptionGap()
        OptionGrid(
            left = {
                OptionItem(label = "全局超时") {
                    IntControl(value = settings.globalTimeoutSecs, placeholder = "秒，0 不限") { v ->
                        onUpdate { it.copy(globalTimeoutSecs = v) }
                    }
                }
            },
            right = {
                OptionItem(label = "HTTPing 探测地址") {
                    CfTextField(
                        value = settings.httpingUrl,
                        onValueChange = { v -> onUpdate { it.copy(httpingUrl = v) } },
                        placeholder = "留空用官方默认",
                    )
                }
            },
        )
        OptionGap()
        SwitchItem(
            label = "测试 CIDR 全部 IPv4",
            hint = "关闭则按前缀采样，速度更快",
            checked = settings.testAllIpv4,
            onChecked = { checked -> onUpdate { it.copy(testAllIpv4 = checked) } },
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

/** 端口下拉（HTTPS/HTTP 分组，对齐官方 optgroup） */
@Composable
private fun PortDropdown(value: Int, onSelect: (Int) -> Unit) {
    CfDropdown(
        label = null,
        selected = value.toString(),
        options = buildList {
            add(CfOption("HTTPS", "", header = true))
            HTTPS_PORTS.forEach { add(CfOption(it, it)) }
            add(CfOption("HTTP", "", header = true))
            HTTP_PORTS.forEach { add(CfOption(it, it)) }
        },
        onSelect = { o -> o.value.toIntOrNull()?.let(onSelect) },
    )
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
