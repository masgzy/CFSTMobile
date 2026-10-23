package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.OfficialIpList
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 官方模式地址库展开随机（对齐 CFData expandCIDRTo24s + randomIPFromCIDR） */
class OfficialIpListTest {

    @Test
    fun `subnet at or below target prefix returns as-is`() {
        assertEquals(listOf("10.0.1.0/24"), OfficialIpList.expandToSubnets("10.0.1.0/24", isV6 = false))
        assertEquals(listOf("1.2.3.4/32"), OfficialIpList.expandToSubnets("1.2.3.4/32", isV6 = false))
        assertEquals(listOf("2400:cb00::/48"), OfficialIpList.expandToSubnets("2400:cb00::/48", isV6 = true))
    }

    @Test
    fun `v4 cidr expands one subnet per slash24`() {
        // /16 → 256 个 /24
        val subnets = OfficialIpList.expandToSubnets("10.1.0.0/16", isV6 = false)!!
        assertEquals(256, subnets.size)
        assertEquals("10.1.0.0/24", subnets.first())
        assertEquals("10.1.255.0/24", subnets.last())
    }

    @Test
    fun `oversized cidr is capped at 65536 subnets`() {
        // /8 → shift 钳 16 → 前 65536 个 /24（对齐 CFData 截断而非跳过）
        val subnets = OfficialIpList.expandToSubnets("4.0.0.0/8", isV6 = false)!!
        assertEquals(65536, subnets.size)
        assertEquals("4.0.0.0/24", subnets.first())
        assertEquals("4.255.255.0/24", subnets.last())
    }

    @Test
    fun `v6 cidr expands to slash48 subnets`() {
        val subnets = OfficialIpList.expandToSubnets("2400:cb00::/32", isV6 = true)!!
        assertEquals(65536, subnets.size)
        assertTrue(subnets.first().startsWith("2400:cb00::/48"))
        assertTrue(subnets.last().startsWith("2400:cb00:ffff::/48"))
    }

    @Test
    fun `invalid cidr returns null`() {
        assertNull(OfficialIpList.expandToSubnets("not-an-ip", isV6 = false))
        assertNull(OfficialIpList.expandToSubnets("999.1.1.1/24", isV6 = false))
        assertNull(OfficialIpList.expandToSubnets("1.2.3.4/33", isV6 = false))
        assertNull(OfficialIpList.expandToSubnets("example.com/24", isV6 = false))
    }

    @Test
    fun `random ip stays inside cidr`() {
        val random = Random(42)
        repeat(50) {
            val ip = OfficialIpList.randomIpFromCidr("10.2.3.0/24", random)
            assertNotNull(ip)
            val parts = ip!!.split('.').map { it.toInt() }
            assertEquals(listOf(10, 2, 3), parts.take(3))
            assertTrue(parts[3] in 0..255)
        }
    }

    @Test
    fun `random ip from slash32 is the ip itself`() {
        assertEquals("1.2.3.4", OfficialIpList.randomIpFromCidr("1.2.3.4/32", Random(1)))
    }

    @Test
    fun `v6 random ip keeps network prefix`() {
        val random = Random(7)
        repeat(20) {
            val ip = OfficialIpList.randomIpFromCidr("2001:db8:1:2::/64", random)
            assertNotNull(ip)
            // /64 网络 = 前 4 组（2001:db8:1:2），随机只发生在后 8 字节
            assertTrue(ip!!.startsWith("2001:db8:1:2:"))
        }
    }

    @Test
    fun `expand produces one ip per subnet and covers all subnets`() {
        val content = "10.5.0.0/16"
        val ips = OfficialIpList.expandToRandomIps(content, isV6 = false, random = Random(99))
        assertEquals(256, ips.size)
        // 每个 IP 都属于 10.5.x.y
        assertTrue(ips.all { it.startsWith("10.5.") })
        // 每个 /24 恰好贡献一个 IP
        val thirdOctets = ips.map { it.split('.')[2].toInt() }.toSet()
        assertEquals((0..255).toSet(), thirdOctets)
    }

    @Test
    fun `expand handles mixed lines comments and bare ips`() {
        val content = """
            # 注释行

            10.9.0.0/30
            5.6.7.8
            bad line
            1.2.3.4:8443
        """.trimIndent()
        // /30 → 1 个 /24（原样）；5.6.7.8 → /32 自身；bad line 跳过；
        // "1.2.3.4:8443" 无 '/' 且非合法 IP 字面量 → 跳过（官方地址库不含端口）
        val ips = OfficialIpList.expandToRandomIps(content, isV6 = false, random = Random(3))
        assertEquals(2, ips.size)
        assertTrue(ips.contains("5.6.7.8"))
        assertTrue(ips.any { it.startsWith("10.9.0.") })
    }

    @Test
    fun `builtin fallback covers all official v4 segments`() {
        // 官方 15 段全部可解析展开（每 /24 随机 1 IP）：
        // /20×3→48 + /22×5→20 + /18×2→128 + /17→128 + /15→512 + /13×2→4096 + /14→1024
        val content = com.masgzy.cfstmobile.data.BUILT_IN_IPS.joinToString("\n")
        val ips = OfficialIpList.expandToRandomIps(content, isV6 = false, random = Random(11))
        assertEquals(5956, ips.size)
    }

    @Test
    fun `v6 random ip text is rfc5952 compressed`() {
        // formatV6 分支全覆盖（/128 无 host 位 → 确定性输出网络地址自身）：
        // 此前用 InetAddress.hostAddress 会得到不压缩全展开形（如 "2400:cb00:0:0:0:0:0:0"）
        assertEquals(
            "::",
            OfficialIpList.randomIpFromCidr("::/128", Random(1)),
        )
        assertEquals(
            "2400:cb00::",
            OfficialIpList.randomIpFromCidr("2400:cb00:0:0:0:0:0:0/128", Random(1)),
        )
        assertEquals(
            "2400:cb00:ffff::",
            OfficialIpList.randomIpFromCidr("2400:cb00:ffff:0:0:0:0:0/128", Random(1)),
        )
        assertEquals(
            "2001:db8::1",
            OfficialIpList.randomIpFromCidr("2001:db8:0:0:0:0:0:1/128", Random(1)),
        )
        // 单零组（长度 1 的零段）按 RFC 5952 不得压缩
        assertEquals(
            "1:0:2:3:4:5:6:7",
            OfficialIpList.randomIpFromCidr("1:0:2:3:4:5:6:7/128", Random(1)),
        )
        // 随机抽取不变式：≥2 长零段必被压缩（不会出现相邻零组），字符集合法
        repeat(50) { n ->
            val ip = OfficialIpList.randomIpFromCidr("2400:cb00::/32", Random(n))!!
            assertTrue(ip.startsWith("2400:cb00"))
            assertTrue(!ip.contains("0:0:") && !ip.contains("::0"))
            assertTrue(ip.matches(Regex("[0-9a-f:]+")))
        }
    }
}
