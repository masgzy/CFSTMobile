package com.masgzy.cfstmobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * 数据中心二次详细测试的网络探测（对齐 CFData-WEB 官方模式 tasks_official.go）。
 *
 * - **trace 探测**：对 `IP:端口` 直连（TCP 拨号后按端口决定是否包 TLS，SNI/Host 恒为
 *   `speed.cloudflare.com`），手写 HTTP/1.1 `GET /cdn-cgi/trace`，从响应体解析
 *   `colo=XXX`——与 CFData `scanOfficialIP` / `scanOfficialHTTP` 同源同语义；
 * - **二次详细测试**：tcping 连续 10 次建连计时（CFData `testIPLatency` 同款，无间隔）、
 *   httping 3 次 TTFB（含 TLS 握手与首字节、不含 TCP 拨号），统计 min/max/avg/loss；
 * - **倍率**：httping 时 TLS 端口 ×4.0、明文端口 ×1.3（CFData `latencyMultiplier`）。
 *
 * 纯 Kotlin + java.net/javax.net.ssl，无 Android 依赖，解析函数可 JVM 单测。
 */
object DcProbe {

    const val TRACE_HOST = "speed.cloudflare.com"
    const val TRACE_PATH = "/cdn-cgi/trace"
    const val TRACE_USER_AGENT = "Mozilla/5.0"

    /** trace 探测的 TCP 连接 / 读超时（CFData timeout = 3s） */
    const val PROBE_TIMEOUT_MS = 3000

    /** Cloudflare TLS 端口（CFData `isTLSPort` 同款） */
    val TLS_PORTS: Set<Int> = setOf(443, 2053, 2083, 2087, 2096, 8443)

    /** TCP 二次测试次数（CFData testIPLatency：tcping 10 次） */
    const val TCPING_ATTEMPTS = 10

    /** HTTPing 二次测试次数（CFData testIPLatency：httping 3 次） */
    const val HTTPING_ATTEMPTS = 3

    fun isTlsPort(port: Int): Boolean = port in TLS_PORTS

    /** CFData latencyMultiplier：tcping = 1.0；httping 明文 = 1.3；httping TLS = 4.0 */
    fun latencyMultiplier(httping: Boolean, tls: Boolean): Double =
        if (!httping) 1.0 else if (tls) 4.0 else 1.3

    /** trace 请求（Connection: close：响应读至 EOF 即完整） */
    fun traceRequest(): String =
        "GET $TRACE_PATH HTTP/1.1\r\nHost: $TRACE_HOST\r\n" +
            "User-Agent: $TRACE_USER_AGENT\r\nConnection: close\r\n\r\n"

    // ---------------------------------------------------------- 响应解析（纯函数）

    /** 从 trace 响应体解析 colo（如 `colo=HKG`）；缺失或为空返回 null */
    fun parseTraceColo(body: String): String? {
        val line = body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("colo=") }
            ?: return null
        return line.removePrefix("colo=").trim().takeIf { it.isNotEmpty() }
    }

    /** 拆分完整 HTTP 响应为 (headers, body)；找不到 \\r\\n\\r\\n 时整体视作 header、body 为空 */
    fun splitHttpResponse(raw: ByteArray): Pair<String, ByteArray> {
        val sep = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A)
        val idx = indexOf(raw, sep, 0)
        if (idx < 0) return String(raw, Charsets.ISO_8859_1) to ByteArray(0)
        return String(raw, 0, idx, Charsets.ISO_8859_1) to
            raw.copyOfRange(idx + sep.size, raw.size)
    }

    /** Transfer-Encoding 是否为 chunked */
    fun isChunked(headers: String): Boolean {
        val h = headers.lowercase(Locale.US)
        return h.lineSequence().any {
            it.startsWith("transfer-encoding:") && it.contains("chunked")
        }
    }

    /** Content-Length（缺失或非法返回 null） */
    fun contentLength(headers: String): Int? =
        headers.lineSequence()
            .firstOrNull { it.lowercase(Locale.US).startsWith("content-length:") }
            ?.substringAfter(':')?.trim()?.toIntOrNull()

    /** 解码 chunked body（忽略 trailer；截断时返回已解出的部分） */
    fun decodeChunked(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (true) {
            val eol = indexOfEol(input, pos)
            if (eol < 0) break
            val sizeLine = String(input, pos, eol - pos, Charsets.ISO_8859_1).trim()
            val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
            if (size <= 0) break
            val bodyStart = eol + 2
            val end = minOf(bodyStart + size, input.size)
            if (bodyStart >= input.size) break
            out.write(input, bodyStart, end - bodyStart)
            pos = end
            // 跳过块尾 CRLF
            if (pos + 2 <= input.size && input[pos] == 0x0D.toByte() && input[pos + 1] == 0x0A.toByte()) {
                pos += 2
            }
        }
        return out.toByteArray()
    }

    /**
     * 证书 SAN 与主机名匹配（对齐 CFData TLS ServerName 校验语义）：
     * 精确匹配或通配符单级匹配（`*.cloudflare.com` 匹配 `speed.cloudflare.com`）。
     */
    fun certMatchesHost(sans: List<String>, host: String): Boolean {
        val h = host.lowercase(Locale.US)
        return sans.any { san ->
            val s = san.lowercase(Locale.US)
            s == h || (s.startsWith("*.") && h.endsWith(s.substring(1)) &&
                h.removeSuffix(s.substring(1)).none { it == '.' })
        }
    }

    // ---------------------------------------------------------- trace 探测

    /**
     * 获取 IP 所在数据中心（trace colo）。失败返回 null（中断会正常传播）。
     * 不跟随 HTTP 重定向：非 TLS 端口上若 301 则视为探测失败（保留 IP 语义准确性）。
     */
    suspend fun fetchColo(ip: String, port: Int): String? = try {
        parseTraceColo(trace(ip, port))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun trace(ip: String, port: Int): String = runInterruptible(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(ip, port), PROBE_TIMEOUT_MS)
            socket.soTimeout = PROBE_TIMEOUT_MS
            val stream = maybeWrapTls(socket, port)
            val out = stream.getOutputStream()
            out.write(traceRequest().toByteArray(Charsets.US_ASCII))
            out.flush()
            val raw = readAll(stream.getInputStream(), capBytes = 64 * 1024)
            val (headers, body) = splitHttpResponse(raw)
            val bodyText = if (isChunked(headers)) {
                String(decodeChunked(body), Charsets.UTF_8)
            } else {
                String(body, Charsets.UTF_8)
            }
            bodyText
        } finally {
            runCatching { socket.close() }
        }
    }

    /** 按端口决定是否包 TLS；TLS 时校验对端证书归属（SAN 必须为 cloudflare.com 系） */
    private fun maybeWrapTls(socket: Socket, port: Int): Socket {
        if (!isTlsPort(port)) return socket
        val ssl = sslFactory().createSocket(socket, TRACE_HOST, port, true) as SSLSocket
        ssl.startHandshake()
        val cert = ssl.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw IOException("对端未提供证书")
        val sans = cert.subjectAlternativeNames
            .orEmpty()
            .mapNotNull { entry -> entry?.get(1) as? String }
        if (!certMatchesHost(sans, TRACE_HOST)) {
            throw IOException("对端证书与 $TRACE_HOST 不匹配")
        }
        return ssl
    }

    private fun sslFactory(): SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory

    // ---------------------------------------------------------- 二次详细测试

    /** 详细测试统计（对齐 CFData TestResult：min/max/avg/loss） */
    class DetailedStats(
        val minMs: Long,
        val maxMs: Long,
        val avgMs: Double,
        val received: Int,
        val attempts: Int,
    )

    /**
     * TCP 二次详细测试：连续 [TCPING_ATTEMPTS] 次建连计时，连接超时即该次失败
     * （CFData testIPLatency tcping 分支：dialer timeout = delay，delay <= 0 时回退 3s）。
     */
    suspend fun detailedTcpTest(ip: String, port: Int, delayMs: Int): DetailedStats? {
        val timeoutMs = if (delayMs > 0) delayMs else PROBE_TIMEOUT_MS
        val acc = Acc(TCPING_ATTEMPTS)
        repeat(TCPING_ATTEMPTS) {
            tcpPingOnce(ip, port, timeoutMs = timeoutMs)?.let(acc::add)
        }
        return acc.finish()
    }

    /**
     * HTTPing 二次详细测试：连续 [HTTPING_ATTEMPTS] 次 TTFB，超时阈值 = delay × 倍率
     * （TLS 端口 ×4.0 / 明文 ×1.3，连接超时 3s + 读超时 effective+1s，对齐 CFData）。
     */
    suspend fun detailedHttpTest(ip: String, port: Int, delayMs: Int): DetailedStats? {
        val tls = isTlsPort(port)
        val effective = (delayMs * latencyMultiplier(httping = true, tls = tls))
            .toInt().coerceAtLeast(1)
        val acc = Acc(HTTPING_ATTEMPTS)
        repeat(HTTPING_ATTEMPTS) {
            httpPingOnce(ip, port, effective)?.let(acc::add)
        }
        return acc.finish()
    }

    /** 单次 TCP 建连延迟（ms）；超时/失败/中断返回 null（中断正常传播） */
    suspend fun tcpPingOnce(ip: String, port: Int, timeoutMs: Int): Long? = try {
        runInterruptible(Dispatchers.IO) {
            val socket = Socket()
            try {
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                (System.nanoTime() - start) / 1_000_000L
            } finally {
                runCatching { socket.close() }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * 单次 HTTPing TTFB（ms）：TCP 拨号后（不含拨号时间，对齐 CFData reqStart 时机），
     * TLS 握手 + 写请求 + 收到首字节。失败/超时返回 null。
     */
    suspend fun httpPingOnce(ip: String, port: Int, effectiveDelayMs: Int): Long? = try {
        runInterruptible(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(ip, port), PROBE_TIMEOUT_MS)
                socket.soTimeout = effectiveDelayMs + 1000
                val stream = maybeWrapTls(socket, port)
                val start = System.nanoTime()
                val out = stream.getOutputStream()
                out.write(traceRequest().toByteArray(Charsets.US_ASCII))
                out.flush()
                if (stream.getInputStream().read() < 0) {
                    return@runInterruptible null
                }
                val ttfb = (System.nanoTime() - start) / 1_000_000L
                if (ttfb > effectiveDelayMs) null else ttfb
            } finally {
                runCatching { socket.close() }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private class Acc(val attempts: Int) {
        var received = 0
        var minMs = Long.MAX_VALUE
        var maxMs = 0L
        var totalMs = 0L

        fun add(ms: Long) {
            received++
            totalMs += ms
            if (ms < minMs) minMs = ms
            if (ms > maxMs) maxMs = ms
        }

        fun finish(): DetailedStats? {
            if (received == 0) return null
            return DetailedStats(
                minMs = minMs,
                maxMs = maxMs,
                avgMs = totalMs.toDouble() / received,
                received = received,
                attempts = attempts,
            )
        }
    }

    // ---------------------------------------------------------- 工具

    /** 读至 EOF（Connection: close；capBytes 防御性上限） */
    private fun readAll(input: InputStream, capBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() >= capBytes) break
        }
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        if (pattern.isEmpty()) return from
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun indexOfEol(data: ByteArray, from: Int): Int {
        var i = from
        while (i + 1 < data.size) {
            if (data[i] == 0x0D.toByte() && data[i + 1] == 0x0A.toByte()) return i
            i++
        }
        return -1
    }
}
