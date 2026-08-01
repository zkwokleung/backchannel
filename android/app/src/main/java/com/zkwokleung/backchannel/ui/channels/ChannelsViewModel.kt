package com.zkwokleung.backchannel.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.data.ChannelRepository
import com.zkwokleung.backchannel.data.db.ChannelEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AddChannelState(
    val inProgress: Boolean = false,
    val error: String? = null,
)

class ChannelsViewModel(private val repository: ChannelRepository) : ViewModel() {

    val channels: StateFlow<List<ChannelEntity>> = repository.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addState = MutableStateFlow(AddChannelState())
    val addState: StateFlow<AddChannelState> = _addState.asStateFlow()

    /** Returns via [onDone] whether the add succeeded (dialog closes on success). */
    fun addChannel(handleOrUrl: String, onDone: (Boolean) -> Unit) {
        if (handleOrUrl.isBlank() || _addState.value.inProgress) return
        _addState.value = AddChannelState(inProgress = true)
        viewModelScope.launch {
            try {
                repository.addChannel(handleOrUrl.trim())
                _addState.value = AddChannelState()
                onDone(true)
            } catch (t: Throwable) {
                _addState.value = AddChannelState(error = t.message ?: "Could not add channel")
                onDone(false)
            }
        }
    }

    fun clearAddError() {
        _addState.value = _addState.value.copy(error = null)
    }

    fun removeChannel(youtubeId: String) {
        viewModelScope.launch { repository.removeChannel(youtubeId) }
    }
}
