package com.zkwokleung.backchannel.playback

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.engine.YtdlpEngine
import kotlinx.coroutines.runBlocking

/**
 * Turns `backchannel://stream?v=…&mode=…` URIs into real googlevideo URLs at open time.
 *
 * Resolution is deliberately late and per-open: stream URLs expire (~6h) and are bound to the
 * device that resolved them, so queue items stay valid indefinitely while each read gets a live
 * URL. A rejected URL (403 — expired or revoked mid-playback) is re-resolved once, bypassing the
 * cache, before the failure is surfaced to the player.
 */
@UnstableApi
class ResolvingStreamDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val engine: YtdlpEngine,
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        ResolvingStreamDataSource(upstreamFactory.createDataSource(), engine)
}

@UnstableApi
private class ResolvingStreamDataSource(
    private val upstream: DataSource,
    private val engine: YtdlpEngine,
) : DataSource {

    override fun open(dataSpec: DataSpec): Long {
        val request = StreamRequest.from(dataSpec.uri) ?: return upstream.open(dataSpec)
        return try {
            upstream.open(resolve(dataSpec, request, forceRefresh = false))
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode != 403 && e.responseCode != 410) throw e
            Log.w(TAG, "stream URL rejected (${e.responseCode}); re-resolving ${request.videoId}")
            engine.invalidateStream(request.videoId, request.mode)
            upstream.close()
            upstream.open(resolve(dataSpec, request, forceRefresh = true))
        }
    }

    private fun resolve(dataSpec: DataSpec, request: StreamRequest, forceRefresh: Boolean): DataSpec {
        val stream = runBlocking { engine.resolveStream(request.videoId, request.mode, forceRefresh) }
        return dataSpec.buildUpon()
            .setUri(stream.url.toUri())
            .setHttpRequestHeaders(stream.httpHeaders)
            .build()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    private companion object {
        const val TAG = "StreamResolver"
    }
}

private data class StreamRequest(val videoId: String, val mode: StreamMode) {
    companion object {
        fun from(uri: Uri): StreamRequest? {
            if (uri.scheme != PlaybackItems.SCHEME) return null
            val videoId = uri.getQueryParameter("v") ?: return null
            val mode = runCatching {
                StreamMode.valueOf(uri.getQueryParameter("mode") ?: StreamMode.AUDIO.name)
            }.getOrDefault(StreamMode.AUDIO)
            return StreamRequest(videoId, mode)
        }
    }
}
