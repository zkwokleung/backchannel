package com.zkwokleung.backchannel.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.engine.YtdlpEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val engine: YtdlpEngine) : ViewModel() {

    val initState: StateFlow<YtdlpEngine.InitState> = engine.initState

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    fun retryInit() {
        viewModelScope.launch { engine.initialize() }
    }

    fun updateYtdlp() {
        if (_updateState.value.inProgress) return
        _updateState.value = UpdateUiState(inProgress = true)
        viewModelScope.launch {
            _updateState.value = try {
                val version = engine.update()
                UpdateUiState(message = "yt-dlp updated to ${version ?: "the latest version"}")
            } catch (t: Throwable) {
                UpdateUiState(message = t.message ?: "Update failed")
            }
        }
    }

    fun consumeMessage() {
        _updateState.value = _updateState.value.copy(message = null)
    }
}
