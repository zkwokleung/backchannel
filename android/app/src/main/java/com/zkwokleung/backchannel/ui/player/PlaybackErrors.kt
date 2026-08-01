package com.zkwokleung.backchannel.ui.player

import androidx.media3.datasource.HttpDataSource
import com.zkwokleung.backchannel.playback.StreamResolutionException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns a playback failure into a sentence worth showing someone.
 *
 * ExoPlayer wraps the real cause, and the raw text is either generic ("Source error") or a Java
 * exception string naming a googlevideo host — neither tells a person what to do.
 */
fun readablePlaybackError(error: Throwable): String {
    val causes = generateSequence<Throwable>(error) { it.cause }.toList()

    // An extraction failure already carries text written for a person.
    causes.filterIsInstance<StreamResolutionException>().firstOrNull()?.message
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    causes.forEach { cause ->
        when (cause) {
            is UnknownHostException, is ConnectException,
            is NoRouteToHostException, is SocketTimeoutException ->
                return "Can't reach YouTube — check your connection."

            is HttpDataSource.InvalidResponseCodeException ->
                return when (cause.responseCode) {
                    403, 410 -> "That stream expired. Tap retry to fetch a fresh one."
                    404 -> "That stream is no longer available."
                    429 -> "YouTube is rate-limiting this device. Try again in a few minutes."
                    else -> "YouTube refused the stream (HTTP ${cause.responseCode})."
                }
        }
    }

    if (causes.any { it is IOException }) {
        return "Playback stopped — check your connection and tap retry."
    }
    return "Couldn't play that video. Update yt-dlp in Settings if it keeps happening."
}
