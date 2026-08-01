package com.zkwokleung.backchannel.ui.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zkwokleung.backchannel.data.db.ChannelEntity
import com.zkwokleung.backchannel.ui.common.ChannelAvatar
import com.zkwokleung.backchannel.ui.common.EmptyState
import com.zkwokleung.backchannel.ui.common.MediaRow
import com.zkwokleung.backchannel.ui.theme.FabListClearance
import com.zkwokleung.backchannel.ui.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(onOpenChannel: (String) -> Unit) {
    val viewModel = appViewModel { ChannelsViewModel(it.channelRepository) }
    val channels by viewModel.channels.collectAsState()
    val addState by viewModel.addState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ChannelEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Channels") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add channel")
            }
        },
    ) { padding ->
        if (channels.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                icon = Icons.Filled.Subscriptions,
                title = "No channels yet",
                message = "Add a channel by its @handle or URL, and its uploads become a listening queue.",
                actionLabel = "Add a channel",
                onAction = { showAddDialog = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                // Clears the FAB, which used to sit on top of the last row.
                contentPadding = PaddingValues(bottom = FabListClearance),
            ) {
                items(channels, key = { it.youtubeId }) { channel ->
                    MediaRow(
                        title = channel.title,
                        subtitle = channel.handle,
                        leading = { ChannelAvatar(channel.thumbnail) },
                        onClick = { onOpenChannel(channel.youtubeId) },
                        onClickLabel = "Open channel",
                        trailing = {
                            IconButton(onClick = { pendingDelete = channel }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Remove channel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddChannelDialog(
            inProgress = addState.inProgress,
            error = addState.error,
            onDismiss = {
                if (!addState.inProgress) {
                    showAddDialog = false
                    viewModel.clearAddError()
                }
            },
            onAdd = { input -> viewModel.addChannel(input) { ok -> if (ok) showAddDialog = false } },
        )
    }

    pendingDelete?.let { channel ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove channel?") },
            text = { Text("\"${channel.title}\" and its cached videos will be removed. Watchlist entries stay.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeChannel(channel.youtubeId)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddChannelDialog(
    inProgress: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add channel") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    enabled = !inProgress,
                    label = { Text("@handle or channel URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (inProgress) {
                    Spacer(Modifier.size(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Fetching channel…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(input) }, enabled = !inProgress && input.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inProgress) { Text("Cancel") }
        },
    )
}
