package com.masgzy.cfstmobile.engine

import com.masgzy.cfstmobile.model.CfstProgress
import com.masgzy.cfstmobile.model.CfstResult
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.DownloadProgress
import com.masgzy.cfstmobile.model.PingProgress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.random.Random

/**
 * 演示模式会话：未加载 libcfst.so 时以模拟数据驱动完整 UI 流程
 * （延迟阶段 → 下载阶段 → 完成），支持停止。数据形态与原生引擎一致。
 *
 * 时间线：PING 6s → DOWNLOAD 8s → DONE；stop 后在下一轮 poll 收尾。
 */
class MockCfstSession : CfstSession {

    private val running = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val resultTaken = AtomicBoolean(false)
    private var startedAtNanos: Long = 0
    private val rng = Random(System.nanoTime())

    override fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return false
        startedAtNanos = System.nanoTime()
        return true
    }

    override fun phase(): Int {
        if (!running.get()) return CfstPhase.IDLE
        return when {
            stopped.get() || elapsedSecs() >= TOTAL_SECS -> CfstPhase.DONE
            elapsedSecs() < PING_SECS -> CfstPhase.PING
            else -> CfstPhase.DOWNLOAD
        }
    }

    override fun stop() {
        stopped.set(true)
    }

    override fun poll(): CfstProgress {
        if (!running.get()) return CfstProgress(phase = "idle")
        val elapsed = elapsedSecs()
        if (stopped.get() || elapsed >= TOTAL_SECS) {
            return CfstProgress(
                phase = "done",
                stopRequested = stopped.get(),
                ping = PingProgress(tested = MOCK_TOTAL, success = MOCK_SUCCESS, total = MOCK_TOTAL),
                download = DownloadProgress(tested = DOWNLOAD_TOTAL, qualified = QUALIFIED, total = DOWNLOAD_TOTAL),
            )
        }
        return if (elapsed < PING_SECS) {
            val tested = (MOCK_TOTAL * (elapsed / PING_SECS)).toLong()
            CfstProgress(
                phase = "ping",
                stopRequested = false,
                ping = PingProgress(tested = tested, success = (tested * 0.28).toLong(), total = MOCK_TOTAL),
            )
        } else {
            val frac = ((elapsed - PING_SECS) / DOWNLOAD_SECS).coerceIn(0.0, 1.0)
            val tested = (DOWNLOAD_TOTAL * frac).toLong().coerceAtLeast(1)
            val speed = 9.0 + 6.0 * sin(elapsed * 2.2) + rng.nextDouble() * 2.0
            CfstProgress(
                phase = "download",
                stopRequested = false,
                ping = PingProgress(tested = MOCK_TOTAL, success = MOCK_SUCCESS, total = MOCK_TOTAL),
                download = DownloadProgress(tested = tested, qualified = (tested * 0.6).toLong(), total = DOWNLOAD_TOTAL, speedMbps = speed),
            )
        }
    }

    override fun takeResult(): CfstResult? {
        if (!running.get() || !resultTaken.compareAndSet(false, true)) return null
        val colos = listOf("HKG", "SJC", "LAX", "NRT", "SIN", "FRA", "AMS", "YYZ")
        val prefixes = listOf("104.16", "104.17", "104.18", "172.64", "172.65", "162.158", "141.101")
        val items = List(RESULT_COUNT) { i ->
            val delay = 2.0 + rng.nextDouble() * 58.0
            CfstResultItem(
                ip = "${prefixes[rng.nextInt(prefixes.size)]}.${rng.nextInt(256)}.${rng.nextInt(256)}",
                port = 443,
                sent = 4,
                received = 4,
                lossRate = if (rng.nextDouble() < 0.85) 0.0 else 0.25,
                delayMs = (delay * 100).toInt() / 100.0,
                downloadSpeedMbps = (1.5 + rng.nextDouble() * 13.0 * (1.0 - i / 12.0) * 100).toInt() / 100.0,
                colo = colos[rng.nextInt(colos.size)],
            )
        }
        return CfstResult(count = items.size, items = items)
    }

    override fun close() {
        running.set(false)
    }

    private fun elapsedSecs(): Double =
        (System.nanoTime() - startedAtNanos) / 1_000_000_000.0

    companion object {
        private const val PING_SECS = 6.0
        private const val DOWNLOAD_SECS = 8.0
        private const val TOTAL_SECS = PING_SECS + DOWNLOAD_SECS
        private const val MOCK_TOTAL = 800L
        private const val MOCK_SUCCESS = 214L
        private const val DOWNLOAD_TOTAL = 30L
        private const val QUALIFIED = 18L
        private const val RESULT_COUNT = 8
    }
}
