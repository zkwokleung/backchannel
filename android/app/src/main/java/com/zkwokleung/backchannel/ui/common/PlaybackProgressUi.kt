package com.zkwokleung.backchannel.ui.common

import com.zkwokleung.backchannel.data.db.PlaybackStateEntity

/**
 * How far through an item the listener is, as the list rows show it.
 *
 * The rule here has to agree with `PlaybackRepository.resumePositionMillis`: a row that displays
 * 99% but restarts from zero when tapped is a bug the user feels. Both treat a position within
 * [END_TOLERANCE_MILLIS] of the end as finished.
 */
sealed interface PlaybackProgressUi {
    data object None : PlaybackProgressUi
    data class InProgress(val fraction: Float) : PlaybackProgressUi
    data object Played : PlaybackProgressUi

    companion object {
        private const val END_TOLERANCE_MILLIS = 5_000L

        fun of(state: PlaybackStateEntity?, fallbackDurationSeconds: Long?): PlaybackProgressUi {
            if (state == null) return None
            if (state.completed) return Played

            val duration = state.durationMillis
                ?: fallbackDurationSeconds?.takeIf { it > 0 }?.times(1_000)
                ?: return None
            if (duration <= 0) return None

            if (state.positionMillis >= duration - END_TOLERANCE_MILLIS) return Played
            if (state.positionMillis <= 0) return None

            // Quantised to whole percent: playback writes a position every 5s, and Room's
            // table-level invalidation re-emits the whole list each time. Rounding lets
            // distinctUntilChanged swallow ticks that would not change a single pixel.
            val percent = (state.positionMillis * 100 / duration).toInt().coerceIn(0, 100)
            return InProgress(percent / 100f)
        }
    }
}
