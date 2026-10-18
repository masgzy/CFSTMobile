package com.masgzy.cfstmobile.ui

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masgzy.cfstmobile.BuildConfig
import com.masgzy.cfstmobile.R
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

/** 触感反馈（M3 推荐的选择类轻触感，minSdk 24 可用） */
internal fun android.view.View.tickHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

/**
 * 应用根（完全 Material You + Expressive）：
 * LargeFlexibleTopAppBar（Expressive 弹性大顶栏：主标题 + 版本 subtitle + 右上操作）→
 * 状态横幅 → 模式切换（SegmentedButton）→ 参数卡 → 开始按钮 → 进度条 →
 * 结果区（官方双 Tab：扫描/详细测试）→ 终端日志。
 * 外观设置走 ModalBottomSheet（M3 底部弹层：颜色模式/动态取色/AMOLED）。
 * 全部过渡动效接入 MaterialTheme.motionScheme（官方 Expressive spring 参数）。
 * 非标模式隐藏详细测试 Tab（对齐 CFData switchMode）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppRoot(viewModel: TestViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val dcState by viewModel.dcState.collectAsStateWithLifecycle()
    val scanRows by viewModel.scanRows.collectAsStateWithLifecycle()
    val compactState by viewModel.compactState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var showExport by remember { mutableStateOf(false) }
    var scanTab by rememberSaveable { mutableStateOf(true) }
    val official = settings.ipSource == UiSettings.SOURCE_BUILTIN
    // transitionSpec lambda 非 @Composable 上下文，motionScheme 须在组合期先捕获
    val motion = MaterialTheme.motionScheme

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
            LargeFlexibleTopAppBar(
                title = { Text("CFSTMobile") },
                subtitle = { Text("v${BuildConfig.VERSION_NAME}") },
                actions = { TopActions(viewModel, settings) },
                scrollBehavior = scrollBehavior,
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
                    onLeft = {
                        scanTab = true
                        viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_BUILTIN) }
                    },
                    onRight = {
                        scanTab = true
                        viewModel.updateSettings { it.copy(ipSource = UiSettings.SOURCE_CUSTOM) }
                    },
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

            // ---------------- 开始按钮（开始/停止切换，spring 形变） ----------------
            item(key = "start") {
                AnimatedContent(
                    targetState = uiState.isRunning,
                    transitionSpec = {
                        (fadeIn(animationSpec = motion.fastEffectsSpec()) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = motion.fastSpatialSpec(),
                            ))
                            .togetherWith(
                                fadeOut(animationSpec = motion.fastEffectsSpec()) +
                                    scaleOut(
                                        targetScale = 0.96f,
                                        animationSpec = motion.fastEffectsSpec(),
                                    ),
                            )
                    },
                    label = "startButton",
                ) { running ->
                    if (running) {
                        PrimaryButton(
                            text = if (uiState.stopRequested) "正在停止…" else "停止测试",
                            onClick = { view.tickHaptic(); viewModel.stopTest() },
                            icon = R.drawable.ic_sym_stop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        )
                    } else {
                        PrimaryButton(
                            text = "开始扫描与测试",
                            onClick = { view.tickHaptic(); viewModel.startTest() },
                            icon = R.drawable.ic_sym_play_arrow,
                        )
                    }
                }
            }

            // ---------------- 进度条 ----------------
            if (uiState.isRunning) {
                item(key = "progress") { ProgressBarLine(uiState) }
            }

            // ---------------- 结果区（官方双 Tab；非标只有扫描结果；切换带滑动+淡入） ----------------
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
            item(key = "results") {
                AnimatedContent(
                    targetState = if (!official) "scan" else if (scanTab) "scan" else "dc",
                    transitionSpec = {
                        val dir = if (targetState == "dc") 1 else -1
                        (fadeIn(animationSpec = motion.fastEffectsSpec()) +
                            slideInVertically(
                                animationSpec = motion.defaultSpatialSpec<IntOffset>(),
                            ) { it / 24 * dir })
                            .togetherWith(fadeOut(animationSpec = motion.fastEffectsSpec()))
                    },
                    label = "resultTabs",
                ) { which ->
                    when (which) {
                        "dc" -> DcTestSection(
                            viewModel = viewModel,
                            state = dcState,
                            hasResults = uiState.results.isNotEmpty(),
                            speedMinMbps = settings.officialSpeedMin,
                            onExportClick = { showExport = true },
                        )
                        else -> ResultsSection(
                            viewModel = viewModel,
                            scanRows = scanRows,
                            dcState = dcState,
                            official = official,
                            onExportClick = { showExport = true },
                        )
                    }
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

/** 右上操作排：外观 / GitHub / 配置菜单 / 设置菜单 */
@Composable
private fun TopActions(viewModel: TestViewModel, settings: UiSettings) {
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showConfigMenu by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/masgzy/CFSTMobile")),
                )
            }
        }) {
            Icon(painter = painterResource(R.drawable.ic_sym_code), contentDescription = "GitHub 仓库")
        }
        Box {
            IconButton(onClick = { showConfigMenu = true }) {
                Icon(painter = painterResource(R.drawable.ic_sym_save), contentDescription = "保存/加载配置")
            }
            ConfigMenu(
                expanded = showConfigMenu,
                onDismiss = { showConfigMenu = false },
                viewModel = viewModel,
            )
        }
        Box {
            IconButton(onClick = { showSettingsMenu = true }) {
                Icon(painter = painterResource(R.drawable.ic_sym_settings), contentDescription = "设置")
            }
            SettingsMenu(
                expanded = showSettingsMenu,
                onDismiss = { showSettingsMenu = false },
                viewModel = viewModel,
                onOpenAppearance = {
                    showSettingsMenu = false
                    showAppearance = true
                },
                onOpenHistory = {
                    showSettingsMenu = false
                    showHistory = true
                },
            )
        }
    }

    if (showAppearance) {
        AppearanceSheet(
            settings = settings,
            viewModel = viewModel,
            onDismiss = { showAppearance = false },
        )
    }
    if (showHistory) {
        HistoryDialog(viewModel = viewModel, onDismiss = { showHistory = false })
    }
}

// ---------------------------------------------------------------- 外观设置（M3 底部弹层）

/**
 * 外观设置底部弹层（M3 ModalBottomSheet，对齐 ImageToolbox 等 Material You 标杆应用的设置交互）：
 * 颜色模式三选（SegmentedButton）/ 动态取色开关 / AMOLED 纯黑开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    settings: UiSettings,
    viewModel: TestViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val darkNow = settings.themeMode == UiSettings.THEME_DARK ||
        (settings.themeMode == UiSettings.THEME_AUTO && isSystemInDarkTheme())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("外观", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Material You 个性化：跟随壁纸取色与明暗偏好",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            // 颜色模式（自动 / 浅色 / 深色）
            Text(
                text = "颜色模式",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = settings.themeMode == UiSettings.THEME_AUTO,
                    onClick = { viewModel.updateSettings { it.copy(themeMode = UiSettings.THEME_AUTO) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                ) { Text("自动") }
                SegmentedButton(
                    selected = settings.themeMode == UiSettings.THEME_LIGHT,
                    onClick = { viewModel.updateSettings { it.copy(themeMode = UiSettings.THEME_LIGHT) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                ) { Text("浅色") }
                SegmentedButton(
                    selected = settings.themeMode == UiSettings.THEME_DARK,
                    onClick = { viewModel.updateSettings { it.copy(themeMode = UiSettings.THEME_DARK) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                ) { Text("深色") }
            }
            Spacer(Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("动态取色（Material You）") },
                supportingContent = { Text("Android 12+ 跟随壁纸取色，关闭则用 Cloudflare 蓝品牌色") },
                trailingContent = {
                    Switch(
                        checked = settings.dynamicColors,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(dynamicColors = checked) }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text("AMOLED 纯黑深色") },
                supportingContent = { if (darkNow) Text("深色下表面转纯黑，OLED 更省电") else Text("需先切到深色模式") },
                trailingContent = {
                    Switch(
                        checked = settings.amoled,
                        enabled = darkNow,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(amoled = checked) }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------- 设置菜单

@Composable
private fun SettingsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: TestViewModel,
    onOpenAppearance: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("外观") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_palette),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = onOpenAppearance,
        )
        DropdownMenuItem(
            text = { Text("历史记录") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_history),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = onOpenHistory,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        DropdownMenuItem(
            text = { Text("恢复全部默认配置", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_restart_alt),
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
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_save),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                onDismiss()
                viewModel.saveConfigSnapshot()
            },
        )
        DropdownMenuItem(
            text = { Text("加载保存配置") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_folder_open),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            onClick = {
                onDismiss()
                viewModel.loadConfigSnapshot()
            },
        )
        DropdownMenuItem(
            text = { Text("自动加载配置") },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_sync),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
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
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            },
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
            style = MaterialTheme.typography.labelMedium,
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
                painter = painterResource(R.drawable.ic_sym_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "演示模式：未检测到 libcfst.so，当前展示为模拟数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
