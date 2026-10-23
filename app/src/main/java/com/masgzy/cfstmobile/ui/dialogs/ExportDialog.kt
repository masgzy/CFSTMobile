package com.masgzy.cfstmobile.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.masgzy.cfstmobile.R
import com.masgzy.cfstmobile.data.EdgetunnelClient
import com.masgzy.cfstmobile.data.EdgetunnelStore
import com.masgzy.cfstmobile.data.EtAccount
import com.masgzy.cfstmobile.data.GhConfig
import com.masgzy.cfstmobile.data.GithubClient
import com.masgzy.cfstmobile.data.GithubStore
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.ExportFormatter
import com.masgzy.cfstmobile.model.ExportFormatter.ExportField
import com.masgzy.cfstmobile.model.ExportFormatter.ExportOptions
import com.masgzy.cfstmobile.ui.components.copyToClipboard
import kotlinx.coroutines.launch

/**
 * 导出数据源（对齐 CFData copyModal 随当前 tab 切换）：
 * [isQualified] 非空时弹窗内提供「合格筛选」（全部/仅合格/仅不合格，对齐 CFData）。
 */
data class ExportSource(
    val label: String,
    val items: List<CfstResultItem>,
    val isQualified: ((CfstResultItem) -> Boolean)? = null,
)

/**
 * 导出与上传底部抽屉（Material You ModalBottomSheet）：
 *
 * 1. 结果导出：TXT（CFData-WEB 兼容 `IP:端口#数据中心`）或 CSV（XIU2 CloudflareST
 *    兼容列序），预览后一键复制；支持行范围 / IP 类型 / 合格筛选 / 字段选择 /
 *    自定义字段 / TXT 分隔符 / IPv6 方括号自定义（管线对齐 CFData-WEB 一键复制弹窗）。
 *    多数据源（扫描结果 / 详细测试）可切换。
 * 2. edgetunnel 上传：把导出内容写入 edgetunnel（CM 系 Worker）的订阅 ADD.txt，
 *    协议与 CFData-WEB 完全一致；支持多账户管理（新增/选择/删除）与
 *    覆盖/追加两种模式。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportDialog(sources: List<ExportSource>, onDismiss: () -> Unit, initialSource: Int = 0) {
    val context = LocalContext.current
    val store = remember { EdgetunnelStore(context) }
    val scope = rememberCoroutineScope()

    // ---------------- 数据源（扫描/详细测试切换，对齐 CFData copyModalMode） ----------------
    var sourceIdx by remember { mutableStateOf(initialSource.coerceIn(0, (sources.size - 1).coerceAtLeast(0))) }
    val source = sources.getOrElse(sourceIdx) { sources.first() }

    // ---------------- 导出状态（行范围/IP 类型/合格筛选/字段/分隔符，管线对齐 CFData） ----------------
    var format by remember { mutableStateOf(FORMAT_TXT) }
    var fieldOrder by remember { mutableStateOf(ExportFormatter.DEFAULT_TXT_FIELDS) }
    var startRowText by remember { mutableStateOf("1") }
    var endRowText by remember { mutableStateOf("0") }
    var ipFilter by remember { mutableStateOf(ExportFormatter.IP_ALL) }
    var qualifiedFilter by remember { mutableStateOf("all") }
    var txtSeparator by remember { mutableStateOf("-") }
    var ipv6Brackets by remember { mutableStateOf(false) }
    var customFieldsText by remember { mutableStateOf("") }

    // 合格筛选（对齐 CFData 复制合格结果：全部/仅合格/仅不合格）在 ipFilter 之前生效
    val filteredItems = remember(source, ipFilter, qualifiedFilter) {
        source.items.filter { r ->
            val passQualified = when (qualifiedFilter) {
                "qualified" -> source.isQualified?.invoke(r) ?: true
                "unqualified" -> source.isQualified?.let { !it(r) } ?: false
                else -> true
            }
            passQualified && (ipFilter == ExportFormatter.IP_ALL || ExportFormatter.ipVersion(r.ip) == ipFilter)
        }
    }

    val exportOptions = ExportOptions(
        startRow = startRowText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        endRow = endRowText.toIntOrNull()?.coerceAtLeast(0) ?: 0,
        ipFilter = ExportFormatter.IP_ALL, // IP 类型已并入 filteredItems
        fields = fieldOrder,
        txtSeparator = txtSeparator.ifBlank { "-" },
        ipv6Brackets = ipv6Brackets,
        customFields = ExportFormatter.parseCustomFields(customFieldsText),
    )
    val content = remember(format, filteredItems, exportOptions) {
        ExportFormatter.build(filteredItems, format, exportOptions)
    }
    val exportedCount = remember(content) { content.lines().count { it.isNotBlank() } }
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

    // ---------------- GitHub 上传状态 ----------------
    val ghStore = remember { GithubStore(context) }
    val ghSaved = remember { ghStore.load() }
    var ghToken by remember { mutableStateOf(ghSaved.token) }
    var ghOwner by remember { mutableStateOf(ghSaved.owner) }
    var ghRepo by remember { mutableStateOf(ghSaved.repo) }
    var ghBranch by remember { mutableStateOf(ghSaved.branch) }
    var ghPath by remember { mutableStateOf(ghSaved.path) }
    var ghMessage by remember { mutableStateOf(ghSaved.message) }
    var ghSaveToken by remember { mutableStateOf(ghSaved.saveToken) }
    var ghUploading by remember { mutableStateOf(false) }
    var ghStatus by remember { mutableStateOf("等待上传") }
    var ghStatusOk by remember { mutableStateOf<Boolean?>(null) }

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
                text = "导出与上传（${source.label} ${source.items.size} 条，筛选后 ${filteredItems.size} 条）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // ---------------- 数据源切换（扫描结果 / 详细测试） ----------------
            if (sources.size > 1) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    sources.forEachIndexed { idx, s ->
                        SegmentedButton(
                            selected = sourceIdx == idx,
                            onClick = { sourceIdx = idx },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = sources.size),
                            label = { Text(s.label, maxLines = 1) },
                        )
                    }
                }
            }

            // ---------------- 导出 ----------------
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = format == FORMAT_TXT,
                    // 对齐 CFData：切换格式时字段重置为该格式默认列序
                    onClick = {
                        format = FORMAT_TXT
                        fieldOrder = ExportFormatter.DEFAULT_TXT_FIELDS
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("TXT（CFData 兼容）", maxLines = 1) },
                )
                SegmentedButton(
                    selected = format == FORMAT_CSV,
                    onClick = {
                        format = FORMAT_CSV
                        fieldOrder = ExportFormatter.DEFAULT_CSV_FIELDS
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("CSV（XIU2 兼容）", maxLines = 1) },
                )
            }
            Text(
                text = if (format == FORMAT_TXT) {
                    "每行 IP:端口#数据中心，可直接粘贴到 edgetunnel / 代理软件优选列表"
                } else {
                    "列序对齐 XIU2/CloudflareST result.csv，便于表格工具对表"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 行范围（对齐 CFData 开始行/结束行，结束行 0=到末尾）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startRowText,
                    onValueChange = { startRowText = it.filter(Char::isDigit).take(5) },
                    label = { Text("开始行") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = endRowText,
                    onValueChange = { endRowText = it.filter(Char::isDigit).take(5) },
                    label = { Text("结束行(0=末尾)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
            // IP 类型筛选
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("全部" to ExportFormatter.IP_ALL, "仅 IPv4" to ExportFormatter.IP_V4, "仅 IPv6" to ExportFormatter.IP_V6)
                    .forEachIndexed { idx, (label, value) ->
                        SegmentedButton(
                            selected = ipFilter == value,
                            onClick = { ipFilter = value },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
            }
            // 合格筛选（对齐 CFData copyModal：全部/仅合格/仅不合格；仅提供 isQualified 的源显示）
            if (source.isQualified != null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("全部" to "all", "仅合格" to "qualified", "仅不合格" to "unqualified")
                        .forEachIndexed { idx, (label, value) ->
                            SegmentedButton(
                                selected = qualifiedFilter == value,
                                onClick = { qualifiedFilter = value },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                }
            }
            // 字段选择（选择顺序即输出列序；TXT 的 IP:PORT 恒为首列）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ExportField.entries.forEach { f ->
                    FilterChip(
                        selected = f in fieldOrder,
                        onClick = {
                            fieldOrder = if (f in fieldOrder) fieldOrder - f else fieldOrder + f
                        },
                        label = { Text(f.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    )
                }
            }
            // TXT 专属：字段分隔符 + IPv6 方括号
            if (format == FORMAT_TXT) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = txtSeparator,
                        onValueChange = { txtSeparator = it.take(3) },
                        label = { Text("TXT 分隔符") },
                        modifier = Modifier.width(150.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    FilterChip(
                        selected = ipv6Brackets,
                        onClick = { ipv6Brackets = !ipv6Brackets },
                        label = { Text("IPv6 方括号", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    )
                }
            }
            // 自定义字段（对齐 CFData copyCustomFields：标题:内容，逗号分隔，值恒追加）
            OutlinedTextField(
                value = customFieldsText,
                onValueChange = { customFieldsText = it },
                label = { Text("自定义字段") },
                placeholder = { Text("标题:内容，逗号分隔，如 备注:测试版,归属:cfst") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Text(
                    text = preview,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
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
                    etStatus = "已复制到剪贴板（$exportedCount 条）"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("复制到剪贴板") }

            HorizontalDivider()

            // ---------------- edgetunnel 上传 ----------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_sym_upload),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = "edgetunnel 上传", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "写入 edgetunnel（CM 系 Worker）订阅 ADD.txt，协议与 CFData-WEB 一致",
                style = MaterialTheme.typography.bodySmall,
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
                        Icon(painter = painterResource(R.drawable.ic_sym_arrow_drop_down), contentDescription = "选择账户")
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
                        painter = painterResource(R.drawable.ic_sym_delete),
                        contentDescription = "删除账户",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OutlinedTextField(
                value = etName,
                onValueChange = { etName = it },
                label = { Text("账户名称") },
                placeholder = { Text("例如 main-tunnel", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = etHost,
                onValueChange = { etHost = it },
                label = { Text("edgetunnel 域名") },
                placeholder = { Text("https://example.com", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = etPassword,
                onValueChange = { etPassword = it },
                label = { Text("管理页密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "保存密码到应用私有存储", style = MaterialTheme.typography.bodyMedium)
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
                                ExportFormatter.toTxt(filteredItems)
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
                enabled = !etUploading && filteredItems.isNotEmpty() &&
                    etHost.isNotBlank() && etPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (etUploading) "上传中..." else "上传到 edgetunnel")
            }

            Text(
                text = etStatus,
                style = MaterialTheme.typography.bodySmall,
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

            GithubSection(
                ghToken = ghToken,
                ghOwner = ghOwner,
                ghRepo = ghRepo,
                ghBranch = ghBranch,
                ghPath = ghPath,
                ghMessage = ghMessage,
                ghSaveToken = ghSaveToken,
                ghUploading = ghUploading,
                ghStatus = ghStatus,
                ghStatusOk = ghStatusOk,
                resultCount = filteredItems.size,
                uploadContent = if (format == FORMAT_TXT) content else ExportFormatter.toTxt(filteredItems),
                onToken = { ghToken = it },
                onOwner = { ghOwner = it },
                onRepo = { ghRepo = it },
                onBranch = { ghBranch = it },
                onPath = { ghPath = it },
                onMessage = { ghMessage = it },
                onSaveToken = { ghSaveToken = it },
                onSaveConfig = {
                    ghStore.save(
                        GhConfig(
                            token = if (ghSaveToken) ghToken.trim() else "",
                            owner = ghOwner.trim(),
                            repo = ghRepo.trim(),
                            branch = ghBranch.trim().ifBlank { GithubClient.DEFAULT_BRANCH },
                            path = ghPath.trim().ifBlank { GithubClient.DEFAULT_PATH },
                            message = ghMessage.trim().ifBlank { GithubClient.DEFAULT_MESSAGE },
                            saveToken = ghSaveToken,
                        ),
                    )
                    ghStatus = "GitHub 配置已保存"
                    ghStatusOk = true
                },
                onUpload = {
                    scope.launch {
                        ghUploading = true
                        ghStatusOk = null
                        GithubClient.upload(
                            token = ghToken,
                            owner = ghOwner,
                            repo = ghRepo,
                            branch = ghBranch,
                            path = ghPath,
                            message = ghMessage,
                            content = if (format == FORMAT_TXT) content else ExportFormatter.toTxt(filteredItems),
                            onStatus = { msg -> ghStatus = msg },
                        ).fold(
                            onSuccess = { url ->
                                ghStatus = "上传成功：$url"
                                ghStatusOk = true
                            },
                            onFailure = { e ->
                                ghStatus = "上传失败：${e.message ?: "未知错误"}"
                                ghStatusOk = false
                            },
                        )
                        ghUploading = false
                    }
                },
            )

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("关闭")
            }
        }
    }
}

/**
 * GitHub 上传区块:配置持久化到 [GithubStore],上传走 [GithubClient]
 * (Contents API,行为对齐 CFData-WEB);订阅内容与 edgetunnel 一致使用
 * TXT 行格式,CSV 仅用于表格工具对表。
 */
@Composable
private fun GithubSection(
    ghToken: String,
    ghOwner: String,
    ghRepo: String,
    ghBranch: String,
    ghPath: String,
    ghMessage: String,
    ghSaveToken: Boolean,
    ghUploading: Boolean,
    ghStatus: String,
    ghStatusOk: Boolean?,
    resultCount: Int,
    uploadContent: String,
    onToken: (String) -> Unit,
    onOwner: (String) -> Unit,
    onRepo: (String) -> Unit,
    onBranch: (String) -> Unit,
    onPath: (String) -> Unit,
    onMessage: (String) -> Unit,
    onSaveToken: (Boolean) -> Unit,
    onSaveConfig: () -> Unit,
    onUpload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_sym_sync),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "GitHub 上传", fontWeight = FontWeight.Bold)
        }
        Text(
            text = "把优选结果提交到 GitHub 仓库，raw 直链可分享/订阅；Token 需该仓库 Contents 写权限",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = ghToken,
            onValueChange = onToken,
            label = { Text("GitHub Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ghOwner,
                onValueChange = onOwner,
                label = { Text("属主 owner") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = ghRepo,
                onValueChange = onRepo,
                label = { Text("仓库 repo") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ghBranch,
                onValueChange = onBranch,
                label = { Text("分支") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = ghPath,
                onValueChange = onPath,
                label = { Text("文件路径") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = ghMessage,
            onValueChange = onMessage,
            label = { Text("提交信息") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "保存 Token 到应用私有存储", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = ghSaveToken, onCheckedChange = onSaveToken)
        }
        OutlinedButton(onClick = onSaveConfig, modifier = Modifier.fillMaxWidth()) {
            Text("保存配置")
        }
        Button(
            onClick = onUpload,
            enabled = !ghUploading && resultCount > 0 &&
                ghToken.isNotBlank() && ghOwner.isNotBlank() &&
                ghRepo.isNotBlank() && ghPath.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ghUploading) "上传中..." else "上传到 GitHub")
        }
        Text(
            text = ghStatus,
            style = MaterialTheme.typography.bodySmall,
            color = when (ghStatusOk) {
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
    }
}

private const val FORMAT_TXT = "txt"
private const val FORMAT_CSV = "csv"
