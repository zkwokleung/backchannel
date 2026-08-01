package com.zkwokleung.backchannel.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.ui.common.EmptyState
import com.zkwokleung.backchannel.ui.common.MediaRow
import com.zkwokleung.backchannel.ui.common.Thumbnail
import com.zkwokleung.backchannel.ui.common.formatMillis
import com.zkwokleung.backchannel.ui.theme.Spacing
import com.zkwokleung.backchannel.ui.theme.timecode

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun NowPlayingScreen(onOpenVideo: () -> Unit, onBrowseChannels: () -> Unit) {
    val viewModel = sharedPlayerViewModel()
    val state by viewModel.uiState.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            val result = snackbarHostState.showSnackbar(message, actionLabel = "Retry")
            if (result == SnackbarResult.ActionPerformed) viewModel.retry() else viewModel.consumeError()
        }
    }

    // No app bar: the screen is self-evidently the player, and the tab is already labelled.
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (!state.hasItem) {
            EmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Filled.Headphones,
                title = "Nothing playing",
                message = "Pick a video from a channel or watchlist and it starts here.",
                actionLabel = "Browse channels",
                onAction = onBrowseChannels,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                NowPlayingHeader(state = state)
            }

            item {
                Column(Modifier.padding(horizontal = Spacing.xl)) {
                    SeekBar(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        onSeek = viewModel::seekTo,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    TransportControls(
                        state = state,
                        onPrevious = viewModel::previous,
                        onBack10 = { viewModel.seekBy(-10_000) },
                        onPlayPause = viewModel::playPause,
                        onForward30 = { viewModel.seekBy(30_000) },
                        onNext = viewModel::next,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    SecondaryControls(
                        speed = state.speed,
                        onSpeed = viewModel::setSpeed,
                        onSwitchToVideo = {
                            viewModel.switchMode(StreamMode.VIDEO)
                            onOpenVideo()
                        },
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }
            }

            if (queue.isNotEmpty()) {
                item {
                    Text(
                        text = "Up next · ${queue.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xs),
                    )
                }
                items(queue, key = { it.mediaId }) { entry ->
                    MediaRow(
                        title = entry.title,
                        subtitle = entry.artist,
                        leading = { Thumbnail(entry.artworkUri, width = 84.dp) },
                        onClick = { viewModel.playQueueItem(entry.index) },
                        onClickLabel = "Play this next item",
                    )
                }
            }
        }
    }
}

/**
 * Artwork over an ambient wash of itself.
 *
 * The backdrop is the same image requested at 24px and scaled up — a blur that costs nothing and
 * looks identical on every API level. `Modifier.blur` is API 31+ and silently does nothing on
 * our minSdk 26, which would leave a third of the fleet with a visibly different screen.
 */
@Composable
private fun NowPlayingHeader(state: PlayerUiState) {
    val context = LocalContext.current

    Box(Modifier.fillMaxWidth()) {
        if (state.artworkUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(state.artworkUri)
                    .size(24)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                alpha = 0.55f,
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    )
            )
        }

        Column(
            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Thumbnail(
                model = state.artworkUri,
                contentDescription = "Artwork for ${state.title}",
                modifier = Modifier.fillMaxWidth(),
                width = Dp.Unspecified,
                shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                state.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.artist?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TransportControls(
    state: PlayerUiState,
    onPrevious: () -> Unit,
    onBack10: () -> Unit,
    onPlayPause: () -> Unit,
    onForward30: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
        }
        IconButton(onClick = onBack10) {
            Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.size(Spacing.sm))
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
            } else {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        Spacer(Modifier.size(Spacing.sm))
        IconButton(onClick = onForward30) {
            Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun SecondaryControls(
    speed: Float,
    onSpeed: (Float) -> Unit,
    onSwitchToVideo: () -> Unit,
) {
    var speedMenu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { speedMenu = true }) {
                Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.sm))
                Text(formatSpeed(speed))
            }
            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                SPEEDS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(formatSpeed(option)) },
                        onClick = { speedMenu = false; onSpeed(option) },
                    )
                }
            }
        }

        TextButton(onClick = onSwitchToVideo) {
            Icon(Icons.Filled.OndemandVideo, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Spacing.sm))
            Text("Watch video")
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"

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
                style = MaterialTheme.typography.timecode,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMillis(durationMs),
                style = MaterialTheme.typography.timecode,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
