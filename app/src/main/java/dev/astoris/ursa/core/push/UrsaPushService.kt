package dev.astoris.ursa.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.LocalEventKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Receives UnifiedPush events from the distributor. Declared NON-exported in the
 * manifest - the connector ships its own internal receiver that forwards events here,
 * so there is no exported push surface to harden (MASVS-PLATFORM-1).
 *
 * The push body is Kuma's Webhook JSON; it is untrusted input, parsed tolerantly by
 * [PushParse] and only ever rendered into a notification (never acted upon).
 */
class UrsaPushService : PushService() {

    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "New endpoint for instance=$instance")
        PushStore.recordRegistered(this, endpoint.url)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        val raw = String(message.content, Charsets.UTF_8)
        val notice = PushParse.parse(raw) ?: PushNotice(
            monitorId = null,
            monitorName = "Uptime Kuma",
            title = "Uptime Kuma",
            body = raw.take(240).ifBlank { "Monitor update" },
            important = false,
        )
        val deliveryTest = PushStore.recordMessage(this, notice.body)
        val policyStore = PushAlertModeStore(this)
        val enriched = if (deliveryTest) {
            notice.copy(
                monitorName = getString(dev.astoris.ursa.R.string.push_test_kuma_notification_title),
                title = getString(dev.astoris.ursa.R.string.push_test_kuma_notification_title),
                body = getString(dev.astoris.ursa.R.string.push_test_kuma_notification_body),
                important = false,
            )
        } else {
            enrichWithDowntime(notice)
        }
        if (
            !deliveryTest &&
            !PushAlertPolicy.shouldNotify(
                policyStore.mode(enriched.serverId, enriched.monitorId),
                enriched.status,
            )
        ) {
            return
        }
        val severity = policyStore.severity(enriched.serverId, enriched.monitorId)
        if (postNotification(this, enriched, severity = severity) == PushLocalTestResult.POSTED && !deliveryTest) {
            eventScope.launch {
                EventLogStore(this@UrsaPushService).append(
                    serverUrl = null,
                    monitorId = enriched.monitorId,
                    monitorName = enriched.monitorName,
                    kind = LocalEventKind.PUSH_ALERT,
                    detail = enriched.body,
                )
            }
        }
    }

    /** Append "Was down for X" to a recovery notification, using the locally tracked
     *  down -> up transition (#177). No-op when the monitor id or status is unknown. */
    private fun enrichWithDowntime(notice: PushNotice): PushNotice {
        val id = notice.monitorId ?: return notice
        val store = DownSinceStore(this)
        return when (notice.status) {
            0 -> { store.markDown(id, System.currentTimeMillis()); notice }
            1 -> {
                val since = store.takeDown(id) ?: return notice
                val elapsed = System.currentTimeMillis() - since
                notice.copy(body = "${notice.body}\nWas down for ${PushParse.formatDowntime(elapsed)}.")
            }
            else -> notice
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(TAG, "Registration failed for instance=$instance: $reason")
        val safeReason = runCatching { PushRegistrationError.valueOf(reason.name) }
            .getOrDefault(PushRegistrationError.INTERNAL_ERROR)
        PushStore.recordRegistrationError(this, safeReason)
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "Unregistered instance=$instance")
        PushStore.recordUnregistered(this)
    }

    override fun onDestroy() {
        eventScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UrsaPush"
        const val CHANNEL_ID = "ursa_monitors_critical"
        private const val LOCAL_TEST_NOTIFICATION_ID = 0x55525341

        fun postLocalTest(context: Context): PushLocalTestResult = postNotification(
            context,
            PushNotice(
                monitorId = null,
                monitorName = context.getString(dev.astoris.ursa.R.string.push_test_local_notification_title),
                title = context.getString(dev.astoris.ursa.R.string.push_test_local_notification_title),
                body = context.getString(dev.astoris.ursa.R.string.push_test_local_notification_body),
                important = false,
            ),
            LOCAL_TEST_NOTIFICATION_ID,
        )

        /** Returns the exact reason a local notification was or was not posted. */
        private fun postNotification(
            context: Context,
            notice: PushNotice,
            idOverride: Int? = null,
            severity: PushSeverity = PushSeverity.CRITICAL,
        ): PushLocalTestResult {
            ensureChannel(context)
            val route = PushSeverityPolicy.route(severity)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted; dropping notification")
                return PushLocalTestResult.PERMISSION_REQUIRED
            }
            val notifications = NotificationManagerCompat.from(context)
            if (!notifications.areNotificationsEnabled()) {
                return PushLocalTestResult.APP_NOTIFICATIONS_DISABLED
            }
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(route.channelId)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                return PushLocalTestResult.CHANNEL_DISABLED
            }

            val open = Intent()
            open.setClassName(context.packageName, MainActivity::class.java.name)
            open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, route.channelId)
                .setSmallIcon(dev.astoris.ursa.R.drawable.ic_stat_ursa)
                .setContentTitle(notice.title)
                .setContentText(notice.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(
                    when {
                        route.highPriority -> NotificationCompat.PRIORITY_HIGH
                        !route.sound -> NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    },
                )

            notice.monitorId?.let { monitorId ->
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    monitorActionIntent(context, monitorId, MonitorActionReceiver.ACTION_PAUSE),
                )
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    monitorActionIntent(context, monitorId, MonitorActionReceiver.ACTION_RESUME),
                )
            }
            val id = idOverride ?: notice.monitorId ?: notice.title.hashCode()
            notifications.notify(id, builder.build())
            return PushLocalTestResult.POSTED
        }

        private fun monitorActionIntent(context: Context, monitorId: Int, action: String): PendingIntent {
            val intent = Intent()
            intent.setClassName(context.packageName, MonitorActionReceiver::class.java.name)
            intent.action = action
            intent.putExtra(MonitorActionReceiver.EXTRA_MONITOR_ID, monitorId)
            val requestCode = "$monitorId:$action".hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /** Idempotent channel creation; minSdk 26 so channels always exist. */
        fun ensureChannel(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            val channels = listOf(
                PushSeverity.CRITICAL to dev.astoris.ursa.R.string.push_channel_critical,
                PushSeverity.STANDARD to dev.astoris.ursa.R.string.push_channel_standard,
                PushSeverity.SILENT to dev.astoris.ursa.R.string.push_channel_silent,
            )
            channels.forEach { (severity, nameRes) ->
                val route = PushSeverityPolicy.route(severity)
                if (mgr.getNotificationChannel(route.channelId) == null) {
                    val importance = when {
                        route.highPriority -> NotificationManager.IMPORTANCE_HIGH
                        !route.sound -> NotificationManager.IMPORTANCE_LOW
                        else -> NotificationManager.IMPORTANCE_DEFAULT
                    }
                    mgr.createNotificationChannel(
                        NotificationChannel(route.channelId, context.getString(nameRes), importance).apply {
                            description = context.getString(dev.astoris.ursa.R.string.push_channel_description)
                            enableVibration(route.vibration)
                            if (!route.sound) setSound(null, null)
                        },
                    )
                }
            }
        }
    }
}
