package com.masgzy.cfstmobile.model

/**
 * 结果导出格式化（纯 Kotlin，JVM 单测覆盖）。
 *
 * 两种格式与主流工具互通，CFData-WEB 用户迁移零成本：
 * - [toTxt]：CFData-WEB 默认 TXT 等价格式 —— 每行 `IP:端口#数据中心`，
 *   附加字段以 `-` 连接（本引擎仅含数据中心一列）；无附加字段时只有 `IP:端口`。
 *   可直接粘贴进 edgetunnel 的 ADD.txt 优选订阅或任意代理软件的优选列表。
 * - [toCsv]：XIU2/CloudflareST 传统 result.csv 列序（已发送/已接收/丢包率等），
 *   便于与桌面测速工作流对表。
 */
object ExportFormatter {

    /** TXT：`IP:端口` 或 `IP:端口#数据中心`（数据中心缺失时省略 `#` 段） */
    fun toTxt(results: List<CfstResultItem>): String = buildString {
        results.forEach { r ->
            append(r.ip).append(':').append(r.port)
            val extras = listOfNotNull(r.colo?.trim().takeUnless { it.isNullOrEmpty() })
            if (extras.isNotEmpty()) {
                append('#').append(extras.joinToString("-"))
            }
            append('\n')
        }
    }

    /** CSV：列序对齐 XIU2/CloudflareST result.csv，无下载测速时速度列为空 */
    fun toCsv(results: List<CfstResultItem>): String = buildString {
        appendLine("IP 地址,端口,已发送,已接收,丢包率(%),平均延迟(ms),下载速度(Mbps),数据中心")
        results.forEach { r ->
            appendLine(
                listOf(
                    r.ip,
                    r.port.toString(),
                    r.sent.toString(),
                    r.received.toString(),
                    fmt2(r.lossRate * 100),
                    fmt2(r.delayMs),
                    r.downloadSpeedMbps?.let { fmt2(it) } ?: "",
                    r.colo ?: "",
                ).joinToString(","),
            )
        }
    }

    /** 两位小数（与 CFSTAPP / CloudflareST 的展示精度一致） */
    private fun fmt2(v: Double): String = String.format(java.util.Locale.ROOT, "%.2f", v)
}
