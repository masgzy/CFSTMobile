package com.masgzy.cfstmobile.data

import android.content.Context
import com.masgzy.cfstmobile.model.ProtocolJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** edgetunnel 账户（与 CFData-WEB 的多账户管理对齐） */
@Serializable
data class EtAccount(
    /** 显示名，例如 main-tunnel */
    val name: String,
    /** Worker 域名，例如 https://example.com */
    val host: String,
    /** 管理页密码；savePassword=false 时留空，上传时临时输入 */
    val password: String = "",
    /** 是否把密码保存在应用私有存储 */
    val savePassword: Boolean = false,
)

/**
 * edgetunnel 账户持久化：SharedPreferences 单键存 JSON 数组。
 * 密码保存在应用私有目录（device-encrypted storage），不参与任何同步/备份导出。
 */
class EdgetunnelStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = ProtocolJson.json

    fun load(): List<EtAccount> =
        prefs.getString(KEY_ACCOUNTS, null)
            ?.let { raw ->
                runCatching {
                    json.decodeFromString(ListSerializer(EtAccount.serializer()), raw)
                }.getOrNull()
            }
            ?: emptyList()

    fun save(accounts: List<EtAccount>) {
        prefs.edit()
            .putString(KEY_ACCOUNTS, json.encodeToString(ListSerializer(EtAccount.serializer()), accounts))
            .apply()
    }

    /** 按名称 upsert（同名覆盖，其余保持原序），返回新列表 */
    fun upsert(account: EtAccount, current: List<EtAccount>): List<EtAccount> {
        val next = current.filterNot { it.name == account.name } + account
        save(next)
        return next
    }

    fun remove(name: String, current: List<EtAccount>): List<EtAccount> {
        val next = current.filterNot { it.name == name }
        save(next)
        return next
    }

    private companion object {
        const val PREFS_NAME = "cfst_edgetunnel"
        const val KEY_ACCOUNTS = "accounts_v1"
    }
}
