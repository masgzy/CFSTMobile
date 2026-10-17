package com.masgzy.cfstmobile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FFI 协议模型（v1）——与 engine/README.md 第 3/4/5 节的字段一一对应。
 *
 * 序列化约定：
 * - snake_case 字段名（引擎侧 serde 直接反序列化）；
 * - [ProtocolJson.encodeDefaults] = false：未修改的字段不下发，引擎按自身默认值处理，
 *   因此本文件各默认值必须与引擎默认值保持一致；
 * - [ProtocolJson.explicitNulls] = false：可空字段缺省即不发送。
 */
object ProtocolJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
}

/** 测速配置（cfst_engine_create 的 config_json 载荷） */
@Serializable
data class CfstConfig(
    @SerialName("ip_text") val ipText: String = "",
    @SerialName("ip_file") val ipFile: String = "",
    @SerialName("tcp_port") val tcpPort: Int = 443,
    /** "tcp" / "http" / "icmp"；null = 引擎默认（tcp） */
    @SerialName("ping_mode") val pingMode: String? = null,
    @SerialName("httping_url") val httpingUrl: String? = null,
    @SerialName("httping_code") val httpingCode: String = "",
    @SerialName("httping_colo") val httpingColo: String = "",
    /** 下载测速 URL（disable_download=false 时引擎要求非空） */
    @SerialName("url") val url: String = "",
    @SerialName("disable_download") val disableDownload: Boolean = false,
    @SerialName("download_time") val downloadTime: Int? = null,
    @SerialName("min_speed_mb_s") val minSpeedMbS: Float? = null,
    @SerialName("test_count") val testCount: Int? = null,
    @SerialName("target_num") val targetNum: Int? = null,
    @SerialName("print_num") val printNum: Int? = null,
    @SerialName("ping_times") val pingTimes: Int? = null,
    @SerialName("max_threads") val maxThreads: Int? = null,
    /** 测速并发：同时下载测速的 IP 数量（1=串行最准，>1 吞吐更高但单 IP 速度被摊薄；对齐 CFData nsbspeedtest） */
    @SerialName("speed_threads") val speedThreads: Int? = null,
    @SerialName("min_delay_ms") val minDelayMs: Int? = null,
    @SerialName("max_delay_ms") val maxDelayMs: Int? = null,
    @SerialName("max_loss_rate") val maxLossRate: Float? = null,
    @SerialName("global_timeout_secs") val globalTimeoutSecs: Int? = null,
    @SerialName("test_all_ipv4") val testAllIpv4: Boolean = false,
    @SerialName("show_port") val showPort: Boolean = false,
    @SerialName("bind_ip") val bindIp: String? = null,
    @SerialName("bind_interface") val bindInterface: String? = null,
    @SerialName("output_csv") val outputCsv: String? = null,
)

/** 延迟测试阶段进度 */
@Serializable
data class PingProgress(
    @SerialName("tested") val tested: Long = 0,
    @SerialName("success") val success: Long = 0,
    @SerialName("total") val total: Long = 0,
)

/** 下载测试阶段进度（speedMbps 为最近一个 500ms 采样窗口的实时速度） */
@Serializable
data class DownloadProgress(
    @SerialName("tested") val tested: Long = 0,
    @SerialName("qualified") val qualified: Long = 0,
    @SerialName("total") val total: Long = 0,
    @SerialName("speed_mbps") val speedMbps: Double = 0.0,
)

/** 进度（cfst_engine_poll 的返回载荷） */
@Serializable
data class CfstProgress(
    /** "idle" | "ping" | "download" | "done" | "error" */
    @SerialName("phase") val phase: String = "idle",
    @SerialName("stop_requested") val stopRequested: Boolean = false,
    @SerialName("ping") val ping: PingProgress? = null,
    @SerialName("download") val download: DownloadProgress? = null,
    /** 仅 phase=error 时携带错误原因 */
    @SerialName("message") val message: String? = null,
)

/** 单个优选结果（引擎已按综合评分排序） */
@Serializable
data class CfstResultItem(
    @SerialName("ip") val ip: String,
    @SerialName("port") val port: Int,
    @SerialName("sent") val sent: Int = 0,
    @SerialName("received") val received: Int = 0,
    @SerialName("loss_rate") val lossRate: Double = 0.0,
    @SerialName("delay_ms") val delayMs: Double = 0.0,
    /** 未做下载测速时为 null */
    @SerialName("download_speed_mbps") val downloadSpeedMbps: Double? = null,
    /** 数据中心识别（httping 模式下引擎返回，可为 null） */
    @SerialName("colo") val colo: String? = null,
    /** 非标优选：域名条目解析展开时保留的原始输入（如 vip.example.com）；纯 IP 条目为 null */
    @SerialName("origin") val origin: String? = null,
) {
    /** 复制到剪贴板用（IP:端口，代理软件可直接粘贴） */
    fun address(): String = "$ip:$port"
}

/** 结果集（cfst_engine_take_result 的返回载荷，一次性） */
@Serializable
data class CfstResult(
    @SerialName("count") val count: Int = 0,
    @SerialName("items") val items: List<CfstResultItem> = emptyList(),
)

/** 精简配置（cfst_compact_create 的 config_json 载荷，对齐 engine compact.rs CompactConfig） */
@Serializable
data class CompactConfig(
    @SerialName("ip_text") val ipText: String = "",
    @SerialName("threads") val threads: Int? = null,
    @SerialName("connect_timeout_ms") val connectTimeoutMs: Long? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("max_empty_rounds") val maxEmptyRounds: Int? = null,
)

/** 精简进度（cfst_compact_poll 的返回载荷，对齐 CFData compact_ipv4_progress） */
@Serializable
data class CompactProgress(
    /** "idle" / "running" / "done" / "error" */
    @SerialName("phase") val phase: String = "idle",
    @SerialName("stop_requested") val stopRequested: Boolean = false,
    @SerialName("pass") val pass: Int = 0,
    @SerialName("current") val current: Int = 0,
    @SerialName("total") val total: Int = 0,
    @SerialName("found") val found: Int = 0,
    @SerialName("percent") val percent: String = "0.00",
    @SerialName("status") val status: String = "",
    @SerialName("logs") val logs: List<String> = emptyList(),
    @SerialName("message") val message: String? = null,
)

/** 精简结果（cfst_compact_take_result 的返回载荷，一次性） */
@Serializable
data class CompactResult(
    @SerialName("count") val count: Int = 0,
    @SerialName("content") val content: String = "",
    @SerialName("file") val file: String = "ips-v4.txt",
)

/** 排序模式（引擎返回的是综合评分序；展示层可按需重排） */
object SortMode {
    const val SCORE = "score"   // 引擎综合排序（速度/延迟/丢包 z-score）
    const val DELAY = "delay"   // 延迟升序
    const val SPEED = "speed"   // 下载速度降序
}

/** 按排序模式重排结果（不改写引擎原始顺序的副本） */
fun List<CfstResultItem>.sortedByMode(mode: String): List<CfstResultItem> = when (mode) {
    SortMode.DELAY -> sortedBy { it.delayMs }
    SortMode.SPEED -> sortedByDescending { it.downloadSpeedMbps ?: 0.0 }
    else -> this
}
