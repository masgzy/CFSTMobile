package com.masgzy.cfstmobile.data

import android.content.Context
import com.masgzy.cfstmobile.model.ProtocolJson

/**
 * 设置持久化：SharedPreferences 单键存 JSON。
 * 数据量小（<2KB），读写都很快，无需 DataStore。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = ProtocolJson.json

    fun load(): UiSettings =
        prefs.getString(KEY_SETTINGS, null)
            ?.let { raw ->
                runCatching { json.decodeFromString(UiSettings.serializer(), raw) }
                    .getOrNull()
                    ?.sanitized()
            }
            ?: UiSettings()

    fun save(settings: UiSettings) {
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(UiSettings.serializer(), settings)).apply()
    }

    private companion object {
        const val PREFS_NAME = "cfst_settings"
        const val KEY_SETTINGS = "settings_v1"
    }
}
