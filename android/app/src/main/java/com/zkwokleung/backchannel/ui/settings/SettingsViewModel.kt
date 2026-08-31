package com.zkwokleung.backchannel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.data.DownloadRepository
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.download.DownloadManager
import com.zkwokleung.backchannel.engine.YtdlpEngine
import com.zkwokleung.backchannel.update.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EngineUpdateUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
)

/** What the Downloads rows say: how many finished copies exist and what they weigh. */
data class DownloadsSummary(val count: Int = 0, val bytes: Long = 0) {
    val isEmpty: Boolean get() = count == 0
}

class SettingsViewModel(
    private val engine: YtdlpEngine,
    private val updater: AppUpdater,
    downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    val downloads: StateFlow<DownloadsSummary> = downloadRepository.observeAll()
        .map { rows ->
            val complete = rows.filter { it.status == DownloadStatus.COMPLETE }
            DownloadsSummary(complete.size, complete.sumOf { it.sizeBytes })
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsSummary())

    /** Runs on the application scope inside DownloadManager, like the app updater below. */
    fun deleteAllDownloads() = downloadManager.removeAll()

    val initState: StateFlow<YtdlpEngine.InitState> = engine.initState

    private val _engineUpdate = MutableStateFlow(EngineUpdateUiState())
    val engineUpdate: StateFlow<EngineUpdateUiState> = _engineUpdate.asStateFlow()

    fun retryInit() {
        viewModelScope.launch { engine.initialize() }
    }

    fun updateYtdlp() {
        if (_engineUpdate.value.inProgress) return
        _engineUpdate.value = EngineUpdateUiState(inProgress = true)
        viewModelScope.launch {
            _engineUpdate.value = try {
                val version = engine.update()
                EngineUpdateUiState(message = "yt-dlp updated to ${version ?: "the latest version"}")
            } catch (t: Throwable) {
                EngineUpdateUiState(message = t.message ?: "Update failed")
            }
        }
    }

    fun consumeMessage() {
        _engineUpdate.value = _engineUpdate.value.copy(message = null)
    }

    // ── App updates ───────────────────────────────────────────────────────────
    //
    // Straight pass-through, on purpose. The work lives in AppUpdater on the application scope:
    // viewModelScope dies with this screen's nav entry, which is exactly what happens when the
    // user tabs away mid-download.

    val appVersion: String get() = updater.currentVersion
    val appUpdate: StateFlow<AppUpdater.State> = updater.state

    fun checkForAppUpdate() = updater.check()
    fun downloadAppUpdate() = updater.download()
    fun cancelAppUpdate() = updater.cancel()
    fun installAppUpdate() = updater.install()
    fun onConfirmationLaunched() = updater.onConfirmationLaunched()
    fun refreshInstallPermission() = updater.refreshInstallPermission()
    fun unknownSourcesIntent() = updater.unknownSourcesIntent()
}
