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
        else -> UiSettings.DEFAULT_DOWNLOAD_URL
    }
}

/**
 * 主题配色种子（ImageToolbox ColorTuple 语义）：主色必填，
 * 其余五个角色为空时由调色引擎按主色 HCT 规则派生。
 * 颜色用 Long ARGB 存储（JSON 序列化稳定）。
 */
@Serializable
data class SeedColors(
    val primary: Long = DEFAULT_SEED,
    val secondary: Long? = null,
    val tertiary: Long? = null,
    val surface: Long? = null,
    val neutralVariant: Long? = null,
    val error: Long? = null,
) {
    companion object {
        /** 默认种子：Cloudflare 品牌蓝（原品牌回退方案主色） */
        const val DEFAULT_SEED = 0xFF0B57D0
    }
}

/**
 * UI 层设置（持久化为 JSON；新增字段靠 kotlinx-serialization 默认值向后兼容）。
 *
 * 参数按官方/非标分成两组（对齐 CFData-WEB config.official / config.nsb 独立持久化语义）：
 * 两组各自的 并发/合格延迟/合格速度/结果数量/自动测速/数据中心 互不影响；
 * 扫描方式、测速网址、高级参数为共用项。
 */
@Serializable
data class UiSettings(
    /** IP 来源：内置段（官方优选）/ 自定义目标（非标优选） */
    val ipSource: String = SOURCE_BUILTIN,
    /** 官方优选 IP 类型："4" / "6"（决定地址库用 v4 还是 v6） */
    val ipType: String = IP_V4,

    // ---------------- 非标输入源（文件 / URL 二选一，手动列表为 App 补充） ----------------
    /** 手动目标列表（每行一个：CIDR / IP[:端口] / 域名[:端口]） */
    val customIpText: String = "",
    /** 网络 URL（启动时拉取 IP 列表，对齐 CFData nsbSourceUrl） */
    val nsbSourceUrl: String = "",
    /** 已选本地文件的展示名（内容读入 customIpText 追加区） */
    val nsbFileName: String = "",
    /** 本地文件内容（选择文件后写入，与手动列表合并下发） */
    val nsbFileText: String = "",

    // ---------------- 官方参数组（对齐 CFData config.official 默认值） ----------------
    val officialPort: Int = 443,
    val officialThreads: Int = 100,
    /** 扫描合格延迟（官方默认 500ms） */
    val officialDelayMs: Int = 500,
    val officialSpeedMin: Double = 0.1,
    /** 测速结果数量，0 = 关闭测速 */
    val officialSpeedLimit: Int = 5,
    /** 自动测速：扫描完成后自动选数据中心做二次详细测试 */
    val officialAutoSpeed: Boolean = false,
    /** 自动测速目标数据中心（留空选最低延迟 DC；仅自动测速开启时可用） */
    val officialTargetDc: String = "",
    /** 自动测速完成后自动上传结果账户（off / github-txt / github-csv，对齐 CFData officialAutoUploadAccount） */
    val officialAutoUpload: String = UPLOAD_OFF,
    /** 官方扫描使用本地精简地址库（filesDir/ips-v4.txt，由「精简地址库」生成） */
    val officialUseCompactLib: Boolean = false,

    // ---------------- 非标参数组（对齐 CFData config.nsb 默认值） ----------------
    /** 备用端口：条目未带端口时使用（TLS 关闭默认 80） */
    val nsbFallbackPort: Int = 443,
    val nsbThreads: Int = 100,
    /** 扫描合格延迟（非标默认 0 = 不过滤） */
    val nsbDelayMs: Int = 0,
    /** 扫描合格数量（延迟测试通过的结果上限） */
    val nsbResultLimit: Int = 1000,
    val nsbSpeedMin: Double = 0.1,
    /** 测速结果数量，0 = 关闭测速 */
    val nsbSpeedLimit: Int = 5,
    /** 自动测速（非标默认关闭） */
    val nsbAutoSpeed: Boolean = false,
    /** 数据中心筛选（留空不限制，如 NRT） */
    val nsbTargetDc: String = "",
    /** TLS 模式（关闭时备用端口默认 80） */
    val nsbTls: Boolean = true,
    /** 输出文件名（CSV 导出默认文件名） */
    val nsbOutFile: String = "ip.csv",
    /** 测速并发：同时下载测速的 IP 数量，0 = 不测速；1 结果最准（对齐 CFData nsbSpeedTest） */
    val nsbSpeedThreads: Int = 4,
    /** 自动测速完成后自动上传结果账户（off / github-txt / github-csv，对齐 CFData nsbAutoUploadAccount） */
    val nsbAutoUpload: String = UPLOAD_OFF,
    /** 非标结果表格精简/完整列切换（对齐 CFData nsbCompactView，默认精简） */
    val nsbCompactView: Boolean = true,

    // ---------------- 共用参数 ----------------
    /** 延迟测速方式：tcp / http（icmp 需 root，普通 App 不暴露） */
    val pingMode: String = PING_TCP,
    /** ping_mode=http 的探测地址；空 = 引擎默认 */
    val httpingUrl: String = "",
    /** 测速网址预设（auto/cloudflare/cm/mobile/custom） */
    val speedUrlPreset: String = SpeedUrlPresets.AUTO,
    val downloadUrl: String = DEFAULT_DOWNLOAD_URL,
    /** 单 IP 下载测速时长（秒） */
    val downloadTimeSecs: Int = 10,
    /** 结果保留数量上限 */
    val printNum: Int = 20,
    /** 每 IP 延迟测速次数 */
    val pingTimes: Int = 4,
    /** 丢包率上限（0.0 ~ 1.0） */
    val maxLossRate: Double = 1.0,
    /** 全局超时（秒），0 = 不限 */
    val globalTimeoutSecs: Int = 0,
    /** 测试 CIDR 全部 IPv4（否则按前缀采样） */
    val testAllIpv4: Boolean = false,
    /** 结果排序：score / delay / speed */
    val sortBy: String = SortMode.SCORE,
    /** 主题模式：auto / light / dark（对齐 CFData 设置菜单颜色模式） */
    val themeMode: String = THEME_AUTO,
    /** Material You 动态取色（Android 12+ 跟随壁纸；关闭则用调色板种子色） */
    val dynamicColors: Boolean = true,
    /** AMOLED 纯黑深色（浅色模式下也可开启，实际作用于夜间模式） */
    val amoled: Boolean = false,
    /** 主题配色种子（动态取色关闭时生效，ImageToolbox 式调色板） */
    val colorTuple: SeedColors = SeedColors(),
    /** 调色板风格（PaletteStyle 枚举名，默认 TonalSpot） */
    val paletteStyle: String = STYLE_DEFAULT,
    /** 对比度 -1..1（DynamicScheme contrastLevel） */
    val themeContrast: Float = 0f,
    /** 启动时自动加载已保存配置 */
    val autoLoadConfig: Boolean = false,
) {
    val official: Boolean get() = ipSource == SOURCE_BUILTIN

    /** 官方模式离线回退段（在线地址库不可用时的内置官方全集） */
    val fallbackSegments: List<String>
        get() = if (ipType == IP_V6) BUILT_IN_IPS_V6 else BUILT_IN_IPS

    /** 当前模式是否做下载测速（测速结果数量 > 0 且自动测速开关放行；非标还要求测速并发 > 0，
     *  对齐 CFData「需测速并发与测速结果数量均大于 0」） */
    val downloadEnabled: Boolean
        get() = if (official) officialSpeedLimit > 0 else nsbAutoSpeed && nsbSpeedLimit > 0 && nsbSpeedThreads > 0

    /** 当前模式的测速并发（官方串行 1 = 上游默认最准；非标用用户设置值，0 视为 1 由 downloadEnabled 门控） */
    val speedThreads: Int
        get() = if (official) 1 else nsbSpeedThreads.coerceIn(1, 64)

    /** 当前模式的测速结果数量 */
    val speedLimit: Int get() = if (official) officialSpeedLimit else nsbSpeedLimit

    /** 当前模式的合格速度下限 */
    val speedMin: Double get() = if (official) officialSpeedMin else nsbSpeedMin

    /** 当前模式的并发数量 */
    val threads: Int get() = if (official) officialThreads else nsbThreads

    /** 当前模式的扫描合格延迟 */
    val delayMs: Int get() = if (official) officialDelayMs else nsbDelayMs

    /** 非标目标列表（文件内容 + 手动列表合并，跳过空行与注释行） */
    val nsbTargets: List<String>
        get() = (nsbFileText.lines() + customIpText.lines())
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    /** 当前模式的引擎端口（官方=测试端口，非标=备用端口） */
    val enginePort: Int get() = if (official) officialPort else nsbFallbackPort

    /**
     * 组装引擎配置载荷（未填字段交给引擎默认值/校验）。
     * [extraTargets]：非标模式从网络 URL 实时拉取的目标行（与文件/手动列表合并）。
     * 官方模式的 IP 列表由 OfficialIpList 展开后走 ipFile（对齐 CFData 地址库），
     * 本方法不再携带官方 CIDR 段。
     */
    fun toCfstConfig(extraTargets: List<String> = emptyList()): CfstConfig = CfstConfig(
        ipText = if (official) "" else (nsbTargets + extraTargets).joinToString(","),
        tcpPort = enginePort,
        pingMode = pingMode,
        httpingUrl = httpingUrl.trim().ifBlank { null },
        httpingColo = if (official) "" else nsbTargetDc.trim(),
        url = if (downloadEnabled) SpeedUrlPresets.resolve(speedUrlPreset, downloadUrl) else "",
        disableDownload = !downloadEnabled,
        downloadTime = downloadTimeSecs,
        testCount = speedLimit,
        targetNum = if (official) null else nsbResultLimit,
        printNum = printNum,
        pingTimes = pingTimes,
        maxThreads = threads,
        speedThreads = speedThreads,
        maxDelayMs = delayMs,
        maxLossRate = maxLossRate.toFloat(),
        minSpeedMbS = speedMin.toFloat(),
        globalTimeoutSecs = if (globalTimeoutSecs > 0) globalTimeoutSecs else null,
        testAllIpv4 = testAllIpv4,
    )

    /** 启动前的轻校验（引擎侧还有完整校验与钳制） */
    fun validate(): String? = when {
        enginePort !in 1..65535 ->
            if (official) "测试端口必须在 1~65535 之间" else "备用端口必须在 1~65535 之间"
        !official && nsbSourceUrl.isNotBlank() && nsbFileText.isNotBlank() ->
            "IP 文件与网络 URL 请二选一（对齐 CFData 输入约束）"
        !official && nsbSourceUrl.isBlank() && nsbFileText.isBlank() && customIpText.isBlank() ->
            "请提供扫描目标：选择文件 / 填写网络 URL / 手动输入列表"
        downloadEnabled && SpeedUrlPresets.resolve(speedUrlPreset, downloadUrl).isBlank() ->
            "启用自动测速时必须填写测速网址"
        else -> null
    }

    /**
     * 清洗非标手动列表：若内容全部是 Cloudflare 官方段（v4/v6 内置集合），
     * 视为历史遗留的误填充数据，返回清空后的副本（官方段由官方模式内置提供，
     * 非标手动列表语义是"用户自有目标"，默认应为空）。
     * 混有非官方行的内容视为用户真实数据，原样保留。
     */
    fun sanitized(): UiSettings {
        val lines = customIpText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) return this
        val officialSet = (BUILT_IN_IPS + BUILT_IN_IPS_V6).toSet()
        val allOfficial = lines.all { it in officialSet }
        return if (allOfficial) copy(customIpText = "") else this
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

        /** 调色板风格默认值（PaletteStyle.TonalSpot 枚举名） */
        const val STYLE_DEFAULT = "TonalSpot"

        /** 自动上传账户：不上传 */
        const val UPLOAD_OFF = "off"
        /** 自动上传账户：GitHub 账户，TXT 格式 */
        const val UPLOAD_GITHUB_TXT = "github-txt"
        /** 自动上传账户：GitHub 账户，CSV 格式 */
        const val UPLOAD_GITHUB_CSV = "github-csv"

        /** 引擎 README 验证过的官方大流量下载地址（200MB） */
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=200000000"
    }
}
