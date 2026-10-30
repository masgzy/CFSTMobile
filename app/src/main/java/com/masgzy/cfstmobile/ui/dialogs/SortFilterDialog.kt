package com.masgzy.cfstmobile.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.masgzy.cfstmobile.data.SortPriorities
import com.masgzy.cfstmobile.data.TableFilterState
import com.masgzy.cfstmobile.ui.components.CfDropdown
import com.masgzy.cfstmobile.ui.components.CfOption
import com.masgzy.cfstmobile.ui.components.CfTextField
import com.masgzy.cfstmobile.ui.components.M3FieldShape

/**
 * 结果区「筛选排序」合一弹窗（对齐 CFData-WEB 1.8.4 openSortModal）：
 *
 * - 筛选区：结果筛选（全部/合格/不合格）+ 延迟低于（毫秒，0=不限制）+ 丢包低于（%，100=不限制）；
 * - 排序区：第 1/2/3 优先级下拉（丢包率/延迟/网速），默认 丢包率→网速→延迟；
 * - 打开时回填当前值（对齐 publishSortFilterControls）；
 * - 应用：延迟下限负值归 0；丢包钳制 0-100、≤0 → 0.01（对齐 applySortModalFilters）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterDialog(
    title: String,
    filter: TableFilterState,
    priorities: SortPriorities,
    onApply: (TableFilterState, SortPriorities) -> Unit,
    onDismiss: () -> Unit,
) {
    var qualified by remember { mutableStateOf(filter.qualified) }
    var latencyText by remember {
        mutableStateOf(if (filter.maxLatencyMs > 0) trimNum(filter.maxLatencyMs) else "0")
    }
    var lossText by remember {
        mutableStateOf(if (filter.maxLossPercent < 100) trimNum(filter.maxLossPercent) else "100")
    }
    var first by remember { mutableStateOf(priorities.first) }
    var second by remember { mutableStateOf(priorities.second) }
    var third by remember { mutableStateOf(priorities.third) }

    val priorityOptions = listOf(
        CfOption("丢包率", SortPriorities.KEY_LOSS),
        CfOption("延迟", SortPriorities.KEY_DELAY),
        CfOption("网速", SortPriorities.KEY_SPEED),
    )
    fun priorityLabel(key: String) = priorityOptions.firstOrNull { it.value == key }?.label ?: key

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // ---------------- 筛选区（对齐 sort-modal-section-title「筛选」） ----------------
            Text("筛选", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            CfDropdown(
                label = null,
                selected = when (qualified) {
                    TableFilterState.QUALIFIED_ONLY -> "仅展示合格"
                    TableFilterState.QUALIFIED_EXCLUDE -> "仅展示不合格"
                    else -> "展示全部结果"
                },
                options = listOf(
                    CfOption("展示全部结果", TableFilterState.QUALIFIED_ALL),
                    CfOption("仅展示合格", TableFilterState.QUALIFIED_ONLY),
                    CfOption("仅展示不合格", TableFilterState.QUALIFIED_EXCLUDE),
                ),
                onSelect = { qualified = it.value },
            )
            CfTextField(
                value = latencyText,
                onValueChange = { latencyText = it },
                placeholder = "延迟低于（毫秒，0=不限制）",
                number = true,
            )
            CfTextField(
                value = lossText,
                onValueChange = { lossText = it },
                placeholder = "丢包低于（%，100=不限制）",
                number = true,
            )

            // ---------------- 排序区（三优先级下拉，默认 丢包率→网速→延迟） ----------------
            Text("排序", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            CfDropdown(
                label = "排序优先级 1",
                selected = priorityLabel(first),
                options = priorityOptions,
                onSelect = { first = it.value },
            )
            CfDropdown(
                label = "排序优先级 2",
                selected = priorityLabel(second),
                options = priorityOptions,
                onSelect = { second = it.value },
            )
            CfDropdown(
                label = "排序优先级 3",
                selected = priorityLabel(third),
                options = priorityOptions,
                onSelect = { third = it.value },
            )
            Text(
                text = "筛选：延迟/丢包为空或测速失败的行，在启用对应筛选后会被隐藏；" +
                    "0ms 与 100% 表示不限制。排序：丢包率和延迟按从低到高排序；" +
                    "网速按从高到低排序，网速为空或未测速排在最后。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    val latency = latencyText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val lossRaw = lossText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 100.0
                    val loss = if (lossRaw <= 0.0) 0.01 else lossRaw
                    onApply(
                        TableFilterState(
                            qualified = qualified,
                            maxLatencyMs = latency,
                            maxLossPercent = loss,
                        ),
                        SortPriorities(first = first, second = second, third = third),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = M3FieldShape,
            ) {
                Text("应用筛选排序", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

/** 数值回显去尾零（12.0 → "12"，0.01 → "0.01"） */
private fun trimNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
