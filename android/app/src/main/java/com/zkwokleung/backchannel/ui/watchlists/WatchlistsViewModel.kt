package com.zkwokleung.backchannel.ui.watchlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.data.WatchlistRepository
import com.zkwokleung.backchannel.data.db.WatchlistEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchlistsViewModel(private val repository: WatchlistRepository) : ViewModel() {

    val watchlists: StateFlow<List<WatchlistEntity>> = repository.observeWatchlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { repository.create(name.trim()) }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { repository.rename(id, name.trim()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
