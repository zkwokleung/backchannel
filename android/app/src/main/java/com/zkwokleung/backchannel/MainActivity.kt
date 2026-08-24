package com.zkwokleung.backchannel

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import com.zkwokleung.backchannel.playback.PipCoordinator
import com.zkwokleung.backchannel.ui.AppRoot
import com.zkwokleung.backchannel.ui.theme.BackchannelTheme

class MainActivity : ComponentActivity() {

    /**
     * Bumped for each request to open the player, rather than held as a boolean: the activity is
     * `singleTask`, so a second notification tap arrives at the same instance and has to be
     * distinguishable from the first.
     */
    private var playerRequests by mutableIntStateOf(0)

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.wantsPlayer()) playerRequests++
        setContent {
            BackchannelTheme {
                AppRoot(openPlayerRequests = playerRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.wantsPlayer()) playerRequests++
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hand the service binding back when the UI is really going away (not on a rotation),
        // so a paused session can stop instead of lingering as a foreground service.
        if (isFinishing) {
            appContainer.playerConnection.disconnect()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home-press while watching video → keep playing in a PiP window.
        if (PipCoordinator.videoScreenVisible) {
            PipCoordinator.enterPip(this)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipCoordinator.onPipModeChanged(isInPictureInPictureMode)
    }

    companion object {
        /** Set by the media session's notification so tapping it lands on the player. */
        const val EXTRA_OPEN_PLAYER = "com.zkwokleung.backchannel.OPEN_PLAYER"
    }
}

private fun Intent.wantsPlayer(): Boolean =
    getBooleanExtra(MainActivity.EXTRA_OPEN_PLAYER, false)
