package com.masgzy.cfstmobile.data

import android.content.Context
import java.io.File

/**
 * 本地精简地址库（filesDir/ips-v4.txt）管理，对齐 CFData-WEB 的 ips-v4.txt 语义：
 * 「精简地址库」把当前官方段精简为活跃 /24 子网并覆盖本文件，
 * 后续官方扫描开启「使用精简地址库」时以此作为 IP 来源（引擎 ip_file 直读）。
 */
class CompactLibrary(context: Context) {

    private val file: File = File(context.filesDir, FILE_NAME)

    fun exists(): Boolean = file.isFile && file.length() > 0

    /** 全部 /24 子网行（跳过空行与注释行） */
    fun readLines(): List<String> =
        if (!exists()) {
            emptyList()
        } else {
            runCatching {
                file.readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            }.getOrElse { emptyList() }
        }

    /** 子网行数（UI 显示「已精简 N 个 /24」） */
    fun subnetCount(): Int = readLines().size

    /** 引擎可直读的绝对路径（ip_file）；不存在返回 null */
    fun engineFilePath(): String? = if (exists()) file.absolutePath else null

    /** 原子覆盖写入精简结果（先写临时文件再 rename，避免半写状态） */
    fun write(content: String): Boolean = runCatching {
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(content)
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            // rename 失败（跨 inode 等）退化为直接写入
            file.writeText(content)
            tmp.delete()
        }
        true
    }.getOrDefault(false)

    fun delete(): Boolean = runCatching { file.delete() }.getOrDefault(false)

    companion object {
        const val FILE_NAME = "ips-v4.txt"
    }
}
