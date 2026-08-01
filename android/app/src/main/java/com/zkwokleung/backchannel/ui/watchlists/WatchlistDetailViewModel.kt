package com.zkwokleung.backchannel.ui.watchlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.data.WatchlistRepository
import com.zkwokleung.backchannel.data.db.WatchlistEntity
import com.zkwokleung.backchannel.data.db.WatchlistItemEntity
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.playback.QueueEntry
import com.zkwokleung.backchannel.playback.QueuePlayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchlistDetailViewModel(
    private val watchlistId: Long,
    private val repository: WatchlistRepository,
    private val queuePlayer: QueuePlayer,
) : ViewModel() {

    val watchlist: StateFlow<WatchlistEntity?> = repository.observeWatchlist(watchlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<WatchlistItemEntity>> = repository.observeItems(watchlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(startItem: WatchlistItemEntity, mode: StreamMode) {
        val list = items.value
        val index = list.indexOfFirst { it.id == startItem.id }.coerceAtLeast(0)
        queuePlayer.playQueue(
            entries = list.map {
                QueueEntry(it.videoYoutubeId, it.title, it.channelTitle, it.thumbnail)
            },
            startIndex = index,
            mode = mode,
        )
    }

    fun remove(item: WatchlistItemEntity) {
        viewModelScope.launch { repository.removeItem(item.id) }
    }

    fun move(item: WatchlistItemEntity, up: Boolean) {
        viewModelScope.launch {
            val current = repository.getItems(watchlistId).toMutableList()
            val index = current.indexOfFirst { it.id == item.id }
            if (index < 0) return@launch
            val target = if (up) index - 1 else index + 1
            if (target !in current.indices) return@launch
            val moved = current.removeAt(index)
            current.add(target, moved)
            repository.reorder(current.map { it.id })
        }
    }
}
