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

/** UI 设置新增字段（IP 类型/主题/测速网址预设/数据中心筛选）与序列化兼容性 */
class UiSettingsTest {

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

    @Test
    fun `targetDc maps to httping_colo and blank drops field`() {
        val withDc = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            UiSettings(targetDc = "NRT").toCfstConfig(),
        )
        assertTrue(withDc.contains("\"httping_colo\":\"NRT\""))

        val withoutDc = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            UiSettings(targetDc = "  ").toCfstConfig(),
        )
        assertTrue(!withoutDc.contains("httping_colo"))
    }

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
    fun `new fields deserialize from legacy json with defaults`() {
        // 旧版本持久化 JSON 不含新字段 → 反序列化走默认值（向后兼容）
        val legacy = """{"ipSource":"builtin","tcpPort":443}"""
        val s = ProtocolJson.json.decodeFromString(UiSettings.serializer(), legacy)
        assertEquals(UiSettings.IP_V4, s.ipType)
        assertEquals(UiSettings.THEME_AUTO, s.themeMode)
        assertEquals(SpeedUrlPresets.AUTO, s.speedUrlPreset)
        assertEquals(false, s.autoLoadConfig)
        assertEquals(100, s.maxThreads)
    }

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
}
