package com.zkwokleung.backchannel.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelEntity::class,
        VideoEntity::class,
        WatchlistEntity::class,
        WatchlistItemEntity::class,
        PlaybackStateEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
abstract class BackchannelDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun videoDao(): VideoDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        fun build(context: Context): BackchannelDatabase =
            Room.databaseBuilder(context, BackchannelDatabase::class.java, "backchannel.db")
                .build()
    }
}
