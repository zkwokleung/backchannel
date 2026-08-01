package com.zkwokleung.backchannel.engine

import android.content.Context

/**
 * Persisted engine state: when yt-dlp was last updated, and when an update was last attempted.
 *
 * The two are tracked separately on purpose. Only a success counts as "up to date", but a
 * failure still has to be remembered — otherwise an unreachable update endpoint means every
 * extraction re-attempts a multi-megabyte download and stalls behind it.
 */
class EnginePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("engine", Context.MODE_PRIVATE)

    var lastUpdateCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    var lastAttemptMillis: Long
        get() = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_ATTEMPT, value).apply()

    /** True until yt-dlp has been updated at least once — the bundled build is usually stale. */
    val neverUpdated: Boolean
        get() = lastUpdateCheckMillis == 0L

    fun isCheckDue(now: Long): Boolean =
        now - lastUpdateCheckMillis >= CHECK_INTERVAL_MILLIS

    /** Whether enough time has passed since the last failed attempt to try again. */
    fun isRetryDue(now: Long): Boolean =
        now - lastAttemptMillis >= RETRY_INTERVAL_MILLIS

    companion object {
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val KEY_LAST_ATTEMPT = "last_update_attempt"
        private const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000 // daily
        private const val RETRY_INTERVAL_MILLIS = 15L * 60 * 1000 // 15 min after a failure
    }
}
