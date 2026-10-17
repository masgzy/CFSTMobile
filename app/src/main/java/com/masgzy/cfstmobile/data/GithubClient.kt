package com.masgzy.cfstmobile.data

import com.masgzy.cfstmobile.model.ProtocolJson
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * GitHub 结果上传客户端(Contents API),行为对齐 PoemMisty/CFData-WEB 的
 * uploadGitHubContent:
 *
 * 1. GET  /repos/{owner}/{repo}/contents/{path}?ref={branch} —— 取现有文件 SHA,
 *    404 视为新建(SHA 为空);2xx 解析 {"sha": ...}。
 * 2. PUT  同 URL,body {message, content(base64), branch[, sha]} —— 成功返回
 *    raw.githubusercontent.com 直链。
 *
 * 请求头与 CFData 一致:Accept vnd.github+json、Bearer Token、
 * X-GitHub-Api-Version 2022-11-28。纯 java.net 实现、object 内不引用
 * android.* 类,保证 JVM 单测可直接加载纯函数。
 */
object GithubClient {

    const val DEFAULT_BRANCH = "main"
    const val DEFAULT_MESSAGE = "update cfst results"
    const val DEFAULT_PATH = "cfst-results.txt"

    private const val API_BASE = "https://api.github.com"
    private const val API_VERSION = "2022-11-28"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    /**
     * 上传优选结果到 GitHub 仓库([token] 需对该 repo 有 Contents 写权限的
     * classic token 或 fine-grained PAT)。返回可分享的 raw 直链。
     */
    suspend fun upload(
        token: String,
        owner: String,
        repo: String,
        branch: String = DEFAULT_BRANCH,
        path: String = DEFAULT_PATH,
        message: String = DEFAULT_MESSAGE,
        content: String,
        onStatus: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val t = token.trim()
            val o = escapeSegment(owner.trim())
            val r = escapeSegment(repo.trim())
            val b = branch.trim().ifBlank { DEFAULT_BRANCH }
            val p = normalizePath(path)
            val m = message.trim().ifBlank { DEFAULT_MESSAGE }
            require(t.isNotEmpty()) { "GitHub Token 不能为空" }
            require(o.isNotEmpty()) { "仓库属主(owner)不能为空" }
            require(r.isNotEmpty()) { "仓库名(repo)不能为空" }
            require(p.isNotEmpty()) { "文件路径不能为空" }
            require(content.isNotBlank()) { "上传内容不能为空" }

            val apiUrl = "$API_BASE/repos/$o/$r/contents/$p"
            onStatus("正在查询目标文件...")
            val sha = fetchSha(apiUrl, t, b)

            onStatus("正在上传...")
            putContent(apiUrl, t, b, m, content, sha)
            onStatus("上传完成")
            rawUrl(o, r, b, p)
        }
    }

    /** 清洗结果路径:去首尾 `/`,按段转义保留层级(与 CFData escapeGitHubContentPath 一致) */
    fun normalizePath(path: String): String {
        val trimmed = path.trim().trim('/')
        if (trimmed.isEmpty()) return ""
        return trimmed.split('/').joinToString("/") { escapeSegment(it) }
    }

    /** raw.githubusercontent.com 直链(与 CFData githubRawURL 同构) */
    fun rawUrl(owner: String, repo: String, branch: String, path: String): String =
        "https://raw.githubusercontent.com/$owner/$repo/refs/heads/$branch/$path"

    /** 单段路径转义:URLEncoder 为 form 编码(空格→+),转回 RFC3986 的 %20 */
    private fun escapeSegment(segment: String): String =
        URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    /** 查询目标文件 SHA;404 → 空串(新建文件) */
    private fun fetchSha(apiUrl: String, token: String, branch: String): String {
        val conn = open("$apiUrl?ref=" + escapeSegment(branch), method = "GET", token = token)
        val status = conn.responseCode
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            conn.disconnect()
            return ""
        }
        if (status !in 200..299) {
            val body = readAll(safeStream(conn)).take(200)
            conn.disconnect()
            throw IllegalStateException("查询 GitHub 文件失败 HTTP $status${if (body.isBlank()) "" else ": $body"}")
        }
        val bodyText = readAll(conn.inputStream)
        conn.disconnect()
        return runCatching {
            ProtocolJson.json.parseToJsonElement(bodyText).jsonObject["sha"]?.jsonPrimitive?.content.orEmpty()
        }.getOrDefault("")
    }

    /** PUT 新内容;带 sha 即为更新,否则新建 */
    private fun putContent(
        apiUrl: String,
        token: String,
        branch: String,
        message: String,
        content: String,
        sha: String,
    ) {
        val payload = buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)))
            put("branch", branch)
            if (sha.isNotEmpty()) put("sha", sha)
        }.toString()

        val conn = open(apiUrl, method = "PUT", token = token)
        conn.doOutput = true
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        val body = if (status in 200..299) "" else readAll(safeStream(conn)).take(300)
        conn.disconnect()
        if (status !in 200..299) {
            throw IllegalStateException("GitHub API 返回 HTTP $status${if (body.isBlank()) "" else ": $body"}")
        }
    }

    private fun open(url: String, method: String, token: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "CFSTMobile")
        }

    private fun safeStream(conn: HttpURLConnection): InputStream? =
        conn.errorStream ?: runCatching { conn.inputStream }.getOrNull()

    private fun readAll(stream: InputStream?): String {
        stream ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
