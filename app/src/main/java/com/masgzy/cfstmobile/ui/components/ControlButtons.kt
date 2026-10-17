package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masgzy.cfstmobile.ui.theme.BadRed
import com.masgzy.cfstmobile.viewmodel.TestUiState

/** 开始 / 停止 主按钮 + 导出按钮（CFSTAPP 同款布局） */
@Composable
fun ControlButtons(
    state: TestUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = if (state.isRunning) onStopClick else onStartClick,
            modifier = Modifier.weight(1f),
            colors = if (state.isRunning) {
                ButtonDefaults.buttonColors(containerColor = BadRed)
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = if (state.isRunning) "停止" else "开始测速")
        }

        OutlinedButton(
            onClick = onExportClick,
            enabled = !state.isRunning && state.results.isNotEmpty(),
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(text = "导出")
        }
    }
}
