package com.masgzy.cfstmobile.engine

import com.masgzy.cfstmobile.ffi.CfstFfi
import com.masgzy.cfstmobile.model.CfstProgress
import com.masgzy.cfstmobile.model.CfstResult
import com.masgzy.cfstmobile.model.ProtocolJson

/** 引擎阶段常量（与 cfst_engine_phase 返回值一致） */
object CfstPhase {
    const val IDLE = 0
    const val PING = 1
    const val DOWNLOAD = 2
    const val DONE = 3
    const val ERROR = 4
}

/** 引擎创建失败（配置非法 / ABI 不匹配 / runtime 初始化失败等） */
class EngineCreateException(message: String) : Exception(message)

/**
 * 一次测速会话。生命周期：create → start → poll(250ms 循环) →
 * done/error → takeResult → close。可随时 [stop]（协作式，部分结果仍可取）。
 */
interface CfstSession : AutoCloseable {
    /** 启动测速；重复调用返回 false */
    fun start(): Boolean

    /** 当前阶段，见 [CfstPhase] */
    fun phase(): Int

    /** 请求停止（协作式：引擎在检查点收尾并输出部分结果） */
    fun stop()

    /** 轮询进度 */
    fun poll(): CfstProgress

    /** 取结果（done 阶段一次性；无结果返回 null） */
    fun takeResult(): CfstResult?

    override fun close()
}

/** 会话工厂：libcfst.so 可用时走原生引擎，否则退回 Mock（演示模式） */
object CfstEngineFactory {
    const val EXPECTED_ABI = 1

    fun create(configJson: String): CfstSession =
        if (CfstFfi.available) {
            val abi = CfstFfi.abiVersion()
            if (abi != EXPECTED_ABI) {
                throw EngineCreateException(
                    "引擎 ABI 版本不匹配（期望 $EXPECTED_ABI，实际 $abi），请更新安装包"
                )
            }
            NativeCfstSession.create(configJson)
        } else {
            MockCfstSession()
        }
}

/** 原生会话：句柄包装层。所有方法线程安全（引擎内部自持锁）。 */
class NativeCfstSession private constructor(private val ptr: Long) : CfstSession {

    companion object {
        /** errOut 建议 512 字节（引擎错误消息按容量截断） */
        private const val ERR_CAP = 512

        fun create(configJson: String): NativeCfstSession {
            val errBuf = ByteArray(ERR_CAP)
            val ptr = CfstFfi.engineCreate(configJson.toByteArray(Charsets.UTF_8), errBuf)
            if (ptr == 0L) {
                val msg = errBuf.toString(Charsets.UTF_8)
                    .trimEnd('\u0000')
                    .ifBlank { "引擎初始化失败" }
                throw EngineCreateException(msg)
            }
            return NativeCfstSession(ptr)
        }
    }

    override fun start(): Boolean = CfstFfi.engineStart(ptr)

    override fun phase(): Int = CfstFfi.enginePhase(ptr)

    override fun stop() = CfstFfi.engineStop(ptr)

    override fun poll(): CfstProgress =
        CfstFfi.enginePoll(ptr)
            ?.decodeToString()
            ?.let { ProtocolJson.json.decodeFromString(CfstProgress.serializer(), it) }
            ?: CfstProgress()

    override fun takeResult(): CfstResult? =
        CfstFfi.engineTakeResult(ptr)
            ?.decodeToString()
            ?.let { ProtocolJson.json.decodeFromString(CfstResult.serializer(), it) }

    override fun close() = CfstFfi.engineDestroy(ptr)
}

// ==================================================================
// 地址库精简会话（CompactSession）
// ==================================================================

/** 精简会话阶段常量（与 cfst_compact_phase 返回值一致） */
object CompactPhase {
    const val IDLE = 0
    const val RUNNING = 1
    const val DONE = 2
    const val ERROR = 3
}

/**
 * 一次地址库精简会话。生命周期：create → start → poll(250ms 循环) →
 * done/error → takeResult → close。可随时 [stop]（协作式，已保留子网仍可取）。
 */
interface CompactSessionHandle : AutoCloseable {
    fun start(): Boolean
    fun phase(): Int
    fun stop()
    fun poll(): com.masgzy.cfstmobile.model.CompactProgress
    fun takeResult(): com.masgzy.cfstmobile.model.CompactResult?
    override fun close()
}

/** 精简会话工厂：libcfst.so 可用时走原生实现，否则抛异常（精简依赖原生 TCP 探测） */
object CompactSessionFactory {
    fun create(configJson: String): CompactSessionHandle {
        if (!CfstFfi.available) {
            throw EngineCreateException("精简功能需要原生引擎（未检测到 libcfst.so）")
        }
        return NativeCompactSession.create(configJson)
    }
}

/** 原生精简会话：句柄包装层 */
class NativeCompactSession private constructor(private val ptr: Long) : CompactSessionHandle {

    companion object {
        private const val ERR_CAP = 512

        fun create(configJson: String): NativeCompactSession {
            val errBuf = ByteArray(ERR_CAP)
            val ptr = CfstFfi.compactCreate(configJson.toByteArray(Charsets.UTF_8), errBuf)
            if (ptr == 0L) {
                val msg = errBuf.toString(Charsets.UTF_8)
                    .trimEnd('\u0000')
                    .ifBlank { "精简会话初始化失败" }
                throw EngineCreateException(msg)
            }
            return NativeCompactSession(ptr)
        }
    }

    override fun start(): Boolean = CfstFfi.compactStart(ptr)

    override fun phase(): Int = CfstFfi.compactPhase(ptr)

    override fun stop() = CfstFfi.compactStop(ptr)

    override fun poll(): com.masgzy.cfstmobile.model.CompactProgress =
        CfstFfi.compactPoll(ptr)
            ?.decodeToString()
            ?.let { ProtocolJson.json.decodeFromString(com.masgzy.cfstmobile.model.CompactProgress.serializer(), it) }
            ?: com.masgzy.cfstmobile.model.CompactProgress()

    override fun takeResult(): com.masgzy.cfstmobile.model.CompactResult? =
        CfstFfi.compactTakeResult(ptr)
            ?.decodeToString()
            ?.let { ProtocolJson.json.decodeFromString(com.masgzy.cfstmobile.model.CompactResult.serializer(), it) }

    override fun close() = CfstFfi.compactDestroy(ptr)
}
