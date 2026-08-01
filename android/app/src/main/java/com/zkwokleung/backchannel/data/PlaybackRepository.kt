package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.PlaybackDao
import com.zkwokleung.backchannel.data.db.PlaybackStateEntity
import kotlinx.coroutines.flow.Flow

class PlaybackRepository(private val playbackDao: PlaybackDao) {

    fun observe(videoYoutubeId: String): Flow<PlaybackStateEntity?> =
        playbackDao.observe(videoYoutubeId)

    fun observeAll(): Flow<List<PlaybackStateEntity>> = playbackDao.observeAll()

    suspend fun get(videoYoutubeId: String): PlaybackStateEntity? = playbackDao.get(videoYoutubeId)

    suspend fun savePosition(videoYoutubeId: String, positionMillis: Long, durationMillis: Long?) {
        val completed = durationMillis != null && durationMillis > 0 &&
            positionMillis >= durationMillis * COMPLETION_THRESHOLD
        playbackDao.upsert(
            PlaybackStateEntity(
                videoYoutubeId = videoYoutubeId,
                positionMillis = if (completed) 0 else positionMillis,
                durationMillis = durationMillis,
                completed = completed,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    companion object {
        private const val COMPLETION_THRESHOLD = 0.95
    }
}
