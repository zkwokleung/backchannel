package com.zkwokleung.backchannel.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.zkwokleung.backchannel.engine.StreamMode

/**
 * Media items carry a `backchannel://stream?v=<id>&mode=<AUDIO|VIDEO>` URI; the real
 * googlevideo URL is resolved lazily by [StreamResolver] when ExoPlayer opens the source —
 * that keeps queue items valid indefinitely while stream URLs expire after ~6h.
 *
 * The URI is set twice: as the playable local configuration, and as request metadata. A
 * MediaItem's local configuration is stripped when it crosses the session boundary to a
 * controller, while request metadata survives — it is what lets controller-side code (the
 * mode badge, the audio ⇄ video switch) read an item's current stream mode.
 */
object PlaybackItems {

    const val SCHEME = "backchannel"

    fun streamUri(videoId: String, mode: StreamMode): Uri =
        "$SCHEME://stream?v=$videoId&mode=${mode.name}".toUri()

    fun build(
        videoId: String,
        title: String,
        channelTitle: String?,
        thumbnail: String?,
        mode: StreamMode,
    ): MediaItem {
        val uri = streamUri(videoId, mode)
        return MediaItem.Builder()
            .setMediaId(videoId)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(channelTitle)
                    .setArtworkUri(thumbnail?.toUri())
                    .build()
            )
            .build()
    }

    fun modeOf(item: MediaItem): StreamMode {
        val uri = item.requestMetadata.mediaUri
            ?: item.localConfiguration?.uri
            ?: return StreamMode.AUDIO
        if (uri.scheme != SCHEME) return StreamMode.AUDIO
        return runCatching { StreamMode.valueOf(uri.getQueryParameter("mode") ?: "AUDIO") }
            .getOrDefault(StreamMode.AUDIO)
    }

    /** Same item with the other stream mode (used for the audio ⇄ video switch). */
    fun withMode(item: MediaItem, mode: StreamMode): MediaItem {
        val uri = streamUri(item.mediaId, mode)
        return item.buildUpon()
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .build()
    }
}
