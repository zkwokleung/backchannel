package com.zkwokleung.backchannel.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zkwokleung.backchannel.BuildConfig
import com.zkwokleung.backchannel.R
import com.zkwokleung.backchannel.engine.YtdlpEngine
import com.zkwokleung.backchannel.ui.common.appViewModel
import com.zkwokleung.backchannel.ui.theme.Spacing

/**
 * Three controls and a footer.
 *
 * Deliberately has no section headers: with one row per domain they label rather than group, and
 * leading icons carry the same information in less space. Add headers back when a section holds
 * two or more rows.
 *
 * Explanations live where they are needed — in the error messages that tell you to update yt-dlp,
 * and in docs/USAGE.md — not as permanent paragraphs under every row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val viewModel = appViewModel { SettingsViewModel(it.engine) }
    val initState by viewModel.initState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(updateState.message) {
        updateState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            EngineRow(
                initState = initState,
                updating = updateState.inProgress,
                onUpdate = viewModel::updateYtdlp,
                onRetry = viewModel::retryInit,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // The whole row is the target — a row that leaves the app does not need a button
            // bolted onto its end.
            LinkRow(
                icon = Icons.Rounded.BatteryFull,
                title = "Unrestricted battery use",
                subtitle = "Keeps audio playing with the screen off",
                onClick = {
                    context.openSafely(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LinkRow(
                icon = Icons.Rounded.Code,
                title = "Source code",
                subtitle = "github.com/zkwokleung/backchannel",
                onClick = {
                    context.openSafely(
                        Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))
                    )
                },
            )

            AppFooter()
        }
    }
}

/**
 * yt-dlp's version and its update action in one row.
 *
 * These used to be two rows — "yt-dlp" and "Update yt-dlp" — which said the engine's name twice
 * and spent a sentence explaining what an Update button does.
 */
@Composable
private fun EngineRow(
    initState: YtdlpEngine.InitState,
    updating: Boolean,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
) {
    val status = when (initState) {
        is YtdlpEngine.InitState.Ready -> initState.ytdlpVersion ?: "Ready"
        is YtdlpEngine.InitState.Initializing -> "Starting…"
        // The real error goes to the snackbar; an engine stack trace does not belong in a row.
        is YtdlpEngine.InitState.Failed -> "Couldn't start"
        is YtdlpEngine.InitState.NotStarted -> "Not started"
    }

    ListItem(
        leadingContent = {
            Icon(
                Icons.Rounded.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text("yt-dlp") },
        supportingContent = {
            Text(status, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            when {
                updating -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)

                initState is YtdlpEngine.InitState.Failed ->
                    TextButton(onClick = onRetry) { Text("Retry") }

                initState is YtdlpEngine.InitState.Ready ->
                    FilledTonalButton(onClick = onUpdate) { Text("Update") }

                else -> {}
            }
        },
    )
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick, onClickLabel = title),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

/** Ends the screen with the app's own mark instead of a paragraph about it. */
@Composable
private fun AppFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xxl, bottom = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Backchannel ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val SOURCE_URL = "https://github.com/zkwokleung/backchannel"

private fun android.content.Context.openSafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
