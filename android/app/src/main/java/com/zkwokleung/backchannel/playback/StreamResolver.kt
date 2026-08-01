package com.zkwokleung.backchannel.playback

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.engine.YtdlpEngine
import kotlinx.coroutines.runBlocking

/**
 * Swaps `backchannel://stream` URIs for real googlevideo URLs at open time.
 * Runs on ExoPlayer's loading thread, so blocking here is fine; the engine's
 * TTL cache makes repeat opens (seeks, retries) cheap.
 */
@UnstableApi
class StreamResolver(private val engine: YtdlpEngine) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != PlaybackItems.SCHEME) return dataSpec

        val videoId = uri.getQueryParameter("v")
            ?: throw IllegalArgumentException("Missing video id in $uri")
        val mode = runCatching { StreamMode.valueOf(uri.getQueryParameter("mode") ?: "AUDIO") }
            .getOrDefault(StreamMode.AUDIO)

        val stream = runBlocking { engine.resolveStream(videoId, mode) }
        return dataSpec.buildUpon()
            .setUri(stream.url.toUri())
            .setHttpRequestHeaders(stream.httpHeaders)
            .build()
    }
}
