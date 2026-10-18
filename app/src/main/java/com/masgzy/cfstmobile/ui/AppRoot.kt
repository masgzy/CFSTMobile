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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import com.masgzy.cfstmobile.data.SeedColors
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
import com.masgzy.cfstmobile.ui.theme.ColorTuple
import com.masgzy.cfstmobile.ui.theme.ColorTupleDefaults
import com.masgzy.cfstmobile.ui.theme.CfMotion
import com.masgzy.cfstmobile.ui.theme.PaletteStyle
import com.masgzy.cfstmobile.ui.dialogs.ExportDialog
import com.masgzy.cfstmobile.ui.dialogs.HistoryDialog
import com.masgzy.cfstmobile.viewmodel.TestUiState
import com.masgzy.cfstmobile.viewmodel.TestViewModel

/** 触感反馈（M3 推荐的选择类轻触感，minSdk 24 可用） */
internal fun android.view.View.tickHaptic() {
    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

/**
 * 应用根（完全 Material You）：
 * LargeTopAppBar（滚动折叠：主标题 + 版本号副行 + 右上操作）→
 * 状态横幅 → 模式切换（SegmentedButton）→ 参数卡 → 开始按钮 → 进度条 →
 * 结果区（官方双 Tab：扫描/详细测试）→ 终端日志。
 * 外观设置走 ModalBottomSheet（M3 底部弹层：颜色模式/动态取色/AMOLED）。
 * 全部过渡动效接入 CfMotion（M3 Expressive 官方 spring 参数，见 theme/Motion.kt）。
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
    val view = LocalView.current

    var showExport by remember { mutableStateOf(false) }
    var scanTab by rememberSaveable { mutableStateOf(true) }
    val official = settings.ipSource == UiSettings.SOURCE_BUILTIN
    // CfMotion 为无状态单例，任何 lambda（含非组合的 transitionSpec）均可直接引用
    val motion = CfMotion

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
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
            Icon(painter = painterResource(R.drawable.ic_github), contentDescription = "GitHub 仓库")
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
 * 外观设置底部弹层（M3 ModalBottomSheet，对齐 ImageToolbox / AnythingMobile 的设置列表交互）：
 * - 颜色模式：点击弹单选列表对话框（跟随系统/浅色/深色）
 * - 动态取色：开关（Android 12+）
 * - 配色方案：ImageToolbox ColorSchemeSettingItem 同款——入口行右侧带当前种子色圆片预览，
 *   动态取色开启时禁用（点击 Toast 提示），关闭后弹 PaletteSheet 调色板
 * - AMOLED 纯黑：ImageToolbox 语义——常显开关（浅色模式下也可开启），
 *   启用后在夜间模式下表面色将设为纯黑
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    settings: UiSettings,
    viewModel: TestViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showPaletteSheet by remember { mutableStateOf(false) }

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
                text = "Material You 个性化：颜色模式 / 动态取色 / 配色方案 / AMOLED",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            // 颜色模式（点击弹列表对话框）
            ListItem(
                headlineContent = { Text("颜色模式") },
                supportingContent = { Text(themeModeLabel(settings.themeMode)) },
                trailingContent = {
                    Icon(
                        painter = painterResource(R.drawable.ic_sym_keyboard_arrow_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showThemeModeDialog = true },
            )
            // 动态取色
            ListItem(
                headlineContent = { Text("动态取色（Material You）") },
                supportingContent = { Text("Android 12+ 跟随壁纸取色；关闭后可用配色方案") },
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
            // 配色方案（ImageToolbox ColorSchemeSettingItem：入口行 + 种子色预览圆片）
            ListItem(
                headlineContent = { Text("配色方案") },
                supportingContent = {
                    Text(
                        if (settings.dynamicColors) "需先关闭动态取色"
                        else "调色板种子色 · ${styleLabel(settings.paletteStyle)}",
                    )
                },
                trailingContent = {
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(settings.colorTuple.primary.toInt()))
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (settings.dynamicColors) {
                            Toast.makeText(context, "先关闭动态取色，再自定义配色", Toast.LENGTH_SHORT).show()
                        } else {
                            showPaletteSheet = true
                        }
                    },
            )
            // AMOLED（ImageToolbox 语义：常显开关，作用夜间模式）
            ListItem(
                headlineContent = { Text("AMOLED 纯黑深色") },
                supportingContent = { Text("启用后在夜间模式下表面色将设为纯黑") },
                trailingContent = {
                    Switch(
                        checked = settings.amoled,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(amoled = checked) }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showThemeModeDialog) {
        OptionsDialog(
            title = "颜色模式",
            options = listOf(
                UiSettings.THEME_AUTO to "跟随系统",
                UiSettings.THEME_LIGHT to "浅色",
                UiSettings.THEME_DARK to "深色",
            ),
            selected = settings.themeMode,
            onSelect = { mode -> viewModel.updateSettings { it.copy(themeMode = mode) } },
            onDismiss = { showThemeModeDialog = false },
        )
    }
    if (showPaletteSheet) {
        PaletteSheet(
            settings = settings,
            viewModel = viewModel,
            onDismiss = { showPaletteSheet = false },
        )
    }
}

/**
 * 配色方案底部弹层（ImageToolbox AvailableColorTuplesSheet 对齐）：
 * 调色板风格（九种 PaletteStyle）/ 对比度滑条 / 简单变体预设（17 圆片）/
 * 自定义种子色取色器（SV 区 + 色相条 + HEX 三路同步）。
 * 所有编辑即时生效（主题实时预览），"恢复默认"一键还原 Cloudflare 蓝。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteSheet(
    settings: UiSettings,
    viewModel: TestViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showStyleDialog by remember { mutableStateOf(false) }
    var contrastDraft by remember(settings.themeContrast) { mutableStateOf(settings.themeContrast) }
    val seedColor = Color(settings.colorTuple.primary.toInt())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_palette),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "配色方案",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // 调色板风格（ImageToolbox 九种调色板风格）
            SheetRow(
                iconRes = R.drawable.ic_sym_tune,
                title = "调色板风格",
                subtitle = styleLabel(settings.paletteStyle),
                onClick = { showStyleDialog = true },
            )

            // 对比度（-1..1，DynamicScheme contrastLevel，松手提交）
            SheetRow(
                iconRes = R.drawable.ic_sym_adjust,
                title = "对比度",
                subtitle = String.format("%.2f", contrastDraft),
                onClick = {},
            ) {
                Slider(
                    value = contrastDraft,
                    onValueChange = { contrastDraft = it },
                    valueRange = -1f..1f,
                    onValueChangeFinished = {
                        viewModel.updateSettings { s -> s.copy(themeContrast = contrastDraft) }
                    },
                    modifier = Modifier.width(140.dp),
                )
            }

            // 简单变体（ImageToolbox 16 预设 + Cloudflare 蓝默认，主色派生全角色）
            Text(
                "简单变体",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(ColorTupleDefaults.presets, key = { it.first }) { (name, tuple) ->
                    PresetChip(
                        name = name,
                        tuple = tuple,
                        selected = tuple.primary == seedColor,
                        onClick = {
                            viewModel.updateSettings {
                                it.copy(colorTuple = SeedColors(primary = tuple.primary.toArgb().toLong() and 0xFFFFFFFF))
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // 自定义种子色（SV 取色区 + 色相滑条 + HEX 输入，三路双向同步）
            Text(
                "自定义颜色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeedColorEditor(
                argb = settings.colorTuple.primary.toInt(),
                onChange = { argb ->
                    viewModel.updateSettings { it.copy(colorTuple = SeedColors(primary = argb.toLong() and 0xFFFFFFFF)) }
                },
            )

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = {
                viewModel.updateSettings {
                    it.copy(
                        colorTuple = SeedColors(),
                        paletteStyle = UiSettings.STYLE_DEFAULT,
                        themeContrast = 0f,
                    )
                }
            }) {
                Text("恢复默认")
            }
        }
    }

    if (showStyleDialog) {
        OptionsDialog(
            title = "调色板风格",
            options = PaletteStyle.entries.map { it.name to styleLabel(it.name) },
            selected = settings.paletteStyle,
            onSelect = { style -> viewModel.updateSettings { it.copy(paletteStyle = style) } },
            onDismiss = { showStyleDialog = false },
        )
    }
}

/** 通用单选列表对话框（对齐 AnythingMobile OptionsDialog 的列表选择交互） */
@Composable
private fun <T> OptionsDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

/** 弹层内通用设置行：左图标 + 标题/副标题 + 右侧控件（对齐 AnythingMobile SheetActionRow）。 */
@Composable
private fun SheetRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

/** "简单变体"预设圆片：主/辅/第三三色扇区（对齐 ImageToolbox 花瓣色板）。 */
@Composable
private fun PresetChip(
    name: String,
    tuple: ColorTuple,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(color = tuple.primary, startAngle = -90f, sweepAngle = 180f, useCenter = true)
            drawArc(
                color = tuple.secondary ?: tuple.primary,
                startAngle = 90f, sweepAngle = 90f, useCenter = true,
            )
            drawArc(
                color = tuple.tertiary ?: tuple.primary,
                startAngle = 180f, sweepAngle = 90f, useCenter = true,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        if (selected) {
            val checkTint = if (tuple.primary.luminance() > 0.5f) Color.Black else Color.White
            Icon(
                painter = painterResource(R.drawable.ic_sym_check),
                contentDescription = "已选中 $name",
                tint = checkTint,
            )
        }
    }
}

/** 种子色取色器：SV 取色区 + 色相滑条 + #RRGGBB 输入框（以 argb 为 key 三路同步）。 */
@Composable
private fun SeedColorEditor(argb: Int, onChange: (Int) -> Unit) {
    var hsv by remember(argb) { mutableStateOf(argb.toHsv()) }
    var hex by remember(argb) { mutableStateOf(hexOf(argb)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SvArea(hsv = hsv, onChange = { next ->
            hsv = next
            onChange(next.toColor().toArgb())
        })
        HueSlider(hsv = hsv, onChange = { next ->
            hsv = next
            onChange(next.toColor().toArgb())
        })
        HexColorField(hex = hex, onHexChange = { input ->
            hex = input
            parseHexColor(input)?.let { c ->
                hsv = c.toHsv()
                onChange(c)
            }
        })
    }
}

/** 颜色模式显示名 */
private fun themeModeLabel(mode: String): String = when (mode) {
    UiSettings.THEME_LIGHT -> "浅色"
    UiSettings.THEME_DARK -> "深色"
    else -> "跟随系统"
}

/** 调色板风格显示名（对齐 ImageToolbox 中文命名） */
private fun styleLabel(name: String): String = when (name) {
    "Neutral" -> "中性"
    "Vibrant" -> "鲜艳"
    "Expressive" -> "表现力"
    "Rainbow" -> "彩虹"
    "FruitSalad" -> "水果沙拉"
    "Monochrome" -> "单色"
    "Fidelity" -> "保真"
    "Content" -> "内容"
    else -> "色调斑点 · 默认"
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
