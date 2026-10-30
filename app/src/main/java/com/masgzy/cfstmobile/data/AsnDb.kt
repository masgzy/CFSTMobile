package com.masgzy.cfstmobile.data

import android.content.Context
import com.maxmind.db.Reader
import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * GeoLite2-ASN 离线库查询（对齐 CFData asn.go：同源镜像下载 + maxmind 官方 reader）。
 *
 * - 库文件缓存 filesDir/GeoLite2-ASN.mmdb（CFData asnDBFile 同名同源）；
 * - 下载失败/解析失败静默降级为空结果（ASN 列隐藏，不影响测速与导出主流程）；
 * - 查询纯本地文件操作，批量数千 IP 毫秒级。
 */
object AsnDb {

    private const val DB_FILE = "GeoLite2-ASN.mmdb"

    /** CFData asn.go 同款镜像（国内可达） */
    const val DB_URL =
        "https://jsd.onmicrosoft.cn/gh/seketiti/GeoLiet2@release/GeoLite2-ASN.mmdb"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000

    /** ASN 查询结果（号码 + 组织） */
    data class AsnInfo(val number: String, val org: String)

    private val loadMutex = Mutex()

    @Volatile
    private var reader: Reader? = null

    /**
     * 确保库就绪（缓存有效 → 直接打开；缺失 → 下载后打开）。成功返回 true。
     * 并发调用安全（互斥）；重复调用幂等。
     */
    suspend fun ensureLoaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            if (reader != null) return@withLock true
            val file = File(context.filesDir, DB_FILE)
            if (!file.isFile || file.length() <= 0) {
                val downloaded = download() ?: return@withLock false
                runCatching {
                    val tmp = File(file.parentFile, "$DB_FILE.tmp")
                    tmp.writeBytes(downloaded)
                    if (file.exists()) file.delete()
                    if (!tmp.renameTo(file)) tmp.delete()
                }.onFailure { return@withLock false }
            }
            runCatching { reader = Reader(file) }.isSuccess
        }
    }

    /** 批量查询去重后的 IP 集合 → ip:ip → AsnInfo（库未就绪/失败返回空表） */
    suspend fun lookupAll(context: Context, ips: Collection<String>): Map<String, AsnInfo> {
        if (!ensureLoaded(context)) return emptyMap()
        return withContext(Dispatchers.IO) {
            val r = reader ?: return@withContext emptyMap()
            val out = HashMap<String, AsnInfo>()
            for (ip in ips.toSet()) {
                lookup(r, ip)?.let { out[ip] = it }
            }
            out
        }
    }

    /** 单条查询（库未加载返回 null） */
    private fun lookup(r: Reader, ip: String): AsnInfo? = runCatching {
        val node = r.get(InetAddress.getByName(ip)) ?: return null
        val map = node as? Map<*, *> ?: return null
        val number = (map["autonomous_system_number"])?.toString()?.takeIf { it != "null" } ?: return null
        val org = (map["autonomous_system_organization"])?.toString().orEmpty()
        AsnInfo(number, org)
    }.getOrNull()

    /** 释放 reader（进程内常驻，一般无需调用；供测试清理） */
    @Synchronized
    fun close() {
        runCatching { reader?.close() }
        reader = null
    }

    private fun download(): ByteArray? = runCatching {
        val conn = java.net.URL(DB_URL).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input -> input.readBytes() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
