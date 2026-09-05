package dev.astoris.ursa.core.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.astoris.ursa.R
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.ui.AppDeepLink
import androidx.core.net.toUri

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
                    context.getString(R.string.slow_response_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.slow_response_channel_desc) },
            )
        }
    }

    /** Post a slow-response notification. Returns false without notification permission. */
    fun notify(
        context: Context,
        serverUrl: String,
        monitorId: Int,
        monitorName: String,
        pingMs: Int,
        thresholdMs: Int,
        monitorKey: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel(context)
        val text = context.getString(R.string.slow_response_notification_text, monitorName, pingMs, thresholdMs)
        val open = Intent()
        open.setClassName(context.packageName, MainActivity::class.java.name)
        open.action = Intent.ACTION_VIEW
        open.data = AppDeepLink.monitor(serverUrl, monitorId).toUri()
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(dev.astoris.ursa.R.drawable.ic_stat_ursa)
            .setContentTitle(context.getString(R.string.slow_response_notification_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, monitorKey.hashCode(), open, PendingIntent.FLAG_IMMUTABLE))
            .build()
        NotificationManagerCompat.from(context).notify(monitorKey.hashCode(), notification)
        return true
    }
}
