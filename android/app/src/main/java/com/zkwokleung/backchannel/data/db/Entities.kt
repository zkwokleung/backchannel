package com.zkwokleung.backchannel.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val youtubeId: String,
    val handle: String?,
    val title: String,
    val thumbnail: String?,
    val addedAt: Long,
)

/**
 * Cache of a channel's uploads. [sortIndex] preserves the newest-first order coming from
 * flat extraction, which usually has no upload dates.
 */
@Entity(
    tableName = "videos",
    indices = [Index("channelYoutubeId")],
)
data class VideoEntity(
    @PrimaryKey val youtubeId: String,
    val channelYoutubeId: String,
    val title: String,
    val durationSeconds: Long?,
    val thumbnail: String?,
    val publishedAt: Long?,
    val sortIndex: Int,
    val cachedAt: Long,
)

@Entity(tableName = "watchlists")
data class WatchlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * Watchlist entries carry a denormalized snapshot of the video (title/thumbnail/duration/channel)
 * so lists survive channel-cache refreshes and channel removal.
 */
@Entity(
    tableName = "watchlist_items",
    indices = [
        Index("watchlistId"),
        Index(value = ["watchlistId", "videoYoutubeId"], unique = true),
    ],
)
data class WatchlistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val watchlistId: Long,
    val videoYoutubeId: String,
    val position: Int,
    val title: String,
    val thumbnail: String?,
    val durationSeconds: Long?,
    val channelTitle: String?,
    val addedAt: Long,
)

@Entity(tableName = "playback_states")
data class PlaybackStateEntity(
    @PrimaryKey val videoYoutubeId: String,
    val positionMillis: Long,
    val durationMillis: Long?,
    val completed: Boolean,
    val updatedAt: Long,
)
