package com.zkwokleung.backchannel.ui

import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.ui.common.DownloadStateUi
import com.zkwokleung.backchannel.ui.downloads.DownloadsUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun row(
    id: String,
    status: DownloadStatus,
    percent: Int = 0,
    mode: StreamMode = StreamMode.AUDIO,
    sizeBytes: Long = 0,
    createdAt: Long = 0,
    completedAt: Long? = null,
    error: String? = null,
) = DownloadEntity(
    videoYoutubeId = id,
    mode = mode,
    status = status,
    title = id,
    channelTitle = null,
    thumbnail = null,
    durationSeconds = null,
    filePath = null,
    sizeBytes = sizeBytes,
    progressPercent = percent,
    error = error,
    createdAt = createdAt,
    completedAt = completedAt,
)

class DownloadStateUiTest {

    @Test
    fun `no row means nothing to show`() {
        assertEquals(DownloadStateUi.None, DownloadStateUi.of(null))
        assertNull(DownloadStateUi.None.label())
    }

    @Test
    fun `each status maps to its row state and label`() {
        assertEquals("Queued", DownloadStateUi.of(row("a", DownloadStatus.QUEUED)).label())
        assertEquals("Downloading 42%", DownloadStateUi.of(row("a", DownloadStatus.DOWNLOADING, 42)).label())
        assertEquals("Finishing…", DownloadStateUi.of(row("a", DownloadStatus.DOWNLOADING, 100)).label())
        assertEquals(
            DownloadStateUi.Downloaded(StreamMode.VIDEO),
            DownloadStateUi.of(row("a", DownloadStatus.COMPLETE, mode = StreamMode.VIDEO)),
        )
        assertEquals(
            DownloadStateUi.Failed("Not enough free storage on this device."),
            DownloadStateUi.of(row("a", DownloadStatus.FAILED, error = "Not enough free storage on this device.")),
        )
    }

    @Test
    fun `progress outside 0-100 is clamped`() {
        assertEquals(DownloadStateUi.Downloading(0), DownloadStateUi.of(row("a", DownloadStatus.DOWNLOADING, -3)))
        assertEquals(DownloadStateUi.Downloading(100), DownloadStateUi.of(row("a", DownloadStatus.DOWNLOADING, 250)))
    }
}

class DownloadsUiTest {

    @Test
    fun `an empty table is an empty screen`() {
        assertTrue(DownloadsUi.of(emptyList()).isEmpty)
    }

    @Test
    fun `active work comes oldest-first and saved copies newest-first`() {
        val ui = DownloadsUi.of(
            listOf(
                row("old-done", DownloadStatus.COMPLETE, sizeBytes = 100, createdAt = 1, completedAt = 10),
                row("new-done", DownloadStatus.COMPLETE, sizeBytes = 250, createdAt = 2, completedAt = 20),
                row("later", DownloadStatus.QUEUED, createdAt = 5),
                row("sooner", DownloadStatus.DOWNLOADING, createdAt = 3),
                row("broken", DownloadStatus.FAILED, createdAt = 4),
            )
        )
        assertEquals(listOf("sooner", "broken", "later"), ui.active.map { it.videoYoutubeId })
        assertEquals(listOf("new-done", "old-done"), ui.completed.map { it.videoYoutubeId })
    }

    @Test
    fun `storage total counts only finished files`() {
        val ui = DownloadsUi.of(
            listOf(
                row("a", DownloadStatus.COMPLETE, sizeBytes = 100),
                row("b", DownloadStatus.COMPLETE, sizeBytes = 50),
                row("c", DownloadStatus.DOWNLOADING, sizeBytes = 999),
            )
        )
        assertEquals(150L, ui.totalBytes)
    }
}
