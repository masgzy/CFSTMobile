package com.masgzy.cfstmobile.data

import android.content.Context
import com.masgzy.cfstmobile.model.ProtocolJson
import kotlinx.serialization.Serializable

/**
 * 配置快照（多配置版）：配置以「命名条目」存 filesDir/configs/ 下独立 JSON，
 * 支持保存多套 / 列表管理 / 切换加载 / 删除；「自动加载」作用于最近使用的一条。
 * 旧版单文件 saved_config.json 首次访问时迁移为「默认配置」。
 *
 * @param configDir 配置目录（JVM 单测可注入临时目录）
 * @param legacyFile 旧版单快照文件（迁移源）
 */
class ConfigBackup(
    private val configDir: java.io.File,
    private val legacyFile: java.io.File,
) {
    constructor(context: Context) : this(
        java.io.File(context.filesDir, DIR_NAME),
        java.io.File(context.filesDir, LEGACY_FILE),
    )

    @Serializable
    data class Entry(val id: String, val name: String, val time: Long)

    @Serializable
    private data class Stored(val name: String, val time: Long, val settings: UiSettings)

    private val json = ProtocolJson.json

    /** 全部配置（按保存时间倒序） */
    fun list(): List<Entry> {
        migrateLegacyIfNeeded()
        val entries = configFiles().mapNotNull { f ->
            runCatching {
                val stored = json.decodeFromString(Stored.serializer(), f.readText())
                Entry(f.nameWithoutExtension, stored.name, stored.time)
            }.getOrNull()
        }
        return entries.sortedByDescending { it.time }
    }

    /** 保存为新配置；同名覆盖时复用已有 id */
    fun save(name: String, settings: UiSettings): String {
        migrateLegacyIfNeeded()
        configDir.mkdirs()
        val existing = list().firstOrNull { it.name == name }
        val id = existing?.id ?: "cfg_${System.currentTimeMillis()}_${name.hashCode()}"
        val stored = Stored(name = name, time = System.currentTimeMillis(), settings = settings)
        java.io.File(configDir, "$id.json").writeText(json.encodeToString(Stored.serializer(), stored))
        return id
    }

    /** 读取配置；不存在或损坏返回 null（官方段误填的手动列表一并清洗） */
    fun load(id: String): UiSettings? = runCatching {
        val f = java.io.File(configDir, "$id.json")
        if (!f.isFile) return null
        json.decodeFromString(Stored.serializer(), f.readText())
            .settings
            .sanitized()
    }.getOrNull()

    /** 删除配置 */
    fun delete(id: String) {
        java.io.File(configDir, "$id.json").delete()
    }

    /** 最近使用的一条（自动加载目标；无任何配置返回 null） */
    fun latest(): Entry? = list().firstOrNull()

    private fun configFiles(): List<java.io.File> =
        configDir.listFiles { f -> f.isFile && f.extension == "json" }?.toList() ?: emptyList()

    /** 旧版单文件 saved_config.json → 「默认配置」条目（迁移后删除旧文件） */
    private fun migrateLegacyIfNeeded() {
        if (!legacyFile.isFile) return
        val settings = runCatching {
            json.decodeFromString(UiSettings.serializer(), legacyFile.readText())
        }.getOrNull() ?: run {
            legacyFile.delete()
            return
        }
        runCatching {
            configDir.mkdirs()
            val stored = Stored(name = "默认配置", time = legacyFile.lastModified(), settings = settings)
            java.io.File(configDir, "cfg_legacy.json").writeText(
                json.encodeToString(Stored.serializer(), stored),
            )
        }
        legacyFile.delete()
    }

    private companion object {
        const val DIR_NAME = "configs"
        const val LEGACY_FILE = "saved_config.json"
    }
}
