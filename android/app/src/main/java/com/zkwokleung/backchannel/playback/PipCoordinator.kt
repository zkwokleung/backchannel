package com.zkwokleung.backchannel.playback

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.util.Rational
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the video screen is visible (so MainActivity can auto-enter PiP on
 * home-press) and whether the activity is currently in PiP (so the UI can hide chrome).
 */
object PipCoordinator {

    /** Set by the video screen while it is composed. */
    @Volatile
    var videoScreenVisible: Boolean = false

    private val _inPip = MutableStateFlow(false)
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    fun onPipModeChanged(isInPip: Boolean) {
        _inPip.value = isInPip
    }

    fun enterPip(activity: Activity) {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return
        }
        runCatching {
            activity.enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }
}
