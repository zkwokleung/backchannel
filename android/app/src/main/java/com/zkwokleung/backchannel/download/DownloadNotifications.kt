package com.zkwokleung.backchannel.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.zkwokleung.backchannel.MainActivity
import com.zkwokleung.backchannel.R
import com.zkwokleung.backchannel.data.db.DownloadEntity
import com.zkwokleung.backchannel.data.db.DownloadStatus

object DownloadNotifications {
    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of items being saved for offline listening"
            }
        )
    }

    fun build(context: Context, row: DownloadEntity?): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_brand_mark)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (row == null) {
            return builder.setContentTitle("Preparing download…").setProgress(0, 0, true).build()
        }
        val transferring = row.status == DownloadStatus.DOWNLOADING && row.progressPercent > 0
        val text = when {
            row.status == DownloadStatus.QUEUED -> "Queued"
            row.progressPercent >= 100 -> "Finishing…"
            transferring -> "Downloading · ${row.progressPercent}%"
            else -> "Starting…"
        }
        return builder
            .setContentTitle(row.title)
            .setContentText(text)
            .setProgress(100, row.progressPercent, !transferring || row.progressPercent >= 100)
            .addAction(0, "Cancel", DownloadService.cancelIntent(context, row.videoYoutubeId))
            .build()
    }
}
