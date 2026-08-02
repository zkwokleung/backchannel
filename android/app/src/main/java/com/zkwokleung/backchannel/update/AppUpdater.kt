package com.zkwokleung.backchannel.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.zkwokleung.backchannel.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the app itself current from GitHub Releases, the way `YtdlpEngine` keeps the engine
 * current from PyPI.
 *
 * The repo is public, so every request here is unauthenticated — there is no token in the app and
 * nothing to leak. That costs a 60 requests/hour per-IP budget, which a once-daily check plus the
 * occasional manual tap sits comfortably inside. Asset bytes come from `browser_download_url`,
 * which is served off github.com and doesn't touch that budget at all.
 *
 * Everything runs on the caller's [scope] — `AppContainer.applicationScope` in production — so
 * navigating away from Settings cannot cancel an 18 MB transfer in flight. `viewModelScope` dies
 * with the nav back-stack entry, which is exactly what happens when you tab away.
 */
class AppUpdater(
    private val appContext: Context,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val installer: ApkInstaller = ApkInstaller(appContext),
    private val installedVersion: String = BuildConfig.VERSION_NAME,
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.orEmpty().toList(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** What the update row is showing. Mirrors `YtdlpEngine.InitState` in shape and intent. */
    sealed interface State {
        /** Nothing asked yet this session, and nothing remembered from the last one. */
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val update: AvailableUpdate) : State
        data class Downloading(val update: AvailableUpdate, val percent: Int) : State
        data class Verifying(val update: AvailableUpdate) : State
        data class ReadyToInstall(val update: AvailableUpdate, val apk: File) : State

        /**
         * The system wants the user to confirm. Parked here rather than launched from the
         * broadcast receiver: a `startActivity` from the background is silently dropped on
         * Android 10+, so the Settings screen launches it when it is actually on screen.
         */
        data class AwaitingConfirmation(val update: AvailableUpdate, val intent: Intent) : State
        data object Installing : State

        /** Carries the offer it failed on, so Retry has something to resume. */
        data class Failed(val failure: UpdateFailure, val update: AvailableUpdate? = null) : State
    }

    private val downloadDir = File(appContext.cacheDir, DOWNLOAD_DIR)
    private val prefs = UpdatePrefs(appContext)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val checkMutex = Mutex()

    private val _state = MutableStateFlow<State>(
        // A version found on a previous launch outlives the process; without this the row would
        // claim "Up to date" until the daily interval came round again.
        prefs.knownVersion
            ?.takeIf { compareVersions(it, installedVersion) == VersionOrder.NEWER }
            ?.let { State.Available(AvailableUpdate.placeholder(it)) }
            ?: State.Idle,
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** The version this build reports, for the Settings row. */
    val currentVersion: String get() = installedVersion

    // ── Checking ──────────────────────────────────────────────────────────────

    /** Asks GitHub now, regardless of when the last check ran. Never throws. */
    fun check() {
        scope.launch { runCheck() }
    }

    /**
     * The once-a-day check, called at startup. Silent about failures — a launch with no
     * connectivity should not leave an error sitting in Settings that nobody asked for.
     */
    suspend fun checkIfDue() {
        // A self-update kills this process, so post-install cleanup never runs. This sweep is
        // the only reliable way an abandoned 18 MB download gets reclaimed.
        if (_state.value !is State.ReadyToInstall) clearDownloads()
        if (!prefs.isCheckDue(System.currentTimeMillis())) return
        runCheck(silent = true)
    }

    private suspend fun runCheck(silent: Boolean = false) = checkMutex.withLock {
        // Anything past Available already knows about the newest release; re-checking would only
        // stomp a download in progress.
        if (_state.value.isBusy) return
        if (!silent) _state.value = State.Checking
        _state.value = try {
            resolveLatest()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val failure = failureFor(t)
            Log.w(TAG, "update check failed: $failure", t)
            if (silent) State.Idle else State.Failed(failure)
        }
    }

    private suspend fun resolveLatest(): State {
        val release = fetchLatestRelease()
        prefs.lastCheckMillis = System.currentTimeMillis()

        if (compareVersions(release.versionName, installedVersion) != VersionOrder.NEWER) {
            prefs.knownVersion = null
            return State.UpToDate
        }

        val asset = pickAsset(release.assets, supportedAbis)
            ?: return State.Failed(UpdateFailure.NoMatchingAsset)

        prefs.knownVersion = release.versionName
        Log.i(TAG, "update available: ${release.versionName} (${asset.name})")
        return State.Available(
            AvailableUpdate(
                version = release.versionName,
                notes = release.body?.trim()?.takeIf { it.isNotEmpty() },
                assetName = asset.name,
                downloadUrl = asset.browserDownloadUrl,
                sizeBytes = asset.size,
                digest = asset.digest,
                checksumsUrl = release.assets
                    .firstOrNull { it.name == CHECKSUMS_ASSET_NAME }
                    ?.browserDownloadUrl,
            ),
        )
    }

    private suspend fun fetchLatestRelease(): GitHubRelease = withContext(dispatcher) {
        val body = getString(LATEST_RELEASE_URL, github = true)
        try {
            json.decodeFromString<GitHubRelease>(body)
        } catch (t: SerializationException) {
            throw UpdateException(UpdateFailure.MalformedResponse, t)
        }
    }

    // ── Downloading and verifying ─────────────────────────────────────────────

    /** Downloads, checksums and signature-checks the offered update. No-op unless one is offered. */
    fun download() {
        val update = (_state.value as? State.Available)?.update ?: return
        if (update.downloadUrl.isEmpty()) {
            // Restored from prefs on a cold start — only the version was persisted, so go and
            // fetch the asset list before there is anything to download.
            check()
            return
        }
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = State.Downloading(update, percent = 0)
            try {
                val apk = fetchApk(update)
                _state.value = State.Verifying(update)
                // Everything the OS is about to check, checked here first so a failure is a
                // sentence rather than a system toast.
                val rejection = withContext(dispatcher) { installer.verify(apk) }
                if (rejection == null) {
                    _state.value = State.ReadyToInstall(update, apk)
                } else {
                    clearDownloads()
                    _state.value = State.Failed(rejection, update)
                }
            } catch (t: Throwable) {
                withContext(NonCancellable) { clearDownloads() }
                _state.value = if (t is CancellationException) {
                    // The user cancelled; put the offer back rather than showing an error.
                    State.Available(update)
                } else {
                    val failure = failureFor(t)
                    Log.w(TAG, "update download failed: $failure", t)
                    State.Failed(failure, update)
                }
                if (t is CancellationException) throw t
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun fetchApk(update: AvailableUpdate): File = withContext(dispatcher) {
        val expected = expectedHash(update) ?: throw UpdateException(UpdateFailure.Unverifiable)

        clearDownloads()
        downloadDir.mkdirs()
        // Written under .part and renamed on success, so a truncated file can never be mistaken
        // for a finished one.
        val partial = File(downloadDir, update.assetName + PARTIAL_SUFFIX)
        val target = File(downloadDir, update.assetName)

        val digest = MessageDigest.getInstance("SHA-256")
        // The read loop below runs in a plain lambda, so it holds the job rather than reaching
        // for currentCoroutineContext() on every 64 KB chunk.
        val callerJob = currentCoroutineContext().job
        val written = get(update.downloadUrl) { body ->
            // contentLength() can be -1 once github.com redirects to its asset host.
            val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
            body.byteStream().use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var read = 0L
                    var lastEmit = 0L
                    while (true) {
                        callerJob.ensureActive()
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        read += n
                        // Throttled by bytes, not time: ~70 emissions for an 18 MB APK, and
                        // deterministic enough to reason about.
                        if (read - lastEmit >= PROGRESS_STEP_BYTES) {
                            lastEmit = read
                            _state.value = State.Downloading(update, percentOf(read, total))
                        }
                    }
                    read
                }
            }
        }

        if (update.sizeBytes > 0 && written != update.sizeBytes) {
            throw UpdateException(UpdateFailure.DownloadIncomplete)
        }
        val actual = digest.digest().toHexString()
        if (!actual.equals(expected, ignoreCase = true)) {
            Log.w(TAG, "checksum mismatch for ${update.assetName}: expected $expected, got $actual")
            throw UpdateException(UpdateFailure.ChecksumMismatch)
        }
        if (!partial.renameTo(target)) throw UpdateException(UpdateFailure.DownloadIncomplete)
        Log.i(TAG, "downloaded and checksummed ${update.assetName}")
        target
    }

    /**
     * The expected SHA-256, preferring the asset's own `digest` when GitHub populates it (it is
     * null on real releases today) and falling back to the `SHA256SUMS.txt` that `release.yml`
     * uploads. See [UpdateFailure.ChecksumMismatch] for what this does and does not prove.
     */
    private suspend fun expectedHash(update: AvailableUpdate): String? {
        update.digest?.removePrefix(DIGEST_PREFIX)
            ?.takeIf { it.length == SHA256_HEX_LENGTH }
            ?.let { return it }

        val url = update.checksumsUrl ?: return null
        return parseChecksums(getString(url))[update.assetName]
    }

    // ── Installing ────────────────────────────────────────────────────────────

    /**
     * Hands the verified APK to the system installer. Called from the Settings row; the result
     * arrives asynchronously via [UpdateInstallReceiver].
     */
    fun install() {
        val ready = _state.value as? State.ReadyToInstall ?: return
        if (!installer.canInstall()) {
            _state.value = State.Failed(UpdateFailure.InstallNotPermitted, ready.update)
            return
        }
        scope.launch {
            _state.value = State.Installing
            runCatching { installer.install(ready.apk) }.onFailure { t ->
                Log.w(TAG, "commit failed", t)
                _state.value = State.Failed(failureFor(t), ready.update)
            }
        }
    }

    /** The system screen for Backchannel's "install unknown apps" toggle. */
    fun unknownSourcesIntent(): Intent = installer.unknownSourcesIntent()

    /**
     * Re-reads the "install unknown apps" toggle and re-arms Install if it is now on.
     *
     * That settings screen returns no result — its documented output is "Nothing" — so this is
     * driven from the Settings screen's ON_RESUME rather than an activity result.
     */
    fun refreshInstallPermission() {
        val failed = _state.value as? State.Failed ?: return
        if (failed.failure != UpdateFailure.InstallNotPermitted) return
        val update = failed.update ?: return
        if (!installer.canInstall()) return
        readyFileFor(update)?.let { _state.value = State.ReadyToInstall(update, it) }
    }

    /** Called by [UpdateInstallReceiver] when the system needs the user to confirm. */
    fun onConfirmationRequired(intent: Intent) {
        val update = _state.value.currentUpdate ?: return
        _state.value = State.AwaitingConfirmation(update, intent)
    }

    /** Called once the Settings screen has actually launched the confirmation activity. */
    fun onConfirmationLaunched() {
        if (_state.value is State.AwaitingConfirmation) _state.value = State.Installing
    }

    /**
     * Success is best-effort: replacing the package kills this process, so the broadcast may
     * never be delivered. The row is correct either way — on the next launch
     * `BuildConfig.VERSION_NAME` is the new one and the check reports up to date.
     */
    fun onInstallSucceeded() {
        prefs.knownVersion = null
        scope.launch { withContext(dispatcher) { clearDownloads() } }
        _state.value = State.UpToDate
    }

    fun onInstallFailed(failure: UpdateFailure) {
        val pending = _state.value.currentUpdate
        // Backing out of the system dialog is not an error, and the verified APK is still good —
        // put the row back to Install so trying again is instant.
        val readyAgain = if (failure == UpdateFailure.UserAborted && pending != null) {
            readyFileFor(pending)?.let { State.ReadyToInstall(pending, it) }
        } else {
            null
        }
        _state.value = readyAgain ?: State.Failed(failure, pending)
    }

    private fun readyFileFor(update: AvailableUpdate): File? =
        File(downloadDir, update.assetName).takeIf { it.isFile }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private suspend fun getString(url: String, github: Boolean = false): String =
        get(url, github) { it.string() }

    /** Blocking; every caller is already inside `withContext(dispatcher)`. */
    private suspend fun <T> get(url: String, github: Boolean = false, read: (ResponseBody) -> T): T {
        val request = Request.Builder()
            .url(url)
            // GitHub rejects requests with no UA, and an explicit one is what belongs in their logs.
            .header("User-Agent", USER_AGENT)
            .apply {
                if (github) {
                    header("Accept", GITHUB_JSON)
                    header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                }
            }
            .build()

        val call = client.newCall(request)
        // ensureActive() in the read loop covers the normal case; this unblocks a socket read
        // that is already parked when the job is cancelled.
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw UpdateException(failureForStatus(response.code))
                val body = response.body ?: throw UpdateException(UpdateFailure.MalformedResponse)
                read(body)
            }
        } finally {
            handle.dispose()
        }
    }

    private fun clearDownloads() {
        runCatching { downloadDir.deleteRecursively() }
    }

    private val State.isBusy: Boolean
        get() = this is State.Downloading || this is State.Verifying ||
            this is State.ReadyToInstall || this is State.AwaitingConfirmation ||
            this is State.Installing

    private val State.currentUpdate: AvailableUpdate?
        get() = when (this) {
            is State.Available -> update
            is State.Downloading -> update
            is State.Verifying -> update
            is State.ReadyToInstall -> update
            is State.AwaitingConfirmation -> update
            is State.Failed -> update
            else -> null
        }

    private companion object {
        const val TAG = "AppUpdater"
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/zkwokleung/backchannel/releases/latest"
        const val GITHUB_JSON = "application/vnd.github+json"
        const val GITHUB_API_VERSION = "2022-11-28"
        const val USER_AGENT =
            "Backchannel/${BuildConfig.VERSION_NAME} (+https://github.com/zkwokleung/backchannel)"
        const val DOWNLOAD_DIR = "updates"
        const val PARTIAL_SUFFIX = ".part"
        const val BUFFER_BYTES = 64 * 1024
        const val PROGRESS_STEP_BYTES = 256L * 1024
        const val DIGEST_PREFIX = "sha256:"
        const val SHA256_HEX_LENGTH = 64
    }
}

/** The parts of a release the UI and the download need, decoupled from the API DTOs. */
data class AvailableUpdate(
    val version: String,
    val notes: String?,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val digest: String?,
    val checksumsUrl: String?,
) {
    internal companion object {
        /**
         * A version remembered from a previous launch, before the asset list has been re-fetched.
         * Enough to say "0.3.0 available"; tapping Update re-checks to fill in the rest.
         */
        fun placeholder(version: String) = AvailableUpdate(
            version = version,
            notes = null,
            assetName = "",
            downloadUrl = "",
            sizeBytes = 0,
            digest = null,
            checksumsUrl = null,
        )
    }
}

internal fun percentOf(done: Long, total: Long): Int =
    if (total <= 0) 0 else ((done * 100) / total).coerceIn(0, 100).toInt()

internal fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
