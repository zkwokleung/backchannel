package com.zkwokleung.backchannel.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.data.ChannelRepository
import com.zkwokleung.backchannel.data.PlaybackRepository
import com.zkwokleung.backchannel.data.WatchlistRepository
import com.zkwokleung.backchannel.data.db.ChannelEntity
import com.zkwokleung.backchannel.data.db.VideoEntity
import com.zkwokleung.backchannel.data.db.WatchlistEntity
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.playback.QueueEntry
import com.zkwokleung.backchannel.playback.QueuePlayer
import com.zkwokleung.backchannel.ui.common.PlaybackProgressUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A cached upload plus how far through it the listener already is. */
data class VideoRowUi(
    val video: VideoEntity,
    val progress: PlaybackProgressUi,
)

class ChannelDetailViewModel(
    private val channelYoutubeId: String,
    private val channelRepository: ChannelRepository,
    private val watchlistRepository: WatchlistRepository,
    private val playbackRepository: PlaybackRepository,
    private val queuePlayer: QueuePlayer,
) : ViewModel() {

    val channel: StateFlow<ChannelEntity?> = channelRepository.observeChannel(channelYoutubeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Uploads joined with playback history, so rows can show what has already been heard. */
    val rows: StateFlow<List<VideoRowUi>> = combine(
        channelRepository.observeVideos(channelYoutubeId),
        playbackRepository.observeAll(),
    ) { videos, states ->
        val byId = states.associateBy { it.videoYoutubeId }
        videos.map { video ->
            VideoRowUi(video, PlaybackProgressUi.of(byId[video.youtubeId], video.durationSeconds))
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val watchlists: StateFlow<List<WatchlistEntity>> = watchlistRepository.observeWatchlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                channelRepository.refreshVideos(channelYoutubeId)
            } catch (t: Throwable) {
                _message.value = t.message ?: "Refresh failed"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Plays this channel's uploads as a queue starting at [video], in [mode]. */
    fun play(video: VideoEntity, mode: StreamMode) {
        // rows, not a second flow: the screen only collects rows, so an unsubscribed
        // WhileSubscribed flow would sit empty here and queue nothing.
        val list = rows.value.map { it.video }
        val index = list.indexOfFirst { it.youtubeId == video.youtubeId }.coerceAtLeast(0)
        val channelTitle = channel.value?.title
        queuePlayer.playQueue(
            entries = list.map {
                QueueEntry(it.youtubeId, it.title, channelTitle, it.thumbnail)
            },
            startIndex = index,
            mode = mode,
        )
    }

    fun addToWatchlist(video: VideoEntity, watchlistId: Long) {
        viewModelScope.launch {
            val added = watchlistRepository.addVideo(watchlistId, video, channel.value?.title)
            _message.value = if (added) "Added to watchlist" else "Already in that watchlist"
        }
    }

    fun createWatchlistAndAdd(name: String, video: VideoEntity) {
        viewModelScope.launch {
            val id = watchlistRepository.create(name)
            watchlistRepository.addVideo(id, video, channel.value?.title)
            _message.value = "Added to \"$name\""
        }
    }
}
