package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.PlaybackDao
import com.zkwokleung.backchannel.data.db.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Resume points for individual videos.
 *
 * Position and completion are deliberately independent: a save is just "this is where playback
 * is right now", including a pause two minutes from the end. Only a real end-of-item signal
 * ([markCompleted]) retires an item, and only then does the next play start from the beginning.
 */
class PlaybackRepository(private val playbackDao: PlaybackDao) {

    fun observe(videoYoutubeId: String): Flow<PlaybackStateEntity?> =
        playbackDao.observe(videoYoutubeId)

    fun observeAll(): Flow<List<PlaybackStateEntity>> = playbackDao.observeAll()

    suspend fun get(videoYoutubeId: String): PlaybackStateEntity? = playbackDao.get(videoYoutubeId)

    suspend fun savePosition(videoYoutubeId: String, positionMillis: Long, durationMillis: Long?) {
        playbackDao.upsert(
            PlaybackStateEntity(
                videoYoutubeId = videoYoutubeId,
                positionMillis = positionMillis.coerceAtLeast(0),
                durationMillis = durationMillis,
                completed = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Marks an item finished so it restarts from the beginning next time it is played. */
    suspend fun markCompleted(videoYoutubeId: String) {
        val existing = playbackDao.get(videoYoutubeId)
        playbackDao.upsert(
            PlaybackStateEntity(
                videoYoutubeId = videoYoutubeId,
                positionMillis = 0,
                durationMillis = existing?.durationMillis,
                completed = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Where playback should start for [videoYoutubeId] — 0 for anything unwatched, finished, or
     * saved within [END_TOLERANCE_MILLIS] of the end (resuming there would just replay the
     * outro and immediately finish).
     */
    suspend fun resumePositionMillis(videoYoutubeId: String): Long {
        val state = playbackDao.get(videoYoutubeId) ?: return 0
        if (state.completed) return 0
        val duration = state.durationMillis
        if (duration != null && duration > 0 &&
            state.positionMillis >= duration - END_TOLERANCE_MILLIS
        ) {
            return 0
        }
        return state.positionMillis.coerceAtLeast(0)
    }

    companion object {
        private const val END_TOLERANCE_MILLIS = 5_000L
    }
}
