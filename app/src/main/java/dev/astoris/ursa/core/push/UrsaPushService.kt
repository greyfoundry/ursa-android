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
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.LocalEventKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.util.UUID

/**
 * Receives UnifiedPush events from the distributor. Declared NON-exported in the
 * manifest - the connector ships its own internal receiver that forwards events here,
 * so there is no exported push surface to harden (MASVS-PLATFORM-1).
 *
 * The push body is Kuma's Webhook JSON; it is untrusted input and parsed tolerantly
 * by [PushParse]. Bound managed status may update local delivery state, but never
 * authorizes a remote server mutation.
 */
class UrsaPushService : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "New endpoint for instance=$instance")
        PushStore.recordRegistered(this, endpoint.url)
        cancelPathIssue(this)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        cancelPathIssue(this)
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
        val transitionStore = PushTransitionStore(this)
        val scope = if (deliveryTest) null else ManagedPushScopeStore(this).load(notice.serverId)
        val scopeIssue = if (deliveryTest) null else {
            ManagedPushScopePolicy.issue(scope, notice.serverId, notice.monitorId)
        }
        if (scopeIssue != null) {
            notice.serverId?.let { PushStore.recordScopeIssue(this, scopeIssue, it) }
            postScopeIssue(this, scopeIssue)
        }
        val enriched = if (deliveryTest) {
            notice.copy(
                monitorId = null,
                monitorName = getString(dev.astoris.ursa.R.string.push_test_kuma_notification_title),
                title = getString(dev.astoris.ursa.R.string.push_test_kuma_notification_title),
                body = getString(dev.astoris.ursa.R.string.push_test_kuma_notification_body),
                important = false,
                status = null,
                serverId = null,
            )
        } else {
            enrichWithDowntime(if (scopeIssue == null) notice else notice.copy(serverId = null))
        }
        val boundScope = scope.takeIf { scopeIssue == null }
        val managedIdentity = PushAlertWork.identity(enriched.serverId, enriched.monitorId)
            .takeIf { boundScope != null }
        val mode = policyStore.mode(enriched.serverId, enriched.monitorId)
        val configuredSeverity = policyStore.severity(enriched.serverId, enriched.monitorId)
        val timing = policyStore.timing(enriched.serverId, enriched.monitorId)
        val activeAlert = managedIdentity?.let {
            PushPendingAlertStore(this).loadActive(enriched.serverId ?: return@let null, enriched.monitorId ?: return@let null)
        }
        val alertId = if (deliveryTest) null else activeAlert?.id ?: UUID.randomUUID().toString()
        if (!deliveryTest && alertId != null) {
            recordTimeline(
                serverUrl = boundScope?.serverUrl,
                monitorId = enriched.monitorId,
                monitorName = enriched.monitorName,
                kind = LocalEventKind.PUSH_RECEIVED,
                detailRes = when (enriched.status) {
                    0 -> dev.astoris.ursa.R.string.push_timeline_received_down
                    1 -> dev.astoris.ursa.R.string.push_timeline_received_recovery
                    else -> dev.astoris.ursa.R.string.push_timeline_received_transition
                },
                alertId = alertId,
                decision = PushAlertTimeline.decision(
                    outcome = PushAlertTimeline.RECEIVED,
                    mode = mode,
                    configuredSeverity = configuredSeverity,
                    timing = timing,
                ),
            )
        }
        if (!deliveryTest && boundScope != null) {
            transitionStore.recordStatus(enriched.serverId, enriched.monitorId, enriched.status)
        }
        if (!deliveryTest && enriched.status == 1 && managedIdentity != null) {
            val serverId = enriched.serverId ?: return
            val monitorId = enriched.monitorId ?: return
            policyStore.setSnoozedUntil(serverId, monitorId, null)
            PushAlertWorker.cancel(this, serverId, monitorId)
            activeAlert?.let { alert ->
                recordTimeline(
                    serverUrl = boundScope?.serverUrl,
                    monitorId = monitorId,
                    monitorName = enriched.monitorName,
                    kind = LocalEventKind.PUSH_RECOVERED,
                    detailRes = dev.astoris.ursa.R.string.push_timeline_recovered,
                    alertId = alert.id,
                    decision = PushAlertTimeline.decision(
                        outcome = PushAlertTimeline.RECOVERED,
                        mode = mode,
                        configuredSeverity = configuredSeverity,
                        timing = timing,
                        deliveredCount = alert.deliveredCount,
                    ),
                )
            }
        }
        if (
            !deliveryTest &&
            !PushAlertPolicy.shouldNotify(
                mode,
                enriched.status,
            )
        ) {
            alertId?.let {
                recordTimeline(
                    boundScope?.serverUrl,
                    enriched.monitorId,
                    enriched.monitorName,
                    LocalEventKind.PUSH_SUPPRESSED,
                    dev.astoris.ursa.R.string.push_timeline_policy_suppressed,
                    it,
                    PushAlertTimeline.decision(
                        outcome = PushAlertTimeline.SUPPRESSED,
                        reason = PushAlertTimeline.REASON_POLICY,
                        mode = mode,
                        configuredSeverity = configuredSeverity,
                        timing = timing,
                    ),
                )
            }
            return
        }
        val eventPreferences = PushEventPreferencesStore(this).load()
        if (!deliveryTest && !PushEventPolicy.shouldNotify(enriched.status, eventPreferences)) {
            alertId?.let {
                recordTimeline(
                    boundScope?.serverUrl,
                    enriched.monitorId,
                    enriched.monitorName,
                    LocalEventKind.PUSH_SUPPRESSED,
                    dev.astoris.ursa.R.string.push_timeline_event_suppressed,
                    it,
                    PushAlertTimeline.decision(
                        outcome = PushAlertTimeline.SUPPRESSED,
                        reason = PushAlertTimeline.REASON_EVENT_PREFERENCE,
                        mode = mode,
                        configuredSeverity = configuredSeverity,
                        timing = timing,
                    ),
                )
            }
            return
        }
        if (
            !deliveryTest &&
            !transitionStore.shouldDeliver(
                enriched.serverId,
                enriched.monitorId,
                enriched.status,
            )
        ) {
            alertId?.let {
                recordTimeline(
                    boundScope?.serverUrl,
                    enriched.monitorId,
                    enriched.monitorName,
                    LocalEventKind.PUSH_SUPPRESSED,
                    dev.astoris.ursa.R.string.push_timeline_duplicate_suppressed,
                    it,
                    PushAlertTimeline.decision(
                        outcome = PushAlertTimeline.SUPPRESSED,
                        reason = PushAlertTimeline.REASON_DUPLICATE,
                        mode = mode,
                        configuredSeverity = configuredSeverity,
                        timing = timing,
                    ),
                )
            }
            return
        }
        if (!deliveryTest && enriched.status == 0 && boundScope != null && managedIdentity != null) {
            val serverId = enriched.serverId ?: return
            val monitorId = enriched.monitorId ?: return
            val suppression = PushDependencyStore(this).suppression(serverId, monitorId, transitionStore)
            if (suppression != null) {
                timelineScope.launch {
                    recordPushDependencySuppression(
                        this@UrsaPushService,
                        boundScope,
                        monitorId,
                        enriched.monitorName,
                        suppression,
                        alertId,
                    )
                }
                return
            }
        }
        val quietSeverity = if (deliveryTest) configuredSeverity else PushQuietHoursPolicy.effectiveSeverity(
            configuredSeverity,
            PushQuietHoursStore(this).load(),
        )
        val eventRoute = if (quietSeverity == PushSeverity.SILENT) {
            PushSeverityPolicy.route(PushSeverity.SILENT)
        } else {
            PushEventPolicy.route(enriched.status, configuredSeverity)
        }
        var correlation: PushCorrelationResult? = null
        val start = if (!deliveryTest && enriched.status == 0 && managedIdentity != null && alertId != null) {
            PushAlertWorker.beginDown(
                context = this,
                notice = enriched,
                severity = configuredSeverity,
                timing = timing,
                snoozedUntilMillis = policyStore.snoozedUntil(enriched.serverId, enriched.monitorId),
                alertId = alertId,
                correlationSink = { correlation = it },
            )
        } else null
        val result = when {
            start != null -> start.result
            managedIdentity != null -> postNotification(
                this,
                enriched,
                idOverride = managedIdentity.notificationId,
                severity = configuredSeverity,
                routeOverride = eventRoute,
                serverUrl = boundScope?.serverUrl,
                correlationSink = { correlation = it },
            )
            else -> postNotification(
                this,
                enriched,
                severity = configuredSeverity,
                routeOverride = eventRoute,
                serverUrl = boundScope?.serverUrl,
            )
        }
        if (!deliveryTest && alertId != null && start?.decision is PushAlertDecision.WaitUntil) {
            val scheduledAt = start.decision.atMillis
            recordTimeline(
                serverUrl = boundScope?.serverUrl,
                monitorId = enriched.monitorId,
                monitorName = enriched.monitorName,
                kind = LocalEventKind.PUSH_DELAYED,
                detailRes = dev.astoris.ursa.R.string.push_timeline_delayed,
                alertId = alertId,
                decision = PushAlertTimeline.decision(
                    outcome = PushAlertTimeline.DELAYED,
                    mode = mode,
                    configuredSeverity = configuredSeverity,
                    effectiveSeverity = quietSeverity,
                    timing = timing,
                    scheduledAtMillis = scheduledAt,
                ),
                formatArgs = arrayOf(timing.firstDelayMinutes),
            )
        }
        if (!deliveryTest && alertId != null && result != null) {
            val posted = result == PushLocalTestResult.POSTED
            recordTimeline(
                serverUrl = boundScope?.serverUrl,
                monitorId = enriched.monitorId,
                monitorName = enriched.monitorName,
                kind = if (posted) LocalEventKind.PUSH_ALERT else LocalEventKind.PUSH_SUPPRESSED,
                detailRes = if (posted) {
                    dev.astoris.ursa.R.string.push_timeline_delivered
                } else {
                    result.timelineDetailRes
                },
                alertId = alertId,
                decision = PushAlertTimeline.decision(
                    outcome = if (posted) PushAlertTimeline.DELIVERED else PushAlertTimeline.SUPPRESSED,
                    reason = PushAlertTimeline.suppressionReason(result),
                    mode = mode,
                    configuredSeverity = configuredSeverity,
                    effectiveSeverity = quietSeverity,
                    timing = timing,
                    deliveredCount = if (posted) 1 else 0,
                ),
                formatArgs = if (posted) arrayOf(1) else emptyArray(),
            )
            correlation?.let { correlationResult ->
                recordTimeline(
                    serverUrl = boundScope?.serverUrl,
                    monitorId = enriched.monitorId,
                    monitorName = enriched.monitorName,
                    kind = LocalEventKind.PUSH_CORRELATED,
                    detailRes = dev.astoris.ursa.R.string.push_timeline_correlated,
                    alertId = alertId,
                    decision = PushAlertTimeline.decision(
                        outcome = PushAlertTimeline.CORRELATED,
                        mode = mode,
                        configuredSeverity = configuredSeverity,
                        effectiveSeverity = quietSeverity,
                        timing = timing,
                        deliveredCount = 1,
                        correlation = correlationResult,
                    ),
                    formatArgs = arrayOf(correlationResult.count),
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
            0 -> { store.markDown(notice.serverId, id, System.currentTimeMillis()); notice }
            1 -> {
                val since = store.takeDown(notice.serverId, id) ?: return notice
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
        postPathIssue(this, safeReason)
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "Unregistered instance=$instance")
        if (PushStore.recordUnregistered(this)) cancelPathIssue(this) else postPathIssue(this)
    }

    private fun recordTimeline(
        serverUrl: String?,
        monitorId: Int?,
        monitorName: String,
        kind: LocalEventKind,
        detailRes: Int,
        alertId: String,
        decision: dev.astoris.ursa.core.storage.LocalAlertDecision,
        formatArgs: Array<out Any> = emptyArray(),
    ) {
        val detail = getString(detailRes, *formatArgs)
        timelineScope.launch {
            EventLogStore(this@UrsaPushService).append(
                serverUrl = serverUrl,
                monitorId = monitorId,
                monitorName = monitorName,
                kind = kind,
                detail = detail,
                alertId = alertId,
                alertDecision = decision,
            )
        }
    }

    companion object {
        private const val TAG = "UrsaPush"
        /** Process-scoped so a short-lived connector service cannot cancel durable timeline writes. */
        private val timelineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val CHANNEL_ID = "ursa_monitors_critical"
        private const val LOCAL_TEST_NOTIFICATION_ID = 0x55525341
        private const val PATH_ISSUE_NOTIFICATION_ID = 0x55525350
        private const val SCOPE_ISSUE_NOTIFICATION_ID = 0x55525351
        private val PATH_ISSUE_ROUTE = PushChannelRoute(
            channelId = "ursa_push_health",
            highPriority = false,
            sound = true,
            vibration = false,
        )

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

        internal fun cancelPathIssue(context: Context) {
            NotificationManagerCompat.from(context).cancel(PATH_ISSUE_NOTIFICATION_ID)
        }

        internal fun cancelScopeIssue(context: Context) {
            NotificationManagerCompat.from(context).cancel(SCOPE_ISSUE_NOTIFICATION_ID)
        }

        private fun postPathIssue(context: Context, error: PushRegistrationError? = null) {
            val (titleRes, bodyRes) = when (error) {
                PushRegistrationError.INTERNAL_ERROR ->
                    dev.astoris.ursa.R.string.push_health_internal_title to
                        dev.astoris.ursa.R.string.push_health_internal_body
                PushRegistrationError.NETWORK ->
                    dev.astoris.ursa.R.string.push_health_network_title to
                        dev.astoris.ursa.R.string.push_health_network_body
                PushRegistrationError.ACTION_REQUIRED ->
                    dev.astoris.ursa.R.string.push_health_action_title to
                        dev.astoris.ursa.R.string.push_health_action_body
                PushRegistrationError.VAPID_REQUIRED ->
                    dev.astoris.ursa.R.string.push_health_vapid_title to
                        dev.astoris.ursa.R.string.push_health_vapid_body
                null ->
                    dev.astoris.ursa.R.string.push_health_disconnected_title to
                        dev.astoris.ursa.R.string.push_health_disconnected_body
            }
            postHealthIssue(context, PATH_ISSUE_NOTIFICATION_ID, titleRes, bodyRes)
        }

        private fun postScopeIssue(context: Context, issue: ManagedPushScopeIssue) {
            val (titleRes, bodyRes) = when (issue) {
                ManagedPushScopeIssue.UNKNOWN_PROVIDER ->
                    dev.astoris.ursa.R.string.push_scope_unknown_provider_title to
                        dev.astoris.ursa.R.string.push_scope_unknown_provider_body
                ManagedPushScopeIssue.UNKNOWN_MONITOR ->
                    dev.astoris.ursa.R.string.push_scope_unknown_monitor_title to
                        dev.astoris.ursa.R.string.push_scope_unknown_monitor_body
            }
            postHealthIssue(context, SCOPE_ISSUE_NOTIFICATION_ID, titleRes, bodyRes)
        }

        private fun postHealthIssue(context: Context, id: Int, titleRes: Int, bodyRes: Int) {
            ensureChannel(context)
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val notifications = NotificationManagerCompat.from(context)
            if (!notifications.areNotificationsEnabled()) return
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(PATH_ISSUE_ROUTE.channelId)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return
            notifications.notify(
                id,
                NotificationCompat.Builder(context, PATH_ISSUE_ROUTE.channelId)
                    .setSmallIcon(dev.astoris.ursa.R.drawable.ic_stat_ursa)
                    .setContentTitle(context.getString(titleRes))
                    .setContentText(context.getString(bodyRes))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(bodyRes)))
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .setContentIntent(PushNotificationGroups.contentIntent(context))
                    .build(),
            )
        }

        /** Returns the exact reason a local notification was or was not posted. */
        internal fun postNotification(
            context: Context,
            notice: PushNotice,
            idOverride: Int? = null,
            severity: PushSeverity = PushSeverity.CRITICAL,
            routeOverride: PushChannelRoute? = null,
            serverUrl: String? = null,
            correlationSink: ((PushCorrelationResult) -> Unit)? = null,
        ): PushLocalTestResult {
            ensureChannel(context)
            val route = routeOverride ?: PushSeverityPolicy.route(severity)
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

            val builder = NotificationCompat.Builder(context, route.channelId)
                .setSmallIcon(dev.astoris.ursa.R.drawable.ic_stat_ursa)
                .setContentTitle(notice.title)
                .setContentText(notice.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
                .setAutoCancel(true)
                .setContentIntent(PushNotificationGroups.contentIntent(context))
                .setPriority(
                    when {
                        route.highPriority -> NotificationCompat.PRIORITY_HIGH
                        !route.sound -> NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    },
                )

            val alertServerId = notice.serverId
            val alertMonitorId = notice.monitorId
            val managedIdentity = PushAlertWork.identity(alertServerId, alertMonitorId)
            val id = idOverride ?: notice.monitorId ?: notice.title.hashCode()
            val groupIdentity = PushNotificationGroups.boundIdentity(context, alertServerId)
            if (groupIdentity != null && alertServerId != null) {
                PushNotificationGroups.applyToChild(
                    context,
                    builder,
                    alertServerId,
                    id,
                    groupIdentity,
                    alertMonitorId,
                    notice.status,
                )
            }
            if (
                notice.status == 0 &&
                managedIdentity != null &&
                alertServerId != null &&
                alertMonitorId != null
            ) {
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(dev.astoris.ursa.R.string.push_action_acknowledge),
                    alertActionIntent(
                        context,
                        alertServerId,
                        alertMonitorId,
                        PushAlertActionReceiver.ACTION_ACKNOWLEDGE,
                    ),
                )
                builder.addAction(
                    android.R.drawable.ic_lock_idle_alarm,
                    context.getString(dev.astoris.ursa.R.string.push_action_snooze_hour),
                    alertActionIntent(
                        context,
                        alertServerId,
                        alertMonitorId,
                        PushAlertActionReceiver.ACTION_SNOOZE,
                    ),
                )
            } else if (serverUrl != null) notice.monitorId?.let { monitorId ->
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    monitorActionIntent(context, serverUrl, monitorId, id, MonitorActionReceiver.ACTION_PAUSE),
                )
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    monitorActionIntent(context, serverUrl, monitorId, id, MonitorActionReceiver.ACTION_RESUME),
                )
            }
            notifications.notify(id, builder.build())
            if (groupIdentity != null && alertServerId != null) {
                PushNotificationGroups.refresh(context, alertServerId)?.let { correlationSink?.invoke(it) }
            }
            return PushLocalTestResult.POSTED
        }

        private val PushLocalTestResult.timelineDetailRes: Int
            get() = when (this) {
                PushLocalTestResult.POSTED -> dev.astoris.ursa.R.string.push_timeline_delivered
                PushLocalTestResult.PERMISSION_REQUIRED ->
                    dev.astoris.ursa.R.string.push_timeline_notification_permission
                PushLocalTestResult.APP_NOTIFICATIONS_DISABLED ->
                    dev.astoris.ursa.R.string.push_timeline_app_notifications_disabled
                PushLocalTestResult.CHANNEL_DISABLED ->
                    dev.astoris.ursa.R.string.push_timeline_channel_disabled
            }

        private fun alertActionIntent(
            context: Context,
            serverId: String,
            monitorId: Int,
            action: String,
        ): PendingIntent {
            val intent = Intent()
            intent.setClassName(context.packageName, PushAlertActionReceiver::class.java.name)
            intent.action = action
            intent.putExtra(PushAlertActionReceiver.EXTRA_SERVER_ID, serverId)
            intent.putExtra(PushAlertActionReceiver.EXTRA_MONITOR_ID, monitorId)
            return PendingIntent.getBroadcast(
                context,
                "$serverId:$monitorId:$action".hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun monitorActionIntent(
            context: Context,
            serverUrl: String,
            monitorId: Int,
            notificationId: Int,
            action: String,
        ): PendingIntent {
            val intent = Intent()
            intent.setClassName(context.packageName, MonitorActionReceiver::class.java.name)
            intent.action = action
            intent.putExtra(MonitorActionReceiver.EXTRA_MONITOR_ID, monitorId)
            intent.putExtra(MonitorActionReceiver.EXTRA_SERVER_URL, serverUrl)
            intent.putExtra(MonitorActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            val requestCode = "$serverUrl:$monitorId:$action".hashCode()
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
                PushSeverityPolicy.route(PushSeverity.CRITICAL) to
                    dev.astoris.ursa.R.string.push_channel_critical,
                PushSeverityPolicy.route(PushSeverity.STANDARD) to
                    dev.astoris.ursa.R.string.push_channel_standard,
                PushSeverityPolicy.route(PushSeverity.SILENT) to
                    dev.astoris.ursa.R.string.push_channel_silent,
                PushEventPolicy.RECOVERY_ROUTE to dev.astoris.ursa.R.string.push_channel_recovery,
                PushEventPolicy.MAINTENANCE_ROUTE to dev.astoris.ursa.R.string.push_channel_maintenance,
                PushEventPolicy.UPDATE_ROUTE to dev.astoris.ursa.R.string.push_channel_updates,
                PATH_ISSUE_ROUTE to dev.astoris.ursa.R.string.push_channel_health,
            )
            channels.forEach { (route, nameRes) ->
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
