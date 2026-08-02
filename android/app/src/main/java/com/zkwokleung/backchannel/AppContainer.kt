package com.zkwokleung.backchannel

import android.content.Context
import com.zkwokleung.backchannel.data.ChannelRepository
import com.zkwokleung.backchannel.data.PlaybackRepository
import com.zkwokleung.backchannel.data.WatchlistRepository
import com.zkwokleung.backchannel.data.db.BackchannelDatabase
import com.zkwokleung.backchannel.engine.YtdlpEngine
import com.zkwokleung.backchannel.playback.PlayerConnection
import com.zkwokleung.backchannel.playback.QueuePlayer
import com.zkwokleung.backchannel.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

    /**
     * No call timeout on purpose — a wall-clock limit would kill a legitimately slow 18 MB
     * download. `readTimeout` is the right stall detector for both the API call and the transfer.
     */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val appUpdater = AppUpdater(context.applicationContext, httpClient, applicationScope)

    init {
        // Warm the runtime and refresh yt-dlp early so the first extraction is fast and works
        // against current YouTube (the shipped binary lags and extracts nothing).
        applicationScope.launch {
            engine.initialize()
            engine.updateIfDue()
        }
        // A separate launch, deliberately: the daily app-update check should not queue behind a
        // multi-megabyte yt-dlp refresh.
        applicationScope.launch { appUpdater.checkIfDue() }
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BackchannelApp).container
