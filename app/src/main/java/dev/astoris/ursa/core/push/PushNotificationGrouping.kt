package dev.astoris.ursa.core.push

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.ManagedPushNotification
import java.security.MessageDigest

data class PushNotificationGroupIdentity(
    val key: String,
    val summaryNotificationId: Int,
)

data class PushNotificationGroupSummary(
    val count: Int,
    val lines: List<String>,
)

object PushNotificationGrouping {
    fun identity(serverId: String?): PushNotificationGroupIdentity? {
        if (!ManagedPushNotification.isValidServerId(serverId)) return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("group:$serverId".toByteArray())
            .take(10)
            .joinToString("") { "%02x".format(it) }
        val key = "ursa-push-group-$digest"
        return PushNotificationGroupIdentity(key, "$key:summary".hashCode() and Int.MAX_VALUE)
    }

    fun summarize(titles: List<CharSequence?>): PushNotificationGroupSummary? {
        if (titles.size < 2) return null
        val lines = titles.asSequence()
            .mapNotNull { it?.toString() }
            .map { it.replace('\r', ' ').replace('\n', ' ').trim().take(MAX_LINE_LENGTH) }
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_LINES)
            .toList()
        return PushNotificationGroupSummary(titles.size, lines)
    }

    private const val MAX_LINES = 5
    private const val MAX_LINE_LENGTH = 96
}

object PushNotificationGroups {
    fun boundIdentity(context: Context, serverId: String?): PushNotificationGroupIdentity? {
        val identity = PushNotificationGrouping.identity(serverId) ?: return null
        return identity.takeIf { ManagedPushScopeStore(context).load(serverId) != null }
    }

    fun applyToChild(
        context: Context,
        builder: NotificationCompat.Builder,
        serverId: String,
        notificationId: Int,
        identity: PushNotificationGroupIdentity,
    ) {
        builder
            .setGroup(identity.key)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setDeleteIntent(dismissIntent(context, serverId, notificationId))
    }

    fun refresh(context: Context, serverId: String) {
        val identity = PushNotificationGrouping.identity(serverId) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        val children = manager.activeNotifications
            .filter {
                it.notification.group == identity.key &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
            .sortedByDescending { it.postTime }
        val summary = PushNotificationGrouping.summarize(
            children.map { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) },
        )
        val notifications = NotificationManagerCompat.from(context)
        if (summary == null) {
            children.singleOrNull()?.let { child ->
                if (canPostNotifications(context, notifications)) {
                    manager.notify(
                        child.tag,
                        child.id,
                        Notification.Builder.recoverBuilder(context, child.notification)
                            .setGroup(null)
                            .setGroupSummary(false)
                            .build(),
                    )
                }
            }
            notifications.cancel(identity.summaryNotificationId)
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!notifications.areNotificationsEnabled()) return
        val title = context.resources.getQuantityString(
            R.plurals.push_group_summary_title,
            summary.count,
            summary.count,
        )
        val style = NotificationCompat.InboxStyle().setSummaryText(title)
        summary.lines.forEach(style::addLine)
        notifications.notify(
            identity.summaryNotificationId,
            NotificationCompat.Builder(context, children.first().notification.channelId)
                .setSmallIcon(R.drawable.ic_stat_ursa)
                .setContentTitle(title)
                .setContentText(summary.lines.take(2).joinToString(" · ").ifBlank { title })
                .setStyle(style)
                .setNumber(summary.count)
                .setGroup(identity.key)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent(context))
                .build(),
        )
    }

    fun contentIntent(context: Context): PendingIntent {
        val intent = Intent()
        intent.setClassName(context.packageName, MainActivity::class.java.name)
        intent.action = Intent.ACTION_VIEW
        intent.data = "ursa://push".toUri()
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun dismissIntent(context: Context, serverId: String, notificationId: Int): PendingIntent {
        val intent = Intent()
        intent.setClassName(context.packageName, PushNotificationDismissReceiver::class.java.name)
        intent.action = PushNotificationDismissReceiver.ACTION_DISMISSED
        intent.putExtra(PushNotificationDismissReceiver.EXTRA_SERVER_ID, serverId)
        return PendingIntent.getBroadcast(
            context,
            "$serverId:$notificationId:dismiss".hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(context: Context, notifications: NotificationManagerCompat): Boolean =
        notifications.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            )
}

class PushNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return
        if (PushNotificationGrouping.identity(serverId) == null) return
        PushNotificationGroups.refresh(context.applicationContext, serverId)
    }

    companion object {
        const val ACTION_DISMISSED = "dev.astoris.ursa.action.PUSH_NOTIFICATION_DISMISSED"
        const val EXTRA_SERVER_ID = "server_id"
    }
}
