package com.zkwokleung.backchannel

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import com.zkwokleung.backchannel.playback.PipCoordinator
import com.zkwokleung.backchannel.ui.AppRoot
import com.zkwokleung.backchannel.ui.theme.BackchannelTheme

class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BackchannelTheme {
                AppRoot()
            }
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
}
