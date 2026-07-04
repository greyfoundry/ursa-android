package dev.astoris.ursa.core.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.astoris.ursa.MainActivity
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Receives UnifiedPush events from the distributor. Declared NON-exported in the
 * manifest — the connector ships its own internal receiver that forwards events here,
 * so there is no exported push surface to harden (MASVS-PLATFORM-1).
 *
 * The push body is Kuma's Webhook JSON; it is untrusted input, parsed tolerantly by
 * [PushParse] and only ever rendered into a notification (never acted upon).
 */
class UrsaPushService : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "New endpoint for instance=$instance")
        PushStore.setEndpoint(this, endpoint.url)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        val raw = String(message.content, Charsets.UTF_8)
        val notice = PushParse.parse(raw) ?: PushNotice(
            monitorId = null,
            title = "Uptime Kuma",
            body = raw.take(240).ifBlank { "Monitor update" },
            important = false,
        )
        notify(notice)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(TAG, "Registration failed for instance=$instance: $reason")
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "Unregistered instance=$instance")
        PushStore.setEndpoint(this, null)
    }

    private fun notify(notice: PushNotice) {
        ensureChannel(this)
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = android.app.PendingIntent.getActivity(
            this, 0, open,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // TODO: replace with a branded icon
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(Notification.BigTextStyle().bigText(notice.body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .apply { if (notice.important) setPriority(Notification.PRIORITY_HIGH) }
            .build()

        // POST_NOTIFICATIONS is a runtime permission on API 33+; the UI requests it.
        // If it isn't granted, drop the notification rather than crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; dropping notification")
            return
        }
        // Same monitor id replaces its previous notification.
        val id = notice.monitorId ?: notice.title.hashCode()
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    companion object {
        private const val TAG = "UrsaPush"
        const val CHANNEL_ID = "ursa_monitors"

        /** Idempotent channel creation; minSdk 26 so channels always exist. */
        fun ensureChannel(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Monitor alerts",
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = "Up/down notifications from your Uptime Kuma servers" },
                )
            }
        }
    }
}
