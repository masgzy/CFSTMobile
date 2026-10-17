package com.masgzy.cfstmobile.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloudflare IP 段来源：
 * - 内置官方段（[BUILT_IN_IPS]）；
 * - 在线获取：Cloudflare 官方 ips-v4 / XIU2 仓库的 ip.txt（可走 GitHub 镜像前缀）。
 * 全部为阻塞网络请求，调用方需在 Dispatchers.IO 上执行。
 */
object CloudflareIpSources {

    const val CLOUDFLARE_URL = "https://www.cloudflare.com/ips-v4"
    const val GITHUB_URL = "https://raw.githubusercontent.com/XIU2/CloudflareSpeedTest/master/ip.txt"

    /** GitHub Raw 常用镜像前缀（拼接在完整 URL 之前） */
    val MIRROR_PRESETS = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.vip/",
        "https://ghps.cc/",
    )

    /**
     * 拉取 IP 段列表。[mirrorPrefix] 仅对 GitHub Raw 地址生效。
     * 返回去注释、去空行后的 CIDR/IP 行；失败抛 IOException 等由调用方处理。
     */
    fun fetch(url: String, mirrorPrefix: String = ""): List<String> {
        val target = if (mirrorPrefix.isNotBlank() && url.startsWith("https://raw.githubusercontent.com/")) {
            mirrorPrefix.trimEnd('/') + "/" + url
        } else {
            url
        }
        val conn = URL(target).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "CFSTMobile/1.0 (Android)")
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            conn.inputStream.bufferedReader().use { reader ->
                reader.readText()
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .filter { line -> line.firstOrNull()?.isDigit() == true }
                    .toList()
            }
        } finally {
            conn.disconnect()
        }
    }
}
