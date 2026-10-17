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

/** 内置 Cloudflare 官方 IPv6 段（www.cloudflare.com/ips-v6，引擎原生支持 v6 CIDR） */
val BUILT_IN_IPS_V6: List<String> = listOf(
    "2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32",
    "2405:8100::/32", "2a06:98c0::/29", "2c0f:f248::/32",
)

/** 测速网址预设（对齐 CFData-WEB 测速网址下拉） */
object SpeedUrlPresets {
    const val AUTO = "auto"
    const val CLOUDFLARE = "cloudflare"
    const val CM = "cm"
    const val MOBILE = "mobile"
    const val CUSTOM = "custom"

    /** 预设 → 实际下载 URL（auto/cloudflare 同源，引擎大流量验证过 200MB） */
    fun resolve(preset: String, custom: String): String = when (preset) {
        CM -> "https://cf.090227.xyz/__down?bytes=99999999"
        MOBILE -> "https://speed.okl.abrdns.com"
        CUSTOM -> custom.trim()
        else -> DEFAULT_DOWNLOAD_URL
    }
}

/** UI 层设置（持久化为 JSON；新增字段靠 kotlinx-serialization 默认值向后兼容） */
@Serializable
data class UiSettings(
    /** IP 来源：内置段（官方优选）/ 自定义目标列表（非标优选） */
    val ipSource: String = SOURCE_BUILTIN,
    /** 官方优选 IP 类型："4" / "6"（决定内置段用 v4 选择集还是官方 v6 全集） */
    val ipType: String = IP_V4,
    val selectedSegments: List<String> = BUILT_IN_IPS,
    val customIpText: String = "",
    /** 测速端口（引擎默认 443；非标模式兼作未指定端口条目的备用端口） */
    val tcpPort: Int = 443,
    /** 延迟测速方式：tcp / http（icmp 需 root，普通 App 不暴露） */
    val pingMode: String = PING_TCP,
    /** ping_mode=http 的探测地址；空 = 引擎默认 cp.cloudflare.com trace */
    val httpingUrl: String = "",
    /** httping 模式数据中心筛选（引擎 httping_colo，逗号分隔多个，如 NRT,HKG） */
    val targetDc: String = "",
    /** 是否做下载测速（false = 仅测延迟；对应 CFData「自动测速」开关） */
    val enableDownload: Boolean = true,
    /** 测速网址预设（auto/cloudflare/cm/mobile/custom） */
    val speedUrlPreset: String = SpeedUrlPresets.AUTO,
    val downloadUrl: String = DEFAULT_DOWNLOAD_URL,
    /** 单 IP 下载测速时长（秒） */
    val downloadTimeSecs: Int = 10,
    /** 需要达标的结果数量（CFData「测速结果数量」） */
    val testCount: Int = 10,
    /** 结果保留数量上限 */
    val printNum: Int = 10,
    /** 每 IP 延迟测速次数 */
    val pingTimes: Int = 4,
    /** 并发上限（CFData 默认 100） */
    val maxThreads: Int = 100,
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
    /** 主题模式：auto / light / dark（对齐 CFData 设置菜单颜色模式） */
    val themeMode: String = THEME_AUTO,
    /** Material You 动态取色（Android 12+ 跟随壁纸；关闭则用 Cloudflare 蓝品牌色） */
    val dynamicColors: Boolean = true,
    /** 启动时自动加载已保存配置 */
    val autoLoadConfig: Boolean = false,
) {
    /** 官方模式实际使用的内置段（v4 用选择集，v6 用官方全集） */
    val effectiveSegments: List<String>
        get() = if (ipType == IP_V6) BUILT_IN_IPS_V6 else selectedSegments

    /** 组装引擎配置载荷（未填字段交给引擎默认值/校验） */
    fun toCfstConfig(): CfstConfig = CfstConfig(
        ipText = if (ipSource == SOURCE_BUILTIN) {
            effectiveSegments.joinToString(",")
        } else {
            customIpText.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        },
        tcpPort = tcpPort,
        pingMode = pingMode,
        httpingUrl = httpingUrl.trim().ifBlank { null },
        httpingColo = targetDc.trim().ifBlank { null },
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
        ipSource == SOURCE_BUILTIN && ipType == IP_V4 && selectedSegments.isEmpty() ->
            "请至少选择一个内置 IP 段"
        ipSource == SOURCE_CUSTOM && customIpText.isBlank() ->
            "自定义目标为空（每行一个：CIDR、IP[:端口]、域名[:端口]）"
        enableDownload && downloadUrl.isBlank() -> "启用自动测速时必须填写测速网址"
        else -> null
    }

    companion object {
        const val SOURCE_BUILTIN = "builtin"
        const val SOURCE_CUSTOM = "custom"
        const val PING_TCP = "tcp"
        const val PING_HTTP = "http"
        const val IP_V4 = "4"
        const val IP_V6 = "6"
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        /** 引擎 README 验证过的官方大流量下载地址（200MB） */
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=200000000"
    }
}
