package com.zkwokleung.backchannel.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zkwokleung.backchannel.data.db.VideoEntity
import com.zkwokleung.backchannel.data.db.WatchlistEntity
import com.zkwokleung.backchannel.engine.StreamMode
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import com.zkwokleung.backchannel.ui.common.EmptyState
import com.zkwokleung.backchannel.ui.common.EqualizerIndicator
import com.zkwokleung.backchannel.ui.common.MediaRow
import com.zkwokleung.backchannel.ui.common.PlaybackProgressUi
import com.zkwokleung.backchannel.ui.common.Thumbnail
import com.zkwokleung.backchannel.ui.player.sharedPlayerViewModel
import com.zkwokleung.backchannel.ui.common.appViewModel
import com.zkwokleung.backchannel.ui.common.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    channelYoutubeId: String,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    val viewModel = appViewModel {
        ChannelDetailViewModel(
            channelYoutubeId,
            it.channelRepository,
            it.watchlistRepository,
            it.playbackRepository,
            it.queuePlayer,
        )
    }
    val channel by viewModel.channel.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val playerState by sharedPlayerViewModel().uiState.collectAsState()
    val watchlists by viewModel.watchlists.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var watchlistTarget by remember { mutableStateOf<VideoEntity?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(channel?.title ?: "Channel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh uploads")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (rows.isEmpty()) {
                // Scrollable so the pull-to-refresh gesture is still delivered when empty.
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            modifier = Modifier.fillParentMaxSize(),
                            title = if (refreshing) "Fetching uploads…" else "No videos cached",
                            message = "Pull down or tap refresh to fetch this channel's uploads.",
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows, key = { it.video.youtubeId }) { row ->
                        val video = row.video
                        VideoRow(
                            video = video,
                            progress = row.progress,
                            isCurrent = playerState.mediaId == video.youtubeId,
                            isPlaying = playerState.isPlaying,
                            onPlayAudio = {
                                viewModel.play(video, StreamMode.AUDIO)
                                onOpenNowPlaying()
                            },
                            onPlayVideo = {
                                viewModel.play(video, StreamMode.VIDEO)
                                onOpenVideo()
                            },
                            onAddToWatchlist = { watchlistTarget = video },
                        )
                    }
                }
            }
        }
    }

    watchlistTarget?.let { video ->
        WatchlistPickerDialog(
            watchlists = watchlists,
            onDismiss = { watchlistTarget = null },
            onPick = { watchlist ->
                viewModel.addToWatchlist(video, watchlist.id)
                watchlistTarget = null
            },
            onCreate = { name ->
                viewModel.createWatchlistAndAdd(name, video)
                watchlistTarget = null
            },
        )
    }
}

@Composable
private fun VideoRow(
    video: VideoEntity,
    progress: PlaybackProgressUi,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlayAudio: () -> Unit,
    onPlayVideo: () -> Unit,
    onAddToWatchlist: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val played = progress is PlaybackProgressUi.Played

    MediaRow(
        title = video.title,
        subtitle = listOfNotNull(
            formatDuration(video.durationSeconds),
            "Played".takeIf { played },
        ).joinToString(" · "),
        titleColor = if (played && !isCurrent) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        highlighted = isCurrent,
        badge = if (isCurrent) {
            { EqualizerIndicator(playing = isPlaying) }
        } else {
            null
        },
        leading = {
            Thumbnail(
                model = video.thumbnail,
                modifier = Modifier.alpha(if (played) 0.45f else 1f),
            ) {
                // Resume progress sits on the thumbnail's bottom edge, costing no row height.
                if (progress is PlaybackProgressUi.InProgress) {
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                        drawStopIndicator = {},
                    )
                }
            }
        },
        onClick = onPlayAudio,
        onClickLabel = "Listen",
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Listen (audio)") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                        onClick = { menuOpen = false; onPlayAudio() },
                    )
                    DropdownMenuItem(
                        text = { Text("Watch (video)") },
                        leadingIcon = { Icon(Icons.Filled.OndemandVideo, null) },
                        onClick = { menuOpen = false; onPlayVideo() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to watchlist") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                        onClick = { menuOpen = false; onAddToWatchlist() },
                    )
                }
            }
        },
    )
}

@Composable
fun WatchlistPickerDialog(
    watchlists: List<WatchlistEntity>,
    onDismiss: () -> Unit,
    onPick: (WatchlistEntity) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(watchlists.isEmpty()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New watchlist" else "Add to watchlist") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column {
                    watchlists.forEach { watchlist ->
                        Text(
                            watchlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(watchlist) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                    Text("Create & add")
                }
            } else {
                TextButton(onClick = { creating = true }) { Text("New watchlist") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
