package com.zkwokleung.backchannel.ui.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.playback.PipCoordinator
import com.zkwokleung.backchannel.ui.common.appViewModel

@UnstableApi
@Composable
fun VideoPlayerScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { PlayerViewModel(it.playerConnection, it.queuePlayer) }
    val controller by viewModel.controller.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val inPip by PipCoordinator.inPip.collectAsState()

    DisposableEffect(Unit) {
        PipCoordinator.videoScreenVisible = true
        onDispose { PipCoordinator.videoScreenVisible = false }
    }

    // The view is remembered (rather than created inside AndroidView) so teardown is tied to
    // this composition leaving, not to AndroidView's release timing.
    val playerView = remember {
        PlayerView(context).apply {
            useController = true
            setShowNextButton(true)
            setShowPreviousButton(true)
        }
    }

    // Attaching is what registers the view as a listener on — and as a video surface for — the
    // process-lifetime MediaController. Both have to be handed back when the screen goes away,
    // or every visit strands a PlayerView and the Activity behind it.
    DisposableEffect(playerView, controller) {
        playerView.player = controller
        onDispose {
            controller?.clearVideoSurface()
            playerView.player = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (controller != null) {
            AndroidView(
                factory = { playerView },
                update = { view -> view.useController = !inPip },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!inPip) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            IconButton(
                onClick = { activity?.let(PipCoordinator::enterPip) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White)
            }
            if (state.mode == StreamMode.VIDEO) {
                TextButton(
                    onClick = {
                        viewModel.switchMode(StreamMode.AUDIO)
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    Icon(Icons.Filled.Headphones, contentDescription = null, tint = Color.White)
                    Text("  Listen audio-only", color = Color.White)
                }
            }
        }
    }
}
