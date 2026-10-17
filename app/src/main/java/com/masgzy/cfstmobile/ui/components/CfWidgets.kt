package com.masgzy.cfstmobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
 * CFData-WEB 风格基础组件库（Material You 取色映射）：
 * 状态横幅 / 模式切换大按钮 / section-box 参数卡 / option-item 参数行 /
 * 下拉选择 / 徽章 / 绿色操作按钮 / 终端日志面板 / 结果 Tab。
 * 布局与 CFData-WEB index.html 逐块对齐，颜色走 MaterialTheme 语义槽位，
 * 仅语义色（成功绿/日志深底）固定不变。
 */

// ---------------------------------------------------------------- 状态横幅

/** 状态横幅：4dp 左色条 + 淡色底（对齐 .status-bar），ok=绿 / busy=蓝 / error=红 */
@Composable
fun StatusBanner(text: String, state: BannerState, modifier: Modifier = Modifier) {
    val accent = when (state) {
        BannerState.OK -> OkGreen
        BannerState.BUSY -> MaterialTheme.colorScheme.primary
        BannerState.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
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

enum class BannerState { OK, BUSY, ERROR }

// ---------------------------------------------------------------- 模式切换

/** 模式切换大按钮（对齐 .mode-switch：选中实心主色，未选中浅底） */
@Composable
fun ModeSwitch(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ModeButton(label = leftLabel, selected = leftSelected, onClick = onLeft, modifier = Modifier.weight(1f))
        ModeButton(label = rightLabel, selected = !leftSelected, onClick = onRight, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------- 参数卡片

/** section-box 参数卡（对齐 .section-box.controls：标题 + 副标题 + 内容列） */
@Composable
fun ParamsCard(
    title: String,
    hint: String?,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (hint != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * option-item 参数行（对齐 .option-item：左标签 + 右控件，外框圆角 12）。
 * 全部行统一 label 固定 104dp，控件占余宽，与 Web 端 120px 标签列同构。
 */
@Composable
fun OptionItem(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/** option-item 之间的标准间距 */
@Composable
fun OptionGap() = Spacer(Modifier.height(10.dp))

// ---------------------------------------------------------------- 输入控件

/** 选择器（对齐 select：只读输入框 + 下拉箭头 + DropdownMenu，支持分组标题） */
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
            shape = RoundedCornerShape(10.dp),
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
                    .clip(RoundedCornerShape(10.dp))
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

/** 数字/文本输入（对齐 input：填充满控件区，数字键盘可选） */
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
        shape = RoundedCornerShape(10.dp),
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

/** 绿色操作按钮（对齐 .action-btn.export-btn 的语义成功绿，固定不随主题） */
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
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CfGreen,
            contentColor = Color.White,
            disabledContainerColor = CfGreen.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.8f),
        ),
        modifier = modifier.fillMaxWidth().height(42.dp),
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 主色操作按钮（Material You 取色） */
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
        shape = RoundedCornerShape(10.dp),
        colors = colors,
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        Text(text = text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 计数徽章（对齐 .badge：主色圆角胶囊） */
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

/** 结果双 Tab（对齐 .tabs：外框圆角盒 + 选中项淡主色底） */
@Composable
fun CfTabs(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabCell(label = leftLabel, selected = leftSelected, onClick = onLeft, modifier = Modifier.weight(1f))
        TabCell(label = rightLabel, selected = !leftSelected, onClick = onRight, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TabCell(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- 日志面板

/** 终端日志面板（对齐 .log-box：固定深底绿字等宽，自动滚到最新） */
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
            .clip(RoundedCornerShape(12.dp))
            .background(LogBg)
            .border(1.dp, LogBorder, RoundedCornerShape(12.dp))
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

// ---------------------------------------------------------------- 结果表格

/**
 * 结果表格（对齐 scroll-table：横向滚动 + 表头行）。
 * columns 与 cells 等长；行点击回调用于复制。
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
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
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
