package com.zkwokleung.backchannel.ui.watchlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.data.DownloadRepository
import com.zkwokleung.backchannel.data.DownloadRequest
import com.zkwokleung.backchannel.data.PlaybackRepository
import com.zkwokleung.backchannel.data.WatchlistRepository
import com.zkwokleung.backchannel.data.db.WatchlistEntity
import com.zkwokleung.backchannel.data.db.WatchlistItemEntity
import com.zkwokleung.backchannel.download.DownloadManager
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.playback.QueueEntry
import com.zkwokleung.backchannel.playback.QueuePlayer
import com.zkwokleung.backchannel.ui.common.DownloadStateUi
import com.zkwokleung.backchannel.ui.common.PlaybackProgressUi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A queued item plus how far through it the listener already is, and its saved copy. */
data class WatchlistRowUi(
    val item: WatchlistItemEntity,
    val progress: PlaybackProgressUi,
    val download: DownloadStateUi,
)

class WatchlistDetailViewModel(
    private val watchlistId: Long,
    private val repository: WatchlistRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val queuePlayer: QueuePlayer,
) : ViewModel() {

    val watchlist: StateFlow<WatchlistEntity?> = repository.observeWatchlist(watchlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rows: StateFlow<List<WatchlistRowUi>> = combine(
        repository.observeItems(watchlistId),
        playbackRepository.observeAll(),
        downloadRepository.observeAll(),
    ) { items, states, downloads ->
        val byId = states.associateBy { it.videoYoutubeId }
        val downloadById = downloads.associateBy { it.videoYoutubeId }
        items.map { item ->
            WatchlistRowUi(
                item,
                PlaybackProgressUi.of(byId[item.videoYoutubeId], item.durationSeconds),
                DownloadStateUi.of(downloadById[item.videoYoutubeId]),
            )
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(startItem: WatchlistItemEntity, mode: StreamMode) {
        // rows, not a second flow — see ChannelDetailViewModel.play.
        val list = rows.value.map { it.item }
        val index = list.indexOfFirst { it.id == startItem.id }.coerceAtLeast(0)
        queuePlayer.playQueue(
            entries = list.map {
                QueueEntry(it.videoYoutubeId, it.title, it.channelTitle, it.thumbnail)
            },
            startIndex = index,
            mode = mode,
        )
    }

    fun download(item: WatchlistItemEntity, mode: StreamMode) {
        downloadManager.enqueue(
            DownloadRequest(
                videoId = item.videoYoutubeId,
                title = item.title,
                channelTitle = item.channelTitle,
                thumbnail = item.thumbnail,
                durationSeconds = item.durationSeconds,
                mode = mode,
            )
        )
    }

    fun cancelDownload(item: WatchlistItemEntity) = downloadManager.cancel(item.videoYoutubeId)

    fun removeDownload(item: WatchlistItemEntity) = downloadManager.remove(item.videoYoutubeId)

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
