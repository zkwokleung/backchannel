package com.zkwokleung.backchannel.ui

import com.zkwokleung.backchannel.data.db.PlaybackStateEntity
import com.zkwokleung.backchannel.ui.common.PlaybackProgressUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressUiTest {

    private val hour = 60L * 60 * 1000

    private fun state(
        positionMillis: Long,
        durationMillis: Long? = hour,
        completed: Boolean = false,
    ) = PlaybackStateEntity(
        videoYoutubeId = "vid",
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        completed = completed,
        updatedAt = 0,
    )

    @Test
    fun `an unplayed video shows nothing`() {
        assertEquals(PlaybackProgressUi.None, PlaybackProgressUi.of(null, 3600))
    }

    @Test
    fun `a part-played video reports its fraction`() {
        val progress = PlaybackProgressUi.of(state(positionMillis = hour / 4), 3600)
        assertEquals(PlaybackProgressUi.InProgress(0.25f), progress)
    }

    @Test
    fun `a completed video reads as played`() {
        assertEquals(PlaybackProgressUi.Played, PlaybackProgressUi.of(state(0, completed = true), 3600))
    }

    /** Must agree with PlaybackRepository.resumePositionMillis, which restarts within 5s of the end. */
    @Test
    fun `a position inside the end tolerance reads as played, matching the resume rule`() {
        val progress = PlaybackProgressUi.of(state(positionMillis = hour - 2_000), 3600)
        assertEquals(PlaybackProgressUi.Played, progress)
    }

    @Test
    fun `a position just outside the tolerance is still in progress`() {
        val progress = PlaybackProgressUi.of(state(positionMillis = hour - 30_000), 3600)
        assertTrue(progress is PlaybackProgressUi.InProgress)
    }

    @Test
    fun `a missing stored duration falls back to the cached one`() {
        val progress = PlaybackProgressUi.of(
            state(positionMillis = 1_800_000, durationMillis = null),
            fallbackDurationSeconds = 3600,
        )
        assertEquals(PlaybackProgressUi.InProgress(0.5f), progress)
    }

    @Test
    fun `no duration anywhere means nothing to show`() {
        val progress = PlaybackProgressUi.of(state(1_000, durationMillis = null), null)
        assertEquals(PlaybackProgressUi.None, progress)
    }

    @Test
    fun `fractions are quantised so a five-second tick does not redraw every row`() {
        val a = PlaybackProgressUi.of(state(positionMillis = 600_000), 3600)
        val b = PlaybackProgressUi.of(state(positionMillis = 604_000), 3600)
        assertEquals(a, b)
    }
}
