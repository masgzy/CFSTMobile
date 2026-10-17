package com.masgzy.cfstmobile.data

import com.masgzy.cfstmobile.model.CfstResultItem
import java.util.Locale

/** 聚合后的数据中心（对齐 CFData DataCenterInfo：IP 数 + 最低初测延迟，按最低延迟升序） */
data class DcInfo(
    val colo: String,
    /** 国家代码（cca2），位置表缺失时为空串 */
    val country: String = "",
    /** 城市，位置表缺失时为空串 */
    val city: String = "",
    val ipCount: Int,
    val minLatencyMs: Int,
)

/** 二次详细测试单条结果（对齐 CFData TestResult：min/max/avg/loss + 数据中心归属） */
data class DcTestItem(
    val ip: String,
    val port: Int,
    val colo: String,
    val country: String = "",
    val city: String = "",
    val minMs: Long = 0,
    val avgMs: Double = 0.0,
    val maxMs: Long = 0,
    /** 0.0 ~ 1.0 */
    val lossRate: Double = 0.0,
    val attempts: Int = 0,
) {
    fun address(): String = "$ip:$port"
}

/** 数据中心二次测试流程阶段 */
enum class DcStep {
    /** 未开始 */
    IDLE,

    /** 正在逐 IP trace 探测数据中心归属（仅结果缺 colo 时） */
    PROBING_COLO,

    /** 数据中心列表就绪，等待选择 */
    DC_LIST,

    /** 正在对选中数据中心做二次详细测试 */
    TESTING,

    /** 测试完成（含取消后呈现的部分结果） */
    DONE,

    /** 出错 */
    ERROR,
}

/** 数据中心二次测试 UI 状态（独立于主测速状态流） */
data class DcTestUiState(
    val step: DcStep = DcStep.IDLE,
    /** colo 探测进度（已完成 / 总数） */
    val probeDone: Int = 0,
    val probeTotal: Int = 0,
    val dcList: List<DcInfo> = emptyList(),
    /** 正在/刚刚详细测试的数据中心；null = 全部 */
    val selectedDc: String? = null,
    /** 详细测试进度（已完成 / 总数） */
    val testDone: Int = 0,
    val testTotal: Int = 0,
    val results: List<DcTestItem> = emptyList(),
    val message: String? = null,
) {
    /** 探测/测试进行中（关闭对话框前需要请求取消） */
    val busy: Boolean get() = step == DcStep.PROBING_COLO || step == DcStep.TESTING
}

/**
 * 按数据中心聚合测速结果（对齐 CFData buildDcList）：
 * 仅统计携带 colo 的条目，每 DC 记录 IP 数与最低初测延迟，按最低延迟升序。
 */
fun aggregateDcList(items: List<CfstResultItem>, locations: Map<String, DcLocation>): List<DcInfo> =
    items.asSequence()
        .map { it.colo?.trim().orEmpty() }
        .filter { it.isNotEmpty() }
        .toSet()
        .map { colo ->
            val group = items.filter { it.colo?.trim().equals(colo, ignoreCase = true) }
            val loc = locations[colo.uppercase(Locale.US)]
            DcInfo(
                colo = colo,
                country = loc?.cca2.orEmpty(),
                city = loc?.city.orEmpty(),
                ipCount = group.size,
                minLatencyMs = group.minOf { it.delayMs }.toInt(),
            )
        }
        .sortedBy { it.minLatencyMs }

/**
 * 详细测试结果排序（对齐 CFData sortOfficialTestResults）：
 * 丢包率 → 最低延迟 → 最高延迟 → 平均延迟 → IP 字符串。
 */
fun sortDcResults(list: MutableList<DcTestItem>) {
    list.sortWith(
        compareBy(
            { it.lossRate },
            { it.minMs },
            { it.maxMs },
            { it.avgMs },
            { it.ip },
        ),
    )
}
