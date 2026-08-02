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
     * Integrity, not authenticity — `SHA256SUMS.txt` comes from the same release, over the same
     * TLS session, from the same origin as the APK. Anyone able to substitute the APK can
     * substitute the sums file too. This catches a truncated or mangled transfer and nothing
     * more; the trust anchor is [SignatureMismatch] and Android's own enforcement.
     */
    data object ChecksumMismatch : UpdateFailure {
        override val message = "The download was corrupted and won't be installed."
    }

    /** No checksum to compare against. An unverifiable download is not an installable one. */
    data object Unverifiable : UpdateFailure {
        override val message = "Couldn't verify the download, so it won't be installed."
    }

    data object InvalidPackage : UpdateFailure {
        override val message = "That download isn't a valid app package."
    }

    /**
     * The real trust anchor: Android refuses an update signed with a different key. Catching it
     * here turns the system's opaque `INSTALL_FAILED_UPDATE_INCOMPATIBLE` into a sentence.
     */
    data object SignatureMismatch : UpdateFailure {
        override val message =
            "That build is signed with a different key and can't replace this install. " +
                "Download it from GitHub instead."
    }

    /**
     * A debug install can never be replaced by a release-signed APK. The updater deliberately
     * stays enabled in debug builds so the whole path gets exercised in development — this is
     * the last gate, and it should say what actually happened.
     */
    data object DebugBuildInstalled : UpdateFailure {
        override val message =
            "This is a debug build and can't be updated in place. Uninstall it, then install " +
                "the release APK."
    }

    /**
     * `versionCode` is the CI run number, so it isn't derivable from the tag — a re-run can
     * publish a lower one. Cheap to check while the archive is already open, and it saves the
     * user watching the system installer fail after an 18 MB download.
     */
    data object Downgrade : UpdateFailure {
        override val message = "That release is older than what's installed."
    }

    data object InstallNotPermitted : UpdateFailure {
        override val message = "Allow Backchannel to install apps, then tap Install again."
    }

    data object NoInstaller : UpdateFailure {
        override val message = "This device can't install apps from outside an app store."
    }

    data object InsufficientStorage : UpdateFailure {
        override val message = "Not enough free space to install the update."
    }

    /** The user backed out of the system's confirmation screen. Carries no message worth showing. */
    data object UserAborted : UpdateFailure {
        override val message = "Install cancelled."
    }

    data object InstallConflict : UpdateFailure {
        override val message = "Another version of Backchannel is blocking the update."
    }

    data class InstallRejected(val detail: String?) : UpdateFailure {
        override val message = detail?.takeIf { it.isNotBlank() }
            ?.let { "The system rejected the install: $it" }
            ?: "The system rejected the install."
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
