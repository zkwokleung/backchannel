package com.zkwokleung.backchannel.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus
import com.zkwokleung.backchannel.engine.StreamMode

/** What a row shows about an item's saved copy. Mirrors [PlaybackProgressUi]. */
sealed interface DownloadStateUi {
    data object None : DownloadStateUi
    data object Queued : DownloadStateUi
    data class Downloading(val percent: Int) : DownloadStateUi
    data class Downloaded(val mode: StreamMode) : DownloadStateUi
    data class Failed(val message: String) : DownloadStateUi

    /** Short subtitle fragment, or null when there is nothing to say. */
    fun label(): String? = when (this) {
        None -> null
        Queued -> "Queued"
        is Downloading -> if (percent >= 100) "Finishing…" else "Downloading $percent%"
        is Downloaded -> "Saved"
        is Failed -> "Download failed"
    }

    companion object {
        fun of(entity: DownloadEntity?): DownloadStateUi = when (entity?.status) {
            null -> None
            DownloadStatus.QUEUED -> Queued
            DownloadStatus.DOWNLOADING -> Downloading(entity.progressPercent.coerceIn(0, 100))
            DownloadStatus.COMPLETE -> Downloaded(entity.mode)
            DownloadStatus.FAILED -> Failed(entity.error ?: "Download failed")
        }
    }
}

/** The ⋮ menu entries for saving/cancelling/removing, identical on every list screen. */
@Composable
fun DownloadMenuItems(
    state: DownloadStateUi,
    onDownload: (StreamMode) -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    dismiss: () -> Unit,
) {
    when (state) {
        DownloadStateUi.None, is DownloadStateUi.Failed -> {
            DropdownMenuItem(
                text = { Text("Download audio") },
                leadingIcon = { Icon(Icons.Filled.Download, null) },
                onClick = { dismiss(); onDownload(StreamMode.AUDIO) },
            )
            DropdownMenuItem(
                text = { Text("Download video") },
                leadingIcon = { Icon(Icons.Filled.DownloadForOffline, null) },
                onClick = { dismiss(); onDownload(StreamMode.VIDEO) },
            )
        }
        DownloadStateUi.Queued, is DownloadStateUi.Downloading -> DropdownMenuItem(
            text = { Text("Cancel download") },
            leadingIcon = { Icon(Icons.Outlined.Cancel, null) },
            onClick = { dismiss(); onCancel() },
        )
        is DownloadStateUi.Downloaded -> DropdownMenuItem(
            text = { Text("Remove download") },
            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
            onClick = { dismiss(); onRemove() },
        )
    }
}

@Composable
fun DownloadedBadge() {
    Icon(
        Icons.Filled.DownloadDone,
        contentDescription = "Saved for offline",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(16.dp),
    )
}

/**
 * Transfer progress on the thumbnail's top edge. The bottom edge belongs to resume progress in
 * `tertiary`; `secondary` keeps this from reading as "playing".
 */
@Composable
fun BoxScope.DownloadProgressOverlay(state: DownloadStateUi) {
    if (state !is DownloadStateUi.Downloading && state != DownloadStateUi.Queued) return
    val modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .height(3.dp)
    val color = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
    if (state is DownloadStateUi.Downloading && state.percent in 1..99) {
        LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = modifier,
            color = color,
            trackColor = track,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(modifier = modifier, color = color, trackColor = track)
    }
}

/**
 * Wraps a download action with the one-time notification permission ask (API 33+). The
 * download proceeds either way — the foreground service runs without it, just silently.
 */
@Composable
fun rememberDownloadAction(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending.value?.invoke()
        pending.value = null
    }
    return { action ->
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            pending.value = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
}
