package com.zkwokleung.backchannel.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.engine.StreamMode
import com.zkwokleung.backchannel.ui.common.DownloadProgressOverlay
import com.zkwokleung.backchannel.ui.common.DownloadStateUi
import com.zkwokleung.backchannel.ui.common.EmptyState
import com.zkwokleung.backchannel.ui.common.EqualizerIndicator
import com.zkwokleung.backchannel.ui.common.MediaRow
import com.zkwokleung.backchannel.ui.common.Thumbnail
import com.zkwokleung.backchannel.ui.common.appViewModel
import com.zkwokleung.backchannel.ui.common.formatBytes
import com.zkwokleung.backchannel.ui.common.formatDate
import com.zkwokleung.backchannel.ui.common.formatDuration
import com.zkwokleung.backchannel.ui.player.sharedPlayerViewModel
import com.zkwokleung.backchannel.ui.theme.Spacing
import com.zkwokleung.backchannel.ui.theme.timecode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenVideo: () -> Unit,
) {
    val viewModel = appViewModel {
        DownloadsViewModel(it.downloadRepository, it.downloadManager, it.queuePlayer)
    }
    val ui by viewModel.ui.collectAsState()
    val playerState by sharedPlayerViewModel().uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (ui.isEmpty) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                icon = Icons.Filled.Download,
                title = "Nothing saved",
                message = "Save a video for offline listening from its ⋮ menu, and it shows up here.",
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Spacing.lg),
        ) {
            item { StorageHeader(ui.totalBytes, ui.completed.size) }
            if (ui.active.isNotEmpty()) {
                item { SectionLabel("In progress") }
                items(ui.active, key = { it.videoYoutubeId }) { row ->
                    DownloadRow(
                        row = row,
                        isCurrent = false,
                        isPlaying = false,
                        onListen = {},
                        onWatch = {},
                        onRetry = { viewModel.retry(row) },
                        onCancel = { viewModel.cancel(row) },
                        onDelete = { viewModel.remove(row) },
                    )
                }
            }
            if (ui.completed.isNotEmpty()) {
                item { SectionLabel("Saved") }
                items(ui.completed, key = { it.videoYoutubeId }) { row ->
                    DownloadRow(
                        row = row,
                        isCurrent = playerState.mediaId == row.videoYoutubeId,
                        isPlaying = playerState.isPlaying,
                        onListen = {
                            viewModel.play(row, StreamMode.AUDIO)
                            onOpenNowPlaying()
                        },
                        onWatch = {
                            viewModel.play(row, StreamMode.VIDEO)
                            onOpenVideo()
                        },
                        onRetry = {},
                        onCancel = {},
                        onDelete = { viewModel.remove(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageHeader(totalBytes: Long, count: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Text(
            text = formatBytes(totalBytes) ?: "0 KB",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFeatureSettings = MaterialTheme.typography.timecode.fontFeatureSettings,
            ),
        )
        Text(
            text = "used by $count saved ${if (count == 1) "item" else "items"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun DownloadRow(
    row: DownloadEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onListen: () -> Unit,
    onWatch: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val state = DownloadStateUi.of(row)
    val completed = row.status == DownloadStatus.COMPLETE
    val failed = row.status == DownloadStatus.FAILED

    val subtitle = when {
        completed -> listOfNotNull(
            row.channelTitle,
            formatDuration(row.durationSeconds),
            formatBytes(row.sizeBytes),
            row.completedAt?.let(::formatDate),
        )
        failed -> listOfNotNull(row.channelTitle, row.error)
        else -> listOfNotNull(row.channelTitle, state.label())
    }.joinToString(" · ")

    MediaRow(
        title = row.title,
        subtitle = subtitle,
        titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        highlighted = isCurrent,
        badge = if (isCurrent) {
            { EqualizerIndicator(playing = isPlaying) }
        } else {
            null
        },
        leading = { Thumbnail(model = row.thumbnail) { DownloadProgressOverlay(state) } },
        onClick = when {
            completed -> onListen
            failed -> onRetry
            else -> null
        },
        onClickLabel = when {
            completed -> "Listen"
            failed -> "Retry"
            else -> null
        },
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (completed) {
                        DropdownMenuItem(
                            text = { Text("Listen (audio)") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                            onClick = { menuOpen = false; onListen() },
                        )
                        if (row.mode == StreamMode.VIDEO) {
                            DropdownMenuItem(
                                text = { Text("Watch (video)") },
                                leadingIcon = { Icon(Icons.Filled.OndemandVideo, null) },
                                onClick = { menuOpen = false; onWatch() },
                            )
                        }
                    }
                    if (failed) {
                        DropdownMenuItem(
                            text = { Text("Retry") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            onClick = { menuOpen = false; onRetry() },
                        )
                    }
                    if (completed || failed) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Cancel download") },
                            leadingIcon = { Icon(Icons.Outlined.Cancel, null) },
                            onClick = { menuOpen = false; onCancel() },
                        )
                    }
                }
            }
        },
    )
}
