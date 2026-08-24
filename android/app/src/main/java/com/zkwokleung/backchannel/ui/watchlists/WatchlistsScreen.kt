package com.zkwokleung.backchannel.ui.watchlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zkwokleung.backchannel.data.db.WatchlistEntity
import com.zkwokleung.backchannel.ui.common.EmptyState
import com.zkwokleung.backchannel.ui.common.MediaRow
import com.zkwokleung.backchannel.ui.theme.FabListClearance
import com.zkwokleung.backchannel.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistsScreen(onOpenWatchlist: (Long) -> Unit) {
    val viewModel = appViewModel { WatchlistsViewModel(it.watchlistRepository) }
    val watchlists by viewModel.watchlists.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WatchlistEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<WatchlistEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Watchlists") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New watchlist")
            }
        },
    ) { padding ->
        if (watchlists.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Filled.VideoLibrary,
                title = "No watchlists",
                message = "A watchlist is a queue you build yourself — add videos to it from any channel.",
                actionLabel = "New watchlist",
                onAction = { showCreate = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = FabListClearance),
            ) {
                items(watchlists, key = { it.id }) { watchlist ->
                    MediaRow(
                        title = watchlist.name,
                        leading = {
                            // A leading mark gives these rows the same rhythm as the video
                            // lists, which all lead with a thumbnail.
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        },
                        onClick = { onOpenWatchlist(watchlist.id) },
                        onClickLabel = "Open watchlist",
                        trailing = {
                            IconButton(onClick = { renameTarget = watchlist }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "Rename",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleteTarget = watchlist }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "New watchlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { viewModel.create(it); showCreate = false },
        )
    }
    renameTarget?.let { target ->
        NameDialog(
            title = "Rename watchlist",
            initial = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { viewModel.rename(target.id, it); renameTarget = null },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete watchlist?") },
            text = { Text("\"${target.name}\" and its entries will be deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target.id); deleteTarget = null }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
