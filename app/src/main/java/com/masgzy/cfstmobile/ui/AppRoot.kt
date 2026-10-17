package com.masgzy.cfstmobile.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
 * 应用根（完全 Material You）：
 * LargeTopAppBar（滚动折叠：CFSTMobile 主标题 + 版本号副行 + 右上操作）→
 * 状态横幅 → 模式切换（SegmentedButton）→ 参数卡 → 开始按钮 → 进度条 →
 * 结果区（官方双 Tab：扫描/详细测试）→ 终端日志。
 * 非标模式隐藏详细测试 Tab（对齐 CFData switchMode）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: TestViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val dcState by viewModel.dcState.collectAsStateWithLifecycle()
    val scanRows by viewModel.scanRows.collectAsStateWithLifecycle()
    val compactState by viewModel.compactState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showExport by remember { mutableStateOf(false) }
    var scanTab by rememberSaveable { mutableStateOf(true) }
    val official = settings.ipSource == UiSettings.SOURCE_BUILTIN

    LaunchedEffect(viewModel) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 官方模式：数据中心详细测试开始后自动切到详细测试 Tab（对齐 CFData selectDC 的 switchTab('test')）
    LaunchedEffect(dcState.step, official) {
        if (official && dcState.step == com.masgzy.cfstmobile.data.DcStep.TESTING) scanTab = false
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "CFSTMobile",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { TopActions(viewModel, settings) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isMock) {
                item(key = "mock") { MockBanner() }
            }

            // ---------------- 状态横幅 ----------------
            item(key = "status") {
                val (bannerState, bannerText) = bannerInfo(uiState)
                StatusBanner(text = bannerText, state = bannerState)
            }

            // ---------------- 模式切换（SegmentedButton） ----------------
            item(key = "mode") {
                ModeSwitch(
                    leftLabel = "官方优选",
                    rightLabel = "非标优选",
                    leftSelected = official,
                    onLeft = { scanTab = true; viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_BUILTIN) } },
                    onRight = { scanTab = true; viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_CUSTOM) } },
                )
            }

            // ---------------- 参数卡 ----------------
            item(key = "params") {
                ParamsSection(
                    settings = settings,
                    official = official,
                    onUpdate = viewModel::updateSettings,
                    compactState = compactState,
                    onStartCompact = viewModel::startCompact,
                    onStopCompact = viewModel::stopCompact,
                    onDeleteCompact = viewModel::deleteCompactLibrary,
                )
            }

            // ---------------- 开始按钮 ----------------
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

            // ---------------- 进度条 ----------------
            if (uiState.isRunning) {
                item(key = "progress") { ProgressBarLine(uiState) }
            }

            // ---------------- 结果区（官方双 Tab；非标只有扫描结果） ----------------
            if (official) {
                item(key = "tabs") {
                    CfTabs(
                        leftLabel = "扫描结果",
                        rightLabel = "详细测试",
                        leftSelected = scanTab,
                        onLeft = { scanTab = true },
                        onRight = { scanTab = false },
                    )
                }
            }
            if (official && !scanTab) {
                item(key = "dc") {
                    DcTestSection(
                        viewModel = viewModel,
                        state = dcState,
                        hasResults = uiState.results.isNotEmpty(),
                        speedMinMbps = settings.officialSpeedMin,
                        onExportClick = { showExport = true },
                    )
                }
            } else {
                item(key = "scan") {
                    ResultsSection(
                        viewModel = viewModel,
                        scanRows = scanRows,
                        dcState = dcState,
                        official = official,
                        onExportClick = { showExport = true },
                    )
                }
            }

            // ---------------- 终端日志面板 ----------------
            item(key = "logs") { LogPanel(lines = logs) }
        }
    }

    if (showExport) {
        ExportDialog(results = uiState.results, onDismiss = { showExport = false })
    }
}

// ---------------------------------------------------------------- 头部操作

/** 右上操作排：GitHub / 配置菜单 / 设置菜单 */
@Composable
private fun TopActions(viewModel: TestViewModel, settings: UiSettings) {
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showConfigMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
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

@Composable
private fun ThemeModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
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

/** 状态横幅文案（对齐 CFData status-bar 语义：就绪绿/进行主色/错误红） */
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

/** 进度条（M3 LinearProgressIndicator + 百分比文字） */
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
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
