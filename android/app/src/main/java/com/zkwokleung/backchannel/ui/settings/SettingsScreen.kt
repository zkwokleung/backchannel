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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zkwokleung.backchannel.R
import com.zkwokleung.backchannel.engine.YtdlpEngine
import com.zkwokleung.backchannel.ui.common.appViewModel
import com.zkwokleung.backchannel.ui.theme.Spacing
import com.zkwokleung.backchannel.update.AppUpdater
import com.zkwokleung.backchannel.update.AvailableUpdate

/**
 * Four controls and a footer.
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
    val viewModel = appViewModel { SettingsViewModel(it.engine, it.appUpdater) }
    val initState by viewModel.initState.collectAsState()
    val engineUpdate by viewModel.engineUpdate.collectAsState()
    val appUpdate by viewModel.appUpdate.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }

    LaunchedEffect(engineUpdate.message) {
        engineUpdate.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // The system's confirmation screen is launched from here, not from UpdateInstallReceiver: a
    // startActivity from a background receiver is silently dropped on Android 10+. By the time
    // this effect runs, the screen is on-screen by definition.
    LaunchedEffect(appUpdate) {
        (appUpdate as? AppUpdater.State.AwaitingConfirmation)?.let { awaiting ->
            context.openSafely(awaiting.intent)
            viewModel.onConfirmationLaunched()
        }
    }

    // ACTION_MANAGE_UNKNOWN_APP_SOURCES returns no result, so resuming is the only reliable
    // signal that the toggle may have changed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshInstallPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    pendingUpdate?.let { update ->
        UpdateDialog(
            update = update,
            onConfirm = {
                pendingUpdate = null
                viewModel.downloadAppUpdate()
            },
            onDismiss = { pendingUpdate = null },
        )
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
            AppUpdateRow(
                version = viewModel.appVersion,
                state = appUpdate,
                onCheck = viewModel::checkForAppUpdate,
                onUpdate = {
                    val offered = (appUpdate as? AppUpdater.State.Available)?.update
                    // A version remembered from a previous launch has no asset details yet;
                    // there is nothing to put in the dialog until they are re-fetched.
                    if (offered != null && offered.downloadUrl.isNotEmpty()) {
                        pendingUpdate = offered
                    } else {
                        viewModel.checkForAppUpdate()
                    }
                },
                onCancel = viewModel::cancelAppUpdate,
                onInstall = viewModel::installAppUpdate,
                onAllowInstalls = {
                    // A few ROMs ship no unknown-sources screen; the app's own settings page is
                    // at least somewhere the toggle might live.
                    if (!context.openSafely(viewModel.unknownSourcesIntent())) {
                        context.openSafely(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                        )
                    }
                },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            EngineRow(
                initState = initState,
                updating = engineUpdate.inProgress,
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

/**
 * Ends the screen with the app's own mark instead of a paragraph about it.
 *
 * No version number: the update row above carries it, and printing it twice on a screen this
 * deliberately short reads as an oversight.
 */
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
            "Backchannel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val SOURCE_URL = "https://github.com/zkwokleung/backchannel"

/** Returns false when nothing on the device can handle the intent, which some ROMs manage. */
private fun android.content.Context.openSafely(intent: Intent): Boolean =
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
