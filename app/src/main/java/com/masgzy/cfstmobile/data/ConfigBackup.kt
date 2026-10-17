package com.masgzy.cfstmobile.data

import android.content.Context
import com.masgzy.cfstmobile.model.ProtocolJson

/**
 * 配置快照（对齐 CFData-WEB 💾 保存/加载配置菜单）：
 * 当前 UiSettings 序列化为 JSON 存 filesDir，保存/加载/删除/自动加载。
 */
class ConfigBackup(context: Context) {

    private val file = java.io.File(context.filesDir, FILE_NAME)

    /** 保存当前配置快照（覆盖旧快照） */
    fun save(settings: UiSettings) {
        file.writeText(ProtocolJson.json.encodeToString(UiSettings.serializer(), settings))
    }

    /** 读取快照；不存在或损坏返回 null */
    fun load(): UiSettings? =
        runCatching {
            file.takeIf { it.exists() }
                ?.readText()
                ?.let { ProtocolJson.json.decodeFromString(UiSettings.serializer(), it) }
        }.getOrNull()

    /** 快照是否存在 */
    fun exists(): Boolean = file.exists()

    /** 删除快照 */
    fun delete() {
        file.delete()
    }

    private companion object {
        const val FILE_NAME = "saved_config.json"
    }
}
