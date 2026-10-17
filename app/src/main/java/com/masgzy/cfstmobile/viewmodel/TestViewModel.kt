package com.masgzy.cfstmobile.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.masgzy.cfstmobile.data.CloudflareIpSources
import com.masgzy.cfstmobile.data.HistoryRecord
import com.masgzy.cfstmobile.data.HistoryStore
import com.masgzy.cfstmobile.data.SettingsRepository
import com.masgzy.cfstmobile.data.UiSettings
import com.masgzy.cfstmobile.engine.CfstEngineFactory
import com.masgzy.cfstmobile.engine.CfstSession
import com.masgzy.cfstmobile.ffi.CfstFfi
import com.masgzy.cfstmobile.model.DownloadProgress
import com.masgzy.cfstmobile.model.PingProgress
import com.masgzy.cfstmobile.model.ProtocolJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

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

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(settingsRepo.load())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    private val _history = MutableStateFlow(historyStore.load())
    val history: StateFlow<List<HistoryRecord>> = _history.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /** 进行中的会话（跨线程引用仅用于 stop） */
    private var session: CfstSession? = null

    // ------------------------------------------------------------ 设置

    fun updateSettings(transform: (UiSettings) -> UiSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        settingsRepo.save(next)
    }

    // ------------------------------------------------------------ 测速

    fun startTest() {
        if (_uiState.value.isRunning) return
        val s = _settings.value
        s.validate()?.let { error ->
            _toast.tryEmit(error)
            return
        }
        val configJson = ProtocolJson.json.encodeToString(
            com.masgzy.cfstmobile.model.CfstConfig.serializer(),
            s.toCfstConfig(),
        )
        _uiState.value = TestUiState(isRunning = true, isMock = !CfstFfi.available)

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
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    val progress = current.poll()
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
            val s = _settings.value
            val summary = "${s.tcpPort}/${s.pingMode}" + if (s.enableDownload) " · 下载" else " · 仅延迟"
            _history.value = historyStore.add(
                HistoryRecord(System.currentTimeMillis(), summary, result.items),
                _history.value,
            )
        } else if (_uiState.value.phase != "error") {
            _uiState.update { it.copy(message = it.message ?: "测速结束，但没有得到结果") }
        }
    }

    /** 协作式停止：引擎在检查点收尾并保留部分结果 */
    fun stopTest() {
        session?.stop()
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
                        _toast.tryEmit("已获取 ${lines.size} 个 IP 段，已切换为自定义 IP 来源")
                    }
                },
                onFailure = { e ->
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

        val Factory = viewModelFactory {
            initializer {
                TestViewModel(this[APPLICATION_KEY] as Application)
            }
        }
    }
}
