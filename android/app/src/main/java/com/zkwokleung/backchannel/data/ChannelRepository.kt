package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.ChannelDao
import com.zkwokleung.backchannel.data.db.ChannelEntity
import com.zkwokleung.backchannel.data.db.VideoDao
import com.zkwokleung.backchannel.data.db.VideoEntity
import com.zkwokleung.backchannel.engine.YtdlpEngine
import kotlinx.coroutines.flow.Flow

class ChannelRepository(
    private val engine: YtdlpEngine,
    private val channelDao: ChannelDao,
    private val videoDao: VideoDao,
) {
    fun observeChannels(): Flow<List<ChannelEntity>> = channelDao.observeAll()

    fun observeChannel(youtubeId: String): Flow<ChannelEntity?> = channelDao.observeById(youtubeId)

    fun observeVideos(channelYoutubeId: String): Flow<List<VideoEntity>> =
        videoDao.observeForChannel(channelYoutubeId)

    suspend fun getVideo(videoYoutubeId: String): VideoEntity? = videoDao.getById(videoYoutubeId)

    /** Resolves the channel, stores it, and caches its uploads. Returns the stored channel. */
    suspend fun addChannel(handleOrUrl: String): ChannelEntity {
        val meta = engine.resolveChannel(handleOrUrl)
        val channel = ChannelEntity(
            youtubeId = meta.youtubeId,
            handle = meta.handle,
            title = meta.title,
            thumbnail = meta.thumbnail,
            addedAt = System.currentTimeMillis(),
        )
        channelDao.upsert(channel)
        refreshVideos(channel.youtubeId)
        return channel
    }

    /** Re-runs flat extraction and replaces the cached uploads. Returns count cached. */
    suspend fun refreshVideos(channelYoutubeId: String): Int {
        val now = System.currentTimeMillis()
        val uploads = engine.listChannelVideos(channelYoutubeId)
        val entities = uploads.mapIndexed { index, meta ->
            VideoEntity(
                youtubeId = meta.youtubeId,
                channelYoutubeId = channelYoutubeId,
                title = meta.title,
                durationSeconds = meta.durationSeconds,
                thumbnail = meta.thumbnail,
                publishedAt = meta.publishedAt,
                sortIndex = index,
                cachedAt = now,
            )
        }
        videoDao.replaceForChannel(channelYoutubeId, entities)
        return entities.size
    }

    suspend fun removeChannel(youtubeId: String) {
        videoDao.deleteForChannel(youtubeId)
        channelDao.delete(youtubeId)
    }
}
