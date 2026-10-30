package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.DcTestItem
import com.masgzy.cfstmobile.data.SortPriorities
import com.masgzy.cfstmobile.data.TableFilterState
import com.masgzy.cfstmobile.data.compareDcByPriorities
import com.masgzy.cfstmobile.data.compareNsbByPriorities
import com.masgzy.cfstmobile.data.compareSpeed
import com.masgzy.cfstmobile.data.matchesTableFilter
import com.masgzy.cfstmobile.model.CfstResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果区「筛选排序」纯函数测试（对齐 CFData-WEB 1.8.4 openSortModal 语义）：
 * 合格判定、延迟/丢包严格小于筛选、排序优先级链（丢包↑/延迟↑/网速↓、未测速排后）。
 */
class TableFilterTest {

    private fun item(
        loss: Double = 0.0,
        delay: Double = 100.0,
        speed: Double? = null,
        colo: String? = "LAX",
    ) = CfstResultItem(
        ip = "104.16.1.1", port = 443, sent = 4, received = 4,
        lossRate = loss, delayMs = delay, downloadSpeedMbps = speed, colo = colo,
    )

    private fun dcItem(
        loss: Double = 0.0,
        min: Long = 50,
        max: Long = 90,
        avg: Double = 70.0,
        speed: Double? = null,
    ) = DcTestItem(
        ip = "104.16.1.1", port = 443, colo = "LAX",
        minMs = min, avgMs = avg, maxMs = max, lossRate = loss, speedMbps = speed,
    )

    // ---------------- matchesTableFilter ----------------

    @Test
    fun `default filter passes everything`() {
        val f = TableFilterState()
        assertTrue(matchesTableFilter(100.0, 9999.0, null, 0.1, f))
    }

    @Test
    fun `qualified requires speed at or above threshold`() {
        val f = TableFilterState(qualified = TableFilterState.QUALIFIED_ONLY)
        assertTrue(matchesTableFilter(0.0, 100.0, 0.2, 0.1, f))   // 速度≥阈值
        assertFalse(matchesTableFilter(0.0, 100.0, 0.05, 0.1, f)) // 低于阈值
        assertFalse(matchesTableFilter(0.0, 100.0, null, 0.1, f)) // 未测速=不合格
    }

    @Test
    fun `unqualified keeps untested and below threshold`() {
        val f = TableFilterState(qualified = TableFilterState.QUALIFIED_EXCLUDE)
        assertTrue(matchesTableFilter(0.0, 100.0, null, 0.1, f))
        assertTrue(matchesTableFilter(0.0, 100.0, 0.05, 0.1, f))
        assertFalse(matchesTableFilter(0.0, 100.0, 0.2, 0.1, f))
    }

    @Test
    fun `latency and loss are strict less-than with 0 and 100 unlimited`() {
        val f = TableFilterState(maxLatencyMs = 200.0, maxLossPercent = 5.0)
        assertTrue(matchesTableFilter(4.9, 199.9, null, 0.0, f))
        assertFalse(matchesTableFilter(4.9, 200.0, null, 0.0, f))  // 等于阈值 → 排除（严格小于）
        assertFalse(matchesTableFilter(5.0, 100.0, null, 0.0, f))  // 丢包等于阈值 → 排除
        // 0ms / 100% = 不限制
        val none = TableFilterState()
        assertTrue(matchesTableFilter(100.0, 99999.0, null, 0.0, none))
    }

    // ---------------- compareSpeed ----------------

    @Test
    fun `speed sorts descending with untested last`() {
        assertEquals(-1, compareSpeed(10.0, 5.0))   // 高速在前
        assertEquals(1, compareSpeed(5.0, 10.0))
        assertEquals(-1, compareSpeed(1.0, null))   // 有速度在未测速之前
        assertEquals(1, compareSpeed(null, 1.0))
        assertEquals(0, compareSpeed(null, null))
    }

    // ---------------- compareNsbByPriorities ----------------

    @Test
    fun `nsb default priorities loss then speed then delay`() {
        val keys = SortPriorities().keys // loss → speed → delay
        val lowLossNoSpeed = item(loss = 0.0, delay = 500.0, speed = null)
        val highLossFast = item(loss = 0.1, delay = 10.0, speed = 20.0)
        assertTrue(compareNsbByPriorities(lowLossNoSpeed, highLossFast, keys) < 0)

        val sameLossA = item(loss = 0.0, delay = 300.0, speed = 5.0)
        val sameLossB = item(loss = 0.0, delay = 10.0, speed = 10.0)
        assertTrue(compareNsbByPriorities(sameLossA, sameLossB, keys) > 0) // 速度降序：B(10) 排前 → compare(A,B)>0

        val bothNoSpeedA = item(loss = 0.0, delay = 50.0, speed = null)
        val bothNoSpeedB = item(loss = 0.0, delay = 30.0, speed = null)
        assertTrue(compareNsbByPriorities(bothNoSpeedA, bothNoSpeedB, keys) > 0) // 延迟升序兜底
    }

    @Test
    fun `nsb custom priorities delay first`() {
        val keys = listOf(SortPriorities.KEY_DELAY, SortPriorities.KEY_LOSS)
        val slowLowLoss = item(loss = 0.0, delay = 900.0)
        val fastHighLoss = item(loss = 0.5, delay = 20.0)
        assertTrue(compareNsbByPriorities(fastHighLoss, slowLowLoss, keys) < 0)
    }

    // ---------------- compareDcByPriorities ----------------

    @Test
    fun `dc delay key compares min then max then avg`() {
        val keys = listOf(SortPriorities.KEY_DELAY)
        assertTrue(compareDcByPriorities(dcItem(min = 40), dcItem(min = 50), keys) < 0)
        // min 相同比 max
        assertTrue(compareDcByPriorities(dcItem(min = 50, max = 80), dcItem(min = 50, max = 90), keys) < 0)
        // min/max 相同比 avg
        assertTrue(compareDcByPriorities(dcItem(avg = 60.0), dcItem(avg = 70.0), keys) < 0)
        assertEquals(0, compareDcByPriorities(dcItem(), dcItem(), keys))
    }

    @Test
    fun `dc default priorities loss first and untested speed last`() {
        val keys = SortPriorities().keys // loss → speed → delay
        val zeroLossUntested = dcItem(loss = 0.0, speed = null)
        val someLossFast = dcItem(loss = 0.2, speed = 8.0)
        assertTrue(compareDcByPriorities(zeroLossUntested, someLossFast, keys) < 0)

        val bothZeroLossA = dcItem(loss = 0.0, speed = 3.0, min = 60)
        val bothZeroLossB = dcItem(loss = 0.0, speed = 9.0, min = 80)
        assertTrue(compareDcByPriorities(bothZeroLossB, bothZeroLossA, keys) < 0) // 速度降序
    }
}
