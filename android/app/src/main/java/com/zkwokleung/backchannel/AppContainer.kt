package com.zkwokleung.backchannel

import android.content.Context
import com.zkwokleung.backchannel.data.ChannelRepository
import com.zkwokleung.backchannel.data.PlaybackRepository
import com.zkwokleung.backchannel.data.WatchlistRepository
import com.zkwokleung.backchannel.data.db.BackchannelDatabase
import com.zkwokleung.backchannel.engine.YtdlpEngine
import com.zkwokleung.backchannel.playback.PlayerConnection
import com.zkwokleung.backchannel.playback.QueuePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container shared by activities and services. */
class AppContainer(context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val engine = YtdlpEngine(context.applicationContext)

    private val database = BackchannelDatabase.build(context.applicationContext)

    val channelRepository = ChannelRepository(engine, database.channelDao(), database.videoDao())
    val watchlistRepository = WatchlistRepository(database.watchlistDao())
    val playbackRepository = PlaybackRepository(database.playbackDao())

    val playerConnection = PlayerConnection(context.applicationContext)
    val queuePlayer = QueuePlayer(playerConnection, playbackRepository, applicationScope)

    init {
        // Warm the runtime and refresh yt-dlp early so the first extraction is fast and works
        // against current YouTube (the shipped binary lags and extracts nothing).
        applicationScope.launch {
            engine.initialize()
            engine.updateIfDue()
        }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BackchannelApp).container
