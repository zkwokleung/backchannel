package com.zkwokleung.backchannel.data.db

import android.content.Context
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class BackchannelDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun videoDao(): VideoDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun playbackDao(): PlaybackDao

    companion object {
        fun build(context: Context): BackchannelDatabase =
            Room.databaseBuilder(context, BackchannelDatabase::class.java, "backchannel.db")
                .build()
    }
}
