package com.masgzy.cfstmobile.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masgzy.cfstmobile.data.EdgetunnelClient
import com.masgzy.cfstmobile.data.EdgetunnelStore
import com.masgzy.cfstmobile.data.EtAccount
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.ExportFormatter
import com.masgzy.cfstmobile.ui.components.copyToClipboard
import kotlinx.coroutines.launch

/**
 * 导出与上传底部抽屉（Material You ModalBottomSheet）：
 *
 * 1. 结果导出：TXT（CFData-WEB 兼容 `IP:端口#数据中心`）或 CSV（XIU2 CloudflareST
 *    兼容列序），预览后一键复制；CFData-WEB 用户迁移零成本。
 * 2. edgetunnel 上传：把导出内容写入 edgetunnel（CM 系 Worker）的订阅 ADD.txt，
 *    协议与 CFData-WEB 完全一致；支持多账户管理（新增/选择/删除）与
 *    覆盖/追加两种模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(results: List<CfstResultItem>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EdgetunnelStore(context) }
    val scope = rememberCoroutineScope()

    // ---------------- 导出状态 ----------------
    var format by remember { mutableStateOf(FORMAT_TXT) }
    val content = remember(format, results) {
        if (format == FORMAT_TXT) ExportFormatter.toTxt(results) else ExportFormatter.toCsv(results)
    }
    val preview = remember(content) { content.lines().take(6).joinToString("\n") }

    // ---------------- edgetunnel 状态 ----------------
    var accounts by remember { mutableStateOf(store.load()) }
    var selectedName by remember { mutableStateOf(accounts.firstOrNull()?.name.orEmpty()) }
    var accountMenu by remember { mutableStateOf(false) }
    var etName by remember { mutableStateOf("") }
    var etHost by remember { mutableStateOf("") }
    var etPassword by remember { mutableStateOf("") }
    var etSavePassword by remember { mutableStateOf(false) }
    var etMode by remember { mutableStateOf(EdgetunnelClient.MODE_APPEND) }
    var etUploading by remember { mutableStateOf(false) }
    var etStatus by remember { mutableStateOf("等待上传") }
    var etStatusOk by remember { mutableStateOf<Boolean?>(null) }

    // 选中账户变化时填充表单（未保存密码的账户密码留空待输入）
    LaunchedEffect(selectedName, accounts) {
        val picked = accounts.firstOrNull { it.name == selectedName } ?: return@LaunchedEffect
        etName = picked.name
        etHost = picked.host
        etPassword = picked.password
        etSavePassword = picked.savePassword
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "导出与上传（共 ${results.size} 条）",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            // ---------------- 导出 ----------------
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = format == FORMAT_TXT,
                    onClick = { format = FORMAT_TXT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("TXT（CFData 兼容）", maxLines = 1) },
                )
                SegmentedButton(
                    selected = format == FORMAT_CSV,
                    onClick = { format = FORMAT_CSV },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("CSV（XIU2 兼容）", maxLines = 1) },
                )
            }
            if (format == FORMAT_TXT) {
                Text(
                    text = "每行 IP:端口#数据中心，可直接粘贴到 edgetunnel / 代理软件优选列表",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Text(
                    text = preview,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
            }
            Button(
                onClick = {
                    copyToClipboard(context, content)
                    etStatusOk = true
                    etStatus = "已复制到剪贴板（${results.size} 条）"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("复制到剪贴板") }

            HorizontalDivider()

            // ---------------- edgetunnel 上传 ----------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "edgetunnel 上传", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "写入 edgetunnel（CM 系 Worker）订阅 ADD.txt，协议与 CFData-WEB 一致",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 账户选择 + 删除
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = selectedName.ifBlank { "选择已有账户" },
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            maxLines = 1,
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择账户")
                    }
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        if (accounts.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无账户，请在下方新增") },
                                onClick = { accountMenu = false },
                            )
                        }
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name, maxLines = 1) },
                                onClick = {
                                    selectedName = acc.name
                                    accountMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (selectedName.isNotBlank()) {
                            accounts = store.remove(selectedName, accounts)
                            selectedName = ""
                            etName = ""
                            etHost = ""
                            etPassword = ""
                            etSavePassword = false
                            etStatus = "账户已删除"
                            etStatusOk = null
                        }
                    },
                    enabled = selectedName.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除账户",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OutlinedTextField(
                value = etName,
                onValueChange = { etName = it },
                label = { Text("账户名称") },
                placeholder = { Text("例如 main-tunnel", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp),
            )
            OutlinedTextField(
                value = etHost,
                onValueChange = { etHost = it },
                label = { Text("edgetunnel 域名") },
                placeholder = { Text("https://example.com", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp),
            )
            OutlinedTextField(
                value = etPassword,
                onValueChange = { etPassword = it },
                label = { Text("管理页密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = TextStyle(fontSize = 13.sp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "保存密码到应用私有存储", fontSize = 13.sp)
                Switch(
                    checked = etSavePassword,
                    onCheckedChange = { etSavePassword = it },
                )
            }
            OutlinedButton(
                onClick = {
                    val nameTrim = etName.trim()
                    val hostNorm = EdgetunnelClient.normalizeHost(etHost)
                    if (nameTrim.isBlank()) {
                        etStatus = "请先填写账户名称"
                        etStatusOk = false
                        return@OutlinedButton
                    }
                    if (hostNorm.isBlank()) {
                        etStatus = "请先填写 edgetunnel 域名"
                        etStatusOk = false
                        return@OutlinedButton
                    }
                    accounts = store.upsert(
                        EtAccount(
                            name = nameTrim,
                            host = hostNorm,
                            password = if (etSavePassword) etPassword else "",
                            savePassword = etSavePassword,
                        ),
                        accounts,
                    )
                    selectedName = nameTrim
                    etStatus = "账户「$nameTrim」已保存"
                    etStatusOk = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存账户") }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = etMode == EdgetunnelClient.MODE_APPEND,
                    onClick = { etMode = EdgetunnelClient.MODE_APPEND },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("追加（保留原有）", maxLines = 1) },
                )
                SegmentedButton(
                    selected = etMode == EdgetunnelClient.MODE_OVERWRITE,
                    onClick = { etMode = EdgetunnelClient.MODE_OVERWRITE },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("覆盖（替换全部）", maxLines = 1) },
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        etUploading = true
                        etStatusOk = null
                        EdgetunnelClient.upload(
                            host = etHost,
                            password = etPassword,
                            content = if (format == FORMAT_TXT) {
                                content
                            } else {
                                // 订阅文件用 TXT 行格式；CSV 仅用于表格工具对表
                                ExportFormatter.toTxt(results)
                            },
                            mode = etMode,
                            onStatus = { msg -> etStatus = msg },
                        ).fold(
                            onSuccess = {
                                etStatus = "上传成功：${EdgetunnelClient.normalizeHost(etHost)}/admin/ADD.txt（${etMode}）"
                                etStatusOk = true
                            },
                            onFailure = { e ->
                                etStatus = "上传失败：${e.message ?: "未知错误"}"
                                etStatusOk = false
                            },
                        )
                        etUploading = false
                    }
                },
                enabled = !etUploading && results.isNotEmpty() &&
                    etHost.isNotBlank() && etPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (etUploading) "上传中..." else "上传到 edgetunnel")
            }

            Text(
                text = etStatus,
                fontSize = 12.sp,
                color = when (etStatusOk) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭")
            }
        }
    }
}

private const val FORMAT_TXT = "txt"
private const val FORMAT_CSV = "csv"
