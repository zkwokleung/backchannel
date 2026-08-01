package com.zkwokleung.backchannel.engine

/** Tab suffixes a user can copy from the address bar while on a channel page. */
private val CHANNEL_TABS = setOf(
    "videos", "streams", "shorts", "playlists", "featured", "community",
    "about", "podcasts", "releases", "store", "channels",
)

/**
 * Normalizes @handle, bare handle, UC… id, or full URL to a canonical channel URL.
 *
 * Channel URLs are trimmed back to the channel root, because callers append the tab
 * themselves: pasting the address bar from a channel's Videos tab would otherwise ask
 * yt-dlp for `/videos/videos`, which it cannot resolve.
 */
internal fun normalizeChannelUrl(input: String): String {
    val value = input.trim()
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
        return when {
            value.startsWith("UC") && value.length == 24 ->
                "https://www.youtube.com/channel/$value"
            value.startsWith("@") -> "https://www.youtube.com/$value"
            else -> "https://www.youtube.com/@$value"
        }
    }

    val withoutQuery = value.substringBefore('?').substringBefore('#').trimEnd('/')
    if (isVideoUrl(withoutQuery)) {
        throw EngineException("That's a link to a video, not a channel.")
    }
    val segments = withoutQuery.split('/').toMutableList()
    if (segments.size > 3 && segments.last().lowercase() in CHANNEL_TABS) {
        segments.removeAt(segments.lastIndex)
    }
    return segments.joinToString("/")
}

private fun isVideoUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("youtu.be/") ||
        lower.contains("/watch") ||
        lower.contains("/shorts/") ||
        lower.contains("/live/")
}
