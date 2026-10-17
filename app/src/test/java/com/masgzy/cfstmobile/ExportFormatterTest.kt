package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.ExportFormatter
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
    ) = CfstResultItem(
        ip = ip, port = port, sent = 4, received = 4,
        lossRate = loss, delayMs = delay, downloadSpeedMbps = speed, colo = colo,
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
}
