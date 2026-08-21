package com.zkwokleung.backchannel.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Raw yt-dlp JSON (--dump-single-json) ──────────────────────────────────────

@Serializable
internal data class YtThumbnail(
    val url: String? = null,
    val id: String? = null,
)

@Serializable
internal data class YtFlatEntry(
    val id: String? = null,
    val title: String? = null,
    val duration: Double? = null,
    val thumbnails: List<YtThumbnail> = emptyList(),
    @SerialName("upload_date") val uploadDate: String? = null,
)

@Serializable
internal data class YtPlaylistJson(
    @SerialName("channel_id") val channelId: String? = null,
    val channel: String? = null,
    @SerialName("uploader_id") val uploaderId: String? = null,
    val title: String? = null,
    val thumbnails: List<YtThumbnail> = emptyList(),
    val entries: List<YtFlatEntry> = emptyList(),
)

@Serializable
internal data class YtVideoJson(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val duration: Double? = null,
    val thumbnail: String? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    @SerialName("channel_id") val channelId: String? = null,
    val channel: String? = null,
    @SerialName("view_count") val viewCount: Long? = null,
    val url: String? = null,
    val ext: String? = null,
    @SerialName("http_headers") val httpHeaders: Map<String, String> = emptyMap(),
    @SerialName("requested_formats") val requestedFormats: List<YtRequestedFormat> = emptyList(),
)

/** One track of a `video+audio` format selection, from yt-dlp's `requested_formats`. */
@Serializable
internal data class YtRequestedFormat(
    val url: String? = null,
    val ext: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    @SerialName("http_headers") val httpHeaders: Map<String, String> = emptyMap(),
) {
    val hasVideo: Boolean get() = vcodec != null && vcodec != "none"
    val hasAudio: Boolean get() = acodec != null && acodec != "none"
}

// ── Engine result types ───────────────────────────────────────────────────────

data class ChannelMeta(
    val youtubeId: String,
    val handle: String?,
    val title: String,
    val thumbnail: String?,
)

data class VideoMeta(
    val youtubeId: String,
    val title: String,
    val durationSeconds: Long?,
    val thumbnail: String?,
    val publishedAt: Long?,
)

data class VideoDetails(
    val youtubeId: String,
    val title: String,
    val description: String?,
    val durationSeconds: Long?,
    val thumbnail: String?,
    val publishedAt: Long?,
    val channelYoutubeId: String?,
    val channelTitle: String?,
    val viewCount: Long?,
)

enum class StreamMode { AUDIO, VIDEO }

data class ResolvedStream(
    val videoId: String,
    val mode: StreamMode,
    val url: String,
    val httpHeaders: Map<String, String>,
    val mimeType: String?,
    val expiresAtMillis: Long,
)

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
