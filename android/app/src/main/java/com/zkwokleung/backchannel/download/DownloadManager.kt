package com.zkwokleung.backchannel.download

import android.content.Context
import android.util.Log
import com.zkwokleung.backchannel.data.DownloadRepository
import com.zkwokleung.backchannel.data.DownloadRequest
import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.playback.QueuePlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Works through the `downloads` table one item at a time. Room is the queue: the UI enqueues
 * rows, this picks the oldest pending one, and every state change is written back so the
 * lists, the notification and a relaunch all read the same truth.
 *
 * Lives on `AppContainer.applicationScope` for the same reason `AppUpdater` does — a transfer
 * must not die with the screen that started it.
 */
class DownloadManager(
    private val appContext: Context,
    private val repository: DownloadRepository,
    private val downloader: MediaDownloader,
    private val queuePlayer: QueuePlayer,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** The row currently transferring, else the next queued one, else null. Drives the notification. */
    val active: Flow<DownloadEntity?> = repository.observeAll()
        .map { rows ->
            rows.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                ?: rows.filter { it.status == DownloadStatus.QUEUED }.minByOrNull { it.createdAt }
        }
        .distinctUntilChanged()

    /**
     * Wakes the worker. Conflated on purpose: a poke that lands while the worker is busy is
     * remembered (once), so a row enqueued in the instant the queue looked empty is never lost
     * to a start-a-new-worker race.
     */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var activeVideoId: String? = null

    @Volatile
    private var activeProcessId: String? = null

    /** Cancels asked for before yt-dlp had a process to kill, honoured when it would have started. */
    private val cancelRequested: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    init {
        scope.launch(dispatcher) { runQueue() }
    }

    fun enqueue(request: DownloadRequest) {
        scope.launch {
            cancelRequested.remove(request.videoId)
            repository.enqueue(request)
            wake.trySend(Unit)
        }
    }

    fun retry(videoId: String) {
        scope.launch {
            repository.retry(videoId)
            wake.trySend(Unit)
        }
    }

    /** A cancelled download is dropped entirely; there is nothing worth keeping about it. */
    fun cancel(videoId: String) {
        scope.launch {
            if (videoId == activeVideoId) {
                cancelRequested.add(videoId)
                activeProcessId?.let(downloader::cancelDownload)
            } else {
                repository.remove(videoId)
            }
        }
    }

    /** Deleting the copy that is playing right now hands that item back to streaming. */
    fun remove(videoId: String) {
        scope.launch {
            if (videoId == activeVideoId) {
                cancelRequested.add(videoId)
                activeProcessId?.let(downloader::cancelDownload)
            } else {
                repository.remove(videoId)
                queuePlayer.reloadItem(videoId)
            }
        }
    }

    fun removeAll() {
        scope.launch {
            activeVideoId?.let { id ->
                cancelRequested.add(id)
                activeProcessId?.let(downloader::cancelDownload)
            }
            withContext(NonCancellable) { repository.removeAll() }
            queuePlayer.reloadItem(videoId = null)
        }
    }

    suspend fun resumeOnLaunch() {
        repository.requeueInterrupted()
        wake.trySend(Unit)
    }

    private suspend fun runQueue() {
        var serviceStarted = false
        while (true) {
            val next = repository.nextPending()
            if (next == null) {
                serviceStarted = false
                wake.receive()
                continue
            }
            if (!serviceStarted) serviceStarted = DownloadService.start(appContext)
            runOne(next)
        }
    }

    private suspend fun runOne(row: DownloadEntity) {
        val id = row.videoYoutubeId
        if (cancelRequested.remove(id)) {
            repository.remove(id)
            return
        }
        if (repository.downloadsDir.apply { mkdirs() }.usableSpace < MIN_FREE_BYTES) {
            repository.markFailed(id, DownloadFailure.DiskFull.message)
            return
        }
        repository.markDownloading(id)
        val processId = "download:$id"
        activeVideoId = id
        activeProcessId = processId
        val progress = MutableStateFlow(0)
        val progressWriter = scope.launch(dispatcher) {
            progress.collect { repository.updateProgress(id, it) }
        }
        try {
            val file = downloader.download(id, row.mode, repository.downloadsDir, processId) { percent, _ ->
                if (percent >= 0) progress.value = percent.coerceAtMost(100)
            }
            progressWriter.cancel()
            if (cancelRequested.remove(id)) repository.remove(id) else repository.markComplete(id, file)
        } catch (t: Throwable) {
            progressWriter.cancel()
            val failure = DownloadFailure.from(t)
            withContext(NonCancellable) {
                if (failure is DownloadFailure.Cancelled || cancelRequested.remove(id)) {
                    repository.remove(id)
                } else {
                    Log.w(TAG, "download failed for $id: ${failure.message}", t)
                    repository.markFailed(id, failure.message)
                }
            }
            if (t is CancellationException) throw t
        } finally {
            activeVideoId = null
            activeProcessId = null
        }
    }

    private companion object {
        const val TAG = "DownloadManager"
        const val MIN_FREE_BYTES = 200L * 1024 * 1024
    }
}
