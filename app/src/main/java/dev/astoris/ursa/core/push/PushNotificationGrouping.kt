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
import android.os.Bundle
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

data class PushCorrelationCandidate(
    val monitorId: Int,
    val status: Int?,
    val postedAtMillis: Long,
)

enum class PushCorrelationBasis {
    SHARED_PARENT,
    SHARED_TAG,
    SERVER_BURST,
}

data class PushCorrelationResult(
    val count: Int,
    val basis: PushCorrelationBasis,
)

object PushStormCorrelation {
    fun evaluate(
        candidates: List<PushCorrelationCandidate>,
        scope: ManagedPushScope,
    ): PushCorrelationResult? {
        val down = candidates.asSequence()
            .filter { it.status == 0 && it.monitorId > 0 && it.postedAtMillis >= 0L }
            .sortedByDescending(PushCorrelationCandidate::postedAtMillis)
            .distinctBy(PushCorrelationCandidate::monitorId)
            .toList()
        val newest = down.maxOfOrNull(PushCorrelationCandidate::postedAtMillis) ?: return null
        val recent = down.filter { newest - it.postedAtMillis <= WINDOW_MILLIS }
            .mapNotNull { candidate -> scope.monitor(candidate.monitorId)?.let { candidate to it } }
        if (recent.size < SHARED_CONTEXT_THRESHOLD) return null

        val parentCount = recent.mapNotNull { it.second.parentId }
            .groupingBy { it }
            .eachCount()
            .values
            .maxOrNull() ?: 0
        if (parentCount >= SHARED_CONTEXT_THRESHOLD) {
            return PushCorrelationResult(parentCount, PushCorrelationBasis.SHARED_PARENT)
        }
        val tagCount = recent.flatMap { (_, monitor) -> monitor.tags.distinct() }
            .groupingBy { it }
            .eachCount()
            .values
            .maxOrNull() ?: 0
        if (tagCount >= SHARED_CONTEXT_THRESHOLD) {
            return PushCorrelationResult(tagCount, PushCorrelationBasis.SHARED_TAG)
        }
        return recent.takeIf { it.size >= SERVER_BURST_THRESHOLD }
            ?.let { PushCorrelationResult(it.size, PushCorrelationBasis.SERVER_BURST) }
    }

    private const val SHARED_CONTEXT_THRESHOLD = 3
    private const val SERVER_BURST_THRESHOLD = 5
    private const val WINDOW_MILLIS = 2 * 60_000L
}

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

    internal fun belongsToGroup(notificationGroup: String?, storedGroup: String?, expectedGroup: String): Boolean =
        notificationGroup == expectedGroup || storedGroup == expectedGroup

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
        monitorId: Int?,
        status: Int?,
    ) {
        builder
            .setGroup(identity.key)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setDeleteIntent(dismissIntent(context, serverId, notificationId))
            .addExtras(Bundle().apply { putString(EXTRA_GROUP_KEY, identity.key) })
        if (monitorId != null && monitorId > 0 && status != null) {
            builder.addExtras(
                Bundle().apply {
                    putInt(EXTRA_MONITOR_ID, monitorId)
                    putInt(EXTRA_STATUS, status)
                },
            )
        }
    }

    @Synchronized
    fun refresh(context: Context, serverId: String) {
        val identity = PushNotificationGrouping.identity(serverId) ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        val children = manager.activeNotifications
            .filter {
                it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    PushNotificationGrouping.belongsToGroup(
                        it.notification.group,
                        it.notification.extras.getString(EXTRA_GROUP_KEY),
                        identity.key,
                    )
            }
            .sortedByDescending { it.postTime }
        val summary = PushNotificationGrouping.summarize(
            children.map { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) },
        )
        val correlation = if (PushAlertModeStore(context).stormCorrelationEnabled(serverId)) {
            ManagedPushScopeStore(context).load(serverId)?.let { scope ->
                PushStormCorrelation.evaluate(
                    children.mapNotNull { child ->
                        val extras = child.notification.extras
                        if (!extras.containsKey(EXTRA_MONITOR_ID)) return@mapNotNull null
                        PushCorrelationCandidate(
                            monitorId = extras.getInt(EXTRA_MONITOR_ID),
                            status = extras.getInt(EXTRA_STATUS).takeIf { extras.containsKey(EXTRA_STATUS) },
                            postedAtMillis = child.postTime,
                        )
                    },
                    scope,
                )
            }
        } else null
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
                            .addExtras(Bundle().apply { putString(EXTRA_GROUP_KEY, identity.key) })
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
        val correlationText = correlation?.let {
            context.getString(R.string.push_group_correlation_hint, it.count)
        }
        children.filter { it.notification.group != identity.key }.forEach { child ->
            manager.notify(
                child.tag,
                child.id,
                Notification.Builder.recoverBuilder(context, child.notification)
                    .setGroup(identity.key)
                    .setGroupSummary(false)
                    .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                    .setOnlyAlertOnce(true)
                    .addExtras(Bundle().apply { putString(EXTRA_GROUP_KEY, identity.key) })
                    .build(),
            )
        }
        val style = NotificationCompat.InboxStyle().setSummaryText(correlationText ?: title)
        summary.lines.forEach(style::addLine)
        notifications.notify(
            identity.summaryNotificationId,
            NotificationCompat.Builder(context, children.first().notification.channelId)
                .setSmallIcon(R.drawable.ic_stat_ursa)
                .setContentTitle(title)
                .setContentText(
                    correlationText ?: summary.lines.take(2).joinToString(" · ").ifBlank { title },
                )
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

    private const val EXTRA_MONITOR_ID = "dev.astoris.ursa.extra.GROUP_MONITOR_ID"
    private const val EXTRA_STATUS = "dev.astoris.ursa.extra.GROUP_STATUS"
    private const val EXTRA_GROUP_KEY = "dev.astoris.ursa.extra.GROUP_KEY"
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
