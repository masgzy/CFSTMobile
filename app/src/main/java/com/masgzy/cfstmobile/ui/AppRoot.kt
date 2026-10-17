package com.masgzy.cfstmobile.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.masgzy.cfstmobile.data.DcStep
import com.masgzy.cfstmobile.ui.components.ControlButtons
import com.masgzy.cfstmobile.ui.components.ResultsSection
import com.masgzy.cfstmobile.ui.components.StatusCard
import com.masgzy.cfstmobile.ui.dialogs.DcTestDialog
import com.masgzy.cfstmobile.ui.dialogs.ExportDialog
import com.masgzy.cfstmobile.ui.dialogs.HistoryDialog
import com.masgzy.cfstmobile.ui.dialogs.SettingsDialog
import com.masgzy.cfstmobile.viewmodel.TestViewModel

/** 应用根：顶栏（历史/设置）+ 状态卡 + 控制按钮 + 结果区 + 各对话框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: TestViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDcTest by remember { mutableStateOf(false) }
    val openDcTest: () -> Unit = {
        showDcTest = true
        if (viewModel.dcState.value.step == DcStep.IDLE) viewModel.startDcTest()
    }

    LaunchedEffect(viewModel) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Cloudflare 优选测速", fontWeight = FontWeight.Bold)
                },
                // 跟随 Material You 动态取色（关闭动态取色时为品牌蓝）
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(imageVector = Icons.Filled.History, contentDescription = "历史记录")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (uiState.isMock) {
                MockBanner()
                Spacer(Modifier.height(12.dp))
            }

            StatusCard(state = uiState, settings = settings)

            Spacer(Modifier.height(16.dp))

            ControlButtons(
                state = uiState,
                onStartClick = viewModel::startTest,
                onStopClick = viewModel::stopTest,
                onExportClick = { showExport = true },
            )

            Spacer(Modifier.height(16.dp))

            ResultsSection(
                state = uiState,
                sortBy = settings.sortBy,
                onSortChange = { mode -> viewModel.updateSettings { it.copy(sortBy = mode) } },
                onDcTestClick = openDcTest,
            )
        }
    }

    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    }
    if (showExport) {
        ExportDialog(results = uiState.results, onDismiss = { showExport = false })
    }
    if (showHistory) {
        HistoryDialog(viewModel = viewModel, onDismiss = { showHistory = false })
    }
    if (showDcTest) {
        DcTestDialog(viewModel = viewModel, onDismiss = { showDcTest = false })
    }
}

/** 演示模式横幅：未检测到 libcfst.so */
@Composable
private fun MockBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "演示模式：未检测到 libcfst.so，当前展示为模拟数据",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

