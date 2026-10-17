package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.ExportFormatter
import com.masgzy.cfstmobile.model.ExportFormatter.ExportField
import com.masgzy.cfstmobile.model.ExportFormatter.ExportOptions
import org.junit.Assert.assertEquals
import org.junit.Test

/** 导出格式回归网：TXT 与 CFData-WEB 兼容、CSV 与 XIU2/CloudflareST 兼容 */
class ExportFormatterTest {

    private fun item(
        ip: String = "104.16.1.1",
        port: Int = 443,
        speed: Double? = 12.345,
        colo: String? = "LAX",
        loss: Double = 0.0,
        delay: Double = 88.88,
        origin: String? = null,
    ) = CfstResultItem(
        ip = ip, port = port, sent = 4, received = 4,
        lossRate = loss, delayMs = delay, downloadSpeedMbps = speed, colo = colo,
        origin = origin,
    )

    @Test
    fun `txt with colo appends hash section`() {
        assertEquals("104.16.1.1:443#LAX\n", ExportFormatter.toTxt(listOf(item())))
    }

    @Test
    fun `txt without colo is bare address`() {
        assertEquals("1.0.0.1:8443\n", ExportFormatter.toTxt(listOf(item(ip = "1.0.0.1", port = 8443, colo = null))))
    }

    @Test
    fun `txt blank colo is treated as missing`() {
        assertEquals("1.1.1.1:443\n", ExportFormatter.toTxt(listOf(item(ip = "1.1.1.1", colo = "  "))))
    }

    @Test
    fun `txt preserves order and emits one line per result`() {
        val out = ExportFormatter.toTxt(
            listOf(item(ip = "1.1.1.1"), item(ip = "2.2.2.2", colo = null), item(ip = "3.3.3.3", colo = "NRT")),
        )
        assertEquals("1.1.1.1:443#LAX\n2.2.2.2:443\n3.3.3.3:443#NRT\n", out)
    }

    @Test
    fun `csv header matches cloudflarest columns`() {
        val out = ExportFormatter.toCsv(listOf(item()))
        val lines = out.trim().lines()
        assertEquals(
            "IP 地址,端口,已发送,已接收,丢包率(%),平均延迟(ms),下载速度(Mbps),数据中心",
            lines[0],
        )
        assertEquals("104.16.1.1,443,4,4,0.00,88.88,12.35,LAX", lines[1])
    }

    @Test
    fun `csv empty speed column when download skipped`() {
        val out = ExportFormatter.toCsv(listOf(item(speed = null, colo = null, loss = 0.25, delay = 210.5)))
        val line = out.trim().lines()[1]
        assertEquals("104.16.1.1,443,4,4,25.00,210.50,,", line)
    }

    // ---------------- 导出自定义（行范围 / IP 类型 / 字段 / 分隔符 / 方括号） ----------------

    private val rows = listOf(
        item(ip = "1.1.1.1", colo = "HKG"),
        item(ip = "2.2.2.2", colo = null),
        item(ip = "2606:4700::1", colo = "NRT"),
        item(ip = "3.3.3.3", colo = "LAX"),
    )

    @Test
    fun `row range slices inclusive and zero end means to the last`() {
        val opts = ExportOptions(startRow = 2, endRow = 3, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("2.2.2.2:443\n2606:4700::1:443#NRT\n", ExportFormatter.build(rows, "txt", opts))
        val tail = ExportOptions(startRow = 3, endRow = 0, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("2606:4700::1:443#NRT\n3.3.3.3:443#LAX\n", ExportFormatter.build(rows, "txt", tail))
    }

    @Test
    fun `row range clamps out of bounds`() {
        val beyond = ExportOptions(startRow = 10, endRow = 0, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("", ExportFormatter.build(rows, "txt", beyond))
        val overEnd = ExportOptions(startRow = 3, endRow = 99, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("2606:4700::1:443#NRT\n3.3.3.3:443#LAX\n", ExportFormatter.build(rows, "txt", overEnd))
        val inverted = ExportOptions(startRow = 3, endRow = 1, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("", ExportFormatter.build(rows, "txt", inverted))
    }

    @Test
    fun `ip type filter splits v4 and v6`() {
        assertEquals(ExportFormatter.ipVersion("1.1.1.1"), ExportFormatter.IP_V4)
        assertEquals(ExportFormatter.ipVersion("2606:4700::1"), ExportFormatter.IP_V6)
        val v6 = ExportOptions(ipFilter = ExportFormatter.IP_V6, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("2606:4700::1:443#NRT\n", ExportFormatter.build(rows, "txt", v6))
        val v4 = ExportOptions(ipFilter = ExportFormatter.IP_V4, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("1.1.1.1:443#HKG\n2.2.2.2:443\n3.3.3.3:443#LAX\n", ExportFormatter.build(rows, "txt", v4))
    }

    @Test
    fun `txt custom fields joined by separator with empty skipped`() {
        val opts = ExportOptions(
            fields = listOf(ExportField.IPPORT, ExportField.DELAY, ExportField.SPEED, ExportField.DC),
            txtSeparator = "|",
        )
        // 空速度列与空数据中心均跳过；数值保留两位小数
        assertEquals("2.2.2.2:443#88.88\n", ExportFormatter.build(listOf(item(ip = "2.2.2.2", colo = null, speed = null)), "txt", opts))
        assertEquals("1.1.1.1:443#88.88-12.35-HKG\n", ExportFormatter.build(rows.take(1), "txt", opts.copy(txtSeparator = "-")))
    }

    @Test
    fun `txt ipport always first even when not selected`() {
        val opts = ExportOptions(fields = listOf(ExportField.DC), txtSeparator = "-")
        assertEquals("1.1.1.1:443#HKG\n", ExportFormatter.build(rows.take(1), "txt", opts))
        // 即使选中了 ipport 以外的顺序组合，附加列按选择顺序输出
        val reordered = ExportOptions(fields = listOf(ExportField.DC, ExportField.IPPORT))
        assertEquals("1.1.1.1:443#HKG\n", ExportFormatter.build(rows.take(1), "txt", reordered))
    }

    @Test
    fun `txt ipv6 brackets only affect v6 addresses`() {
        val opts = ExportOptions(ipv6Brackets = true, fields = ExportFormatter.DEFAULT_TXT_FIELDS)
        assertEquals("[2606:4700::1]:443#NRT\n", ExportFormatter.build(rows.subList(2, 3), "txt", opts))
        assertEquals("1.1.1.1:443#HKG\n", ExportFormatter.build(rows.take(1), "txt", opts))
        assertEquals(ExportFormatter.formatIpPort("2606:4700::1", 443, false), "2606:4700::1:443")
    }

    @Test
    fun `csv custom subset uses field labels as header`() {
        val opts = ExportOptions(fields = listOf(ExportField.IPPORT, ExportField.DELAY, ExportField.ORIGIN))
        val out = ExportFormatter.build(
            listOf(item(ip = "1.1.1.1", origin = "vip.example.com")),
            "csv",
            opts,
        )
        assertEquals("IP:PORT,平均延迟(ms),原始输入\n1.1.1.1:443,88.88,vip.example.com\n", out)
    }

    @Test
    fun `csv escapes commas quotes and newlines`() {
        val out = ExportFormatter.build(
            listOf(item(ip = "1.1.1.1", origin = "备注,含\"引号\"")),
            "csv",
            ExportOptions(fields = listOf(ExportField.ORIGIN)),
        )
        assertEquals("原始输入\n\"备注,含\"\"引号\"\"\"\n", out)
    }

    @Test
    fun `default helpers equal explicit options`() {
        assertEquals(
            ExportFormatter.toTxt(rows),
            ExportFormatter.build(rows, "txt", ExportOptions(fields = ExportFormatter.DEFAULT_TXT_FIELDS)),
        )
        assertEquals(
            ExportFormatter.toCsv(rows),
            ExportFormatter.build(rows, "csv", ExportOptions(fields = ExportFormatter.DEFAULT_CSV_FIELDS)),
        )
    }
}
