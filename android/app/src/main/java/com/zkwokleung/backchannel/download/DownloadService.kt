package com.zkwokleung.backchannel.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zkwokleung.backchannel.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while [DownloadManager] works and mirrors its progress into a
 * notification. Holds no state of its own: it stops itself the moment the queue empties.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            DownloadNotifications.NOTIFICATION_ID,
            DownloadNotifications.build(this, null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0,
        )
        val notifications = getSystemService(NotificationManager::class.java)
        serviceScope.launch {
            appContainer.downloadManager.active.collect { row ->
                if (row == null) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notifications.notify(
                        DownloadNotifications.NOTIFICATION_ID,
                        DownloadNotifications.build(this@DownloadService, row),
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_VIDEO_ID)?.let(appContainer.downloadManager::cancel)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val ACTION_CANCEL = "com.zkwokleung.backchannel.download.CANCEL"
        private const val EXTRA_VIDEO_ID = "videoId"

        /**
         * False when Android forbids the launch — a process brought up in the background (say,
         * lock-screen media resumption) may not start a foreground service on API 31+. The
         * queue still runs; it just has no notification until the app comes forward.
         */
        fun start(context: Context): Boolean = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }.onFailure { Log.w(TAG, "could not start download service", it) }.isSuccess

        fun cancelIntent(context: Context, videoId: String): PendingIntent = PendingIntent.getService(
            context,
            videoId.hashCode(),
            Intent(context, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_VIDEO_ID, videoId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
