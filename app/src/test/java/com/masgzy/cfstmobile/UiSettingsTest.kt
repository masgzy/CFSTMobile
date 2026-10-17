package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.BUILT_IN_IPS
import com.masgzy.cfstmobile.data.BUILT_IN_IPS_V6
import com.masgzy.cfstmobile.data.SpeedUrlPresets
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.model.ProtocolJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UI 设置分模式参数组（官方/非标独立）与序列化兼容性 */
class UiSettingsTest {

    // ---------------------------------------------------------------- 内置段

    @Test
    fun `ipType v4 uses selected segments`() {
        val s = UiSettings(ipType = UiSettings.IP_V4, selectedSegments = BUILT_IN_IPS.take(3))
        assertEquals(BUILT_IN_IPS.take(3), s.effectiveSegments)
    }

    @Test
    fun `ipType v6 uses official v6 ranges regardless of selection`() {
        val s = UiSettings(ipType = UiSettings.IP_V6, selectedSegments = emptyList())
        assertEquals(BUILT_IN_IPS_V6, s.effectiveSegments)
    }

    @Test
    fun `v6 config ip_text contains v6 cidr`() {
        val s = UiSettings(ipType = UiSettings.IP_V6)
        val json = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            s.toCfstConfig(),
        )
        assertTrue(json.contains("2400:cb00::/32"))
    }

    // ---------------------------------------------------------------- 分模式映射

    @Test
    fun `nsb targetDc maps to httping_colo and official targetDc does not`() {
        val nsb = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            UiSettings(ipSource = UiSettings.SOURCE_CUSTOM, nsbTargetDc = "NRT").toCfstConfig(),
        )
        assertTrue(nsb.contains("\"httping_colo\":\"NRT\""))

        val official = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            UiSettings(ipSource = UiSettings.SOURCE_BUILTIN, officialTargetDc = "HKG").toCfstConfig(),
        )
        assertTrue(!official.contains("httping_colo"))
    }

    @Test
    fun `official defaults follow cfdata - delay 500 limit 5 download on`() {
        val s = UiSettings(ipSource = UiSettings.SOURCE_BUILTIN)
        assertEquals(500, s.officialDelayMs)
        assertEquals(5, s.officialSpeedLimit)
        assertEquals(100, s.officialThreads)
        assertTrue(s.downloadEnabled)
        val json = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            s.toCfstConfig(),
        )
        assertTrue(json.contains("\"max_delay_ms\":500"))
        assertTrue(json.contains("\"test_count\":5"))
    }

    @Test
    fun `nsb defaults follow cfdata - delay 0 resultLimit 1000 download off`() {
        val s = UiSettings(ipSource = UiSettings.SOURCE_CUSTOM, customIpText = "1.2.3.4")
        assertEquals(0, s.nsbDelayMs)
        assertEquals(1000, s.nsbResultLimit)
        assertEquals(443, s.nsbFallbackPort)
        assertEquals(false, s.downloadEnabled)
        val json = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            s.toCfstConfig(),
        )
        assertTrue(json.contains("\"disable_download\":true"))
        assertTrue(json.contains("\"target_num\":1000"))
    }

    @Test
    fun `official speedLimit zero disables download`() {
        val s = UiSettings(ipSource = UiSettings.SOURCE_BUILTIN, officialSpeedLimit = 0)
        assertEquals(false, s.downloadEnabled)
    }

    @Test
    fun `nsb download requires autoSpeed on`() {
        val off = UiSettings(ipSource = UiSettings.SOURCE_CUSTOM, nsbSpeedLimit = 5, nsbAutoSpeed = false)
        assertEquals(false, off.downloadEnabled)
        val on = UiSettings(ipSource = UiSettings.SOURCE_CUSTOM, nsbSpeedLimit = 5, nsbAutoSpeed = true)
        assertEquals(true, on.downloadEnabled)
    }

    @Test
    fun `mode specific values select threads delay speed`() {
        val s = UiSettings(
            ipSource = UiSettings.SOURCE_CUSTOM,
            officialThreads = 111,
            officialDelayMs = 500,
            nsbThreads = 222,
            nsbDelayMs = 0,
            officialSpeedMin = 0.5,
            nsbSpeedMin = 0.2,
        )
        assertEquals(222, s.threads)
        assertEquals(0, s.delayMs)
        assertEquals(0.2, s.speedMin, 1e-9)
        val official = s.copy(ipSource = UiSettings.SOURCE_BUILTIN)
        assertEquals(111, official.threads)
        assertEquals(500, official.delayMs)
        assertEquals(0.5, official.speedMin, 1e-9)
    }

    @Test
    fun `nsb targets merge file and manual lines`() {
        val s = UiSettings(
            ipSource = UiSettings.SOURCE_CUSTOM,
            nsbFileText = "1.1.1.1\n2.2.2.2:8443\n",
            customIpText = "\n3.3.3.3 443\n# comment\n",
        )
        assertEquals(listOf("1.1.1.1", "2.2.2.2:8443", "3.3.3.3 443"), s.nsbTargets)
    }

    // ---------------------------------------------------------------- 预设与兼容

    @Test
    fun `speed url presets resolve`() {
        assertEquals(
            UiSettings.DEFAULT_DOWNLOAD_URL,
            SpeedUrlPresets.resolve(SpeedUrlPresets.AUTO, ""),
        )
        assertEquals(
            "https://cf.090227.xyz/__down?bytes=99999999",
            SpeedUrlPresets.resolve(SpeedUrlPresets.CM, ""),
        )
        assertEquals("https://speed.okl.abrdns.com", SpeedUrlPresets.resolve(SpeedUrlPresets.MOBILE, ""))
        assertEquals("https://my.example/x", SpeedUrlPresets.resolve(SpeedUrlPresets.CUSTOM, "https://my.example/x"))
    }

    @Test
    fun `legacy json deserializes with new defaults`() {
        // alpha.2 及更早的持久化 JSON（含已删除字段）→ 反序列化走新默认值（向后兼容）
        val legacy = """{"ipSource":"builtin","tcpPort":443,"maxThreads":256,"targetDc":"NRT","mirrorUrl":"https://x"}"""
        val s = ProtocolJson.json.decodeFromString(UiSettings.serializer(), legacy)
        assertEquals(UiSettings.IP_V4, s.ipType)
        assertEquals(UiSettings.THEME_AUTO, s.themeMode)
        assertEquals(SpeedUrlPresets.AUTO, s.speedUrlPreset)
        assertEquals(false, s.autoLoadConfig)
        assertEquals(500, s.officialDelayMs)
        assertEquals(100, s.officialThreads)
    }

    // ---------------------------------------------------------------- 校验

    @Test
    fun `validate v6 skips segment requirement`() {
        assertNull(
            UiSettings(
                ipType = UiSettings.IP_V6,
                selectedSegments = emptyList(),
            ).validate(),
        )
        assertEquals(
            "请至少选择一个内置 IP 段",
            UiSettings(ipType = UiSettings.IP_V4, selectedSegments = emptyList()).validate(),
        )
    }

    @Test
    fun `validate nsb requires one of file url manual`() {
        assertEquals(
            "请提供扫描目标：选择文件 / 填写网络 URL / 手动输入列表",
            UiSettings(ipSource = UiSettings.SOURCE_CUSTOM).validate(),
        )
        assertNull(
            UiSettings(ipSource = UiSettings.SOURCE_CUSTOM, customIpText = "1.2.3.4").validate(),
        )
    }

    @Test
    fun `validate nsb file and url are mutually exclusive`() {
        assertEquals(
            "IP 文件与网络 URL 请二选一（对齐 CFData 输入约束）",
            UiSettings(
                ipSource = UiSettings.SOURCE_CUSTOM,
                nsbFileText = "1.1.1.1",
                nsbSourceUrl = "https://example.com/ips.txt",
            ).validate(),
        )
    }
}
