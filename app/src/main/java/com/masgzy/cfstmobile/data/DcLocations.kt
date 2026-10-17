package com.masgzy.cfstmobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** 数据中心位置（speed.cloudflare.com/locations 与 CFData locations.json 同构格式的字段子集） */
@Serializable
data class DcLocation(
    val iata: String = "",
    val cca2: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
)

/**
 * 数据中心位置表（iata → 国家/城市），用于把 trace 返回的三字码翻译为可读位置。
 *
 * - 优先官方 `speed.cloudflare.com/locations`，失败回退 CFData 同源
 *   `baipiao.eu.org/cloudflare/locations`（CFData initLocations 同款 URL）；
 * - 缓存于应用私有目录（7 天刷新；CFData 为永久缓存，位置表极少变化，
 *   7 天过期可在其失效时自动修复损坏数据）；
 * - 全部失败时返回空表：UI 降级为仅显示 colo 三字码（对齐 CFData 的
 *   "locationMap 未就绪，城市/地区 列将为空" 行为）。
 */
object DcLocations {

    const val OFFICIAL_URL = "https://speed.cloudflare.com/locations"
    const val CFDATA_URL = "https://www.baipiao.eu.org/cloudflare/locations"
    const val CACHE_FILE = "cf_locations.json"
    const val CACHE_TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析 locations JSON → 以 iata（大写）为键的索引表；解析失败返回空表 */
    fun parse(text: String): Map<String, DcLocation> = try {
        val list = json.decodeFromString(ListSerializer(DcLocation.serializer()), text)
        list.filter { it.iata.isNotBlank() }
            .associateBy { it.iata.trim().uppercase(Locale.US) }
    } catch (_: Exception) {
        emptyMap()
    }

    /** 加载位置表：缓存未过期直接用 → 下载（官方 → CFData 同源）→ 失败空表降级 */
    suspend fun load(context: Context): Map<String, DcLocation> = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, CACHE_FILE)
        if (cache.isFile && cache.lastModified() + CACHE_TTL_MS > System.currentTimeMillis()) {
            val cached = parse(runCatching { cache.readText() }.getOrDefault(""))
            if (cached.isNotEmpty()) return@withContext cached
        }
        val text = download(OFFICIAL_URL) ?: download(CFDATA_URL)
        if (text == null) return@withContext emptyMap()
        val parsed = parse(text)
        if (parsed.isNotEmpty()) {
            runCatching { cache.writeText(text) }
        }
        parsed
    }

    private fun download(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}
