package com.zkwokleung.backchannel.update

import android.content.Context

/**
 * Persisted update state: when GitHub was last asked, and what it said.
 *
 * Mirrors [com.zkwokleung.backchannel.engine.EnginePrefs], with one addition. The answer is
 * remembered as well as the timestamp, because the check only runs once a day: without it, an
 * update found yesterday would show as "Up to date" on every launch until the interval elapsed
 * again.
 */
class UpdatePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    var lastCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    /** The newest version GitHub offered, or null when the last check found nothing newer. */
    var knownVersion: String?
        get() = prefs.getString(KEY_KNOWN_VERSION, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_KNOWN_VERSION) else putString(KEY_KNOWN_VERSION, value)
        }.apply()

    fun isCheckDue(now: Long): Boolean = now - lastCheckMillis >= CHECK_INTERVAL_MILLIS

    private companion object {
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_KNOWN_VERSION = "known_version"
        const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000 // daily
    }
}
