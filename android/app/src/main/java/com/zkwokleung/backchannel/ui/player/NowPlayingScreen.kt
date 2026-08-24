package com.zkwokleung.backchannel.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** How far the player has to travel before letting go minimises it rather than snapping back. */
private val DismissDistance = 140.dp

/** A flick past this (px/s) minimises from anywhere, so a short fast swipe works too. */
private const val DISMISS_VELOCITY = 1200f

@Composable
fun NowPlayingScreen(
    onOpenVideo: () -> Unit,
    onBrowseChannels: () -> Unit,
    onCollapse: () -> Unit,
) {
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

    // Drag down to minimise. The chevron stays the discoverable way out; this is the gesture
    // people try first coming from other players.
    val scope = rememberCoroutineScope()
    val queueState = rememberLazyListState()
    // Held as plain state and written synchronously. Behind an Animatable the offset only
    // moved when the queued coroutine ran, so two scroll deltas dispatched in the same frame
    // both clamped against the same stale value, over-consumed, and drove the player above its
    // resting position until the spring pulled it back.
    var dragPx by remember { mutableFloatStateOf(0f) }
    val dismissPx = with(LocalDensity.current) { DismissDistance.toPx() }

    val settle: (Float) -> Unit = { velocity ->
        scope.launch {
            if (dragPx > dismissPx || velocity > DISMISS_VELOCITY) {
                onCollapse()
            } else {
                // Spring rather than tween: a snap-back is a physical recoil, and it has to
                // survive being released mid-flight without looking like it restarted.
                animate(dragPx, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) {
                    value, _ -> dragPx = value
                }
            }
        }
    }

    // The queue scrolls; the player drags. This hands one gesture between them: the player only
    // moves on downward drags the queue itself could not use (so, when it is already at the
    // top), and an upward drag closes that gap before the queue starts scrolling again.
    val dragToDismiss = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || dragPx <= 0f) {
                    return Offset.Zero
                }
                val delta = -minOf(-available.y, dragPx)
                dragPx += delta
                return Offset(0f, delta)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                dragPx += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragPx <= 0f) return Velocity.Zero
                // Swallow the fling: the queue must not coast on after a drag that was aimed
                // at the player.
                settle(available.y)
                return available
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .offset { IntOffset(0, dragPx.roundToInt()) }
            .nestedScroll(dragToDismiss),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    // Dragging the header works wherever the queue happens to be scrolled to.
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            dragPx = (dragPx + delta).coerceAtLeast(0f)
                        },
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Back to browsing",
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "ON AIR",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                // Balances the chevron so the eyebrow sits optically centered.
                Spacer(Modifier.size(48.dp))
            }
        },
    ) { padding ->
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
            state = queueState,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.lg)) {
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
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            state.artist?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
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
    // The oversized lime play button is the design's centerpiece; the seek buttons sit in tonal
    // circles so the whole row reads as one instrument.
    val tonalCircle = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.size(Spacing.xs))
        FilledIconButton(onClick = onBack10, colors = tonalCircle, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(Spacing.md))
        FilledIconButton(
            onClick = onPlayPause,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(92.dp),
        ) {
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.size(Spacing.md))
        FilledIconButton(onClick = onForward30, colors = tonalCircle, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(Spacing.xs))
        IconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next", modifier = Modifier.size(30.dp))
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

    val chipShape = RoundedCornerShape(14.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, chipShape)
                    .clickable(onClick = { speedMenu = true }, onClickLabel = "Playback speed")
                    .padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    formatSpeed(speed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
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

        Row(
            Modifier
                .height(44.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.primary, chipShape)
                .clickable(onClick = onSwitchToVideo, onClickLabel = "Switch to video")
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.OndemandVideo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(
                "Watch video",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column(Modifier.fillMaxWidth()) {
        // A fat rounded bar instead of the stock hairline-and-thumb: the fill edge is the
        // position indicator, matching the chunky transport below.
        Slider(
            value = if (dragging) dragValue else progress.coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (durationMs > 0) onSeek((dragValue * durationMs).toLong())
                dragging = false
            },
            enabled = durationMs > 0,
            thumb = {},
            track = { sliderState ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(sliderState.value.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            },
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
