package com.zkwokleung.backchannel.download

import com.zkwokleung.backchannel.engine.StreamMode
import java.io.File

/**
 * Fetches a video's media to disk. Narrow on purpose, like `ChannelSource`, so the download
 * queue can be exercised with a fake instead of the yt-dlp runtime.
 */
interface MediaDownloader {
    /**
     * Downloads into [targetDir] and returns the finished file. [onProgress] receives whole
     * percentages, or a negative value while the total is unknown. Throws
     * [DownloadCancelledException] when [cancelDownload] was called with the same [processId].
     */
    suspend fun download(
        videoId: String,
        mode: StreamMode,
        targetDir: File,
        processId: String,
        onProgress: (percent: Int, etaSeconds: Long) -> Unit,
    ): File

    fun cancelDownload(processId: String): Boolean
}

class DownloadCancelledException : Exception("Download cancelled")

class DiskFullException : Exception("No space left on device")
