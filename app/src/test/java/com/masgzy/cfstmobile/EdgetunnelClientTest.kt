package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.EdgetunnelClient
import org.junit.Assert.assertEquals
import org.junit.Test

/** edgetunnel 主机归一化：与 CFData-WEB normalizeEdgetunnelHost 行为一致 */
class EdgetunnelClientTest {

    @Test
    fun `adds https scheme when missing`() {
        assertEquals("https://example.com", EdgetunnelClient.normalizeHost("example.com"))
    }

    @Test
    fun `keeps explicit http scheme`() {
        assertEquals("http://example.com", EdgetunnelClient.normalizeHost("http://example.com"))
    }

    @Test
    fun `trims spaces and trailing slashes`() {
        assertEquals("https://example.com", EdgetunnelClient.normalizeHost("  https://example.com/  "))
        assertEquals("https://example.com/path", EdgetunnelClient.normalizeHost("https://example.com/path/"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", EdgetunnelClient.normalizeHost("   "))
    }

    @Test
    fun `mode constants match cfdata vocabulary`() {
        assertEquals("append", EdgetunnelClient.MODE_APPEND)
        assertEquals("overwrite", EdgetunnelClient.MODE_OVERWRITE)
    }
}
