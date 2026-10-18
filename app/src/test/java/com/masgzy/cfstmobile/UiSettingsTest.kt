package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.BUILT_IN_IPS
import com.masgzy.cfstmobile.data.BUILT_IN_IPS_V6
import com.masgzy.cfstmobile.data.SpeedUrlPresets
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.model.ProtocolJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `speed threads - official serial, nsb user value`() {
        // 官方恒为串行 1（上游默认最准）；非标用用户设置值
        val official = UiSettings(ipSource = UiSettings.SOURCE_BUILTIN)
        assertEquals(1, official.speedThreads)
        val nsb = UiSettings(
            ipSource = UiSettings.SOURCE_CUSTOM,
            customIpText = "1.2.3.4",
            nsbSpeedThreads = 8,
        )
        assertEquals(8, nsb.speedThreads)
        val json = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            nsb.toCfstConfig(),
        )
        assertTrue(json.contains("\"speed_threads\":8"))
    }

    @Test
    fun `nsb download requires speed threads over zero`() {
        // 对齐 CFData「需测速并发与测速结果数量均大于 0」
        val zero = UiSettings(
            ipSource = UiSettings.SOURCE_CUSTOM,
            nsbSpeedLimit = 5,
            nsbAutoSpeed = true,
            nsbSpeedThreads = 0,
        )
        assertEquals(false, zero.downloadEnabled)
        assertEquals(true, zero.copy(nsbSpeedThreads = 1).downloadEnabled)
    }

    @Test
    fun `auto upload choice round trips`() {
        val s = UiSettings(officialAutoUpload = UiSettings.UPLOAD_GITHUB_CSV, nsbAutoUpload = UiSettings.UPLOAD_GITHUB_TXT)
        val restored = ProtocolJson.json.decodeFromString(
            UiSettings.serializer(),
            ProtocolJson.json.encodeToString(UiSettings.serializer(), s),
        )
        assertEquals(UiSettings.UPLOAD_GITHUB_CSV, restored.officialAutoUpload)
        assertEquals(UiSettings.UPLOAD_GITHUB_TXT, restored.nsbAutoUpload)
        assertEquals(UiSettings.UPLOAD_OFF, UiSettings().officialAutoUpload)
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

    // ---------------------------------------------------------------- sanitized（非标手动列表官方段清洗）

    @Test
    fun `sanitized clears manual list that is purely built-in segments`() {
        val dirty = BUILT_IN_IPS.take(6).joinToString("\n")
        val cleaned = UiSettings(ipSource = UiSettings.SOURCE_CUSTOM, customIpText = dirty).sanitized()
        assertEquals("", cleaned.customIpText)
    }

    @Test
    fun `sanitized keeps user entries mixed with segments`() {
        val mixed = BUILT_IN_IPS.first() + "\n1.2.3.4:8443\nexample.com:2053"
        val kept = UiSettings(ipSource = UiSettings.SOURCE_CUSTOM, customIpText = mixed).sanitized()
        assertEquals(mixed, kept.customIpText)
    }

    @Test
    fun `sanitized keeps pure user entries`() {
        val user = "1.2.3.4:8443\n5.6.7.8"
        val kept = UiSettings(customIpText = user).sanitized()
        assertEquals(user, kept.customIpText)
    }

    @Test
    fun `sanitized ignores blank and comment lines`() {
        val dirty = "# comment\n\n" + BUILT_IN_IPS.take(3).joinToString("\n")
        val cleaned = UiSettings(customIpText = dirty).sanitized()
        assertEquals("", cleaned.customIpText)
    }

    @Test
    fun `sanitized empty input is no-op`() {
        val s = UiSettings()
        assertEquals(s, s.sanitized())
    }

    // ---------------------------------------------------------------- 外观设置（amoled）

    @Test
    fun `amoled defaults to false and participates in copy`() {
        val s = UiSettings()
        assertFalse(s.amoled)
        assertTrue(s.copy(amoled = true).amoled)
    }

    @Test
    fun `amoled survives sanitized roundtrip`() {
        val s = UiSettings(amoled = true)
        assertTrue(s.sanitized().amoled)
    }

    // ---------------------------------------------------------------- 外观设置（调色板）

    @Test
    fun `palette fields default to cloudflare blue tonal spot`() {
        val s = UiSettings()
        assertEquals(0xFF0B57D0, s.colorTuple.primary)
        assertNull(s.colorTuple.secondary)
        assertNull(s.colorTuple.error)
        assertEquals("TonalSpot", s.paletteStyle)
        assertEquals(0f, s.themeContrast)
    }

    @Test
    fun `seed colors round trip through json`() {
        val s = UiSettings(
            colorTuple = com.masgzy.cfstmobile.data.SeedColors(
                primary = 0xFF88DD20,
                secondary = null,
                tertiary = 0xFF01a0a3,
            ),
            paletteStyle = "Vibrant",
            themeContrast = 0.5f,
        )
        val restored = ProtocolJson.json.decodeFromString(
            UiSettings.serializer(),
            ProtocolJson.json.encodeToString(UiSettings.serializer(), s),
        )
        assertEquals(0xFF88DD20, restored.colorTuple.primary)
        assertNull(restored.colorTuple.secondary)
        assertEquals(0xFF01a0a3, restored.colorTuple.tertiary)
        assertEquals("Vibrant", restored.paletteStyle)
        assertEquals(0.5f, restored.themeContrast)
    }

    @Test
    fun `legacy json without palette fields gets defaults`() {
        // 调色板字段加入前的持久化 JSON → 反序列化走 Cloudflare 蓝默认种子
        val legacy = """{"ipSource":"builtin","themeMode":"dark","dynamicColors":false,"amoled":true}"""
        val s = ProtocolJson.json.decodeFromString(UiSettings.serializer(), legacy)
        assertEquals(0xFF0B57D0, s.colorTuple.primary)
        assertEquals("TonalSpot", s.paletteStyle)
        assertEquals(0f, s.themeContrast)
        assertTrue(s.amoled)
    }

    @Test
    fun `palette fields survive sanitized roundtrip`() {
        val s = UiSettings(
            colorTuple = com.masgzy.cfstmobile.data.SeedColors(primary = 0xFFD7036A),
            paletteStyle = "Expressive",
            themeContrast = -0.25f,
        )
        val kept = s.sanitized()
        assertEquals(0xFFD7036A, kept.colorTuple.primary)
        assertEquals("Expressive", kept.paletteStyle)
        assertEquals(-0.25f, kept.themeContrast)
    }

    @Test
    fun `nsb target dc passes comma separated list through`() {
        // 引擎 parse_colo_filters 逐段 trim + 忽略大小写；App 层只整体 trim
        val json = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            UiSettings(
                ipSource = UiSettings.SOURCE_CUSTOM,
                customIpText = "1.2.3.4",
                nsbTargetDc = "HKG, NRT ",
            ).toCfstConfig(),
        )
        assertTrue(json.contains("\"httping_colo\":\"HKG, NRT\""))
    }
}
