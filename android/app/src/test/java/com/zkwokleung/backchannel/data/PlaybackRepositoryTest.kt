package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.PlaybackDao
import com.zkwokleung.backchannel.data.db.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePlaybackDao : PlaybackDao {
    val rows = mutableMapOf<String, PlaybackStateEntity>()

    override suspend fun upsert(state: PlaybackStateEntity) {
        rows[state.videoYoutubeId] = state
    }

    override suspend fun get(videoYoutubeId: String): PlaybackStateEntity? = rows[videoYoutubeId]

    override fun observe(videoYoutubeId: String): Flow<PlaybackStateEntity?> =
        flowOf(rows[videoYoutubeId])

    override fun observeAll(): Flow<List<PlaybackStateEntity>> = flowOf(rows.values.toList())
}

class PlaybackRepositoryTest {

    private val dao = FakePlaybackDao()
    private val repository = PlaybackRepository(dao)

    private val hourLong = 60L * 60 * 1000

    @Test
    fun `pausing near the end keeps the position instead of discarding it`() = runBlocking {
        // 58:00 of a 60:00 item — 96.6%, which the old threshold rule zeroed.
        repository.savePosition("vid", positionMillis = 58 * 60 * 1000, durationMillis = hourLong)

        assertEquals(58 * 60 * 1000, repository.resumePositionMillis("vid"))
        assertFalse(dao.rows.getValue("vid").completed)
    }

    @Test
    fun `ordinary mid-item position round-trips`() = runBlocking {
        repository.savePosition("vid", positionMillis = 90_000, durationMillis = hourLong)
        assertEquals(90_000, repository.resumePositionMillis("vid"))
    }

    @Test
    fun `completed items restart from the beginning`() = runBlocking {
        repository.savePosition("vid", positionMillis = 58 * 60 * 1000, durationMillis = hourLong)
        repository.markCompleted("vid")

        assertEquals(0, repository.resumePositionMillis("vid"))
        assertTrue(dao.rows.getValue("vid").completed)
    }

    @Test
    fun `completion preserves the known duration`() = runBlocking {
        repository.savePosition("vid", positionMillis = 1_000, durationMillis = hourLong)
        repository.markCompleted("vid")
        assertEquals(hourLong, dao.rows.getValue("vid").durationMillis)
    }

    @Test
    fun `a position within seconds of the end restarts rather than replaying the outro`() =
        runBlocking {
            repository.savePosition("vid", positionMillis = hourLong - 2_000, durationMillis = hourLong)
            assertEquals(0, repository.resumePositionMillis("vid"))
        }

    @Test
    fun `unknown videos start at zero`() = runBlocking {
        assertEquals(0, repository.resumePositionMillis("never-played"))
    }

    @Test
    fun `unknown duration still resumes at the saved position`() = runBlocking {
        repository.savePosition("vid", positionMillis = 12_345, durationMillis = null)
        assertEquals(12_345, repository.resumePositionMillis("vid"))
    }

    @Test
    fun `negative positions are clamped`() = runBlocking {
        repository.savePosition("vid", positionMillis = -1, durationMillis = hourLong)
        assertEquals(0, repository.resumePositionMillis("vid"))
    }

    @Test
    fun `replaying a completed item and pausing clears the completed flag`() = runBlocking {
        repository.markCompleted("vid")
        repository.savePosition("vid", positionMillis = 30_000, durationMillis = hourLong)

        assertFalse(dao.rows.getValue("vid").completed)
        assertEquals(30_000, repository.resumePositionMillis("vid"))
    }
}
