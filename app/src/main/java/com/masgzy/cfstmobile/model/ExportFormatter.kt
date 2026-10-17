package com.masgzy.cfstmobile.model

/**
 * 结果导出格式化（纯 Kotlin，JVM 单测覆盖）。
 *
 * 两种格式与主流工具互通，CFData-WEB 用户迁移零成本：
 * - TXT：CFData-WEB 默认等价格式 —— 每行 `IP:端口#数据中心`，附加字段以分隔符连接；
 *   无附加字段时只有 `IP:端口`。可直接粘贴进 edgetunnel 的 ADD.txt 优选订阅。
 * - CSV：XIU2/CloudflareST 传统 result.csv 列序（已发送/已接收/丢包率等）。
 *
 * 导出管线对齐 CFData-WEB buildResultContent：
 * IP 类型筛选 → 行区间切片（[startRow, endRow]，含端点）→ 按选中字段渲染。
 */
object ExportFormatter {

    // ---------------- IP 类型筛选 ----------------
    const val IP_ALL = "all"
    const val IP_V4 = "ipv4"
    const val IP_V6 = "ipv6"

    /**
     * 可选导出字段。label 即 CSV 表头（默认选择下与 XIU2/CFSTAPP result.csv 完全一致）。
     */
    enum class ExportField(val label: String) {
        IPPORT("IP:PORT"),
        IP("IP 地址"),
        PORT("端口"),
        SENT("已发送"),
        RECEIVED("已接收"),
        LOSS("丢包率(%)"),
        DELAY("平均延迟(ms)"),
        SPEED("下载速度(Mbps)"),
        DC("数据中心"),
        ORIGIN("原始输入"),
    }

    /** TXT 默认字段：`IP:端口#数据中心`（CFData 兼容行格式） */
    val DEFAULT_TXT_FIELDS: List<ExportField> = listOf(ExportField.IPPORT, ExportField.DC)

    /** CSV 默认字段：XIU2/CloudflareST result.csv 列序 */
    val DEFAULT_CSV_FIELDS: List<ExportField> = listOf(
        ExportField.IP,
        ExportField.PORT,
        ExportField.SENT,
        ExportField.RECEIVED,
        ExportField.LOSS,
        ExportField.DELAY,
        ExportField.SPEED,
        ExportField.DC,
    )

    /**
     * 导出选项（对齐 CFData-WEB 导出弹窗的行范围 / IP 类型 / 字段选择 / TXT 分隔符 / IPv6 方括号）。
     *
     * @param startRow 起始行（从 1 计，最小 1）
     * @param endRow   结束行（含）；0 = 到末尾
     */
    data class ExportOptions(
        val startRow: Int = 1,
        val endRow: Int = 0,
        val ipFilter: String = IP_ALL,
        val fields: List<ExportField> = DEFAULT_CSV_FIELDS,
        val txtSeparator: String = "-",
        val ipv6Brackets: Boolean = false,
    )

    /** IP 类型判断：含冒号即 IPv6（引擎结果 ip 为裸地址） */
    fun ipVersion(ip: String): String = if (ip.contains(':')) IP_V6 else IP_V4

    /** IPv6 地址加方括号（CFData formatIPPort 语义，仅对 v6 生效） */
    fun formatIpPort(ip: String, port: Int, brackets: Boolean): String {
        val host = if (brackets && ip.contains(':')) "[$ip]" else ip
        return "$host:$port"
    }

    /** 统一导出入口 */
    fun build(results: List<CfstResultItem>, format: String, options: ExportOptions): String {
        val filtered = results.filter { r ->
            val v = ipVersion(r.ip)
            options.ipFilter == IP_ALL || v == options.ipFilter
        }
        val start = (options.startRow - 1).coerceIn(0, filtered.size)
        val end = if (options.endRow in 1..filtered.size) options.endRow else filtered.size
        val sliced = if (end > start) filtered.subList(start, end) else emptyList()
        return if (format == FORMAT_TXT) buildTxt(sliced, options) else buildCsv(sliced, options)
    }

    /** TXT：`IP:端口` 或 `IP:端口#字段1<分隔符>字段2`（空值跳过；IP:PORT 恒为首字段） */
    private fun buildTxt(results: List<CfstResultItem>, options: ExportOptions): String =
        buildString {
            results.forEach { r ->
                append(formatIpPort(r.ip, r.port, options.ipv6Brackets))
                // 对齐 CFData：ipport 单独渲染，其余选中字段按选择顺序作为附加列，空值跳过
                val extras = options.fields
                    .filter { it != ExportField.IPPORT }
                    .mapNotNull { f -> txtValue(r, f).takeUnless { it.isEmpty() } }
                if (extras.isNotEmpty()) {
                    append('#').append(extras.joinToString(options.txtSeparator))
                }
                append('\n')
            }
        }

    /** CSV：表头 = 选中字段 label，值 csv 转义 */
    private fun buildCsv(results: List<CfstResultItem>, options: ExportOptions): String = buildString {
        appendLine(options.fields.joinToString(",") { it.label })
        results.forEach { r ->
            appendLine(options.fields.joinToString(",") { f -> csvEscape(csvValue(r, f)) })
        }
    }

    private fun txtValue(r: CfstResultItem, f: ExportField): String = when (f) {
        ExportField.IPPORT -> "" // 首字段已单独渲染
        ExportField.IP -> r.ip
        ExportField.PORT -> r.port.toString()
        ExportField.SENT -> r.sent.toString()
        ExportField.RECEIVED -> r.received.toString()
        ExportField.LOSS -> fmt2(r.lossRate * 100)
        ExportField.DELAY -> fmt2(r.delayMs)
        ExportField.SPEED -> r.downloadSpeedMbps?.let { fmt2(it) }.orEmpty()
        ExportField.DC -> r.colo?.trim().orEmpty()
        ExportField.ORIGIN -> r.origin?.trim().orEmpty()
    }.trim()

    private fun csvValue(r: CfstResultItem, f: ExportField): String = when (f) {
        ExportField.IPPORT -> formatIpPort(r.ip, r.port, false)
        else -> txtValue(r, f)
    }

    /** CFData formatCSVCell：含逗号/引号/换行时加引号，内部引号翻倍 */
    private fun csvEscape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${v.replace("\"", "\"\"")}\""
        } else {
            v
        }

    // ---------------- 兼容旧 API ----------------

    const val FORMAT_TXT = "txt"
    const val FORMAT_CSV = "csv"

    /** TXT 默认格式（`IP:端口#数据中心`），供上传等固定格式场景使用 */
    fun toTxt(results: List<CfstResultItem>): String =
        build(results, FORMAT_TXT, ExportOptions(fields = DEFAULT_TXT_FIELDS))

    /** CSV 默认格式（XIU2 列序），无下载测速时速度列为空 */
    fun toCsv(results: List<CfstResultItem>): String =
        build(results, FORMAT_CSV, ExportOptions(fields = DEFAULT_CSV_FIELDS))

    /** 两位小数（与 CFSTAPP / CloudflareST 的展示精度一致） */
    private fun fmt2(v: Double): String = String.format(java.util.Locale.ROOT, "%.2f", v)
}
