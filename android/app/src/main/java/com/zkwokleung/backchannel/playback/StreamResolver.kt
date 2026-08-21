package com.zkwokleung.backchannel.playback

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import com.zkwokleung.backchannel.engine.ResolvedStream
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.engine.YtdlpEngine
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Turns `backchannel://stream?v=…&mode=…` URIs into real googlevideo URLs at open time.
 *
 * Resolution is deliberately late and per-open: stream URLs expire (~6h) and are bound to the
 * device that resolved them, so queue items stay valid indefinitely while each read gets a live
 * URL. A rejected URL (403 — expired or revoked mid-playback) is re-resolved once, bypassing the
 * cache, before the failure is surfaced to the player.
 *
 * Reads are issued as bounded ~10 MiB range requests chained transparently, never as one
 * open-ended request for the whole file: googlevideo throttles open-ended range requests to
 * a few KB/s (below even audio bitrate), while bounded chunks are served at full speed —
 * the same reason yt-dlp downloads these formats with `http_chunk_size`.
 */
/**
 * An extraction failure, carrying text meant for a person. It is an [IOException] so ExoPlayer
 * applies its retry policy instead of treating it as a fatal unexpected error.
 */
class StreamResolutionException(message: String, cause: Throwable?) : IOException(message, cause)

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

    private var request: StreamRequest? = null
    private var originalSpec: DataSpec? = null
    private var position = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var chunkRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        val request = StreamRequest.from(dataSpec.uri)
        this.request = request
        if (request == null) return upstream.open(dataSpec)

        originalSpec = dataSpec
        position = dataSpec.position
        bytesRemaining = dataSpec.length
        chunkRemaining = 0
        openChunk(forceRefresh = false)
        return bytesRemaining
    }

    private fun openChunk(forceRefresh: Boolean) {
        val request = request!!
        val stream = resolve(request, forceRefresh)
        if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            // googlevideo URLs carry the file size as `clen`.
            stream.url.toUri().getQueryParameter("clen")?.toLongOrNull()?.let { total ->
                bytesRemaining = (total - position).coerceAtLeast(0)
            }
        }
        if (bytesRemaining == 0L) return
        val length =
            if (bytesRemaining == C.LENGTH_UNSET.toLong()) CHUNK_BYTES
            else minOf(bytesRemaining, CHUNK_BYTES)
        val chunkSpec = originalSpec!!.buildUpon()
            .setUri(stream.url.toUri())
            .setHttpRequestHeaders(stream.httpHeaders)
            .setPosition(position)
            .setLength(length)
            .build()
        chunkRemaining = try {
            upstream.open(chunkSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            upstream.close()
            when {
                (e.responseCode == 403 || e.responseCode == 410) && !forceRefresh -> {
                    Log.w(TAG, "stream URL rejected (${e.responseCode}); re-resolving ${request.videoId}")
                    engine.invalidateStream(request.videoId, request.mode)
                    return openChunk(forceRefresh = true)
                }
                // Past the end of a file whose size was never learned — a normal end of stream.
                e.responseCode == 416 && bytesRemaining == C.LENGTH_UNSET.toLong() -> {
                    bytesRemaining = 0
                    return
                }
                else -> throw e
            }
        }
        if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            contentRangeTotal(upstream.responseHeaders)?.let { total ->
                bytesRemaining = (total - position).coerceAtLeast(0)
                chunkRemaining = minOf(chunkRemaining, bytesRemaining)
            }
        }
    }

    private fun resolve(request: StreamRequest, forceRefresh: Boolean): ResolvedStream {
        // Extraction failures must surface as IOException: ExoPlayer's Loader only applies its
        // retry/backoff policy to IOException, and treats anything else as a fatal unexpected
        // error whose message replaces ours. Wrapping keeps both the retries and the readable
        // text from EngineErrors.
        return try {
            runBlocking { engine.resolveStream(request.videoId, request.mode, forceRefresh) }
        } catch (e: StreamResolutionException) {
            throw e
        } catch (e: Exception) {
            throw StreamResolutionException(e.message ?: "Could not resolve this stream.", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (request == null) return upstream.read(buffer, offset, length)
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) {
            upstream.close()
            openChunk(forceRefresh = false)
            if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        }
        val toRead =
            if (chunkRemaining == C.LENGTH_UNSET.toLong()) length
            else minOf(length.toLong(), chunkRemaining).toInt()
        val read = upstream.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) {
            bytesRemaining = 0
            return C.RESULT_END_OF_INPUT
        }
        position += read
        if (chunkRemaining != C.LENGTH_UNSET.toLong()) chunkRemaining -= read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        return read
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    private companion object {
        const val TAG = "StreamResolver"

        // Small enough that a rejected chunk retries cheaply, large enough that request
        // round-trips are noise. Matches yt-dlp's own chunked-download size.
        const val CHUNK_BYTES = 10L * 1024 * 1024

        /** Parses the total size out of a `Content-Range: bytes X-Y/TOTAL` response header. */
        fun contentRangeTotal(headers: Map<String, List<String>>): Long? {
            val value = headers.entries
                .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
                ?.value?.firstOrNull() ?: return null
            return value.substringAfterLast('/', "").toLongOrNull()
        }
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
