package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.DcLocation
import com.masgzy.cfstmobile.data.DcLocations
import com.masgzy.cfstmobile.data.DcTestItem
import com.masgzy.cfstmobile.data.aggregateDcList
import com.masgzy.cfstmobile.data.sortDcResults
import com.masgzy.cfstmobile.model.CfstResultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DcAggregationTest {

    private fun item(ip: String, colo: String?, delay: Double) = CfstResultItem(
        ip = ip,
        port = 443,
        lossRate = 0.0,
        delayMs = delay,
    )

    // ------------------------------------------------------ 聚合

    @Test
    fun `aggregate groups by colo with count and min latency`() {
        val items = listOf(
            item("1.1.1.1", "HKG", 120.0),
            item("1.1.1.2", "HKG", 80.0),
            item("1.1.1.3", "SJC", 150.0),
        )
        val dcList = aggregateDcList(items, emptyMap())
        assertEquals(2, dcList.size)
        // 按最低延迟升序：HKG(80) 在前
        val hkg = dcList.first()
        assertEquals("HKG", hkg.colo)
        assertEquals(2, hkg.ipCount)
        assertEquals(80, hkg.minLatencyMs)
        val sjc = dcList.last()
        assertEquals("SJC", sjc.colo)
        assertEquals(1, sjc.ipCount)
        assertEquals(150, sjc.minLatencyMs)
    }

    @Test
    fun `aggregate skips entries without colo`() {
        val items = listOf(
            item("1.1.1.1", null, 50.0),
            item("1.1.1.2", "HKG", 100.0),
            item("1.1.1.3", "  ", 60.0),
        )
        val dcList = aggregateDcList(items, emptyMap())
        assertEquals(1, dcList.size)
        assertEquals("HKG", dcList[0].colo)
        assertEquals(1, dcList[0].ipCount)
    }

    @Test
    fun `aggregate fills location from map case insensitive`() {
        val items = listOf(item("1.1.1.1", "hkg", 90.0))
        val locations = mapOf("HKG" to DcLocation(iata = "HKG", cca2 = "HK", city = "Hong Kong"))
        val dcList = aggregateDcList(items, locations)
        assertEquals("hkg", dcList[0].colo)
        assertEquals("HK", dcList[0].country)
        assertEquals("Hong Kong", dcList[0].city)
    }

    @Test
    fun `aggregate empty input yields empty list`() {
        assertTrue(aggregateDcList(emptyList(), emptyMap()).isEmpty())
    }

    // ------------------------------------------------------ 排序

    @Test
    fun `sort by loss then min then max then avg then ip`() {
        val list = mutableListOf(
            DcTestItem(ip = "2.2.2.2", port = 443, colo = "HKG", minMs = 50, avgMs = 60.0, maxMs = 70, lossRate = 0.0),
            DcTestItem(ip = "1.1.1.1", port = 443, colo = "HKG", minMs = 50, avgMs = 60.0, maxMs = 70, lossRate = 0.1),
            DcTestItem(ip = "3.3.3.3", port = 443, colo = "HKG", minMs = 60, avgMs = 60.0, maxMs = 70, lossRate = 0.0),
            DcTestItem(ip = "4.4.4.4", port = 443, colo = "HKG", minMs = 50, avgMs = 60.0, maxMs = 80, lossRate = 0.0),
            DcTestItem(ip = "5.5.5.5", port = 443, colo = "HKG", minMs = 50, avgMs = 65.0, maxMs = 70, lossRate = 0.0),
            DcTestItem(ip = "0.0.0.9", port = 443, colo = "HKG", minMs = 50, avgMs = 60.0, maxMs = 70, lossRate = 0.0),
        )
        sortDcResults(list)
        // loss=0 的里面：0.0.0.9（min 同）→ max 同 → avg 同 → ip 字符串 "0.0.0.9" < "2.2.2.2" < "3.3.3.3"(min 60 其实更大)
        // 精确顺序：0.0.0.9(min50,max70,avg60) → 2.2.2.2(min50,max70,avg60,ip>0.0.0.9) → 5.5.5.5(avg65) → 4.4.4.4(max80) → 3.3.3.3(min60) → 1.1.1.1(loss0.1)
        assertEquals("0.0.0.9", list[0].ip)
        assertEquals("2.2.2.2", list[1].ip)
        assertEquals("5.5.5.5", list[2].ip)
        assertEquals("4.4.4.4", list[3].ip)
        assertEquals("3.3.3.3", list[4].ip)
        assertEquals("1.1.1.1", list[5].ip)
    }

    // ------------------------------------------------------ locations 解析

    @Test
    fun `parse locations json indexes by iata`() {
        val json = """
            [
              {"iata":"HKG","lat":22.3,"lon":114.2,"cca2":"HK","region":"Asia","city":"Hong Kong","country":"Hong Kong"},
              {"iata":"sjc","cca2":"US","city":"San Jose"},
              {"unknown":true}
            ]
        """.trimIndent()
        val map = DcLocations.parse(json)
        assertEquals(2, map.size)
        assertEquals("HK", map["HKG"]?.cca2)
        assertEquals("San Jose", map["SJC"]?.city)
    }

    @Test
    fun `parse invalid json returns empty map`() {
        assertTrue(DcLocations.parse("not json").isEmpty())
        assertTrue(DcLocations.parse("").isEmpty())
    }
}
