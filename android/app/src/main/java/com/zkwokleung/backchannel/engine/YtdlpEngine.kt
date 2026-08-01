package com.zkwokleung.backchannel.engine

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device yt-dlp. All calls are suspend functions on [dispatcher]; results are parsed
 * with kotlinx.serialization. Stream URLs are cached in memory with a short TTL and are
 * never persisted (they expire upstream after ~6h and are device-bound).
 */
class YtdlpEngine(
    private val appContext: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChannelSource {
    sealed interface InitState {
        data object NotStarted : InitState
        data object Initializing : InitState
        data class Ready(val ytdlpVersion: String?) : InitState
        data class Failed(val message: String) : InitState
    }

    private val _initState = MutableStateFlow<InitState>(InitState.NotStarted)
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    private val prefs = EnginePrefs(appContext)
    private val initMutex = Mutex()
    private val updateMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val streamCache = ConcurrentHashMap<String, ResolvedStream>()

    /** Idempotent; safe to call from multiple places. */
    suspend fun initialize(): InitState = initMutex.withLock {
        val current = _initState.value
        if (current is InitState.Ready) return current
        _initState.value = InitState.Initializing
        return withContext(dispatcher) {
            try {
                YoutubeDL.getInstance().init(appContext)
                val ready = InitState.Ready(currentVersion())
                _initState.value = ready
                Log.i(TAG, "yt-dlp ready, version=${ready.ytdlpVersion}")
                ready
            } catch (t: Throwable) {
                val failed = InitState.Failed(t.message ?: "yt-dlp init failed")
                _initState.value = failed
                Log.e(TAG, "yt-dlp init failed", t)
                failed
            }
        }
    }

    fun currentVersion(): String? = try {
        YoutubeDL.getInstance().version(appContext)
    } catch (t: Throwable) {
        null
    }

    /** Updates the bundled yt-dlp binary (stable channel). Returns the new version. */
    suspend fun update(): String? {
        requireReady()
        return performUpdate()
    }

    private suspend fun performUpdate(): String? = updateMutex.withLock {
        withContext(dispatcher) {
            // Recorded before the attempt so a failure still counts, keeping a broken update
            // endpoint from re-downloading ahead of every extraction.
            prefs.lastAttemptMillis = System.currentTimeMillis()
            try {
                YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
            } catch (t: Throwable) {
                throw EngineException(friendlyMessage(t.message), t)
            }
            val version = currentVersion()
            prefs.lastUpdateCheckMillis = System.currentTimeMillis()
            _initState.value = InitState.Ready(version)
            Log.i(TAG, "yt-dlp updated to $version")
            version
        }
    }

    /**
     * Refreshes yt-dlp when it has never been updated (the shipped binary lags YouTube's
     * changes and typically extracts nothing) or when the daily check is due. Failures are
     * non-fatal — the app keeps working with whatever binary is present.
     */
    suspend fun updateIfDue(): String? {
        val now = System.currentTimeMillis()
        val due = prefs.neverUpdated || prefs.isCheckDue(now)
        if (!due || !prefs.isRetryDue(now)) return null
        return try {
            update()
        } catch (t: Throwable) {
            Log.w(TAG, "background yt-dlp update failed", t)
            null
        }
    }

    // ── Extraction ────────────────────────────────────────────────────────────

    override suspend fun resolveChannel(handleOrUrl: String): ChannelMeta = withContext(dispatcher) {
        val url = normalizeChannelUrl(handleOrUrl) + "/videos"
        val parsed = runFlatPlaylist(url, playlistEnd = 1)
        val channelId = parsed.channelId
            ?: throw EngineException("Couldn't find that channel — check the handle or URL.")
        ChannelMeta(
            youtubeId = channelId,
            handle = parsed.uploaderId,
            title = parsed.channel ?: parsed.title ?: handleOrUrl,
            thumbnail = pickAvatar(parsed.thumbnails),
        )
    }

    override suspend fun listChannelVideos(
        channelYoutubeId: String,
        limit: Int,
    ): List<VideoMeta> = withContext(dispatcher) {
        val url = "https://www.youtube.com/channel/$channelYoutubeId/videos"
        val parsed = runFlatPlaylist(url, playlistEnd = limit)
        parsed.entries.mapNotNull { entry ->
            val id = entry.id ?: return@mapNotNull null
            VideoMeta(
                youtubeId = id,
                title = entry.title ?: id,
                durationSeconds = entry.duration?.toLong(),
                thumbnail = entry.thumbnails.lastOrNull()?.url
                    ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                publishedAt = parseUploadDate(entry.uploadDate),
            )
        }
    }

    suspend fun getVideoInfo(videoId: String): VideoDetails = withContext(dispatcher) {
        val raw = execute(watchUrl(videoId)) {
            addOption("--dump-single-json")
            addOption("--no-playlist")
            addOption("--skip-download")
        }
        val parsed = parse<YtVideoJson>(raw, "video $videoId")
        VideoDetails(
            youtubeId = parsed.id,
            title = parsed.title ?: parsed.id,
            description = parsed.description,
            durationSeconds = parsed.duration?.toLong(),
            thumbnail = parsed.thumbnail,
            publishedAt = parseUploadDate(parsed.uploadDate),
            channelYoutubeId = parsed.channelId,
            channelTitle = parsed.channel,
            viewCount = parsed.viewCount,
        )
    }

    /** Drops a cached stream URL so the next resolve hits yt-dlp again (used on HTTP 403). */
    fun invalidateStream(videoId: String, mode: StreamMode) {
        streamCache.remove("$videoId:$mode")
    }

    /** Resolves a direct stream URL. Cached in-memory for [STREAM_TTL_MILLIS]. */
    suspend fun resolveStream(
        videoId: String,
        mode: StreamMode,
        forceRefresh: Boolean = false,
    ): ResolvedStream {
        val key = "$videoId:$mode"
        if (forceRefresh) {
            streamCache.remove(key)
        } else {
            streamCache[key]?.let { cached ->
                if (cached.expiresAtMillis > System.currentTimeMillis()) return cached
                streamCache.remove(key)
            }
        }
        return withContext(dispatcher) {
            val format = when (mode) {
                StreamMode.AUDIO -> "bestaudio[ext=m4a]/bestaudio"
                StreamMode.VIDEO ->
                    "best[ext=mp4][vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]"
            }
            val raw = execute(watchUrl(videoId), allowFirstRunUpdate = false) {
                addOption("--dump-single-json")
                addOption("--no-playlist")
                addOption("--skip-download")
                addOption("-f", format)
                // The default web client hands out SABR-protected URLs that reject the range
                // requests ExoPlayer makes when seeking; android_vr returns plain ones.
                addOption("--extractor-args", "youtube:player_client=$PLAYER_CLIENTS")
            }
            val parsed = parse<YtVideoJson>(raw, "stream $videoId")
            val url = parsed.url
                ?: throw EngineException("No playable stream for that video.")
            val stream = ResolvedStream(
                videoId = videoId,
                mode = mode,
                url = url,
                httpHeaders = parsed.httpHeaders,
                mimeType = when (mode) {
                    StreamMode.AUDIO -> if (parsed.ext == "m4a") "audio/mp4" else null
                    StreamMode.VIDEO -> if (parsed.ext == "mp4") "video/mp4" else null
                },
                expiresAtMillis = System.currentTimeMillis() + STREAM_TTL_MILLIS,
            )
            streamCache[key] = stream
            stream
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private suspend fun runFlatPlaylist(url: String, playlistEnd: Int): YtPlaylistJson {
        val raw = execute(url) {
            addOption("--dump-single-json")
            addOption("--flat-playlist")
            addOption("--playlist-end", playlistEnd.toString())
            addOption("--skip-download")
        }
        val parsed = parse<YtPlaylistJson>(raw, "playlist $url")
        Log.i(TAG, "flat playlist $url -> ${raw.length} chars, ${parsed.entries.size} entries")
        return parsed
    }

    private suspend fun execute(
        url: String,
        allowFirstRunUpdate: Boolean = true,
        configure: YoutubeDLRequest.() -> Unit,
    ): String {
        if (allowFirstRunUpdate) requireFreshEngine() else requireReady()
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-warnings")
            addOption("--ignore-config")
            configure()
        }
        return try {
            val response = YoutubeDL.getInstance().execute(request)
            if (response.err.isNotBlank()) Log.w(TAG, "yt-dlp stderr for $url: ${response.err.take(500)}")
            response.out
        } catch (t: Throwable) {
            Log.e(TAG, "yt-dlp failed for $url", t)
            throw EngineException(friendlyMessage(t.message), t)
        }
    }

    private inline fun <reified T> parse(raw: String, what: String): T = try {
        json.decodeFromString<T>(raw.trim())
    } catch (t: Throwable) {
        Log.e(TAG, "could not parse yt-dlp output for $what", t)
        throw EngineException(
            "Couldn't read YouTube's response. Update yt-dlp in Settings and try again.",
            t,
        )
    }

    private suspend fun requireReady() {
        if (_initState.value !is InitState.Ready) {
            val state = initialize()
            if (state !is InitState.Ready) {
                throw EngineException("The yt-dlp engine failed to start. Check Settings.")
            }
        }
    }

    /**
     * Gate for extraction calls: the yt-dlp shipped inside youtubedl-android is normally months
     * old and silently returns empty results against current YouTube, so the very first
     * extraction waits for an update before running.
     */
    private suspend fun requireFreshEngine() {
        requireReady()
        if (prefs.neverUpdated && prefs.isRetryDue(System.currentTimeMillis())) {
            runCatching { performUpdate() }
                .onFailure { Log.w(TAG, "first-run yt-dlp update failed; using bundled binary", it) }
        }
    }

    private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    private fun pickAvatar(thumbnails: List<YtThumbnail>): String? =
        thumbnails.firstOrNull { it.id?.contains("avatar") == true }?.url
            ?: thumbnails.lastOrNull()?.url

    private fun parseUploadDate(uploadDate: String?): Long? {
        if (uploadDate.isNullOrBlank()) return null
        return try {
            LocalDate.parse(uploadDate, DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (t: Throwable) {
            null
        }
    }

    companion object {
        private const val TAG = "YtdlpEngine"
        const val DEFAULT_LIST_LIMIT = 100
        private const val PLAYER_CLIENTS = "android_vr,web"

        private const val STREAM_TTL_MILLIS = 30L * 60 * 1000 // 30 min, well under ~6h expiry
    }
}
