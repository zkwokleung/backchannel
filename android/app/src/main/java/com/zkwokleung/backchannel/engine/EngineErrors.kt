package com.zkwokleung.backchannel.engine

/**
 * Turns yt-dlp's stderr into something worth showing a person. Raw output is multi-line and
 * full of stack traces and tracebacks; the underlying text is kept as the exception cause for
 * logcat.
 */
internal fun friendlyMessage(raw: String?): String {
    val text = raw.orEmpty()
    return when {
        text.containsAny("Unable to download", "Failed to resolve", "getaddrinfo", "Network is unreachable",
            "Temporary failure in name resolution", "Connection refused", "timed out") ->
            "Can't reach YouTube — check your connection."

        text.containsAny("Private video", "This video is private") ->
            "That video is private."

        text.containsAny("Video unavailable", "has been removed", "no longer available") ->
            "That video is unavailable."

        text.containsAny("members-only", "This video is available to this channel's members") ->
            "That video is members-only."

        text.containsAny("age-restricted", "Sign in to confirm your age", "inappropriate for some users") ->
            "That video is age-restricted and can't be played here."

        text.containsAny("Sign in to confirm you're not a bot", "confirm you're not a bot") ->
            "YouTube is asking for sign-in verification. Try again later."

        text.containsAny("DRM protected") ->
            "That video is DRM protected."

        text.containsAny("Unable to recognize tab", "Not a valid URL", "Unsupported URL", "404",
            "does not have a videos tab", "This channel does not have") ->
            "Couldn't find that channel — check the handle or URL."

        text.containsAny("Requested format is not available") ->
            "No playable stream for that video."

        text.containsAny("nsig extraction failed", "Signature extraction failed", "player response",
            "Failed to extract") ->
            "YouTube changed something and extraction failed. Update yt-dlp in Settings."

        else -> "Extraction failed. If this keeps happening, update yt-dlp in Settings."
    }
}

private fun String.containsAny(vararg needles: String): Boolean =
    needles.any { contains(it, ignoreCase = true) }
