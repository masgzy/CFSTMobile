package com.masgzy.cfstmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.masgzy.cfstmobile.data.ConfigBackup
import com.masgzy.cfstmobile.data.DcLocation
import com.masgzy.cfstmobile.data.DcLocations
import com.masgzy.cfstmobile.data.DcProbe
import com.masgzy.cfstmobile.data.DcSortKey
import com.masgzy.cfstmobile.data.DcStep
import com.masgzy.cfstmobile.data.DcTestItem
import com.masgzy.cfstmobile.data.DcTestUiState
import com.masgzy.cfstmobile.data.HistoryRecord
import com.masgzy.cfstmobile.data.HistoryStore
import com.masgzy.cfstmobile.data.ScanRow
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
import java.net.HttpURLConnection
import java.net.URL
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
 * 非标 URL 拉取、数据中心聚合（扫描完成自动触发）与批量测速。
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

    /** 批量测速会话（一键测速/继续测速/单行测速，独立于主扫描会话） */
    private var speedSession: CfstSession? = null

    // ------------------------------------------------------ 扫描结果行（enrich 后）

    private val _scanRows = MutableStateFlow(emptyList<ScanRow>())
    val scanRows: StateFlow<List<ScanRow>> = _scanRows.asStateFlow()

    // ------------------------------------------------------ 数据中心二次测试

    private val _dcState = MutableStateFlow(DcTestUiState())
    val dcState: StateFlow<DcTestUiState> = _dcState.asStateFlow()

    private var dcJob: Job? = null

    /** trace 补齐 colo 后的完整结果（二次测试的数据源） */
    private var dcEnriched: List<CfstResultItem> = emptyList()

    /** 数据中心位置表（下载失败降级为空表，仅显示 colo 三字码） */
    private var dcLocations: Map<String, DcLocation> = emptyMap()

    /** 一键排序当前键（对齐 CFData sortModal） */
    private var dcSortKey: String = DcSortKey.LOSS

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
        val s = _settings.value
        s.validate()?.let { error ->
            _toast.tryEmit(error)
            return
        }
        resetDcTest()
        _scanRows.value = emptyList()
        _uiState.value = TestUiState(isRunning = true, isMock = !CfstFfi.available)
        stopLogged = false

        viewModelScope.launch(Dispatchers.IO) {
            // 非标模式：网络 URL 实时拉取目标列表（对齐 CFData nsbSourceUrl 后端拉取）
            var urlTargets: List<String> = emptyList()
            if (!s.official && s.nsbSourceUrl.isNotBlank()) {
                log("正在从网络 URL 获取 IP 列表…")
                urlTargets = runCatching { fetchTextLines(s.nsbSourceUrl.trim()) }
                    .getOrElse {
                        log("网络 URL 获取失败：${it.message ?: "网络错误"}")
                        withContext(Dispatchers.Main) { _toast.tryEmit("URL 获取失败：${it.message ?: "网络错误"}") }
                        _uiState.update { it.copy(isRunning = false, phase = "idle") }
                        return@launch
                    }
                log("已获取 ${urlTargets.size} 个目标（URL）")
            }

            val modeDesc = if (s.official) {
                "官方优选 IPv${s.ipType}"
            } else {
                "非标优选（${(s.nsbTargets.size + urlTargets.size)} 个目标）"
            }
            log(
                "开始扫描：$modeDesc · 端口 ${s.enginePort} · " +
                    (if (s.pingMode == UiSettings.PING_HTTP) "httping" else "tcping") +
                    (if (s.downloadEnabled) " · 自动测速" else ""),
            )
            val configJson = ProtocolJson.json.encodeToString(
                com.masgzy.cfstmobile.model.CfstConfig.serializer(),
                s.toCfstConfig(urlTargets),
            )
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

    /** done 后取结果 + 写历史 + （官方）自动数据中心聚合 */
    private suspend fun collectResult(current: CfstSession) {
        val result = current.takeResult()
        if (result == null || result.items.isEmpty()) {
            if (_uiState.value.phase != "error") {
                _uiState.update { it.copy(message = it.message ?: "测速结束，但没有得到结果") }
                log("测速结束，没有得到结果")
            }
            return
        }
        _uiState.update { it.copy(results = result.items) }
        log("测试完成：${result.items.size} 个结果")
        // 两种模式都立即生成扫描行（colo 未识别时显示 -，官方模式聚合后刷新位置列）
        _scanRows.value = result.items.map { ScanRow.from(it, emptyMap()) }
        val s = _settings.value
        val summary = "${s.enginePort}/${s.pingMode}" + if (s.downloadEnabled) " · 下载" else " · 仅延迟"
        _history.value = historyStore.add(
            HistoryRecord(System.currentTimeMillis(), summary, result.items),
            _history.value,
        )
        // 官方模式：扫描完成即聚合数据中心（对齐 CFData 官方模式 dcList 随扫描就绪）
        if (s.official) {
            val autoSpeed = s.officialAutoSpeed
            startDcTestInternal(autoStart = autoSpeed)
        }
    }

    /** 协作式停止：引擎在检查点收尾并保留部分结果 */
    fun stopTest() {
        session?.stop()
        speedSession?.stop()
    }

    /** 防重复打印停止日志 */
    private var stopLogged = false

    // ------------------------------------------------------ 数据中心二次测试

    /**
     * 手动入口：对当前测速结果做数据中心聚合（详情 Tab 的「开始探测」按钮）。
     * tcping 模式的结果不带 colo，先并发逐 IP trace 探测归属（对齐 CFData 官方模式扫描阶段）；
     * httping 模式结果已携带 colo，直接聚合。
     */
    fun startDcTest() = startDcTestInternal(autoStart = false)

    /**
     * 聚合 + 可选自动测速（对齐 CFData officialAutoSpeed：扫描完成后自动选 DC 开始详细测试；
     * officialTargetDc 非空时选指定 DC，否则选最低延迟 DC）。
     */
    private fun startDcTestInternal(autoStart: Boolean) {
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
            dcEnriched = enriched
            refreshScanRows()
            if (dcList.isEmpty()) {
                _dcState.update {
                    it.copy(
                        step = DcStep.ERROR,
                        message = "未获取到任何数据中心信息（trace 探测全部失败），请检查网络后重试",
                    )
                }
                return@launch
            }
            log("数据中心聚合完成：${dcList.size} 个 DC")
            _dcState.update { it.copy(step = DcStep.DC_LIST, dcList = dcList, probeTotal = missing.size) }
            if (autoStart) {
                val target = _settings.value.officialTargetDc.trim().uppercase(Locale.US)
                val dc = dcList.find { it.colo.uppercase(Locale.US) == target } ?: dcList.first()
                log("自动测速：选择数据中心 ${dc.colo}")
                testDc(dc.colo)
            }
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

    /** 用位置表刷新扫描结果行的落地区域/地区/城市列 */
    private fun refreshScanRows() {
        val items = if (dcEnriched.isNotEmpty()) dcEnriched else _uiState.value.results
        _scanRows.value = items.map { ScanRow.from(it, dcLocations) }
    }

    /** 对选中数据中心的 IP 做二次详细测试（tcping×10 / httping×3） */
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
        val delayMs = _settings.value.delayMs
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
                                    country = loc?.country.orEmpty().ifEmpty { loc?.cca2.orEmpty() },
                                    region = loc?.region.orEmpty(),
                                    city = loc?.city.orEmpty(),
                                    minMs = stats.minMs,
                                    avgMs = stats.avgMs,
                                    maxMs = stats.maxMs,
                                    lossRate = (stats.attempts - stats.received).toDouble() / stats.attempts,
                                    attempts = stats.attempts,
                                )
                                val snapshot = mutex.withLock {
                                    out.add(entry)
                                    out.toMutableList().also { sortDcResults(it, dcSortKey) }
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
                _dcState.update {
                    it.copy(
                        step = DcStep.DONE,
                        testedDcs = it.testedDcs + (colo ?: ""),
                        message = "测试完成",
                    )
                }
            }
        }
    }

    // ------------------------------------------------------ 批量测速（一键测速/继续测速）

    /**
     * 对详细测试结果批量下载测速（对齐 CFData startOfficialBatchSpeed）：
     * [continued]=true 时跳过已有测速结果的 IP（继续测速）。
     * 引擎以「快速 ping + 下载测速」会话实现：ping_times=1、延迟上限放宽，仅取下载速度。
     */
    fun batchSpeedTest(continued: Boolean) {
        if (_dcState.value.speedBatchRunning) return
        val existing = _dcState.value.results
        if (existing.isEmpty()) {
            _toast.tryEmit("当前没有可测速的结果")
            return
        }
        val targets = if (continued) existing.filter { it.speedMbps == null } else existing
        if (targets.isEmpty()) {
            _toast.tryEmit("没有未测速的 IP，全部已测过")
            return
        }
        val s = _settings.value
        if (!s.downloadEnabled) {
            _toast.tryEmit("请先在参数区开启测速（测速结果数量 > 0）")
            return
        }
        dcJob?.cancel()
        dcJob = viewModelScope.launch(Dispatchers.IO) {
            _dcState.update {
                it.copy(speedBatchRunning = true, speedBatchDone = 0, speedBatchTotal = targets.size)
            }
            log(if (continued) "继续测速：${targets.size} 个未测 IP" else "一键测速：${targets.size} 个 IP")
            val ipText = targets.joinToString(",") { it.address() }
            val config = s.copy(
                pingMode = UiSettings.PING_TCP,
                pingTimes = 1,
                maxDelayMs = 100_000,
                maxLossRate = 1.0,
            ).toCfstConfig().copy(
                ipText = ipText,
                testCount = targets.size,
                printNum = targets.size,
                httpingColo = "",
            )
            val configJson = ProtocolJson.json.encodeToString(
                com.masgzy.cfstmobile.model.CfstConfig.serializer(),
                config,
            )
            val current = try {
                CfstEngineFactory.create(configJson)
            } catch (e: Exception) {
                _dcState.update { it.copy(speedBatchRunning = false, message = "创建测速引擎失败：${e.message}") }
                return@launch
            }
            speedSession = current
            try {
                if (!current.start()) {
                    _dcState.update { it.copy(speedBatchRunning = false, message = "引擎已在运行中") }
                    return@launch
                }
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    val progress = current.poll()
                    progress.download?.let { dl ->
                        _dcState.update {
                            it.copy(speedBatchDone = dl.tested.toInt(), speedBatchTotal = dl.total.toInt().coerceAtLeast(targets.size))
                        }
                    }
                    if (progress.phase == "done" || progress.phase == "error") break
                }
                val result = current.takeResult()
                val speeds = result?.items?.associate { it.address() to it.downloadSpeedMbps } ?: emptyMap()
                val merged = _dcState.value.results.map { item ->
                    speeds[item.address()]?.let { v -> item.copy(speedMbps = v) } ?: item
                }.toMutableList().also { sortDcResults(it, dcSortKey) }
                _dcState.update {
                    it.copy(
                        results = merged,
                        speedBatchRunning = false,
                        message = "测速完成：${speeds.count { v -> v.value != null }} 个 IP 获得速度",
                    )
                }
                log("批量测速完成：${speeds.size} 个结果")
            } catch (e: CancellationException) {
                runCatching { current.stop() }
                _dcState.update { it.copy(speedBatchRunning = false) }
                throw e
            } catch (e: Exception) {
                _dcState.update { it.copy(speedBatchRunning = false, message = "测速失败：${e.message}") }
            } finally {
                speedSession = null
                runCatching { current.close() }
            }
        }
    }

    /** 单行测速（对齐 CFData 行内「测速」按钮） */
    fun speedTestOne(item: DcTestItem) {
        if (_dcState.value.speedBatchRunning) return
        dcJob?.cancel()
        dcJob = viewModelScope.launch(Dispatchers.IO) {
            _dcState.update {
                it.copy(speedBatchRunning = true, speedBatchDone = 0, speedBatchTotal = 1, selectedDc = item.colo)
            }
            log("单行测速：${item.address()}")
            val s = _settings.value
            val config = s.copy(
                pingMode = UiSettings.PING_TCP,
                pingTimes = 1,
                maxDelayMs = 100_000,
                maxLossRate = 1.0,
            ).toCfstConfig().copy(
                ipText = item.address(),
                testCount = 1,
                printNum = 1,
                httpingColo = "",
            )
            val configJson = ProtocolJson.json.encodeToString(
                com.masgzy.cfstmobile.model.CfstConfig.serializer(),
                config,
            )
            val current = try {
                CfstEngineFactory.create(configJson)
            } catch (e: Exception) {
                _dcState.update { it.copy(speedBatchRunning = false, message = "创建测速引擎失败：${e.message}") }
                return@launch
            }
            speedSession = current
            try {
                if (!current.start()) {
                    _dcState.update { it.copy(speedBatchRunning = false, message = "引擎已在运行中") }
                    return@launch
                }
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    val progress = current.poll()
                    if (progress.phase == "done" || progress.phase == "error") break
                }
                val speed = current.takeResult()?.items?.firstOrNull()?.downloadSpeedMbps
                val merged = _dcState.value.results.map {
                    if (it.address() == item.address()) it.copy(speedMbps = speed ?: it.speedMbps) else it
                }.toMutableList().also { sortDcResults(it, dcSortKey) }
                _dcState.update {
                    it.copy(
                        results = merged,
                        speedBatchRunning = false,
                        message = if (speed != null) "${item.address()} → ${"%.2f".format(speed)} MB/s" else "${item.address()} 测速无结果",
                    )
                }
            } catch (e: CancellationException) {
                runCatching { current.stop() }
                _dcState.update { it.copy(speedBatchRunning = false) }
                throw e
            } catch (e: Exception) {
                _dcState.update { it.copy(speedBatchRunning = false, message = "测速失败：${e.message}") }
            } finally {
                speedSession = null
                runCatching { current.close() }
            }
        }
    }

    /** 一键排序（对齐 CFData sortModal：丢包率/最低/平均/最大延迟/速度） */
    fun sortDcResults(key: String) {
        dcSortKey = key
        _dcState.update {
            it.copy(results = it.results.toMutableList().also { list -> sortDcResults(list, key) })
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
     * 请求取消进行中的探测/测试/批量测速。
     * 测试中取消后保留已完成的部分结果呈现（对齐 CFData 终止语义）。
     */
    fun cancelDcTest() {
        val job = dcJob ?: return
        dcJob = null
        job.cancel()
        _dcState.update { st ->
            when {
                st.speedBatchRunning -> st.copy(speedBatchRunning = false)
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
        _scanRows.value = emptyList()
    }

    // ------------------------------------------------------ 网络工具

    /** 拉取文本并按行拆分（跳过空行与注释行，限制 2MB 防止超大文件拖垮 UI） */
    private fun fetchTextLines(url: String): List<String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        try {
            conn.inputStream.use { input ->
                val text = input.readBytes().toString(Charsets.UTF_8)
                return text.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            }
        } finally {
            conn.disconnect()
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
