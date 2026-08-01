package com.zkwokleung.backchannel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.zkwokleung.backchannel.ui.AppRoot
import com.zkwokleung.backchannel.ui.theme.BackchannelTheme

class MainActivity : ComponentActivity() {
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
}
