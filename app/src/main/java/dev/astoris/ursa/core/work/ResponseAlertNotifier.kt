package dev.astoris.ursa.core.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Builds and posts "slow response" notifications (#1813). Shared by the background
 * [ResponseAlertWorker] and the live foreground evaluation in the ViewModel so both
 * paths use one channel and one message format.
 */
object ResponseAlertNotifier {

    const val CHANNEL_ID = "ursa_slow_response"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Slow response",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Alerts when a monitor is up but responding slowly" },
            )
        }
    }

    /** Post a slow-response notification. No-ops without POST_NOTIFICATIONS (API 33+). */
    fun notify(context: Context, monitorName: String, pingMs: Int, thresholdMs: Int, monitorKey: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val text = "$monitorName responded in ${pingMs}ms (limit ${thresholdMs}ms)."
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(dev.astoris.ursa.R.drawable.ic_stat_ursa)
            .setContentTitle("Slow response")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(monitorKey.hashCode(), notification)
    }
}
