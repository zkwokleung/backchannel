package com.zkwokleung.backchannel.playback

import androidx.media3.session.MediaController
import com.zkwokleung.backchannel.data.PlaybackRepository
import com.zkwokleung.backchannel.engine.StreamMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QueueEntry(
    val videoId: String,
    val title: String,
    val channelTitle: String?,
    val thumbnail: String?,
)

/** Builds queues and starts playback on the session player, honoring saved resume positions. */
class QueuePlayer(
    private val connection: PlayerConnection,
    private val playbackRepository: PlaybackRepository,
    private val scope: CoroutineScope,
) {
    private suspend fun awaitController(): MediaController {
        connection.connect()
        return connection.controller.filterNotNull().first()
    }

    /** Plays [entries] starting at [startIndex]; resumes the start item at its saved position. */
    fun playQueue(
        entries: List<QueueEntry>,
        startIndex: Int,
        mode: StreamMode = StreamMode.AUDIO,
    ) {
        if (entries.isEmpty()) return
        scope.launch {
            val index = startIndex.coerceIn(entries.indices)
            val resumeMs = playbackRepository.resumePositionMillis(entries[index].videoId)
            val items = entries.map {
                PlaybackItems.build(it.videoId, it.title, it.channelTitle, it.thumbnail, mode)
            }
            val controller = awaitController()
            withContext(Dispatchers.Main) {
                controller.setMediaItems(items, index, resumeMs)
                controller.prepare()
                controller.play()
            }
        }
    }

    /** Switches the current item between audio and video streams, keeping position. */
    fun switchMode(mode: StreamMode) {
        scope.launch {
            val controller = awaitController()
            withContext(Dispatchers.Main) {
                val index = controller.currentMediaItemIndex
                val item = controller.currentMediaItem ?: return@withContext
                if (PlaybackItems.modeOf(item) == mode) return@withContext
                val position = controller.currentPosition
                controller.replaceMediaItem(index, PlaybackItems.withMode(item, mode))
                controller.seekTo(index, position)
                controller.prepare()
                controller.play()
            }
        }
    }
}
