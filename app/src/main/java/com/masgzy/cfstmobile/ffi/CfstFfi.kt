package com.masgzy.cfstmobile.ffi

/**
 * libcfst.so 的 JNI 直连边界（engine/src/jni_bridge.rs 提供 Java_* 符号）。
 *
 * 契约要点（详见 engine/README.md）：
 * - 一个引擎句柄 = 一次测速会话；start 仅 idle 态可调，重复调用返回 false；
 * - 配置/进度/结果均为 UTF-8 JSON 字符串的 ByteArray 形态
 *   （JNI 层拷贝即释放，无悬垂指针，Kotlin 侧无需手动 free）；
 * - [engineCreate] 失败返回 0，错误消息写回 errOut（截去尾部 NUL 即消息）；
 * - [enginePoll] / [engineTakeResult] 返回 null 表示句柄非法或暂无数据；
 * - [engineTakeResult] 一次性：仅 done 阶段首次调用返回结果；
 * - [engineDestroy] 永不阻塞（引擎仍在测速时先请求停止再立即返回）。
 *
 * ⚠️ 类名/包名与 Rust 侧导出符号强耦合：com.masgzy.cfstmobile.ffi.CfstFfi
 * 改名必须同步修改 engine/src/jni_bridge.rs。
 */
object CfstFfi {
    /** libcfst.so 是否加载成功；false 时 UI 进入演示模式（Mock 引擎） */
    val available: Boolean = try {
        System.loadLibrary("cfst")
        true
    } catch (_: Throwable) {
        false
    }

    /** 当前 ABI 版本（应为 1） */
    external fun abiVersion(): Int

    /** 创建引擎；失败返回 0 并把错误写回 errOut */
    external fun engineCreate(configJson: ByteArray, errOut: ByteArray): Long

    /** 启动测速（仅 idle 态有效，返回是否成功受理） */
    external fun engineStart(ptr: Long): Boolean

    /** 当前阶段：0 idle / 1 ping / 2 download / 3 done / 4 error */
    external fun enginePhase(ptr: Long): Int

    /** 协作式停止：立即返回，引擎在检查点收尾并保留部分结果 */
    external fun engineStop(ptr: Long)

    /** 轮询进度 JSON（建议 250ms 间隔） */
    external fun enginePoll(ptr: Long): ByteArray?

    /** 取结果 JSON（done 阶段一次性） */
    external fun engineTakeResult(ptr: Long): ByteArray?

    /** 销毁引擎（不阻塞） */
    external fun engineDestroy(ptr: Long)

    // ---------------- 地址库精简（CompactSession，句柄语义与 engine 系列一致） ----------------

    /** 创建精简会话；失败返回 0 并把错误写回 errOut */
    external fun compactCreate(configJson: ByteArray, errOut: ByteArray): Long

    /** 启动精简（仅 idle 态有效） */
    external fun compactStart(ptr: Long): Boolean

    /** 当前阶段：0 idle / 1 running / 2 done / 3 error */
    external fun compactPhase(ptr: Long): Int

    /** 协作式停止：探测线程在检查点收尾，已保留子网仍可取 */
    external fun compactStop(ptr: Long)

    /** 轮询精简进度 JSON（含 pass/current/total/found/percent/status/logs） */
    external fun compactPoll(ptr: Long): ByteArray?

    /** 取精简结果 JSON（done 阶段一次性：{count, content, file}） */
    external fun compactTakeResult(ptr: Long): ByteArray?

    /** 销毁精简会话（不阻塞） */
    external fun compactDestroy(ptr: Long)
}
