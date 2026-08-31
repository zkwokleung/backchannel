package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.DownloadDao
import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.engine.StreamMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeDownloadDao : DownloadDao {
    val rows = linkedMapOf<String, DownloadEntity>()

    override suspend fun upsert(download: DownloadEntity) {
        rows[download.videoYoutubeId] = download
    }

    override fun observeAll(): Flow<List<DownloadEntity>> =
        flowOf(rows.values.sortedByDescending { it.createdAt })

    override fun observe(videoYoutubeId: String): Flow<DownloadEntity?> = flowOf(rows[videoYoutubeId])

    override suspend fun get(videoYoutubeId: String): DownloadEntity? = rows[videoYoutubeId]

    override suspend fun getAll(): List<DownloadEntity> = rows.values.toList()

    override suspend fun nextPending(): DownloadEntity? = rows.values
        .filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
        .minByOrNull { it.createdAt }

    override fun observeTotalBytes(): Flow<Long> =
        flowOf(rows.values.filter { it.status == DownloadStatus.COMPLETE }.sumOf { it.sizeBytes })

    override suspend fun setStatus(videoYoutubeId: String, status: DownloadStatus) {
        update(videoYoutubeId) { it.copy(status = status, progressPercent = 0, error = null) }
    }

    override suspend fun requeueInterrupted() {
        rows.keys.toList().forEach { id ->
            update(id) {
                if (it.status == DownloadStatus.DOWNLOADING) {
                    it.copy(status = DownloadStatus.QUEUED, progressPercent = 0)
                } else it
            }
        }
    }

    override suspend fun updateProgress(videoYoutubeId: String, percent: Int) {
        update(videoYoutubeId) { it.copy(progressPercent = percent) }
    }

    override suspend fun markComplete(
        videoYoutubeId: String,
        filePath: String,
        sizeBytes: Long,
        completedAt: Long,
    ) {
        update(videoYoutubeId) {
            it.copy(
                status = DownloadStatus.COMPLETE,
                filePath = filePath,
                sizeBytes = sizeBytes,
                progressPercent = 100,
                error = null,
                completedAt = completedAt,
            )
        }
    }

    override suspend fun markFailed(videoYoutubeId: String, error: String) {
        update(videoYoutubeId) {
            it.copy(status = DownloadStatus.FAILED, filePath = null, sizeBytes = 0, error = error)
        }
    }

    override suspend fun delete(videoYoutubeId: String) {
        rows.remove(videoYoutubeId)
    }

    override suspend fun deleteAll() = rows.clear()

    private fun update(id: String, transform: (DownloadEntity) -> DownloadEntity) {
        rows[id]?.let { rows[id] = transform(it) }
    }
}

class DownloadRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dao = FakeDownloadDao()
    private val repository by lazy {
        DownloadRepository(dao, folder.root, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun request(id: String = "vid", mode: StreamMode = StreamMode.AUDIO) = DownloadRequest(
        videoId = id,
        title = "Episode",
        channelTitle = "Channel",
        thumbnail = null,
        durationSeconds = 600,
        mode = mode,
    )

    private fun file(name: String, content: String = "x"): File =
        folder.newFile(name).apply { writeText(content) }

    @Test
    fun `enqueue records a queued snapshot and clears leftovers from an earlier save`() = runBlocking {
        val stale = file("vid.m4a")
        val partial = file("vid.f140.m4a.part")
        val other = file("other.m4a")

        repository.enqueue(request(mode = StreamMode.VIDEO))

        val row = dao.rows.getValue("vid")
        assertEquals(DownloadStatus.QUEUED, row.status)
        assertEquals(StreamMode.VIDEO, row.mode)
        assertEquals("Episode", row.title)
        assertNull(row.filePath)
        assertFalse(stale.exists())
        assertFalse(partial.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `interrupted downloads go back to the queue and partial files are swept`() = runBlocking {
        repository.enqueue(request("a"))
        repository.enqueue(request("b"))
        repository.markDownloading("a")
        repository.updateProgress("a", 42)
        val partial = file("a.m4a.part")
        val finished = file("b.m4a")

        repository.requeueInterrupted()

        val a = dao.rows.getValue("a")
        assertEquals(DownloadStatus.QUEUED, a.status)
        assertEquals(0, a.progressPercent)
        assertEquals(DownloadStatus.QUEUED, dao.rows.getValue("b").status)
        assertFalse(partial.exists())
        assertTrue(finished.exists())
    }

    @Test
    fun `a completed download resolves to its file`() = runBlocking {
        repository.enqueue(request())
        val media = file("vid.m4a", "audio")
        repository.markComplete("vid", media)

        val local = repository.localMediaFor("vid")

        assertEquals(media.absolutePath, local?.file?.absolutePath)
        assertEquals(StreamMode.AUDIO, local?.mode)
        assertEquals(5L, dao.rows.getValue("vid").sizeBytes)
    }

    @Test
    fun `a completed row whose file vanished is downgraded to failed`() = runBlocking {
        repository.enqueue(request())
        val media = file("vid.m4a")
        repository.markComplete("vid", media)
        media.delete()

        assertNull(repository.localMediaFor("vid"))
        val row = dao.rows.getValue("vid")
        assertEquals(DownloadStatus.FAILED, row.status)
        assertEquals(DownloadRepository.MISSING_FILE_MESSAGE, row.error)
    }

    @Test
    fun `queued and failed rows are not playable`() = runBlocking {
        repository.enqueue(request())
        assertNull(repository.localMediaFor("vid"))

        repository.markFailed("vid", "boom")
        assertNull(repository.localMediaFor("vid"))
        assertNull(repository.localMediaFor("never-saved"))
    }

    @Test
    fun `progress is clamped to a percentage`() = runBlocking {
        repository.enqueue(request())
        repository.updateProgress("vid", -1)
        assertEquals(0, dao.rows.getValue("vid").progressPercent)
        repository.updateProgress("vid", 140)
        assertEquals(100, dao.rows.getValue("vid").progressPercent)
    }

    @Test
    fun `removing a download deletes its row and every file with its prefix`() = runBlocking {
        repository.enqueue(request())
        val media = file("vid.mp4")
        val leftover = file("vid.f137.mp4")
        val other = file("other.mp4")

        repository.remove("vid")

        assertFalse(dao.rows.containsKey("vid"))
        assertFalse(media.exists())
        assertFalse(leftover.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `removing everything empties both the table and the directory`() = runBlocking {
        repository.enqueue(request("a"))
        repository.enqueue(request("b"))
        file("a.m4a")
        file("b.mp4.part")

        repository.removeAll()

        assertTrue(dao.rows.isEmpty())
        assertEquals(0, folder.root.listFiles()?.size)
    }

    @Test
    fun `the oldest pending download is served first`() = runBlocking {
        dao.rows["late"] = queued("late", createdAt = 20)
        dao.rows["early"] = queued("early", createdAt = 10)
        dao.rows["done"] = queued("done", createdAt = 1).copy(status = DownloadStatus.COMPLETE)

        assertEquals("early", repository.nextPending()?.videoYoutubeId)
    }

    private fun queued(id: String, createdAt: Long) = DownloadEntity(
        videoYoutubeId = id,
        mode = StreamMode.AUDIO,
        status = DownloadStatus.QUEUED,
        title = id,
        channelTitle = null,
        thumbnail = null,
        durationSeconds = null,
        filePath = null,
        sizeBytes = 0,
        progressPercent = 0,
        error = null,
        createdAt = createdAt,
        completedAt = null,
    )
}
