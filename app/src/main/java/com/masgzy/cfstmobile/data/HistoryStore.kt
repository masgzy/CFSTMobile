package com.masgzy.cfstmobile.data

import android.content.Context
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.ProtocolJson
import java.io.File
import kotlinx.serialization.Serializable

/** 一条历史记录 = 一次测速会话的完整结果 */
@Serializable
data class HistoryRecord(
    /** 会话结束时间戳（epoch millis） */
    val timestamp: Long,
    /** 概要（如 "443/tcp · 下载"） */
    val summary: String,
    val items: List<CfstResultItem>,
)

/** 历史记录持久化：filesDir/history.json，最多保留 [MAX_RECORDS] 条 */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val json = ProtocolJson.json

    fun load(): List<HistoryRecord> =
        runCatching { json.decodeFromString(HISTORY_LIST_serializer(), file.readText()) }
            .getOrDefault(emptyList())

    fun add(record: HistoryRecord, current: List<HistoryRecord>): List<HistoryRecord> =
        (listOf(record) + current).take(MAX_RECORDS).also { save(it) }

    fun removeAt(index: Int, current: List<HistoryRecord>): List<HistoryRecord> =
        current.toMutableList().apply {
            if (index in indices) removeAt(index)
        }.also { save(it) }

    fun clear(): List<HistoryRecord> = emptyList<HistoryRecord>().also { save(it) }

    private fun save(records: List<HistoryRecord>) {
        runCatching {
            file.writeText(json.encodeToString(HISTORY_LIST_serializer(), records))
        }
    }

    private companion object {
        const val FILE_NAME = "history.json"
        const val MAX_RECORDS = 20

        fun HISTORY_LIST_serializer() = kotlinx.serialization.builtins.ListSerializer(HistoryRecord.serializer())
    }
}
