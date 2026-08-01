package com.zkwokleung.backchannel.data

import com.zkwokleung.backchannel.data.db.VideoEntity
import com.zkwokleung.backchannel.data.db.WatchlistDao
import com.zkwokleung.backchannel.data.db.WatchlistEntity
import com.zkwokleung.backchannel.data.db.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow

class WatchlistRepository(private val watchlistDao: WatchlistDao) {

    fun observeWatchlists(): Flow<List<WatchlistEntity>> = watchlistDao.observeAll()

    fun observeWatchlist(id: Long): Flow<WatchlistEntity?> = watchlistDao.observeById(id)

    fun observeItems(watchlistId: Long): Flow<List<WatchlistItemEntity>> =
        watchlistDao.observeItems(watchlistId)

    fun observeItemCount(watchlistId: Long): Flow<Int> = watchlistDao.observeItemCount(watchlistId)

    suspend fun getItems(watchlistId: Long): List<WatchlistItemEntity> =
        watchlistDao.getItems(watchlistId)

    suspend fun create(name: String): Long =
        watchlistDao.insert(WatchlistEntity(name = name, createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = watchlistDao.rename(id, name)

    suspend fun delete(id: Long) {
        watchlistDao.deleteItemsFor(id)
        watchlistDao.delete(id)
    }

    /** Adds a video snapshot to a watchlist; no-op if it is already there. */
    suspend fun addVideo(watchlistId: Long, video: VideoEntity, channelTitle: String?) {
        val position = watchlistDao.maxPosition(watchlistId) + 1
        watchlistDao.insertItem(
            WatchlistItemEntity(
                watchlistId = watchlistId,
                videoYoutubeId = video.youtubeId,
                position = position,
                title = video.title,
                thumbnail = video.thumbnail,
                durationSeconds = video.durationSeconds,
                channelTitle = channelTitle,
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun removeItem(itemId: Long) = watchlistDao.deleteItem(itemId)

    suspend fun reorder(orderedItemIds: List<Long>) = watchlistDao.reorder(orderedItemIds)
}
