package com.zkwokleung.backchannel.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.zkwokleung.backchannel.engine.StreamMode

/**
 * Builds media sources for `backchannel://stream` items.
 *
 * YouTube no longer serves its combined audio+video files (their downloads return 403), so a
 * VIDEO item is played as two progressive streams merged in the player: the item's own URI
 * resolves to the video-only track and a mode-swapped copy resolves to the audio track, each
 * lazily through [ResolvingStreamDataSourceFactory]. Everything else — AUDIO items included —
 * plays as a single progressive stream.
 */
@UnstableApi
class StreamMediaSourceFactory(dataSourceFactory: DataSource.Factory) : MediaSource.Factory {

    private val progressive = ProgressiveMediaSource.Factory(dataSourceFactory)
    private val fallback = DefaultMediaSourceFactory(dataSourceFactory)

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        progressive.setDrmSessionManagerProvider(drmSessionManagerProvider)
        fallback.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        progressive.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        fallback.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun getSupportedTypes(): IntArray = fallback.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (PlaybackItems.modeOf(mediaItem) != StreamMode.VIDEO) {
            return fallback.createMediaSource(mediaItem)
        }
        // The tracks come from the same extraction but aren't guaranteed sample-exact in
        // length; clipping to the shorter one avoids a stall at the very end.
        return MergingMediaSource(
            /* adjustPeriodTimeOffsets = */ true,
            /* clipDurations = */ true,
            progressive.createMediaSource(mediaItem),
            progressive.createMediaSource(PlaybackItems.withMode(mediaItem, StreamMode.AUDIO)),
        )
    }
}
