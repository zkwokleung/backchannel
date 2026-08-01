package com.zkwokleung.backchannel.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.ui.common.EmptyState
import com.zkwokleung.backchannel.ui.common.formatMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(onOpenVideo: () -> Unit) {
    val viewModel = sharedPlayerViewModel()
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            val result = snackbarHostState.showSnackbar(message, actionLabel = "Retry")
            if (result == SnackbarResult.ActionPerformed) viewModel.retry() else viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Now Playing") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.hasItem) {
            EmptyState(
                modifier = Modifier.padding(padding),
                title = "Nothing playing",
                message = "Pick a video from a channel or watchlist to start listening.",
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            AsyncImage(
                model = state.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.artist?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = viewModel::seekTo,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = viewModel::previous, enabled = state.hasPrevious) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp))
                }
                IconButton(onClick = { viewModel.seekBy(-10_000) }) {
                    Icon(Icons.Filled.Replay10, "Back 10 seconds", Modifier.size(32.dp))
                }
                FilledIconButton(onClick = viewModel::playPause, modifier = Modifier.size(72.dp)) {
                    if (state.isBuffering) {
                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                IconButton(onClick = { viewModel.seekBy(30_000) }) {
                    Icon(Icons.Filled.Forward30, "Forward 30 seconds", Modifier.size(32.dp))
                }
                IconButton(onClick = viewModel::next, enabled = state.hasNext) {
                    Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                viewModel.switchMode(StreamMode.VIDEO)
                onOpenVideo()
            }) {
                Icon(Icons.Filled.OndemandVideo, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Switch to video")
            }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = if (dragging) dragValue else progress.coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (durationMs > 0) onSeek((dragValue * durationMs).toLong())
                dragging = false
            },
            enabled = durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatMillis(if (dragging && durationMs > 0) (dragValue * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMillis(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
