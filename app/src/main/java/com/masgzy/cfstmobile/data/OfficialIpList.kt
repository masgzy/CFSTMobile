package com.masgzy.cfstmobile.data

import java.security.SecureRandom
import kotlin.random.Random

/**
 * 官方模式 IP 地址库（对齐 CFData-WEB 官方模式链路 runOfficialTask）：
 *
 * 1. 来源：本地缓存 filesDir/ips-v4.txt（与精简地址库共用一文件，语义对齐
 *    CFData getIPListContent）→ 在线 https://www.baipiao.eu.org/cloudflare/ips-v4
 *    （v6 为 ips-v6）→ 失败回退内置官方段（CFData 无网直接报错，App 保持离线可用）。
 * 2. 展开（parseIPList → expandCIDRTo24s/48s → randomIPFromCIDR）：
 *    CIDR 原样 / 纯 IP 补 /32(/128) → 每条 CIDR 切 /24（v6 /48）子网
 *    （超过 2^16 个时取前 65536 个，对齐 maxIPv4SubnetExpansion 截断）→
 *    每个子网随机取 1 个 IP。
 *
 * 历史 bug 根因记录：旧实现把内置 15 段交给引擎 calculate_sample_count 抽样
 * （每段 2^(hostbits-2)，合计约 19 万），而 CFData 官方模式是 /24 随机取一
 * （官方 15 段约 2.1 万）——"默认测试测 190532 个"即由此来。
 */
object OfficialIpList {

    const val URL_V4 = "https://www.baipiao.eu.org/cloudflare/ips-v4"
    const val URL_V6 = "https://www.baipiao.eu.org/cloudflare/ips-v6"

    /** 每条 CIDR 最多展开的 /24（/48）子网数（对齐 CFData maxIPv4SubnetExpansion = 1<<16） */
    const val MAX_SUBNET_EXPANSION: Int = 1 shl 16

    private const val CACHE_V4 = "ips-v4.txt"
    private const val CACHE_V6 = "ips-v6.txt"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    /** 地址库内容（缓存优先 → 在线下载并缓存 → 内置段回退），全部失败返回 null */
    fun fetchContent(context: android.content.Context, isV6: Boolean): String? {
        val cache = cacheFile(context, if (isV6) CACHE_V6 else CACHE_V4)
        if (cache.isFile && cache.length() > 0) {
            val content = runCatching { cache.readText() }.getOrNull()
            if (!content.isNullOrBlank()) return content
        }
        val downloaded = download(if (isV6) URL_V6 else URL_V4)
        if (downloaded != null) {
            runCatching {
                val tmp = java.io.File(cache.parentFile, "${cache.name}.tmp")
                tmp.writeText(downloaded)
                if (cache.exists()) cache.delete()
                if (!tmp.renameTo(cache)) tmp.delete()
            }
            return downloaded
        }
        return (if (isV6) BUILT_IN_IPS_V6 else BUILT_IN_IPS).joinToString("\n")
    }

    private fun cacheFile(context: android.content.Context, name: String): java.io.File =
        java.io.File(context.filesDir, name)

    private fun download(url: String): String? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 解析 + 展开随机：返回测试 IP 列表（不含端口，端口由引擎统一 tcp_port 下发）。
     * [random] 可注入确定性随机供单测使用。
     */
    fun expandToRandomIps(content: String, isV6: Boolean, random: Random = Random(SecureRandom().nextLong())): List<String> {
        val out = ArrayList<String>(1024)
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val cidr = if (line.contains('/')) line else "$line/${if (isV6) "128" else "32"}"
                val subnets = expandToSubnets(cidr, isV6) ?: return@forEach
                for (subnet in subnets) {
                    randomIpFromCidr(subnet, random)?.let { out.add(it) }
                }
            }
        return out
    }

    /** 展开后的总测试 IP 数（日志预览用） */
    fun countExpanded(content: String, isV6: Boolean): Int =
        expandToRandomIps(content, isV6, Random(0)).size

    /**
     * CIDR → 子网列表：前缀 >= /24（v6 /48）原样返回单条；否则按
     * 24-ones（48-ones）切分（对齐 CFData：shift 上限 16，即最多 65536 个子网，
     * 大段只取前 65536 个 /24 而非跳过）。解析失败返回 null。
     */
    fun expandToSubnets(cidr: String, isV6: Boolean): List<String>? {
        val parsed = parseCidr(cidr, isV6) ?: return null
        val targetPrefix = if (isV6) 48 else 24
        if (parsed.ones >= targetPrefix) return listOf(cidr)
        val shift = (targetPrefix - parsed.ones).coerceAtMost(16)
        val count = (1 shl shift).coerceAtMost(MAX_SUBNET_EXPANSION)
        val list = ArrayList<String>(count)
        for (i in 0 until count) {
            list.add(formatCidr(parsed, i, targetPrefix))
        }
        return list
    }

    /** 从 CIDR 随机取一个 IP（host 位随机；v4 32 位内全随机，v6 按 hi/lo 128 位内随机） */
    fun randomIpFromCidr(cidr: String, random: Random = Random(SecureRandom().nextLong())): String? {
        val parsed = parseCidr(cidr, isV6 = cidr.contains(':')) ?: return null
        val hostBits = parsed.bits - parsed.ones
        if (hostBits <= 0) return formatIp(parsed, parsed.hi, parsed.lo)
        var hi = parsed.hi
        var lo = parsed.lo
        if (parsed.isV6) {
            // v6：host 位跨 hi/lo。ones<=64 时高段 host 在 hi 低 (64-ones) 位且 lo 全随机；
            // ones>64 时 host 全在 lo 低 (128-ones) 位（hostBits=128 时 ones=0 不可能出现于官方段）
            if (hostBits >= 64) {
                val hiHostBits = (hostBits - 64).coerceAtMost(63)
                if (hiHostBits > 0) hi = hi or randomNonNeg(random, 1L shl hiHostBits)
                lo = random.nextLong()
            } else {
                lo = lo or randomNonNeg(random, 1L shl hostBits)
            }
        } else {
            // v4：hostBits=32（/0）时 1L shl 32 溢出，钳到 31 位（官方段无 /0，极端输入仅损失最高位随机）
            val maxHost = 1L shl hostBits.coerceAtMost(31)
            lo = lo or randomNonNeg(random, maxHost)
        }
        return formatIp(parsed, hi, lo)
    }

    /** [0, range) 内的非负随机；range<=1 返回 0 */
    private fun randomNonNeg(random: Random, range: Long): Long =
        if (range <= 1) 0L else (random.nextLong() and Long.MAX_VALUE) % range

    // ---------------------------------------------------------------- 128 位 CIDR 数学

    private data class Cidr(val hi: Long, val lo: Long, val ones: Int, val bits: Int, val isV6: Boolean)

    /**
     * 解析 CIDR 为 (hi, lo) 128 位网络地址 + 前缀。
     * 仅接受字面 IP（禁止主机名触发 DNS 解析，离线安全）。
     */
    private fun parseCidr(cidr: String, isV6: Boolean): Cidr? {
        val parts = cidr.split("/")
        if (parts.size != 2) return null
        val ones = parts[1].trim().toIntOrNull() ?: return null
        val bits = if (isV6) 128 else 32
        if (ones < 0 || ones > bits) return null
        val text = parts[0].trim()
        val addr = parseIpLiteral(text, isV6) ?: return null
        var hi = 0L
        var lo = 0L
        if (isV6) {
            for (i in 0 until 8) hi = (hi shl 8) or (addr[i].toLong() and 0xFF)
            for (i in 8 until 16) lo = (lo shl 8) or (addr[i].toLong() and 0xFF)
        } else {
            for (b in addr) lo = (lo shl 8) or (b.toLong() and 0xFF)
        }
        // 掩掉 host 位（网络地址）：v6 前缀 <=64 时 host 跨 hi/lo 两侧
        val hostBits = bits - ones
        if (isV6) {
            if (hostBits >= 64) {
                val hiHostBits = (hostBits - 64).coerceAtMost(64)
                hi = maskLow(hi, hiHostBits)
                lo = 0L
            } else {
                lo = maskLow(lo, hostBits)
            }
        } else {
            lo = maskLow(lo, hostBits)
        }
        return Cidr(hi, lo, ones, bits, isV6)
    }

    /** 清除 [hostBits] 个低位（Long 版，hostBits=64 全清） */
    private fun maskLow(v: Long, hostBits: Int): Long = when {
        hostBits <= 0 -> v
        hostBits >= 64 -> 0L
        else -> (v ushr hostBits) shl hostBits
    }

    /**
     * IP 字面量 → 字节数组。手动校验格式以避免 InetAddress 对主机名触发 DNS。
     */
    private fun parseIpLiteral(text: String, isV6: Boolean): ByteArray? {
        if (isV6) {
            if (!text.contains(':')) return null
            if (!text.matches(Regex("[0-9a-fA-F:.]+"))) return null
            return runCatching { java.net.InetAddress.getByName(text).address }.getOrNull()
                ?.takeIf { it.size == 16 }
        }
        if (!text.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))) return null
        val octets = text.split('.').map { it.toIntOrNull() ?: return null }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        return byteArrayOf(
            octets[0].toByte(), octets[1].toByte(), octets[2].toByte(), octets[3].toByte(),
        )
    }

    /** 网络地址 + 第 [index] 个子网 → CIDR 字符串（index 挂在目标前缀位上） */
    private fun formatCidr(parsed: Cidr, index: Int, targetPrefix: Int): String {
        val (hi, lo) = if (parsed.isV6) {
            if (targetPrefix <= 64) {
                // index 挂在 hi 的 bit(64-targetPrefix) 起
                val shift = 64 - targetPrefix
                Pair(parsed.hi or (index.toLong() shl shift), parsed.lo)
            } else {
                Pair(parsed.hi, parsed.lo or (index.toLong() shl (128 - targetPrefix)))
            }
        } else {
            val shift = 32 - targetPrefix
            Pair(parsed.hi, parsed.lo or (index.toLong() shl shift))
        }
        return "${formatIp(parsed, hi, lo)}/$targetPrefix"
    }

    private fun formatIp(parsed: Cidr, hi: Long, lo: Long): String = if (parsed.isV6) {
        val bytes = ByteArray(16)
        for (i in 0 until 8) bytes[i] = ((hi ushr ((7 - i) * 8)) and 0xFF).toByte()
        for (i in 0 until 8) bytes[8 + i] = ((lo ushr ((7 - i) * 8)) and 0xFF).toByte()
        java.net.InetAddress.getByAddress(bytes).hostAddress
    } else {
        val v = lo
        "${(v ushr 24) and 0xFF}.${(v ushr 16) and 0xFF}.${(v ushr 8) and 0xFF}.${v and 0xFF}"
    }
}
