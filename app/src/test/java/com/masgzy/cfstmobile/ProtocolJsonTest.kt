package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.model.CfstConfig
import com.masgzy.cfstmobile.model.CfstProgress
import com.masgzy.cfstmobile.model.CfstResult
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.ProtocolJson
import com.masgzy.cfstmobile.model.SortMode
import com.masgzy.cfstmobile.model.sortedByMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FFI JSON 协议回归网：样例报文与 engine/README.md 第 3/4/5 节逐字对齐，
 * 引擎侧 serde 升级时这里第一时间报警。
 */
class ProtocolJsonTest {

    private val json = ProtocolJson.json

    // ------------------------------------------------------------ 配置

    @Test
    fun `config 只序列化非默认字段且字段名为 snake_case`() {
        val config = CfstConfig(
            ipText = "1.1.1.1/24,8.8.8.8",
            tcpPort = 8443,
            pingMode = "http",
            disableDownload = true,
        )
        val encoded = json.encodeToString(CfstConfig.serializer(), config)

        assertTrue(encoded.contains("\"ip_text\":\"1.1.1.1/24,8.8.8.8\""))
        assertTrue(encoded.contains("\"tcp_port\":8443"))
        assertTrue(encoded.contains("\"ping_mode\":\"http\""))
        assertTrue(encoded.contains("\"disable_download\":true"))

        // 默认值不下发：引擎按自身默认处理
        assertFalse(encoded.contains("download_time"))
        assertFalse(encoded.contains("max_threads"))
        assertFalse(encoded.contains("bind_ip"))
        assertFalse(encoded.contains("httping_url"))
    }

    @Test
    fun `config 默认实例可整轮往返`() {
        val decoded = json.decodeFromString(
            CfstConfig.serializer(),
            json.encodeToString(CfstConfig.serializer(), CfstConfig()),
        )
        assertEquals(CfstConfig(), decoded)
    }

    // ------------------------------------------------------------ 进度

    @Test
    fun `进度样例（README 第四节）可解码`() {
        val sample = """
            {"phase":"download","stop_requested":false,
             "ping":{"tested":123,"success":45,"total":262144},
             "download":{"tested":12,"qualified":8,"total":30,"speed_mbps":12.34}}
        """.trimIndent()
        val progress = json.decodeFromString(CfstProgress.serializer(), sample)

        assertEquals("download", progress.phase)
        assertFalse(progress.stopRequested)
        assertEquals(123L, progress.ping?.tested)
        assertEquals(45L, progress.ping?.success)
        assertEquals(262144L, progress.ping?.total)
        assertEquals(12L, progress.download?.tested)
        assertEquals(8L, progress.download?.qualified)
        assertEquals(30L, progress.download?.total)
        assertEquals(12.34, progress.download?.speedMbps!!, 1e-9)
        assertNull(progress.message)
    }

    @Test
    fun `错误进度携带 message 且缺失 ping_download 键时容忍`() {
        val sample = """{"phase":"error","message":"配置非法：端口不能为 0"}"""
        val progress = json.decodeFromString(CfstProgress.serializer(), sample)

        assertEquals("error", progress.phase)
        assertEquals("配置非法：端口不能为 0", progress.message)
        assertNull(progress.ping)
        assertNull(progress.download)
    }

    // ------------------------------------------------------------ 结果

    @Test
    fun `结果样例（README 第五节）可解码且 speed_colo 可空`() {
        val sample = """
            {"count":2,"items":[
              {"ip":"1.0.0.59","port":443,"sent":4,"received":4,"loss_rate":0.0,
               "delay_ms":4.36,"download_speed_mbps":2.92,"colo":"HKG"},
              {"ip":"1.1.1.1","port":8443,"sent":4,"received":3,"loss_rate":0.25,"delay_ms":10.0}]}
        """.trimIndent()
        val result = json.decodeFromString(CfstResult.serializer(), sample)

        assertEquals(2, result.count)
        assertEquals("1.0.0.59", result.items[0].ip)
        assertEquals(443, result.items[0].port)
        assertEquals(2.92, result.items[0].downloadSpeedMbps!!, 1e-9)
        assertEquals("HKG", result.items[0].colo)
        assertEquals(0.25, result.items[1].lossRate, 1e-9)
        assertNull(result.items[1].downloadSpeedMbps)
        assertNull(result.items[1].colo)
        assertEquals("1.1.1.1:8443", result.items[1].address())
    }

    // ------------------------------------------------------------ 排序

    @Test
    fun `排序辅助不改动引擎原始顺序的副本`() {
        val items = listOf(
            CfstResultItem(ip = "1.0.0.1", port = 443, delayMs = 20.0, downloadSpeedMbps = 5.0),
            CfstResultItem(ip = "1.0.0.2", port = 443, delayMs = 5.0, downloadSpeedMbps = 1.0),
            CfstResultItem(ip = "1.0.0.3", port = 443, delayMs = 50.0, downloadSpeedMbps = null),
        )
        val byDelay = items.sortedByMode(SortMode.DELAY)
        val bySpeed = items.sortedByMode(SortMode.SPEED)
        val byScore = items.sortedByMode(SortMode.SCORE)

        assertEquals(listOf("1.0.0.2", "1.0.0.1", "1.0.0.3"), byDelay.map { it.ip })
        assertEquals(listOf("1.0.0.1", "1.0.0.2", "1.0.0.3"), bySpeed.map { it.ip })
        assertEquals(items, byScore) // score = 引擎原始顺序
    }

    // ------------------------------------------------------------ 非标优选 origin

    @Test
    fun `origin 解析为非标优选的原始输入`() {
        val payload = """
            {"count":1,"items":[{
                "ip":"104.16.1.1","port":2053,"origin":"vip.example.com",
                "sent":4,"received":4,"loss_rate":0.0,"delay_ms":42.5,
                "download_speed_mbps":12.3,"colo":"LAX"
            }]}
        """.trimIndent()

        val item = json.decodeFromString(CfstResult.serializer(), payload).items.single()
        assertEquals("vip.example.com", item.origin)
        assertEquals("104.16.1.1:2053", item.address())
    }

    @Test
    fun `无 origin 字段的旧版载荷解析为 null（向后兼容）`() {
        val payload = """
            {"count":1,"items":[{
                "ip":"1.2.3.4","port":443,
                "sent":4,"received":3,"loss_rate":0.25,"delay_ms":180.0
            }]}
        """.trimIndent()

        val item = json.decodeFromString(CfstResult.serializer(), payload).items.single()
        assertNull(item.origin)
        assertNull(item.colo)
        assertNull(item.downloadSpeedMbps)
    }
}
