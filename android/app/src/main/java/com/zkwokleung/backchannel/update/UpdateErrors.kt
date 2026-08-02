package com.zkwokleung.backchannel.update

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Every way a self-update can fail, as a type rather than a string.
 *
 * The engine's `friendlyMessage()` has to sniff yt-dlp's stderr because the failure comes out of
 * another process. Nothing like that applies here: every failure below is raised by this app's
 * own code, so it is modelled directly and the user-facing text is a total function over the
 * type — no unmatched case can fall through to a generic message by accident.
 */
sealed interface UpdateFailure {

    /** One sentence, and where there is something to do about it, what to do. */
    val message: String

    data object Offline : UpdateFailure {
        override val message = "Can't reach GitHub — check your connection."
    }

    /** `/releases/latest` 404s until the first release is published. Not an error. */
    data object NoReleases : UpdateFailure {
        override val message = "No releases published yet."
    }

    /** 60 requests/hour per IP unauthenticated. Shared networks hit this without any help. */
    data object RateLimited : UpdateFailure {
        override val message = "GitHub is rate-limiting this network. Try again later."
    }

    data class ServerError(val status: Int) : UpdateFailure {
        override val message = "GitHub returned an error ($status). Try again later."
    }

    data object MalformedResponse : UpdateFailure {
        override val message = "GitHub sent something this version couldn't read."
    }

    data object NoMatchingAsset : UpdateFailure {
        override val message = "That release has no download for this device."
    }

    data object DownloadIncomplete : UpdateFailure {
        override val message = "The download didn't finish. Try again."
    }

    /**
     * Integrity, not authenticity — `SHA256SUMS.txt` comes from the same release as the APK, so
     * this catches a truncated or corrupted transfer, not a tampered one.
     */
    data object ChecksumMismatch : UpdateFailure {
        override val message = "The download was corrupted and won't be installed."
    }

    /**
     * The real trust anchor: Android refuses an update signed with a different key. Catching it
     * here turns `INSTALL_FAILED_UPDATE_INCOMPATIBLE` into a sentence. Debug installs always
     * land here, since release APKs are signed with the release key.
     */
    data object SignatureMismatch : UpdateFailure {
        override val message = "That build was signed with a different key and can't replace this install."
    }

    data object InstallNotPermitted : UpdateFailure {
        override val message = "Allow Backchannel to install apps, then tap Install again."
    }

    data class Unexpected(val detail: String?) : UpdateFailure {
        override val message = detail?.takeIf { it.isNotBlank() }?.let { "Update failed: $it" }
            ?: "Update failed."
    }
}

class UpdateException(val failure: UpdateFailure, cause: Throwable? = null) :
    Exception(failure.message, cause)

/**
 * Maps a thrown exception to a failure. Transport problems all read as offline — the distinction
 * between DNS, TLS and a dropped socket is not one the user can act on differently.
 */
fun failureFor(t: Throwable): UpdateFailure = when (t) {
    is UpdateException -> t.failure
    is UnknownHostException, is SocketTimeoutException, is SSLException, is IOException ->
        UpdateFailure.Offline
    else -> UpdateFailure.Unexpected(t.message)
}

/** HTTP status to failure, for the two calls that talk to GitHub. */
fun failureForStatus(status: Int): UpdateFailure = when (status) {
    404 -> UpdateFailure.NoReleases
    // GitHub returns 403 (legacy) or 429 when the unauthenticated quota is spent.
    403, 429 -> UpdateFailure.RateLimited
    else -> UpdateFailure.ServerError(status)
}
