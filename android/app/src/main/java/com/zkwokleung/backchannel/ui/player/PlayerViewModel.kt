package com.zkwokleung.backchannel.ui.player

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.playback.PlaybackItems
import com.zkwokleung.backchannel.playback.PlayerConnection
import com.zkwokleung.backchannel.playback.QueuePlayer
import com.zkwokleung.backchannel.ui.common.appActivityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The one player ViewModel for the whole Activity.
 *
 * Every player surface — the mini-player, Now Playing, the video screen — goes through this, so
 * they share one state and attach exactly one listener to the MediaController.
 */
@Composable
fun sharedPlayerViewModel(): PlayerViewModel =
    appActivityViewModel { PlayerViewModel(it.playerConnection, it.queuePlayer) }

data class PlayerUiState(
    val connected: Boolean = false,
    val hasItem: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val mode: StreamMode = StreamMode.AUDIO,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val speed: Float = 1f,
    val error: String? = null,
)

/** An upcoming item, built from the session timeline. */
data class QueueItemUi(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
)

class PlayerViewModel(
    private val connection: PlayerConnection,
    private val queuePlayer: QueuePlayer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val controller: StateFlow<MediaController?> = connection.controller

    private val _queue = MutableStateFlow<List<QueueItemUi>>(emptyList())

    /** What plays after the current item. Kept out of [uiState] so the position ticker,
     *  which fires twice a second, never invalidates it. */
    val queue: StateFlow<List<QueueItemUi>> = _queue.asStateFlow()

    private var listener: Player.Listener? = null
    private var attachedController: MediaController? = null

    init {
        connection.connect()
        viewModelScope.launch {
            connection.controller.collect { controller ->
                if (controller != null && controller !== attachedController) {
                    attach(controller)
                }
            }
        }
        // Position ticker.
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                attachedController?.let { c ->
                    _uiState.value = _uiState.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.takeIf { it > 0 } ?: _uiState.value.durationMs,
                    )
                }
            }
        }
    }

    private fun attach(controller: MediaController) {
        attachedController?.let { old -> listener?.let(old::removeListener) }
        attachedController = controller
        val newListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncFrom(player)
                // Rebuilding a 100-item list on every position discontinuity would be pure
                // waste; the queue only changes on these two events.
                if (events.containsAny(
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                    )
                ) {
                    _queue.value = readQueue(player)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _uiState.value = _uiState.value.copy(error = readablePlaybackError(error))
            }
        }
        controller.addListener(newListener)
        listener = newListener
        syncFrom(controller)
        _queue.value = readQueue(controller)
    }

    private fun syncFrom(player: Player) {
        val item: MediaItem? = player.currentMediaItem
        _uiState.value = _uiState.value.copy(
            connected = true,
            hasItem = item != null,
            mediaId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString() ?: "",
            artist = item?.mediaMetadata?.artist?.toString(),
            artworkUri = item?.mediaMetadata?.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            positionMs = player.currentPosition.coerceAtLeast(0),
            mode = item?.let(PlaybackItems::modeOf) ?: StreamMode.AUDIO,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            speed = player.playbackParameters.speed,
        )
    }

    /**
     * Upcoming items only. Reads nothing but `mediaId` and `mediaMetadata`: those are the fields
     * guaranteed to survive the session boundary, whereas a MediaItem's local configuration is
     * stripped when a timeline crosses it.
     */
    private fun readQueue(player: Player): List<QueueItemUi> {
        if (!player.isCommandAvailable(Player.COMMAND_GET_TIMELINE)) return emptyList()
        val start = player.currentMediaItemIndex + 1
        if (start >= player.mediaItemCount) return emptyList()
        return (start until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            QueueItemUi(
                index = index,
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString(),
                artworkUri = item.mediaMetadata.artworkUri?.toString(),
            )
        }
    }

    fun playPause() {
        val c = attachedController ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() {
        attachedController?.seekToNextMediaItem()
    }

    fun previous() {
        attachedController?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        attachedController?.seekTo(positionMs)
    }

    fun seekBy(deltaMs: Long) {
        val c = attachedController ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0))
    }

    /** Jumps to a queued item. A SEEK-reason transition, so skipped items are not marked played. */
    fun playQueueItem(index: Int) {
        attachedController?.seekToDefaultPosition(index)
    }

    fun setSpeed(speed: Float) {
        val c = attachedController ?: return
        if (c.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) c.setPlaybackSpeed(speed)
    }

    fun switchMode(mode: StreamMode) {
        queuePlayer.switchMode(mode)
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(error = null)
        val c = attachedController ?: return
        c.prepare()
        c.play()
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        attachedController?.let { c -> listener?.let(c::removeListener) }
    }
}
