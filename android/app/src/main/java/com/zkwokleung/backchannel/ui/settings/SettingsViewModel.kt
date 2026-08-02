package com.zkwokleung.backchannel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.engine.YtdlpEngine
import com.zkwokleung.backchannel.update.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EngineUpdateUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val engine: YtdlpEngine,
    private val updater: AppUpdater,
) : ViewModel() {

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
