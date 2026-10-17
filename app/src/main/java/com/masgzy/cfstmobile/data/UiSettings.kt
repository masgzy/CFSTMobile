package com.masgzy.cfstmobile.data

import com.masgzy.cfstmobile.model.CfstConfig
import com.masgzy.cfstmobile.model.SortMode
import kotlinx.serialization.Serializable

/** 内置 Cloudflare 官方 IPv4 段（www.cloudflare.com/ips-v4） */
val BUILT_IN_IPS: List<String> = listOf(
    "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
    "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
    "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
    "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22",
)

/** UI 层设置（持久化为 JSON；默认值与引擎默认一致） */
@Serializable
data class UiSettings(
    /** IP 来源：内置段勾选 / 自定义 CIDR 文本 */
    val ipSource: String = SOURCE_BUILTIN,
    val selectedSegments: List<String> = BUILT_IN_IPS,
    val customIpText: String = "",
    /** 测速端口（引擎默认 443） */
    val tcpPort: Int = 443,
    /** 延迟测速方式：tcp / http（icmp 需 root，普通 App 不暴露） */
    val pingMode: String = PING_TCP,
    /** ping_mode=http 的探测地址；空 = 引擎默认 cp.cloudflare.com trace */
    val httpingUrl: String = "",
    /** 是否做下载测速（false = 仅测延迟） */
    val enableDownload: Boolean = true,
    val downloadUrl: String = DEFAULT_DOWNLOAD_URL,
    /** 单 IP 下载测速时长（秒） */
    val downloadTimeSecs: Int = 10,
    /** 需要达标的结果数量 */
    val testCount: Int = 10,
    /** 结果保留数量上限 */
    val printNum: Int = 10,
    /** 每 IP 延迟测速次数 */
    val pingTimes: Int = 4,
    /** 并发上限 */
    val maxThreads: Int = 256,
    /** 延迟上限（ms），超出者淘汰 */
    val maxDelayMs: Int = 2000,
    /** 丢包率上限（0.0 ~ 1.0） */
    val maxLossRate: Double = 1.0,
    /** 达标速度下限（MB/s），0 = 不限 */
    val minSpeedMbps: Double = 0.0,
    /** 全局超时（秒），0 = 不限 */
    val globalTimeoutSecs: Int = 0,
    /** 测试 CIDR 全部 IPv4（否则按前缀采样） */
    val testAllIpv4: Boolean = false,
    /** GitHub 镜像前缀（仅用于拉取 IP 段列表） */
    val mirrorUrl: String = "",
    /** 结果排序：score / delay / speed */
    val sortBy: String = SortMode.SCORE,
    /** Material You 动态取色（Android 12+ 跟随壁纸；关闭则用 Cloudflare 蓝品牌色） */
    val dynamicColors: Boolean = true,
) {
    /** 组装引擎配置载荷（未填字段交给引擎默认值/校验） */
    fun toCfstConfig(): CfstConfig = CfstConfig(
        ipText = if (ipSource == SOURCE_BUILTIN) {
            selectedSegments.joinToString(",")
        } else {
            customIpText.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        },
        tcpPort = tcpPort,
        pingMode = pingMode,
        httpingUrl = httpingUrl.trim().ifBlank { null },
        url = if (enableDownload) downloadUrl.trim() else "",
        disableDownload = !enableDownload,
        downloadTime = downloadTimeSecs,
        testCount = testCount,
        printNum = printNum,
        pingTimes = pingTimes,
        maxThreads = maxThreads,
        maxDelayMs = maxDelayMs,
        maxLossRate = maxLossRate.toFloat(),
        minSpeedMbS = minSpeedMbps.toFloat(),
        globalTimeoutSecs = if (globalTimeoutSecs > 0) globalTimeoutSecs else null,
        testAllIpv4 = testAllIpv4,
    )

    /** 启动前的轻校验（引擎侧还有完整校验与钳制） */
    fun validate(): String? = when {
        tcpPort !in 1..65535 -> "测速端口必须在 1~65535 之间"
        ipSource == SOURCE_BUILTIN && selectedSegments.isEmpty() ->
            "请至少选择一个内置 IP 段"
        ipSource == SOURCE_CUSTOM && customIpText.isBlank() ->
            "自定义 IP 来源为空（支持 CIDR / 单 IP / 域名[:端口]，每行一个）"
        enableDownload && downloadUrl.isBlank() -> "启用下载测速时必须填写测速 URL"
        else -> null
    }

    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_CUSTOM = "custom"
        const val PING_TCP = "tcp"
        const val PING_HTTP = "http"

        /** 引擎 README 验证过的官方大流量下载地址（200MB） */
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=200000000"
    }
}
