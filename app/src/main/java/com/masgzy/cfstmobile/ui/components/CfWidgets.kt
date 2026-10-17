package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masgzy.cfstmobile.ui.theme.CfGreen
import com.masgzy.cfstmobile.ui.theme.LogBg
import com.masgzy.cfstmobile.ui.theme.LogBorder
import com.masgzy.cfstmobile.ui.theme.LogGreen
import com.masgzy.cfstmobile.ui.theme.OkGreen

/**
 * Material 3 基础组件库（完全 Material You）：
 * 状态横幅 / 模式切换（SegmentedButton）/ 参数卡（M3 Card）/ option-item /
 * 下拉选择 / 徽章 / 语义绿操作按钮 / 终端日志面板 / 结果 Tab（M3 TabRow）。
 * 颜色全部走 MaterialTheme 语义槽位（动态取色随壁纸），
 * 仅语义色（成功绿/日志深底）固定不变。
 */

/** M3 大圆角（卡片/面板统一） */
internal val M3CardShape = RoundedCornerShape(20.dp)
internal val M3FieldShape = RoundedCornerShape(12.dp)
internal val M3ItemShape = RoundedCornerShape(14.dp)

// ---------------------------------------------------------------- 状态横幅

/** 状态横幅：M3 tonal 面（语义色着色），ok=绿 / busy=主色 / error=红 */
@Composable
fun StatusBanner(text: String, state: BannerState, modifier: Modifier = Modifier) {
    val accent = when (state) {
        BannerState.OK -> OkGreen
        BannerState.BUSY -> MaterialTheme.colorScheme.primary
        BannerState.ERROR -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = M3ItemShape,
        color = accent.copy(alpha = 0.10f),
        contentColor = accent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class BannerState { OK, BUSY, ERROR }

// ---------------------------------------------------------------- 模式切换

/** 模式切换（M3 SingleChoiceSegmentedButtonRow） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSwitch(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = leftSelected,
            onClick = onLeft,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text(leftLabel, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        SegmentedButton(
            selected = !leftSelected,
            onClick = onRight,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text(rightLabel, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- 参数卡片

/** section-box 参数卡（M3 Card，20dp 大圆角 + surfaceContainerLow 色调） */
@Composable
fun ParamsCard(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = M3CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * option-item 参数行（label 在上小字 + 控件在下，tonal 内嵌面）。
 * 双列网格时外层传 Modifier.weight(1f)，全宽项直接用默认 fillMaxWidth。
 */
@Composable
fun OptionItem(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(M3ItemShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Box { control() }
    }
}

/** option-item 之间的标准间距 */
@Composable
fun OptionGap() = Spacer(Modifier.height(10.dp))

/** 双列网格行（对齐 CFData .controls 的 option-item 2 列排布） */
@Composable
fun OptionGrid(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.weight(1f)) { left() }
        Box(modifier = Modifier.weight(1f)) { right() }
    }
}

// ---------------------------------------------------------------- 输入控件

/** 选择器（只读输入框 + 下拉箭头 + DropdownMenu，支持分组标题） */
@Composable
fun CfDropdown(
    label: String?,
    selected: String,
    options: List<CfOption>,
    enabled: Boolean = true,
    onSelect: (CfOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = null,
            placeholder = { if (label != null) Text(label, fontSize = 13.sp, maxLines = 1) },
            textStyle = TextStyle(fontSize = 14.sp),
            shape = M3FieldShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            trailingIcon = {
                Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(M3FieldShape)
                    .clickable { expanded = true },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                if (option.header) {
                    Text(
                        text = option.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(option.label, fontSize = 14.sp) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 下拉选项（header=true 时渲染为分组标题，不可点） */
data class CfOption(val label: String, val value: String, val header: Boolean = false)

/** 数字/文本输入（填充满控件区，数字键盘可选） */
@Composable
fun CfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    number: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        placeholder = { if (placeholder != null) Text(placeholder, fontSize = 13.sp, maxLines = 1) },
        textStyle = TextStyle(fontSize = 14.sp),
        shape = M3FieldShape,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- 按钮/徽章

/** 语义成功绿操作按钮（固定语义色，M3 胶囊形） */
@Composable
fun GreenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = CfGreen,
            contentColor = Color.White,
            disabledContainerColor = CfGreen.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.8f),
        ),
        modifier = modifier.fillMaxWidth().height(44.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 主色操作按钮（Material You 取色，M3 胶囊形） */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = colors,
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 计数徽章（主色圆角胶囊） */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

// ---------------------------------------------------------------- 结果 Tab

/** 结果双 Tab（M3 TabRow + SecondaryIndicator） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CfTabs(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = if (leftSelected) 0 else 1,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        indicator = { positions ->
            SecondaryIndicator(
                Modifier.tabIndicatorOffset(positions[if (leftSelected) 0 else 1]),
                height = 3.dp,
            )
        },
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
    ) {
        Tab(
            selected = leftSelected,
            onClick = onLeft,
            text = { Text(leftLabel, fontWeight = FontWeight.Bold) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Tab(
            selected = !leftSelected,
            onClick = onRight,
            text = { Text(rightLabel, fontWeight = FontWeight.Bold) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- 日志面板

/** 终端日志面板（固定深底绿字等宽，自动滚到最新；CFData 终端风格语义色） */
@Composable
fun LogPanel(lines: List<String>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LogBg)
            .border(1.dp, LogBorder, RoundedCornerShape(16.dp))
            .verticalScroll(scroll)
            .padding(12.dp),
    ) {
        if (lines.isEmpty()) {
            Text("[系统] 等待操作...", color = LogGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        } else {
            lines.forEach { line ->
                Text(
                    text = line,
                    color = LogGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 宽表格

/**
 * 宽表格（横向滚动 + 纵向滚动，M3 tonal 容器）。
 * 每列固定宽度，表头与数据行同构对齐；适合 8~12 列的宽表。
 */
@Composable
fun ScrollTable(
    columns: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    columnWidths: List<androidx.compose.ui.unit.Dp> = List(columns.size) { 88.dp },
    maxHeight: androidx.compose.ui.unit.Dp = 320.dp,
    onRowClick: ((Int) -> Unit)? = null,
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(M3ItemShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), M3ItemShape)
            .horizontalScroll(hScroll),
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            columns.forEachIndexed { index, col ->
                Text(
                    text = col,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(columnWidths[index]),
                    maxLines = 1,
                )
            }
        }
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无结果",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .verticalScroll(vScroll),
            ) {
                rows.forEachIndexed { index, cells ->
                    Row(
                        modifier = Modifier
                            .clickable(enabled = onRowClick != null) { onRowClick?.invoke(index) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        cells.forEachIndexed { cellIndex, cell ->
                            Text(
                                text = cell,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(columnWidths[cellIndex]),
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

/**
 * 结果表格（横向滚动 + 表头行）。columns 与 cells 等长；行点击回调用于复制。
 */
@Composable
fun CfTable(
    columns: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    onRowClick: ((Int) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(M3ItemShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), M3ItemShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            columns.forEach { col ->
                Text(
                    text = col,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无结果",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).height(300.dp),
            ) {
                rows.forEachIndexed { index, cells ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = onRowClick != null) { onRowClick?.invoke(index) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        cells.forEach { cell ->
                            Text(
                                text = cell,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}
