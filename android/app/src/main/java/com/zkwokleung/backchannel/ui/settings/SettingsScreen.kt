package com.zkwokleung.backchannel.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zkwokleung.backchannel.BuildConfig
import com.zkwokleung.backchannel.engine.YtdlpEngine
import com.zkwokleung.backchannel.ui.common.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val engine: YtdlpEngine) : ViewModel() {

    val initState: StateFlow<YtdlpEngine.InitState> = engine.initState

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    fun retryInit() {
        viewModelScope.launch { engine.initialize() }
    }

    fun updateYtdlp() {
        if (_updateState.value.inProgress) return
        _updateState.value = UpdateUiState(inProgress = true)
        viewModelScope.launch {
            _updateState.value = try {
                val version = engine.update()
                UpdateUiState(message = "yt-dlp is up to date (${version ?: "unknown"})")
            } catch (t: Throwable) {
                UpdateUiState(message = t.message ?: "Update failed")
            }
        }
    }

    fun consumeMessage() {
        _updateState.value = _updateState.value.copy(message = null)
    }
}

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
            Text(
                "Extraction engine",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text("yt-dlp") },
                supportingContent = {
                    Text(
                        when (val s = initState) {
                            is YtdlpEngine.InitState.Ready -> "Ready · ${s.ytdlpVersion ?: "unknown version"}"
                            is YtdlpEngine.InitState.Initializing -> "Starting up…"
                            is YtdlpEngine.InitState.Failed -> "Failed: ${s.message}"
                            is YtdlpEngine.InitState.NotStarted -> "Not started"
                        }
                    )
                },
                trailingContent = {
                    when (initState) {
                        is YtdlpEngine.InitState.Initializing ->
                            CircularProgressIndicator(Modifier.padding(4.dp))
                        is YtdlpEngine.InitState.Failed ->
                            TextButton(onClick = viewModel::retryInit) { Text("Retry") }
                        else -> {}
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Update yt-dlp") },
                supportingContent = {
                    Text("Fixes extraction breakage without reinstalling the app.")
                },
                trailingContent = {
                    Button(
                        onClick = viewModel::updateYtdlp,
                        enabled = !updateState.inProgress &&
                            initState is YtdlpEngine.InitState.Ready,
                    ) {
                        if (updateState.inProgress) {
                            CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Update")
                        }
                    }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                "Playback",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Battery optimization") },
                supportingContent = {
                    Text(
                        "For reliable background listening, allow Backchannel to run " +
                            "unrestricted in battery settings."
                    )
                },
                trailingContent = {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text("Open") }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Backchannel") },
                supportingContent = {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} · " +
                            "A personal media player for content you're authorized to access. " +
                            "Runs entirely on this device."
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Source code") },
                supportingContent = { Text("github.com/zkwokleung/backchannel") },
                modifier = Modifier,
                trailingContent = {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/zkwokleung/backchannel"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) { Text("Open") }
                },
            )
        }
    }
}
