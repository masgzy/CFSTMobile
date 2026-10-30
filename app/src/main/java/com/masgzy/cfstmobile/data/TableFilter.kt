package com.masgzy.cfstmobile.data

import com.masgzy.cfstmobile.model.CfstResultItem

/**
 * 结果区「筛选排序」状态（对齐 CFData-WEB 1.8.4：结果区筛选与排序合并为筛选排序弹窗）。
 *
 * 语义对齐 CFData officialTest*Filter / nsbTable*Filter：
 * - 筛选作用于表格显示与计数徽章；
 * - 排序就地重排结果集（与复制/导出共享行序，对齐 CFData applySortModal 的就地重排）；
 * - 弹窗打开时回填当前值（对齐 publishSortFilterControls）。
 */
data class TableFilterState(
    /** all=展示全部结果 / qualified=仅展示合格 / unqualified=仅展示不合格 */
    val qualified: String = QUALIFIED_ALL,
    /** 延迟低于（毫秒），0=不限制；判定为严格小于（对齐 CFData latencyLossMatch） */
    val maxLatencyMs: Double = 0.0,
    /** 丢包低于（%），100=不限制；判定为严格小于 */
    val maxLossPercent: Double = 100.0,
) {
    val isDefault: Boolean get() = this == TableFilterState()

    companion object {
        const val QUALIFIED_ALL = "all"
        const val QUALIFIED_ONLY = "qualified"
        const val QUALIFIED_EXCLUDE = "unqualified"
    }
}

/** 排序优先级（三键依次比较；默认 丢包率→网速→延迟，对齐 CFData 排序下拉默认值） */
data class SortPriorities(
    val first: String = KEY_LOSS,
    val second: String = KEY_SPEED,
    val third: String = KEY_DELAY,
) {
    /** 优先级键序列（依次比较，与前键相同则继续后键） */
    val keys: List<String> get() = listOf(first, second, third)

    companion object {
        const val KEY_LOSS = "loss"
        const val KEY_DELAY = "delay"
        const val KEY_SPEED = "speed"
    }
}

/**
 * 表格行筛选判定（对齐 CFData matchesOfficialQualifiedFilter + latencyLossMatch）：
 * 合格 = 已测速且速度 ≥ 阈值（未测速视为不合格）；延迟/丢包为严格小于，
 * 0ms / 100% 表示不限制。[maxLossPercent] 调用前应做 ≤0→0.01 的钳制（对齐 applySortModalFilters）。
 */
fun matchesTableFilter(
    lossPercent: Double,
    latencyMs: Double,
    speedMbps: Double?,
    qualifiedThresholdMbps: Double,
    filter: TableFilterState,
): Boolean {
    if (filter.qualified != TableFilterState.QUALIFIED_ALL) {
        val isQualified = speedMbps?.let { it >= qualifiedThresholdMbps } == true
        val pass = if (filter.qualified == TableFilterState.QUALIFIED_ONLY) isQualified else !isQualified
        if (!pass) return false
    }
    if (filter.maxLatencyMs > 0 && latencyMs >= filter.maxLatencyMs) return false
    if (filter.maxLossPercent < 100 && lossPercent >= filter.maxLossPercent) return false
    return true
}

/** 网速比较（对齐 CFData parseSortSpeed 排序口径）：有速度在前按值降序；未测速（null）排在其后 */
fun compareSpeed(a: Double?, b: Double?): Int {
    if (a == null && b == null) return 0
    if (a == null) return 1
    if (b == null) return -1
    return b.compareTo(a)
}

/**
 * 非标扫描结果按优先级链比较（对齐 CFData applySortModal 就地排序）：
 * 丢包率升序 / 延迟升序 / 网速降序（未测速排后）。
 */
fun compareNsbByPriorities(a: CfstResultItem, b: CfstResultItem, keys: List<String>): Int {
    for (key in keys) {
        val c = when (key) {
            SortPriorities.KEY_LOSS -> a.lossRate.compareTo(b.lossRate)
            SortPriorities.KEY_DELAY -> a.delayMs.compareTo(b.delayMs)
            else -> compareSpeed(a.downloadSpeedMbps, b.downloadSpeedMbps)
        }
        if (c != 0) return c
    }
    return 0
}

/**
 * 详细测试结果按优先级链比较：
 * 丢包率升序 / 延迟按 [min,max,avg] 逐位升序（对齐 CFData official 排序键）/
 * 网速降序（未测速排后）。
 */
fun compareDcByPriorities(a: DcTestItem, b: DcTestItem, keys: List<String>): Int {
    for (key in keys) {
        val c = when (key) {
            SortPriorities.KEY_LOSS -> a.lossRate.compareTo(b.lossRate)
            SortPriorities.KEY_DELAY -> {
                (a.minMs.compareTo(b.minMs)).takeIf { it != 0 }
                    ?: (a.maxMs.compareTo(b.maxMs)).takeIf { it != 0 }
                    ?: a.avgMs.compareTo(b.avgMs)
            }
            else -> compareSpeed(a.speedMbps, b.speedMbps)
        }
        if (c != 0) return c
    }
    return 0
}
