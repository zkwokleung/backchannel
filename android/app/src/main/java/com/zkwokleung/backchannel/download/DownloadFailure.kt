package com.zkwokleung.backchannel.download

import com.zkwokleung.backchannel.engine.EngineException
import com.zkwokleung.backchannel.engine.friendlyMessage

/** Why a download stopped, with the one sentence the Downloads screen shows for it. */
sealed interface DownloadFailure {
    val message: String

    data object Cancelled : DownloadFailure {
        override val message = "Download cancelled."
    }

    data object DiskFull : DownloadFailure {
        override val message = "Not enough free storage on this device."
    }

    data object VideoToolsUnavailable : DownloadFailure {
        override val message = "Video saving isn't available — the media tools failed to start. Audio still works."
    }

    /** Anything yt-dlp reported, already translated by the engine. */
    data class Engine(override val message: String) : DownloadFailure

    companion object {
        fun from(t: Throwable): DownloadFailure = when {
            t is DownloadCancelledException -> Cancelled
            t is DiskFullException || t.messages().any { it.isDiskFull() } -> DiskFull
            t is EngineException -> Engine(t.message ?: friendlyMessage(null))
            else -> Engine(friendlyMessage(t.message))
        }

        private fun Throwable.messages(): Sequence<String> =
            generateSequence(this) { it.cause }.mapNotNull { it.message }

        private fun String.isDiskFull() =
            contains("No space left", ignoreCase = true) || contains("ENOSPC", ignoreCase = true)
    }
}
