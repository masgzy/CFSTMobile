package com.masgzy.cfstmobile.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masgzy.cfstmobile.BuildConfig
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.ui.components.BannerState
import com.masgzy.cfstmobile.ui.components.CfTabs
import com.masgzy.cfstmobile.ui.components.DcTestSection
import com.masgzy.cfstmobile.ui.components.LogPanel
import com.masgzy.cfstmobile.ui.components.ModeSwitch
import com.masgzy.cfstmobile.ui.components.ParamsSection
import com.masgzy.cfstmobile.ui.components.PrimaryButton
import com.masgzy.cfstmobile.ui.components.ResultsSection
import com.masgzy.cfstmobile.ui.components.StatusBanner
import com.masgzy.cfstmobile.ui.dialogs.ExportDialog
import com.masgzy.cfstmobile.ui.dialogs.HistoryDialog
import com.masgzy.cfstmobile.viewmodel.TestUiState
import com.masgzy.cfstmobile.viewmodel.TestViewModel

/**
 * 应用根（对齐 CFData-WEB 单页布局，Material You 取色）：
 * 标题+右上操作（GitHub/配置/设置下拉菜单）→ 状态横幅 → 模式切换（官方/非标优选）→
 * 参数卡 → 开始按钮 → 进度条 → 扫描结果/详细测试双 Tab → 终端日志面板。
 */
@Composable
fun AppRoot(viewModel: TestViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val dcState by viewModel.dcState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showExport by remember { mutableStateOf(false) }
    var scanTab by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(viewModel) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- 标题 + 右上操作（对齐 h1 + top-actions） ----------------
            item(key = "header") {
                Header(viewModel = viewModel, settings = settings)
            }
            if (uiState.isMock) {
                item(key = "mock") { MockBanner() }
            }

            // ---------------- 状态横幅（对齐 .status-bar） ----------------
            item(key = "status") {
                val (bannerState, bannerText) = bannerInfo(uiState)
                StatusBanner(text = bannerText, state = bannerState)
            }

            // ---------------- 模式切换（对齐 .mode-switch） ----------------
            item(key = "mode") {
                ModeSwitch(
                    leftLabel = "官方优选",
                    rightLabel = "非标优选",
                    leftSelected = settings.ipSource == UiSettings.SOURCE_BUILTIN,
                    onLeft = { viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_BUILTIN) } },
                    onRight = { viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_CUSTOM) } },
                )
            }

            // ---------------- 参数卡（对齐 .controls） ----------------
            item(key = "params") {
                ParamsSection(
                    settings = settings,
                    official = settings.ipSource == UiSettings.SOURCE_BUILTIN,
                    onUpdate = viewModel::updateSettings,
                    onFetchIpList = viewModel::fetchIpList,
                )
            }

            // ---------------- 开始按钮（对齐 #btnStart） ----------------
            item(key = "start") {
                if (uiState.isRunning) {
                    PrimaryButton(
                        text = if (uiState.stopRequested) "正在停止…" else "停止测试",
                        onClick = viewModel::stopTest,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    )
                } else {
                    PrimaryButton(text = "开始扫描与测试", onClick = viewModel::startTest)
                }
            }

            // ---------------- 进度条（对齐 .progress-container） ----------------
            if (uiState.isRunning) {
                item(key = "progress") { ProgressBarLine(uiState) }
            }

            // ---------------- 结果双 Tab（对齐 .tabs） ----------------
            item(key = "tabs") {
                CfTabs(
                    leftLabel = "扫描结果",
                    rightLabel = "详细测试",
                    leftSelected = scanTab,
                    onLeft = { scanTab = true },
                    onRight = { scanTab = false },
                )
            }
            if (scanTab) {
                item(key = "scan") {
                    ResultsSection(
                        state = uiState,
                        sortBy = settings.sortBy,
                        onSortChange = { mode -> viewModel.updateSettings { it.copy(sortBy = mode) } },
                        onExportClick = { showExport = true },
                    )
                }
            } else {
                item(key = "dc") {
                    DcTestSection(
                        viewModel = viewModel,
                        state = dcState,
                        hasResults = uiState.results.isNotEmpty(),
                    )
                }
            }

            // ---------------- 终端日志面板（对齐 .log-box） ----------------
            item(key = "logs") { LogPanel(lines = logs) }
        }
    }

    if (showExport) {
        ExportDialog(results = uiState.results, onDismiss = { showExport = false })
    }
}

// ---------------------------------------------------------------- 头部

@Composable
private fun Header(viewModel: TestViewModel, settings: UiSettings) {
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showConfigMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxWidth()) {
        // 标题块（对齐 h1 + version-line，顶部给右上操作留 44dp）
        Column(modifier = Modifier.fillMaxWidth().padding(top = 44.dp)) {
            Text(
                text = "Cloudflare IP Data",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "CFSTMobile v${BuildConfig.VERSION_NAME}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        // 右上操作排（对齐 .top-actions：GitHub / 💾 配置 / ⚙️ 设置）
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/masgzy/CFSTMobile")),
                    )
                }
            }) {
                Icon(imageVector = Icons.Filled.Code, contentDescription = "GitHub 仓库")
            }
            Box {
                IconButton(onClick = { showConfigMenu = true }) {
                    Icon(imageVector = Icons.Filled.Save, contentDescription = "保存/加载配置")
                }
                ConfigMenu(
                    expanded = showConfigMenu,
                    onDismiss = { showConfigMenu = false },
                    viewModel = viewModel,
                )
            }
            Box {
                IconButton(onClick = { showSettingsMenu = true }) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "设置")
                }
                SettingsMenu(
                    expanded = showSettingsMenu,
                    onDismiss = { showSettingsMenu = false },
                    viewModel = viewModel,
                    settings = settings,
                    onOpenHistory = {
                        showSettingsMenu = false
                        showHistory = true
                    },
                )
            }
        }
    }

    if (showHistory) {
        HistoryDialog(viewModel = viewModel, onDismiss = { showHistory = false })
    }
}

// ---------------------------------------------------------------- 设置菜单

@Composable
private fun SettingsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: TestViewModel,
    settings: UiSettings,
    onOpenHistory: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = "颜色模式",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ThemeModeButton("浅色", settings.themeMode == UiSettings.THEME_LIGHT) {
                viewModel.updateSettings { it.copy(themeMode = UiSettings.THEME_LIGHT) }
            }
            ThemeModeButton("深色", settings.themeMode == UiSettings.THEME_DARK) {
                viewModel.updateSettings { it.copy(themeMode = UiSettings.THEME_DARK) }
            }
            ThemeModeButton("自动", settings.themeMode == UiSettings.THEME_AUTO) {
                viewModel.updateSettings { it.copy(themeMode = UiSettings.THEME_AUTO) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        DropdownMenuItem(
            text = { Text("动态取色（Material You）") },
            trailingIcon = {
                Switch(
                    checked = settings.dynamicColors,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(dynamicColors = checked) }
                    },
                )
            },
            onClick = { viewModel.updateSettings { it.copy(dynamicColors = !it.dynamicColors) } },
        )
        DropdownMenuItem(
            text = { Text("历史记录") },
            onClick = onOpenHistory,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        DropdownMenuItem(
            text = { Text("恢复全部默认配置", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                onDismiss()
                viewModel.resetSettings()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ---------------------------------------------------------------- 配置菜单

@Composable
private fun ConfigMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: TestViewModel,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("保存当前配置") },
            onClick = {
                onDismiss()
                viewModel.saveConfigSnapshot()
            },
        )
        DropdownMenuItem(
            text = { Text("加载保存配置") },
            onClick = {
                onDismiss()
                viewModel.loadConfigSnapshot()
            },
        )
        DropdownMenuItem(
            text = { Text("自动加载配置") },
            trailingIcon = {
                Switch(
                    checked = settings.autoLoadConfig,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(autoLoadConfig = checked) }
                    },
                )
            },
            onClick = { viewModel.updateSettings { it.copy(autoLoadConfig = !it.autoLoadConfig) } },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        DropdownMenuItem(
            text = { Text("删除保存配置", color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                viewModel.deleteConfigSnapshot()
            },
        )
    }
}

// ---------------------------------------------------------------- 横幅/进度

/** 状态横幅文案（对齐 CFData status-bar 语义：就绪绿/进行蓝/错误红） */
private fun bannerInfo(state: TestUiState): Pair<BannerState, String> = when {
    state.phase == "error" -> BannerState.ERROR to (state.message ?: "测速失败")
    state.isRunning -> BannerState.BUSY to when {
        state.phase == "ping" && state.pingProgress != null ->
            "延迟测试 ${state.pingProgress.tested} / ${state.pingProgress.total} · 存活 ${state.pingProgress.success}" +
                if (state.stopRequested) " · 正在停止…" else ""
        state.phase == "download" && state.downloadProgress != null ->
            "下载测速 ${state.downloadProgress.tested} / ${state.downloadProgress.total} · " +
                "已达标 ${state.downloadProgress.qualified}" +
                if (state.stopRequested) " · 正在停止…" else ""
        else -> "正在初始化引擎…"
    }
    state.results.isNotEmpty() -> BannerState.OK to "测试完成，共 ${state.results.size} 个结果"
    else -> BannerState.OK to "引擎就绪 · 配置参数后开始扫描与测试"
}

/** 进度条（对齐 .progress-bar：长条 + 百分比文字） */
@Composable
private fun ProgressBarLine(state: TestUiState) {
    val frac: Float? = when (state.phase) {
        "ping" -> state.pingProgress?.takeIf { it.total > 0 }?.let { it.tested.toFloat() / it.total }
        "download" -> state.downloadProgress?.takeIf { it.total > 0 }?.let { it.tested.toFloat() / it.total }
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { frac ?: 0f },
            modifier = Modifier.weight(1f).height(10.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (frac != null) "${(frac * 100).toInt()}%" else "…",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 演示模式横幅：未检测到 libcfst.so */
@Composable
private fun MockBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "演示模式：未检测到 libcfst.so，当前展示为模拟数据",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
