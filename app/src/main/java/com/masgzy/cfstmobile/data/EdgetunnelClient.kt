package com.masgzy.cfstmobile.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * edgetunnel 订阅上传客户端（协议与 PoemMisty/CFData-WEB 的
 * uploadToEdgetunnel 完全一致，双方产物可互换）：
 *
 * 1. 登录   POST {host}/login          body: password=<urlencoded>（form-urlencoded）
 *          成功 = HTTP 200 且响应非 text/html；凭据从 Set-Cookie 取第一个。
 * 2. 读取   GET  {host}/admin/ADD.txt?_t=<unix>（仅追加模式，读取失败自动降级为覆盖）
 * 3. 写入   POST {host}/admin/ADD.txt   body: 新内容（text/plain）
 *          成功 = HTTP 200 且响应 JSON {"success": true}
 *
 * 纯 java.net 实现（零第三方依赖）；object 内不引用 android.* 类，
 * 保证 JVM 单测可直接加载 [normalizeHost]。
 */
object EdgetunnelClient {

    /** 追加模式：保留远程已有内容，新结果追加在末尾 */
    const val MODE_APPEND = "append"

    /** 覆盖模式：用新结果整体替换远程订阅内容 */
    const val MODE_OVERWRITE = "overwrite"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_REMOTE_BYTES = 1L shl 20 // 与 CFData 相同的 1MB 上限

    /**
     * 主机归一化：无 scheme 时补 https://，去掉尾部 `/`。
     * 与 CFData-WEB normalizeEdgetunnelHost 行为一致。
     */
    fun normalizeHost(host: String): String {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return trimmed
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    /**
     * 上传优选结果到 edgetunnel（CM 系 Worker 订阅 ADD.txt）。
     * [onStatus] 在 IO 线程回调阶段性状态（登录中/读取中/上传中），UI 层直接刷新。
     */
    suspend fun upload(
        host: String,
        password: String,
        content: String,
        mode: String = MODE_OVERWRITE,
        onStatus: (String) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeHost(host)
            require(base.isNotEmpty()) { "edgetunnel 域名不能为空" }
            require(password.isNotBlank()) { "edgetunnel 密码不能为空" }
            require(content.isNotBlank()) { "上传内容不能为空" }

            onStatus("正在登录...")
            val cookie = login(base, password)

            var payload = content.trim()
            if (mode == MODE_APPEND) {
                onStatus("正在读取远程订阅...")
                val remote = runCatching { readRemote(base, cookie) }.getOrNull()
                if (remote.isNullOrEmpty() || remote == "null") {
                    onStatus("远程订阅为空，将直接写入")
                } else {
                    payload = "$remote\n$payload"
                }
            }

            onStatus("正在上传...")
            writeRemote(base, cookie, payload)
            onStatus("上传完成")
            Unit
        }
    }

    /** 登录并返回 `name=value` 形式的会话 cookie */
    private fun login(host: String, password: String): String {
        val body = "password=" + URLEncoder.encode(password, Charsets.UTF_8.name())
        val conn = open("${host}/login", method = "POST")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val status = conn.responseCode
        val errBody = if (status in 200..299) "" else readAll(safeStream(conn)).take(200)
        if (status != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("登录失败 HTTP $status${if (errBody.isBlank()) "" else ": $errBody"}")
        }
        val contentType = conn.getHeaderField("Content-Type") ?: ""
        if (contentType.contains("text/html", ignoreCase = true)) {
            throw IllegalStateException("登录失败：密码错误")
        }
        val cookie = conn.headerFields
            ?.filterKeys { it.equals("Set-Cookie", ignoreCase = true) }
            ?.values
            ?.flatten()
            ?.firstOrNull()
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("登录成功但未获取到会话 Cookie")
        conn.disconnect()
        return cookie
    }

    /** 读取远程 ADD.txt 现有内容（追加模式用）；失败抛出由调用方降级 */
    private fun readRemote(host: String, cookie: String): String {
        val conn = open("${host}/admin/ADD.txt?_t=${System.currentTimeMillis() / 1000}", method = "GET")
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("读取远程订阅失败 HTTP ${conn.responseCode}")
        }
        val text = readAll(conn.inputStream)
        conn.disconnect()
        return text.trim()
    }

    /** 写入 ADD.txt，校验 {"success": true} */
    private fun writeRemote(host: String, cookie: String, content: String) {
        val conn = open("${host}/admin/ADD.txt", method = "POST")
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("Content-Type", "text/plain")
        conn.doOutput = true
        conn.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }

        val status = conn.responseCode
        val body = readAll(if (status in 200..299) conn.inputStream else safeStream(conn))
        conn.disconnect()
        if (status != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("上传失败 HTTP $status${if (body.isBlank()) "" else ": ${body.take(200)}"}")
        }
        if (!body.contains("\"success\"") || body.contains("\"success\":false")) {
            throw IllegalStateException("上传返回异常：${body.take(200)}")
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    /** 非 2xx 响应的错误体：errorStream 优先，避免误触 inputStream 抛出掩盖真实状态码 */
    private fun safeStream(conn: HttpURLConnection): java.io.InputStream? =
        conn.errorStream ?: runCatching { conn.inputStream }.getOrNull()

    private fun readAll(stream: java.io.InputStream?): String {
        stream ?: return ""
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            val buf = StringBuilder()
            val chars = CharArray(4096)
            var n: Int
            while (reader.read(chars).also { n = it } >= 0 && buf.length < MAX_REMOTE_BYTES) {
                buf.append(chars, 0, n)
            }
            buf.toString()
        }
    }
}
