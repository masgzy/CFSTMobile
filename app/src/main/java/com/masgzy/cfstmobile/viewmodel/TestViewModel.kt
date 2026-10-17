package com.masgzy.cfstmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.masgzy.cfstmobile.data.CloudflareIpSources
import com.masgzy.cfstmobile.data.ConfigBackup
import com.masgzy.cfstmobile.data.DcLocation
import com.masgzy.cfstmobile.data.DcLocations
import com.masgzy.cfstmobile.data.DcProbe
import com.masgzy.cfstmobile.data.DcStep
import com.masgzy.cfstmobile.data.DcTestItem
import com.masgzy.cfstmobile.data.DcTestUiState
import com.masgzy.cfstmobile.data.HistoryRecord
import com.masgzy.cfstmobile.data.HistoryStore
import com.masgzy.cfstmobile.data.SettingsRepository
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.data.aggregateDcList
import com.masgzy.cfstmobile.data.sortDcResults
import com.masgzy.cfstmobile.engine.CfstEngineFactory
import com.masgzy.cfstmobile.engine.CfstSession
import com.masgzy.cfstmobile.ffi.CfstFfi
import com.masgzy.cfstmobile.model.CfstResultItem
import com.masgzy.cfstmobile.model.DownloadProgress
import com.masgzy.cfstmobile.model.PingProgress
import com.masgzy.cfstmobile.model.ProtocolJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 单条测速 UI 状态 */
data class TestUiState(
    val isRunning: Boolean = false,
    /** false=ping / download / done / error 中的一种；用引擎 phase 字符串直存 */
    val phase: String = "idle",
    val stopRequested: Boolean = false,
    val pingProgress: PingProgress? = null,
    val downloadProgress: DownloadProgress? = null,
    val message: String? = null,
    /** 引擎原始顺序（综合评分）；展示层按 sortBy 重排 */
    val results: List<com.masgzy.cfstmobile.model.CfstResultItem> = emptyList(),
    /** 未加载 libcfst.so 时为 true：数据为模拟 */
    val isMock: Boolean = !CfstFfi.available,
) {
    val isDone: Boolean get() = phase == "done" || phase == "error" || (!isRunning && results.isNotEmpty())
}

/**
 * 测速主 ViewModel：管理设置持久化、引擎会话生命周期（250ms 轮询）、
 * IP 段在线获取与历史记录。
 */
class TestViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val historyStore = HistoryStore(app)
    private val configBackup = ConfigBackup(app)

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    /** 终端日志面板（对齐 CFData-WEB log-box，最新在末尾，上限 200 条） */
    private val _logs = MutableStateFlow(listOf<String>())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    init {
        // 自动加载已保存配置（对齐 CFData configAutoLoad=localStorage）
        if (_settings.value.autoLoadConfig) {
            configBackup.load()?.let { saved ->
                _settings.value = saved
                settingsRepo.save(saved)
                log("已自动加载保存配置")
            }
        }
        log("等待操作...")
    }

    private val _history = MutableStateFlow(historyStore.load())
    val history: StateFlow<List<HistoryRecord>> = _history.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** 进行中的会话（跨线程引用仅用于 stop） */
    private var session: CfstSession? = null

    // ------------------------------------------------------ 数据中心二次测试

    private val _dcState = MutableStateFlow(DcTestUiState())
    val dcState: StateFlow<DcTestUiState> = _dcState.asStateFlow()

    private var dcJob: Job? = null

    /** trace 补齐 colo 后的完整结果（二次测试的数据源） */
    private var dcEnriched: List<CfstResultItem> = emptyList()

    /** 数据中心位置表（下载失败降级为空表，仅显示 colo 三字码） */
    private var dcLocations: Map<String, DcLocation> = emptyMap()

    // ------------------------------------------------------------ 设置

    fun updateSettings(transform: (UiSettings) -> UiSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        settingsRepo.save(next)
    }

    // ------------------------------------------------- 配置快照（💾 保存/加载）

    /** 追加一条终端日志（时间戳前缀，对齐 CFData log-box 样式） */
    private fun log(message: String) {
        val ts = SimpleDateFormat("[HH:mm:ss]", Locale.US).format(Date())
        val next = _logs.value + "$ts $message"
        _logs.value = if (next.size > MAX_LOG_LINES) next.takeLast(MAX_LOG_LINES) else next
    }

    fun saveConfigSnapshot() {
        configBackup.save(_settings.value)
        log("已保存当前配置")
    }

    fun loadConfigSnapshot() {
        val saved = configBackup.load()
        if (saved == null) {
            _toast.tryEmit("没有已保存的配置")
            return
        }
        _settings.value = saved
        settingsRepo.save(saved)
        log("已加载保存配置")
    }

    fun deleteConfigSnapshot() {
        configBackup.delete()
        log("已删除保存配置")
    }

    fun hasConfigSnapshot(): Boolean = configBackup.exists()

    /** 恢复全部默认配置（对齐 CFData 设置菜单危险按钮） */
    fun resetSettings() {
        val defaults = UiSettings()
        _settings.value = defaults
        settingsRepo.save(defaults)
        log("已恢复全部默认配置")
    }

    // ------------------------------------------------------------ 测速

    fun startTest() {
        if (_uiState.value.isRunning) return
        resetDcTest()
        val s = _settings.value
        s.validate()?.let { error ->
            _toast.tryEmit(error)
            return
        }
        val modeDesc = if (s.ipSource == UiSettings.SOURCE_BUILTIN) {
            "官方优选 IPv${s.ipType}"
        } else {
            "非标优选"
        }
        log(
            "开始扫描：$modeDesc · 端口 ${s.tcpPort} · " +
                (if (s.pingMode == UiSettings.PING_HTTP) "httping" else "tcping") +
                (if (s.enableDownload) " · 自动测速" else ""),
        )
        val configJson = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            s.toCfstConfig(),
        )
        _uiState.value = TestUiState(isRunning = true, isMock = !CfstFfi.available)
        stopLogged = false

        viewModelScope.launch(Dispatchers.IO) {
            val current = try {
                CfstEngineFactory.create(configJson)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRunning = false, phase = "error", message = e.message ?: "创建引擎失败")
                }
                return@launch
            }
            session = current
            try {
                if (!current.start()) {
                    _uiState.update { it.copy(isRunning = false, message = "引擎已在运行中，请稍候") }
                    return@launch
                }
                // 250ms 轮询进度，直到 done / error
                var lastPhase = ""
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    val progress = current.poll()
                    if (progress.phase != lastPhase) {
                        when (progress.phase) {
                            "ping" -> log("阶段：延迟测试")
                            "download" -> log("阶段：下载测速")
                            "done" -> log("扫描完成")
                            "error" -> log("出错：${progress.message ?: "未知错误"}")
                        }
                        lastPhase = progress.phase
                    }
                    if (progress.stopRequested && !stopLogged) {
                        log("已请求停止，等待引擎收尾...")
                        stopLogged = true
                    }
                    _uiState.update { st ->
                        st.copy(
                            phase = progress.phase,
                            stopRequested = progress.stopRequested,
                            pingProgress = progress.ping ?: st.pingProgress,
                            downloadProgress = progress.download ?: st.downloadProgress,
                            message = progress.message ?: st.message.takeIf { progress.phase != "error" },
                        )
                    }
                    if (progress.phase == "done" || progress.phase == "error") break
                }
                collectResult(current)
            } catch (e: CancellationException) {
                // ViewModel 销毁：请求引擎收尾，避免线程与 runtime 泄漏
                runCatching { current.stop() }
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isRunning = false, phase = "error", message = e.message ?: "测速过程出现异常")
                }
            } finally {
                session = null
                runCatching { current.close() }
                _uiState.update { it.copy(isRunning = false) }
            }
        }
    }

    /** done 后取结果 + 写历史 */
    private suspend fun collectResult(current: CfstSession) {
        val result = current.takeResult()
        if (result != null && result.items.isNotEmpty()) {
            _uiState.update { it.copy(results = result.items) }
            log("测试完成：${result.items.size} 个结果")
            val s = _settings.value
            val summary = "${s.tcpPort}/${s.pingMode}" + if (s.enableDownload) " · 下载" else " · 仅延迟"
            _history.value = historyStore.add(
                HistoryRecord(System.currentTimeMillis(), summary, result.items),
                _history.value,
            )
        } else if (_uiState.value.phase != "error") {
            _uiState.update { it.copy(message = it.message ?: "测速结束，但没有得到结果") }
            log("测速结束，没有得到结果")
        }
    }

    /** 协作式停止：引擎在检查点收尾并保留部分结果 */
    fun stopTest() {
        session?.stop()
    }

    /** 防重复打印停止日志 */
    private var stopLogged = false

    // ------------------------------------------------------ 数据中心二次测试

    /**
     * 入口：对当前测速结果做数据中心聚合。
     * tcping 模式的结果不带 colo，先并发逐 IP trace 探测归属（对齐 CFData 官方模式扫描阶段）；
     * httping 模式结果已携带 colo，直接聚合。
     */
    fun startDcTest() {
        if (_dcState.value.busy) return
        val items = _uiState.value.results
        if (items.isEmpty()) {
            _toast.tryEmit("暂无测速结果，先完成一次测速")
            return
        }
        log("数据中心测试：开始识别 ${items.size} 个结果的归属")
        dcJob?.cancel()
        dcJob = viewModelScope.launch(Dispatchers.IO) {
            _dcState.value = DcTestUiState(step = DcStep.PROBING_COLO)
            // 位置表与 colo 探测并行；位置表失败降级为空表
            val locationsDeferred = async { DcLocations.load(getApplication()) }
            val missing = items.filter { it.colo.isNullOrBlank() }
            var enriched = items
            if (missing.isNotEmpty()) {
                val colos = probeColos(missing)
                enriched = items.map { it.copy(colo = it.colo ?: colos[it.address()]) }
            }
            dcLocations = locationsDeferred.await()
            val dcList = aggregateDcList(enriched, dcLocations)
            if (dcList.isEmpty()) {
                _dcState.update {
                    it.copy(
                        step = DcStep.ERROR,
                        message = "未获取到任何数据中心信息（trace 探测全部失败），请检查网络后重试",
                    )
                }
                return@launch
            }
            dcEnriched = enriched
            log("数据中心聚合完成：${dcList.size} 个 DC")
            _dcState.update { it.copy(step = DcStep.DC_LIST, dcList = dcList, probeTotal = missing.size) }
        }
    }

    /** 并发 trace 探测每 IP 的 colo（50 并发，对齐 CFData runBoundedWorkers 上限） */
    private suspend fun probeColos(items: List<CfstResultItem>): Map<String, String> {
        val out = ConcurrentHashMap<String, String>()
        val done = AtomicInteger(0)
        val total = items.size
        _dcState.update { it.copy(probeDone = 0, probeTotal = total) }
        val sem = Semaphore(DC_CONCURRENCY)
        coroutineScope {
            items.forEach { item ->
                launch {
                    sem.withPermit {
                        val colo = DcProbe.fetchColo(item.ip, item.port)
                        if (colo != null) out[item.address()] = colo
                        _dcState.update { it.copy(probeDone = done.incrementAndGet(), probeTotal = total) }
                    }
                }
            }
        }
        return out
    }

    /** 对选中数据中心（null = 全部）的 IP 做二次详细测试（tcping×10 / httping×3） */
    fun testDc(colo: String?) {
        if (dcEnriched.isEmpty()) return
        dcJob?.cancel()
        val targets = dcEnriched.filter { item ->
            !item.colo.isNullOrBlank() && (colo == null || item.colo.equals(colo, ignoreCase = true))
        }
        if (targets.isEmpty()) {
            _dcState.update { it.copy(step = DcStep.ERROR, message = "该数据中心没有可测试的 IP") }
            return
        }
        val httpMode = _settings.value.pingMode == UiSettings.PING_HTTP
        val delayMs = _settings.value.maxDelayMs
        dcJob = viewModelScope.launch(Dispatchers.IO) {
            _dcState.update {
                it.copy(
                    step = DcStep.TESTING,
                    selectedDc = colo,
                    testDone = 0,
                    testTotal = targets.size,
                    results = emptyList(),
                    message = null,
                )
            }
            log("二次详细测试：${colo ?: "全部 DC"} · ${targets.size} 个 IP")
            val out = mutableListOf<DcTestItem>()
            val mutex = Mutex()
            val done = AtomicInteger(0)
            val sem = Semaphore(DC_CONCURRENCY)
            coroutineScope {
                targets.forEach { item ->
                    launch {
                        sem.withPermit {
                            val stats = if (httpMode) {
                                DcProbe.detailedHttpTest(item.ip, item.port, delayMs)
                            } else {
                                DcProbe.detailedTcpTest(item.ip, item.port, delayMs)
                            }
                            if (stats != null) {
                                val loc = dcLocations[item.colo?.trim()?.uppercase(Locale.US).orEmpty()]
                                val entry = DcTestItem(
                                    ip = item.ip,
                                    port = item.port,
                                    colo = item.colo.orEmpty(),
                                    country = loc?.cca2.orEmpty(),
                                    city = loc?.city.orEmpty(),
                                    minMs = stats.minMs,
                                    avgMs = stats.avgMs,
                                    maxMs = stats.maxMs,
                                    lossRate = (stats.attempts - stats.received).toDouble() / stats.attempts,
                                    attempts = stats.attempts,
                                )
                                val snapshot = mutex.withLock {
                                    out.add(entry)
                                    out.toMutableList().also(::sortDcResults)
                                }
                                _dcState.update { it.copy(results = snapshot) }
                            }
                            _dcState.update { it.copy(testDone = done.incrementAndGet()) }
                        }
                    }
                }
            }
            if (out.isEmpty()) {
                _dcState.update {
                    it.copy(
                        step = DcStep.ERROR,
                        message = "详细测试完成，但没有任何 IP 通过延迟测试（延迟上限 ${delayMs}ms）",
                    )
                }
            } else {
                log("二次详细测试完成：${out.size} 个 IP 通过")
                _dcState.update { it.copy(step = DcStep.DONE) }
            }
        }
    }

    /** 从详细测试结果返回数据中心列表（可另选其他 DC 重测） */
    fun backToDcList() {
        if (_dcState.value.busy) return
        dcJob?.cancel()
        dcJob = null
        _dcState.update { it.copy(step = DcStep.DC_LIST, selectedDc = null, message = null) }
    }

    /**
     * 请求取消进行中的探测/测试（关闭对话框时调用）。
     * 测试中取消后保留已完成的部分结果呈现（对齐 CFData 终止语义）。
     */
    fun cancelDcTest() {
        val job = dcJob ?: return
        dcJob = null
        job.cancel()
        _dcState.update { st ->
            when {
                st.step == DcStep.TESTING && st.results.isNotEmpty() ->
                    st.copy(step = DcStep.DONE, message = "测试已终止，呈现已完成部分")
                st.step == DcStep.TESTING || st.step == DcStep.PROBING_COLO -> DcTestUiState()
                else -> st
            }
        }
    }

    /** 彻底重置二次测试状态（新测速开始时调用） */
    fun resetDcTest() {
        dcJob?.cancel()
        dcJob = null
        dcEnriched = emptyList()
        _dcState.value = DcTestUiState()
    }

    // ------------------------------------------------------ IP 段在线获取

    /** source: "cloudflare" | "github" */
    fun fetchIpList(source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _settings.value
            val result = runCatching {
                when (source) {
                    SOURCE_CLOUDFLARE -> CloudflareIpSources.fetch(CloudflareIpSources.CLOUDFLARE_URL)
                    else -> CloudflareIpSources.fetch(CloudflareIpSources.GITHUB_URL, s.mirrorUrl)
                }
            }
            result.fold(
                onSuccess = { lines ->
                    withContext(Dispatchers.Main) {
                        updateSettings { it.copy(ipSource = UiSettings.SOURCE_CUSTOM, customIpText = lines.joinToString("\n")) }
                        log("已获取 ${lines.size} 个 IP 段（在线）")
                        _toast.tryEmit("已获取 ${lines.size} 个 IP 段，已切换为自定义 IP 来源")
                    }
                },
                onFailure = { e ->
                    log("在线获取 IP 段失败：${e.message ?: "网络错误"}")
                    _toast.tryEmit("获取失败：${e.message ?: "网络错误"}")
                },
            )
        }
    }

    // ------------------------------------------------------------ 历史

    fun deleteHistory(index: Int) {
        _history.value = historyStore.removeAt(index, _history.value)
    }

    fun clearHistory() {
        _history.value = historyStore.clear()
    }

    companion object {
        const val POLL_INTERVAL_MS = 250L
        const val SOURCE_CLOUDFLARE = "cloudflare"
        const val SOURCE_GITHUB = "github"

        /** 终端日志面板保留条数上限 */
        const val MAX_LOG_LINES = 200

        /** 数据中心探测/详细测试并发上限（对齐 CFData runBoundedWorkers 50 workers） */
        const val DC_CONCURRENCY = 50

        val Factory = viewModelFactory {
            initializer {
                TestViewModel(this[APPLICATION_KEY] as Application)
            }
        }
    }
}
