package com.masgzy.cfstmobile

import com.masgzy.cfstmobile.data.ConfigBackup
import com.masgzy.cfstmobile.data.UiSettings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 多配置快照：保存/列表/切换/删除/旧版单快照迁移 */
class ConfigBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun newBackup(): ConfigBackup =
        ConfigBackup(configDir = tmp.newFolder("configs"), legacyFile = tmp.root.resolve("saved_config.json"))

    @Test
    fun `save list load and delete round trip`() {
        val backup = newBackup()
        val a = UiSettings(officialThreads = 111)
        val b = UiSettings(officialThreads = 222)
        val idA = backup.save("配置 A", a)
        val idB = backup.save("配置 B", b)

        val list = backup.list()
        assertEquals(2, list.size)
        // 最近保存的在前（时间倒序）
        assertEquals("配置 B", list.first().name)

        assertTrue(backup.load(idA)!!.officialThreads == 111)
        assertTrue(backup.load(idB)!!.officialThreads == 222)

        backup.delete(idA)
        assertEquals(1, backup.list().size)
        assertNull(backup.load(idA))
    }

    @Test
    fun `same name overwrites the same entry`() {
        val backup = newBackup()
        val id1 = backup.save("家庭配置", UiSettings(officialThreads = 1))
        val id2 = backup.save("家庭配置", UiSettings(officialThreads = 2))
        assertEquals(id1, id2)
        assertEquals(1, backup.list().size)
        assertEquals(2, backup.load(id1)!!.officialThreads)
    }

    @Test
    fun `latest returns most recently saved`() {
        val backup = newBackup()
        assertNull(backup.latest())
        backup.save("旧配置", UiSettings())
        Thread.sleep(5)
        backup.save("新配置", UiSettings())
        assertEquals("新配置", backup.latest()!!.name)
    }

    @Test
    fun `corrupted entry is skipped by list and load returns null`() {
        val backup = newBackup()
        val id = backup.save("正常配置", UiSettings())
        // 直接把存储文件写坏
        tmp.root.resolve("configs/$id.json").writeText("{ not json")
        // 坏条目被 list 过滤，load 返回 null
        assertEquals(0, backup.list().size)
        assertNull(backup.load(id))
    }

    @Test
    fun `legacy single snapshot migrates to default entry`() {
        val backup = newBackup()
        // 伪造旧版 saved_config.json
        val legacyJson = """
            {"ipSource":"builtin","officialThreads":333,"pingMode":"tcp"}
        """.trimIndent()
        tmp.root.resolve("saved_config.json").writeText(legacyJson)

        val list = backup.list()
        assertEquals(1, list.size)
        assertEquals("默认配置", list.first().name)
        assertEquals(333, backup.load(list.first().id)!!.officialThreads)
        // 迁移后旧文件删除
        assertFalse(tmp.root.resolve("saved_config.json").exists())
    }
}
