package com.zkwokleung.backchannel.ui.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.playback.PipCoordinator
import com.zkwokleung.backchannel.ui.theme.PlayerControlTint
import com.zkwokleung.backchannel.ui.theme.PlayerSurface

@UnstableApi
@Composable
fun VideoPlayerScreen(onBack: () -> Unit) {
    val viewModel = sharedPlayerViewModel()
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

    Box(Modifier.fillMaxSize().background(PlayerSurface)) {
        if (controller != null) {
            AndroidView(
                factory = { playerView },
                update = { view -> view.useController = !inPip },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!inPip) {
            // Controls sit over arbitrary video frames, so they need their own contrast rather
            // than theme colours: a scrim behind white chrome, dark enough to read on a bright
            // frame and light enough not to bury a dark one.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(PlayerSurface.copy(alpha = 0.55f), Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PlayerControlTint,
                    )
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PlayerControlTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(onClick = { activity?.let(PipCoordinator::enterPip) }) {
                    Icon(
                        Icons.Filled.PictureInPictureAlt,
                        contentDescription = "Picture in picture",
                        tint = PlayerControlTint,
                    )
                }
            }

            if (state.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PlayerControlTint,
                )
            }
            if (state.mode == StreamMode.VIDEO) {
                TextButton(
                    onClick = {
                        viewModel.switchMode(StreamMode.AUDIO)
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PlayerControlTint),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Filled.Headphones, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Listen audio-only")
                }
            }
        }
    }
}
