package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.ChannelDao
import com.zkwokleung.backchannel.data.db.ChannelEntity
import com.zkwokleung.backchannel.data.db.VideoDao
import com.zkwokleung.backchannel.data.db.VideoEntity
import com.zkwokleung.backchannel.engine.ChannelSource
import kotlinx.coroutines.flow.Flow

/** The channel is already saved; adding it again would reset it. */
class ChannelAlreadySavedException(title: String) :
    Exception("\"$title\" is already saved.")

/** Extraction returned nothing while a cache exists, so the cache was left untouched. */
class EmptyRefreshException :
    Exception("No videos came back — kept the cached list. Update yt-dlp in Settings and retry.")

class ChannelRepository(
    private val engine: ChannelSource,
    private val channelDao: ChannelDao,
    private val videoDao: VideoDao,
) {
    fun observeChannels(): Flow<List<ChannelEntity>> = channelDao.observeAll()

    fun observeChannel(youtubeId: String): Flow<ChannelEntity?> = channelDao.observeById(youtubeId)

    fun observeVideos(channelYoutubeId: String): Flow<List<VideoEntity>> =
        videoDao.observeForChannel(channelYoutubeId)

    suspend fun getVideo(videoYoutubeId: String): VideoEntity? = videoDao.getById(videoYoutubeId)

    /**
     * Resolves the channel, stores it, and caches its uploads. Returns the stored channel.
     *
     * Rejects a channel that is already saved rather than overwriting it — the same channel can
     * be entered as a handle or a `UC…` URL, and re-adding would reset its position in the list
     * and throw away its cached uploads.
     */
    suspend fun addChannel(handleOrUrl: String): ChannelEntity {
        val meta = engine.resolveChannel(handleOrUrl)
        channelDao.getById(meta.youtubeId)?.let { existing ->
            throw ChannelAlreadySavedException(existing.title)
        }
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

    /**
     * Re-runs flat extraction and replaces the cached uploads. Returns count cached.
     *
     * An extraction that yields nothing is treated as a failure, not as "this channel has no
     * videos": a stale yt-dlp binary or a YouTube layout change returns an empty list without
     * erroring, and replacing the cache with it would silently delete every cached upload —
     * including the ones the user could otherwise still browse offline.
     */
    suspend fun refreshVideos(channelYoutubeId: String): Int {
        val now = System.currentTimeMillis()
        val uploads = engine.listChannelVideos(channelYoutubeId)
        if (uploads.isEmpty() && videoDao.countForChannel(channelYoutubeId) > 0) {
            throw EmptyRefreshException()
        }
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
