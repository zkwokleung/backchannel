package com.zkwokleung.backchannel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zkwokleung.backchannel.ui.common.formatBytes
import com.zkwokleung.backchannel.ui.theme.Spacing
import com.zkwokleung.backchannel.update.AppUpdater
import com.zkwokleung.backchannel.update.AvailableUpdate
import com.zkwokleung.backchannel.update.UpdateFailure

/**
 * The app's own version and update action, in one row — the same shape as [EngineRow] above it.
 *
 * The whole flow lives in this one row rather than a screen of its own: there is exactly one
 * decision to make (take this version or don't), and the confirm dialog is where the detail goes.
 */
@Composable
internal fun AppUpdateRow(
    version: String,
    state: AppUpdater.State,
    onCheck: () -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text("Backchannel") },
        supportingContent = {
            Text(
                state.subtitle(version),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (state is AppUpdater.State.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailingContent = {
            when (state) {
                is AppUpdater.State.Checking,
                is AppUpdater.State.Verifying,
                is AppUpdater.State.AwaitingConfirmation,
                is AppUpdater.State.Installing,
                -> Spinner()

                is AppUpdater.State.Downloading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    CircularProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.size(SPINNER_SIZE),
                        strokeWidth = 2.dp,
                    )
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel download")
                    }
                }

                is AppUpdater.State.Available -> TextButton(onClick = onUpdate) { Text("Update") }

                is AppUpdater.State.ReadyToInstall ->
                    TextButton(onClick = onInstall) { Text("Install") }

                is AppUpdater.State.Failed ->
                    if (state.failure == UpdateFailure.InstallNotPermitted) {
                        TextButton(onClick = onAllowInstalls) { Text("Allow") }
                    } else {
                        TextButton(onClick = onCheck) { Text("Retry") }
                    }

                is AppUpdater.State.Idle,
                is AppUpdater.State.UpToDate,
                -> TextButton(onClick = onCheck) { Text("Check") }
            }
        },
    )
}

private fun AppUpdater.State.subtitle(installed: String): String = when (this) {
    is AppUpdater.State.Idle -> installed
    is AppUpdater.State.Checking -> "Checking…"
    is AppUpdater.State.UpToDate -> "$installed · Up to date"
    is AppUpdater.State.Available -> "${update.version} available"
    is AppUpdater.State.Downloading -> "Downloading… $percent%"
    is AppUpdater.State.Verifying -> "Verifying…"
    is AppUpdater.State.ReadyToInstall -> "${update.version} ready to install"
    is AppUpdater.State.AwaitingConfirmation, is AppUpdater.State.Installing -> "Installing…"
    is AppUpdater.State.Failed -> failure.message
}

@Composable
private fun Spinner() {
    CircularProgressIndicator(Modifier.size(SPINNER_SIZE), strokeWidth = 2.dp)
}

/**
 * Confirms the download before spending the data — an ABI split is ~18 MB and the universal
 * build is nearly 50.
 */
@Composable
internal fun UpdateDialog(
    update: AvailableUpdate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update to ${update.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = NOTES_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                formatBytes(update.sizeBytes)?.let { size ->
                    Text("$size download", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Installing restarts Backchannel and stops playback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // GitHub release notes are Markdown; they are shown as written rather than
                // pulling in a renderer for a handful of lines.
                update.notes?.let { notes ->
                    Text(notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Download") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

private val SPINNER_SIZE = 22.dp
private val NOTES_MAX_HEIGHT = 280.dp
