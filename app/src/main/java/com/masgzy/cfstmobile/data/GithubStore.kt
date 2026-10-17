package com.masgzy.cfstmobile.data

import android.content.Context
import com.masgzy.cfstmobile.model.ProtocolJson
import kotlinx.serialization.Serializable

/** GitHub 上传配置(与 CFData-WEB 的 GitHub 上传字段对齐) */
@Serializable
data class GhConfig(
    /** GitHub PAT(classic 或 fine-grained,需目标 repo 的 Contents 写权限) */
    val token: String = "",
    /** 仓库属主 */
    val owner: String = "",
    /** 仓库名 */
    val repo: String = "",
    /** 目标分支,默认 main */
    val branch: String = GithubClient.DEFAULT_BRANCH,
    /** 仓库内文件路径,默认仓库根目录 */
    val path: String = GithubClient.DEFAULT_PATH,
    /** 提交信息 */
    val message: String = GithubClient.DEFAULT_MESSAGE,
    /** 是否把 Token 保存在应用私有存储 */
    val saveToken: Boolean = false,
)

/** GitHub 上传配置持久化(SharedPreferences 单键存 JSON) */
class GithubStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = ProtocolJson.json

    fun load(): GhConfig =
        prefs.getString(KEY_CONFIG, null)
            ?.let { raw ->
                runCatching { json.decodeFromString(GhConfig.serializer(), raw) }.getOrNull()
            }
            ?: GhConfig()

    fun save(config: GhConfig) {
        prefs.edit()
            .putString(KEY_CONFIG, json.encodeToString(GhConfig.serializer(), config))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "cfst_github"
        const val KEY_CONFIG = "config_v1"
    }
}
