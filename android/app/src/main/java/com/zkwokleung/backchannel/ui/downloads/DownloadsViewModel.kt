package com.zkwokleung.backchannel.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.data.DownloadRepository
import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.download.DownloadManager
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.playback.QueueEntry
import com.zkwokleung.backchannel.playback.QueuePlayer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Downloads screen: work still happening on top, finished copies below, storage total. */
data class DownloadsUi(
    val active: List<DownloadEntity>,
    val completed: List<DownloadEntity>,
    val totalBytes: Long,
) {
    val isEmpty: Boolean get() = active.isEmpty() && completed.isEmpty()

    companion object {
        val EMPTY = DownloadsUi(emptyList(), emptyList(), 0)

        fun of(rows: List<DownloadEntity>): DownloadsUi {
            val (completed, active) = rows.partition { it.status == DownloadStatus.COMPLETE }
            return DownloadsUi(
                active = active.sortedBy { it.createdAt },
                completed = completed.sortedByDescending { it.completedAt ?: it.createdAt },
                totalBytes = completed.sumOf { it.sizeBytes },
            )
        }
    }
}

class DownloadsViewModel(
    downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val queuePlayer: QueuePlayer,
) : ViewModel() {

    val ui: StateFlow<DownloadsUi> = downloadRepository.observeAll()
        .map(DownloadsUi::of)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUi.EMPTY)

    /** Plays the saved copies as one queue starting at [start]; audio mode works for every file. */
    fun play(start: DownloadEntity, mode: StreamMode) {
        val list = ui.value.completed
        val index = list.indexOfFirst { it.videoYoutubeId == start.videoYoutubeId }.coerceAtLeast(0)
        queuePlayer.playQueue(
            entries = list.map { QueueEntry(it.videoYoutubeId, it.title, it.channelTitle, it.thumbnail) },
            startIndex = index,
            mode = mode,
        )
    }

    fun retry(row: DownloadEntity) = downloadManager.retry(row.videoYoutubeId)
    fun cancel(row: DownloadEntity) = downloadManager.cancel(row.videoYoutubeId)
    fun remove(row: DownloadEntity) = downloadManager.remove(row.videoYoutubeId)
}
