package com.zkwokleung.backchannel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Upsert
    suspend fun upsert(channel: ChannelEntity)

    @Query("SELECT * FROM channels ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE youtubeId = :youtubeId")
    suspend fun getById(youtubeId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE youtubeId = :youtubeId")
    fun observeById(youtubeId: String): Flow<ChannelEntity?>

    @Query("DELETE FROM channels WHERE youtubeId = :youtubeId")
    suspend fun delete(youtubeId: String)
}

@Dao
interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<VideoEntity>)

    @Query("DELETE FROM videos WHERE channelYoutubeId = :channelYoutubeId")
    suspend fun deleteForChannel(channelYoutubeId: String)

    @Transaction
    suspend fun replaceForChannel(channelYoutubeId: String, videos: List<VideoEntity>) {
        deleteForChannel(channelYoutubeId)
        insertAll(videos)
    }

    @Query("SELECT * FROM videos WHERE channelYoutubeId = :channelYoutubeId ORDER BY sortIndex ASC")
    fun observeForChannel(channelYoutubeId: String): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE youtubeId = :youtubeId")
    suspend fun getById(youtubeId: String): VideoEntity?

    @Query("SELECT COUNT(*) FROM videos WHERE channelYoutubeId = :channelYoutubeId")
    suspend fun countForChannel(channelYoutubeId: String): Int
}

@Dao
interface WatchlistDao {
    @Insert
    suspend fun insert(watchlist: WatchlistEntity): Long

    @Query("UPDATE watchlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM watchlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM watchlists ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlists WHERE id = :id")
    fun observeById(id: Long): Flow<WatchlistEntity?>

    @Query("SELECT COUNT(*) FROM watchlist_items WHERE watchlistId = :watchlistId")
    fun observeItemCount(watchlistId: Long): Flow<Int>

    // ── items ──

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: WatchlistItemEntity): Long

    @Query("DELETE FROM watchlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("DELETE FROM watchlist_items WHERE watchlistId = :watchlistId")
    suspend fun deleteItemsFor(watchlistId: Long)

    @Query(
        "SELECT * FROM watchlist_items WHERE watchlistId = :watchlistId ORDER BY position ASC, id ASC"
    )
    fun observeItems(watchlistId: Long): Flow<List<WatchlistItemEntity>>

    @Query(
        "SELECT * FROM watchlist_items WHERE watchlistId = :watchlistId ORDER BY position ASC, id ASC"
    )
    suspend fun getItems(watchlistId: Long): List<WatchlistItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM watchlist_items WHERE watchlistId = :watchlistId")
    suspend fun maxPosition(watchlistId: Long): Int

    @Query("UPDATE watchlist_items SET position = :position WHERE id = :itemId")
    suspend fun updatePosition(itemId: Long, position: Int)

    @Transaction
    suspend fun reorder(orderedItemIds: List<Long>) {
        orderedItemIds.forEachIndexed { index, itemId -> updatePosition(itemId, index) }
    }
}

@Dao
interface PlaybackDao {
    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)

    @Query("SELECT * FROM playback_states WHERE videoYoutubeId = :videoYoutubeId")
    suspend fun get(videoYoutubeId: String): PlaybackStateEntity?

    @Query("SELECT * FROM playback_states WHERE videoYoutubeId = :videoYoutubeId")
    fun observe(videoYoutubeId: String): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM playback_states")
    fun observeAll(): Flow<List<PlaybackStateEntity>>
}
