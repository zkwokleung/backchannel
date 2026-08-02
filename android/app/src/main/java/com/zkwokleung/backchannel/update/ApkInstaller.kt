package com.zkwokleung.backchannel.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.zkwokleung.backchannel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Hands a downloaded APK to the system installer, after checking everything the OS is about to
 * check anyway.
 *
 * Uses [PackageInstaller] rather than `ACTION_VIEW` on a `FileProvider` URI. The session API is
 * the only one that reports back what happened — `ACTION_VIEW` returns nothing, and
 * `ACTION_INSTALL_PACKAGE` (which does, via `EXTRA_RETURN_RESULT`) has been deprecated since
 * API 29. It also streams the APK over a binder fd, so the app needs no `<provider>`, no
 * `res/xml/file_paths.xml` and no exported surface at all.
 */
class ApkInstaller(private val appContext: Context) {

    private val packageManager get() = appContext.packageManager

    /** Whether the user has granted "install unknown apps" for Backchannel. */
    fun canInstall(): Boolean = packageManager.canRequestPackageInstalls()

    /**
     * Takes the user straight to Backchannel's own "install unknown apps" toggle.
     *
     * This activity returns no result — its documented output is "Nothing" — so the caller has to
     * re-read [canInstall] on resume rather than trusting a result code.
     */
    fun unknownSourcesIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${appContext.packageName}"),
    )

    /**
     * Everything worth rejecting before the system installer is ever shown. Returns null when the
     * APK is good, or the reason it isn't.
     *
     * Parsing an 18 MB APK with certificate collection happens in-process and takes a few hundred
     * milliseconds — call this off the main thread.
     */
    fun verify(apk: File): UpdateFailure? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archive = archiveInfo(apk.absolutePath, flags) ?: return UpdateFailure.InvalidPackage
        if (archive.packageName != BuildConfig.APPLICATION_ID) return UpdateFailure.InvalidPackage

        val installed = runCatching { installedInfo(flags) }.getOrNull()
            ?: return UpdateFailure.Unexpected("this app isn't installed")

        // versionCode is the CI run number, so it isn't ordered by the tag — a re-run can publish
        // a lower one. Free to check while the archive is open, and it saves the user watching
        // the installer fail with INSTALL_FAILED_VERSION_DOWNGRADE after a full download.
        if (PackageInfoCompat.getLongVersionCode(archive) <
            PackageInfoCompat.getLongVersionCode(installed)
        ) {
            return UpdateFailure.Downgrade
        }

        val archiveSigners = signerHashes(archive)
        val installedSigners = signerHashes(installed)
        return when {
            archiveSigners.isEmpty() -> UpdateFailure.InvalidPackage
            // Any overlap is fine: a partial match is signing-key rotation, which Android accepts.
            archiveSigners.intersect(installedSigners).isNotEmpty() -> null
            // A debug install can never be replaced by a release-signed APK. The updater stays
            // enabled in debug builds on purpose, so this path is what development exercises.
            BuildConfig.DEBUG -> UpdateFailure.DebugBuildInstalled
            else -> UpdateFailure.SignatureMismatch
        }
    }

    /**
     * Streams the APK into an install session and commits it.
     *
     * The result arrives asynchronously at [UpdateInstallReceiver] — including, in the normal
     * case, `STATUS_PENDING_USER_ACTION` carrying the confirmation screen to launch.
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(BuildConfig.APPLICATION_ID)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
            // Deliberately not setRequestUpdateOwnership(true): taking update ownership makes a
            // later manual sideload from GitHub warn the user, and manual sideload is still this
            // app's primary distribution.
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            // commit() throws SecurityException if a stream from openWrite() is still open, so
            // this has to close before the commit below.
            session.openWrite(BASE_APK, 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_BYTES) }
                session.fsync(output)
            }
            session.commit(statusIntent(sessionId).intentSender)
        }
        Log.i(TAG, "committed install session $sessionId")
    }

    /**
     * At targetSdk 35 `commit()` throws `IllegalArgumentException` for an immutable status
     * receiver — verified in the android-35 `PackageInstaller` source. Every pre-2024 sample
     * gets this wrong.
     */
    private fun statusIntent(sessionId: Int): PendingIntent {
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            appContext,
            sessionId,
            Intent(appContext, UpdateInstallReceiver::class.java)
                .setPackage(appContext.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    private fun archiveInfo(path: String, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                path,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(path, flags)
        }

    private fun installedInfo(flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                BuildConfig.APPLICATION_ID,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, flags)
        }

    private fun signerHashes(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.let { signingInfo ->
                    // Per SigningInfo's own javadoc: prefer the rotation history, which already
                    // includes the current certificate. apkContentsSigners is only correct when
                    // hasMultipleSigners() makes the history unavailable.
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
        return signatures.orEmpty().mapNotNull { it?.toByteArray()?.sha256Hex() }.toSet()
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toHexString()

    private companion object {
        const val TAG = "ApkInstaller"
        const val BASE_APK = "base.apk"
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
