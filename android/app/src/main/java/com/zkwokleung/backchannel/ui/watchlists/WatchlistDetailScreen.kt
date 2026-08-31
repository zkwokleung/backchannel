package com.zkwokleung.backchannel.ui.watchlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zkwokleung.backchannel.data.db.WatchlistItemEntity
import com.zkwokleung.backchannel.ui.common.DownloadMenuItems
import com.zkwokleung.backchannel.ui.common.DownloadProgressOverlay
import com.zkwokleung.backchannel.ui.common.DownloadStateUi
import com.zkwokleung.backchannel.ui.common.DownloadedBadge
import com.zkwokleung.backchannel.ui.common.rememberDownloadAction
import com.zkwokleung.backchannel.engine.StreamMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import com.zkwokleung.backchannel.ui.common.EqualizerIndicator
import com.zkwokleung.backchannel.ui.common.MediaRow
import com.zkwokleung.backchannel.ui.common.PlaybackProgressUi
import com.zkwokleung.backchannel.ui.player.sharedPlayerViewModel
import com.zkwokleung.backchannel.ui.common.Thumbnail
import com.zkwokleung.backchannel.ui.theme.Spacing
import com.zkwokleung.backchannel.ui.common.EmptyState
import com.zkwokleung.backchannel.ui.common.appViewModel
import com.zkwokleung.backchannel.ui.common.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistDetailScreen(
    watchlistId: Long,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    val viewModel = appViewModel {
        WatchlistDetailViewModel(
            watchlistId,
            it.watchlistRepository,
            it.playbackRepository,
            it.downloadRepository,
            it.downloadManager,
            it.queuePlayer,
        )
    }
    val requestDownload = rememberDownloadAction()
    val watchlist by viewModel.watchlist.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val playerState by sharedPlayerViewModel().uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(watchlist?.name ?: "Watchlist", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Filled.VideoLibrary,
                title = "Nothing queued",
                message = "Add videos from a channel with the ⋮ menu, and they play here in order.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = Spacing.lg),
            ) {
                itemsIndexed(rows, key = { _, row -> row.item.id }) { index, row ->
                    val item = row.item
                    WatchlistItemRow(
                        item = item,
                        progress = row.progress,
                        download = row.download,
                        isCurrent = playerState.mediaId == item.videoYoutubeId,
                        isPlaying = playerState.isPlaying,
                        canMoveUp = index > 0,
                        canMoveDown = index < rows.lastIndex,
                        onPlayAudio = {
                            viewModel.play(item, StreamMode.AUDIO)
                            onOpenNowPlaying()
                        },
                        onPlayVideo = {
                            viewModel.play(item, StreamMode.VIDEO)
                            onOpenVideo()
                        },
                        onMoveUp = { viewModel.move(item, up = true) },
                        onMoveDown = { viewModel.move(item, up = false) },
                        onRemove = { viewModel.remove(item) },
                        onDownload = { mode -> requestDownload { viewModel.download(item, mode) } },
                        onCancelDownload = { viewModel.cancelDownload(item) },
                        onRemoveDownload = { viewModel.removeDownload(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchlistItemRow(
    item: WatchlistItemEntity,
    progress: PlaybackProgressUi,
    download: DownloadStateUi,
    isCurrent: Boolean,
    isPlaying: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPlayAudio: () -> Unit,
    onPlayVideo: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDownload: (StreamMode) -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val played = progress is PlaybackProgressUi.Played

    MediaRow(
        title = item.title,
        subtitle = listOfNotNull(
            item.channelTitle,
            formatDuration(item.durationSeconds),
            "Played".takeIf { played },
            download.label(),
        ).joinToString(" · "),
        titleColor = when {
            isCurrent -> MaterialTheme.colorScheme.primary
            played -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurface
        },
        highlighted = isCurrent,
        badge = when {
            isCurrent -> {
                { EqualizerIndicator(playing = isPlaying) }
            }
            download is DownloadStateUi.Downloaded -> {
                { DownloadedBadge() }
            }
            else -> null
        },
        leading = {
            Thumbnail(
                model = item.thumbnail,
                modifier = Modifier.alpha(if (played) 0.45f else 1f),
            ) {
                DownloadProgressOverlay(download)
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
            // Four stacked icon buttons used to make this the tallest, most cramped row in the
            // app and squeezed titles to two truncated lines; one menu matches the other screens.
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
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                        enabled = canMoveUp,
                        onClick = { menuOpen = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                        enabled = canMoveDown,
                        onClick = { menuOpen = false; onMoveDown() },
                    )
                    DownloadMenuItems(
                        state = download,
                        onDownload = onDownload,
                        onCancel = onCancelDownload,
                        onRemove = onRemoveDownload,
                        dismiss = { menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        },
    )
}
