package com.zkwokleung.backchannel.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.zkwokleung.backchannel.MainActivity
import com.zkwokleung.backchannel.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Foreground media-playback service: hosts the app's single ExoPlayer behind a MediaSession,
 * which provides the notification, lock-screen controls, and background playback. Playback
 * position is persisted periodically and on pause so episodes resume where they stopped.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val container = appContainer

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val dataSourceFactory = ResolvingStreamDataSourceFactory(httpFactory, container.engine)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(StreamMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionIntent)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition()
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                // Advancing by itself means the previous item played to its end.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    previousItemId?.let(::markCompleted)
                }
                previousItemId = mediaItem?.mediaId
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // The final item of a queue ends without any transition callback.
                if (playbackState == Player.STATE_ENDED) {
                    mediaSession?.player?.currentMediaItem?.mediaId?.let(::markCompleted)
                }
            }
        })

        // Periodic position persistence while playing.
        serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) savePosition()
            }
        }
    }

    private var previousItemId: String? = null

    private fun savePosition() {
        val snapshot = positionSnapshot() ?: return
        serviceScope.launch {
            appContainer.playbackRepository.savePosition(
                snapshot.mediaId,
                snapshot.positionMillis,
                snapshot.durationMillis,
            )
        }
    }

    private fun markCompleted(mediaId: String) {
        serviceScope.launch { appContainer.playbackRepository.markCompleted(mediaId) }
    }

    private data class PositionSnapshot(
        val mediaId: String,
        val positionMillis: Long,
        val durationMillis: Long?,
    )

    /** Reads player state on the caller's thread; the player must not be touched off-Main. */
    private fun positionSnapshot(): PositionSnapshot? {
        val player = mediaSession?.player ?: return null
        val mediaId = player.currentMediaItem?.mediaId ?: return null
        return PositionSnapshot(
            mediaId = mediaId,
            positionMillis = player.currentPosition,
            durationMillis = player.duration.takeIf { it != C.TIME_UNSET },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // The final write has to finish here: launching it on serviceScope would be cancelled
        // below, and the process is often killed immediately after the service goes away.
        positionSnapshot()?.let { snapshot ->
            runBlocking {
                withContext(NonCancellable) {
                    runCatching {
                        appContainer.playbackRepository.savePosition(
                            snapshot.mediaId,
                            snapshot.positionMillis,
                            snapshot.durationMillis,
                        )
                    }.onFailure { Log.w(TAG, "final position save failed", it) }
                }
            }
        }
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
    }
}
