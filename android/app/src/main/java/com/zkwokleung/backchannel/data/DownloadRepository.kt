package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.DownloadDao
import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.engine.StreamMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** What the UI knows about a video when the user asks to save it. */
data class DownloadRequest(
    val videoId: String,
    val title: String,
    val channelTitle: String?,
    val thumbnail: String?,
    val durationSeconds: Long?,
    val mode: StreamMode,
)

data class LocalMedia(val file: File, val mode: StreamMode)

/**
 * Which saved file, if any, can serve a playback request. A video download is one muxed mp4
 * carrying both tracks, so it answers audio requests too; an audio download can only answer
 * audio, and a video request for it falls back to streaming.
 */
object LocalMediaSelection {
    fun resolve(downloaded: LocalMedia?, requested: StreamMode): File? {
        val media = downloaded ?: return null
        if (requested == StreamMode.VIDEO && media.mode != StreamMode.VIDEO) return null
        return media.file
    }
}

/**
 * Owns the `downloads` table and the files under [downloadsDir]. Files are named
 * `<videoId>.<ext>` (yt-dlp adds `.fNNN` and `.part` suffixes while working), so everything
 * belonging to a video shares the prefix before the first dot.
 */
class DownloadRepository(
    private val dao: DownloadDao,
    val downloadsDir: File,
    indexScope: CoroutineScope,
) {

    /**
     * Completed downloads by video id, kept hot because the player's media-source factory asks
     * synchronously on the main thread and cannot wait on a query.
     */
    private val localIndex: StateFlow<Map<String, LocalMedia>> = dao.observeAll()
        .map { rows ->
            rows.filter { it.status == DownloadStatus.COMPLETE && it.filePath != null }
                .associate { it.videoYoutubeId to LocalMedia(File(it.filePath!!), it.mode) }
        }
        .stateIn(indexScope, SharingStarted.Eagerly, emptyMap())

    /** Synchronous, main-thread-safe lookup for the player. Null means stream it. */
    fun localFileFor(videoId: String, mode: StreamMode): File? =
        LocalMediaSelection.resolve(localIndex.value[videoId], mode)?.takeIf { it.isFile }

    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observe(videoId: String): Flow<DownloadEntity?> = dao.observe(videoId)

    fun observeTotalBytes(): Flow<Long> = dao.observeTotalBytes()

    suspend fun get(videoId: String): DownloadEntity? = dao.get(videoId)

    suspend fun nextPending(): DownloadEntity? = dao.nextPending()

    suspend fun enqueue(request: DownloadRequest) {
        deleteFilesFor(request.videoId)
        dao.upsert(
            DownloadEntity(
                videoYoutubeId = request.videoId,
                mode = request.mode,
                status = DownloadStatus.QUEUED,
                title = request.title,
                channelTitle = request.channelTitle,
                thumbnail = request.thumbnail,
                durationSeconds = request.durationSeconds,
                filePath = null,
                sizeBytes = 0,
                progressPercent = 0,
                error = null,
                createdAt = System.currentTimeMillis(),
                completedAt = null,
            )
        )
    }

    suspend fun retry(videoId: String) = dao.setStatus(videoId, DownloadStatus.QUEUED)

    suspend fun markDownloading(videoId: String) = dao.setStatus(videoId, DownloadStatus.DOWNLOADING)

    suspend fun updateProgress(videoId: String, percent: Int) =
        dao.updateProgress(videoId, percent.coerceIn(0, 100))

    suspend fun markComplete(videoId: String, file: File) =
        dao.markComplete(videoId, file.absolutePath, file.length(), System.currentTimeMillis())

    suspend fun markFailed(videoId: String, message: String) {
        deleteFilesFor(videoId)
        dao.markFailed(videoId, message)
    }

    suspend fun remove(videoId: String) {
        deleteFilesFor(videoId)
        dao.delete(videoId)
    }

    suspend fun removeAll() {
        downloadsDir.listFiles()?.forEach { it.delete() }
        dao.deleteAll()
    }

    /**
     * Cold-start recovery: a row still marked DOWNLOADING means the process died mid-transfer.
     * It goes back in the queue and its partial files are swept so the next attempt starts clean.
     */
    suspend fun requeueInterrupted() {
        dao.requeueInterrupted()
        downloadsDir.listFiles { file -> file.name.endsWith(".part") }?.forEach { it.delete() }
    }

    /**
     * The playable local copy, or null when there isn't one. A COMPLETE row whose file has gone
     * missing is downgraded to FAILED so the UI offers a retry instead of a phantom "Saved".
     */
    suspend fun localMediaFor(videoId: String): LocalMedia? {
        val row = dao.get(videoId) ?: return null
        if (row.status != DownloadStatus.COMPLETE) return null
        val file = row.filePath?.let(::File)
        if (file == null || !file.isFile) {
            dao.markFailed(videoId, MISSING_FILE_MESSAGE)
            return null
        }
        return LocalMedia(file, row.mode)
    }

    private fun deleteFilesFor(videoId: String) {
        downloadsDir.listFiles { file -> file.name.substringBefore('.') == videoId }
            ?.forEach { it.delete() }
    }

    companion object {
        const val MISSING_FILE_MESSAGE = "The saved file is missing. Download it again."
    }
}
