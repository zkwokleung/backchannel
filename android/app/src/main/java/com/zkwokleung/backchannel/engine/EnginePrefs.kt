package com.zkwokleung.backchannel.engine

import android.content.Context

/** Small persisted state for the engine (last successful yt-dlp update). */
class EnginePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)

    var lastUpdateCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    /** True until yt-dlp has been updated at least once — the bundled build is usually stale. */
    val neverUpdated: Boolean
        get() = lastUpdateCheckMillis == 0L

    fun isCheckDue(now: Long): Boolean =
        now - lastUpdateCheckMillis >= CHECK_INTERVAL_MILLIS

    companion object {
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000 // daily
    }
}
