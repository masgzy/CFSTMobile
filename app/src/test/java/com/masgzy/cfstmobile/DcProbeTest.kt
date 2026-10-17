package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.DcProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DcProbeTest {

    // ------------------------------------------------------ 端口与倍率

    @Test
    fun `tls ports match CFData isTLSPort`() {
        listOf(443, 2053, 2083, 2087, 2096, 8443).forEach {
            assertTrue("port $it should be TLS", DcProbe.isTlsPort(it))
        }
        listOf(80, 8080, 8880, 2052, 2082, 2086, 2095, 1, 65535).forEach {
            assertFalse("port $it should not be TLS", DcProbe.isTlsPort(it))
        }
    }

    @Test
    fun `latency multiplier matches CFData`() {
        assertEquals(1.0, DcProbe.latencyMultiplier(httping = false, tls = true), 1e-9)
        assertEquals(1.0, DcProbe.latencyMultiplier(httping = false, tls = false), 1e-9)
        assertEquals(4.0, DcProbe.latencyMultiplier(httping = true, tls = true), 1e-9)
        assertEquals(1.3, DcProbe.latencyMultiplier(httping = true, tls = false), 1e-9)
    }

    // ------------------------------------------------------ trace 请求

    @Test
    fun `trace request carries host ua and close`() {
        val req = DcProbe.traceRequest()
        assertTrue(req.startsWith("GET /cdn-cgi/trace HTTP/1.1\r\n"))
        assertTrue(req.contains("Host: speed.cloudflare.com\r\n"))
        assertTrue(req.contains("User-Agent: Mozilla/5.0\r\n"))
        assertTrue(req.contains("Connection: close\r\n"))
        assertTrue(req.endsWith("\r\n\r\n"))
    }

    // ------------------------------------------------------ colo 解析

    @Test
    fun `parse colo from trace body`() {
        val body = "fl=123abc\r\nh=swift.cloudflare.com\r\nip=1.2.3.4\r\ntls=TLSv1.3\r\n" +
            "sni=plaintext\r\nwarp=off\r\nloc=CN\r\ncolo=HKG\r\nhttp=http/2\r\n"
        assertEquals("HKG", DcProbe.parseTraceColo(body))
    }

    @Test
    fun `parse colo trims whitespace`() {
        assertEquals("SJC", DcProbe.parseTraceColo("  colo=SJC  "))
    }

    @Test
    fun `parse colo missing or blank`() {
        assertNull(DcProbe.parseTraceColo("fl=abc\nloc=US\n"))
        assertNull(DcProbe.parseTraceColo("colo="))
        assertNull(DcProbe.parseTraceColo(""))
    }

    // ------------------------------------------------------ HTTP 响应拆分

    @Test
    fun `split response with content-length body`() {
        val raw = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 5\r\n\r\nhello"
        val (headers, body) = DcProbe.splitHttpResponse(raw.toByteArray(Charsets.ISO_8859_1))
        assertTrue(headers.contains("HTTP/1.1 200 OK"))
        assertEquals("hello", String(body))
    }

    @Test
    fun `split response without separator returns empty body`() {
        val raw = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n".toByteArray(Charsets.ISO_8859_1)
        val (headers, body) = DcProbe.splitHttpResponse(raw)
        assertTrue(headers.isNotEmpty())
        assertEquals(0, body.size)
    }

    @Test
    fun `chunked detection and length extraction`() {
        val chunked = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
        val sized = "HTTP/1.1 200 OK\r\nContent-Length: 42\r\n\r\n"
        assertTrue(DcProbe.isChunked(DcProbe.splitHttpResponse(chunked.toByteArray()).first))
        assertFalse(DcProbe.isChunked(DcProbe.splitHttpResponse(sized.toByteArray()).first))
        assertEquals(42, DcProbe.contentLength(DcProbe.splitHttpResponse(sized.toByteArray()).first))
        assertNull(DcProbe.contentLength(DcProbe.splitHttpResponse(chunked.toByteArray()).first))
    }

    // ------------------------------------------------------ chunked 解码

    @Test
    fun `decode standard chunked body`() {
        val raw = "5\r\nhello\r\n3\r\nabc\r\n0\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
        assertEquals("helloabc", String(DcProbe.decodeChunked(raw)))
    }

    @Test
    fun `decode chunked with extension`() {
        val raw = "4;ext=1\r\nabcd\r\n0\r\n\r\n".toByteArray(Charsets.ISO_8859_1)
        assertEquals("abcd", String(DcProbe.decodeChunked(raw)))
    }

    @Test
    fun `decode truncated chunked returns partial`() {
        val raw = "a\r\n0123456789".toByteArray(Charsets.ISO_8859_1)
        assertEquals("0123456789", String(DcProbe.decodeChunked(raw)))
    }

    // ------------------------------------------------------ 证书主机名匹配

    @Test
    fun `cert san matches exact host`() {
        assertTrue(DcProbe.certMatchesHost(listOf("speed.cloudflare.com", "cf.com"), "speed.cloudflare.com"))
    }

    @Test
    fun `cert wildcard matches single level subdomain`() {
        assertTrue(DcProbe.certMatchesHost(listOf("*.cloudflare.com"), "speed.cloudflare.com"))
        assertTrue(DcProbe.certMatchesHost(listOf("*.cloudflare.com"), "a.cloudflare.com"))
    }

    @Test
    fun `cert wildcard rejects multi level or foreign hosts`() {
        assertFalse(DcProbe.certMatchesHost(listOf("*.cloudflare.com"), "a.b.cloudflare.com"))
        assertFalse(DcProbe.certMatchesHost(listOf("*.cloudflare.com"), "evil.com"))
        assertFalse(DcProbe.certMatchesHost(emptyList(), "speed.cloudflare.com"))
    }
}
