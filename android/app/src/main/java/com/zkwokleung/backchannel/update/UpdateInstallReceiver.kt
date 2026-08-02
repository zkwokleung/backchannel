package com.zkwokleung.backchannel.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import com.zkwokleung.backchannel.appContainer

/**
 * Where [PackageInstaller] reports back. Registered in the manifest but not exported — only the
 * `PendingIntent` handed to `commit()` can reach it.
 *
 * It deliberately starts nothing. `STATUS_PENDING_USER_ACTION` carries an activity to launch, but
 * launching it from here would be a background activity start, which Android 10+ drops silently
 * whenever the app isn't in front. The intent is parked in [AppUpdater]'s state instead and the
 * Settings screen launches it while it is actually on screen.
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val updater = context.appContainer.appUpdater
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                if (confirm == null) {
                    updater.onInstallFailed(UpdateFailure.InstallRejected(detail))
                } else {
                    updater.onConfirmationRequired(confirm)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // Rarely observed in practice: replacing the package kills this process, often
                // before the broadcast lands. The row is correct either way on next launch.
                Log.i(TAG, "install succeeded")
                updater.onInstallSucceeded()
            }

            else -> {
                Log.w(TAG, "install failed: status=$status message=$detail")
                updater.onInstallFailed(failureForInstallStatus(status, detail))
            }
        }
    }

    private companion object {
        const val TAG = "UpdateInstall"
    }
}

/** [PackageInstaller] status codes, which are specific enough to be worth keeping apart. */
internal fun failureForInstallStatus(status: Int, detail: String?): UpdateFailure = when (status) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateFailure.UserAborted
    PackageInstaller.STATUS_FAILURE_CONFLICT -> UpdateFailure.InstallConflict
    PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateFailure.InsufficientStorage
    PackageInstaller.STATUS_FAILURE_INVALID -> UpdateFailure.InvalidPackage
    else -> UpdateFailure.InstallRejected(detail)
}
