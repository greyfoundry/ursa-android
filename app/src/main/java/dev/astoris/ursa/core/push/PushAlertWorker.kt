package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.LocalEventKind
import java.util.concurrent.TimeUnit

data class PushAlertStart(
    val alert: PushPendingAlert,
    val decision: PushAlertDecision,
    val result: PushLocalTestResult?,
)

class PushAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val alertId = inputData.getString(INPUT_ALERT_ID) ?: return Result.success()
        val store = PushPendingAlertStore(applicationContext)
        val alert = store.load(alertId)?.takeIf(store::isActive) ?: return Result.success()
        val scope = ManagedPushScopeStore(applicationContext).load(alert.serverId)
        val suppression = scope?.let {
            PushDependencyStore(applicationContext).suppression(alert.serverId, alert.monitorId)
        }
        if (scope != null && suppression != null) {
            store.removeActive(alert.serverId, alert.monitorId)
            recordPushDependencySuppression(
                applicationContext,
                scope,
                alert.monitorId,
                alert.monitorName,
                suppression,
                alert.id,
            )
            return Result.success()
        }
        val deliverySeverity = PushQuietHoursPolicy.effectiveSeverity(
            alert.severity,
            PushQuietHoursStore(applicationContext).load(),
        )
        var correlation: PushCorrelationResult? = null
        val result = UrsaPushService.postNotification(
            context = applicationContext,
            notice = alert.asNotice(),
            idOverride = PushAlertWork.identity(alert.serverId, alert.monitorId)?.notificationId,
            severity = deliverySeverity,
            correlationSink = { correlation = it },
        )
        val eventStore = EventLogStore(applicationContext)
        val mode = PushAlertModeStore(applicationContext).mode(alert.serverId, alert.monitorId)
        if (result != PushLocalTestResult.POSTED) {
            eventStore.append(
                serverUrl = scope?.serverUrl,
                monitorId = alert.monitorId,
                monitorName = alert.monitorName,
                kind = LocalEventKind.PUSH_SUPPRESSED,
                detail = applicationContext.getString(result.timelineDetailRes),
                alertId = alert.id,
                alertDecision = PushAlertTimeline.decision(
                    outcome = PushAlertTimeline.SUPPRESSED,
                    reason = PushAlertTimeline.suppressionReason(result),
                    mode = mode,
                    configuredSeverity = alert.severity,
                    effectiveSeverity = deliverySeverity,
                    timing = alert.timing,
                    deliveredCount = alert.deliveredCount,
                ),
            )
            return Result.success()
        }

        val updated = alert.copy(deliveredCount = alert.deliveredCount + 1)
        store.save(updated)
        eventStore.append(
            serverUrl = scope?.serverUrl,
            monitorId = alert.monitorId,
            monitorName = alert.monitorName,
            kind = if (alert.deliveredCount == 0) LocalEventKind.PUSH_ALERT else LocalEventKind.PUSH_REPEATED,
            detail = applicationContext.getString(
                if (alert.deliveredCount == 0) dev.astoris.ursa.R.string.push_timeline_delivered
                else dev.astoris.ursa.R.string.push_timeline_repeated,
                updated.deliveredCount,
            ),
            alertId = alert.id,
            alertDecision = PushAlertTimeline.decision(
                outcome = if (alert.deliveredCount == 0) {
                    PushAlertTimeline.DELIVERED
                } else {
                    PushAlertTimeline.REPEATED
                },
                mode = mode,
                configuredSeverity = alert.severity,
                effectiveSeverity = deliverySeverity,
                timing = alert.timing,
                deliveredCount = updated.deliveredCount,
            ),
        )
        correlation?.let { result ->
            eventStore.append(
                serverUrl = scope?.serverUrl,
                monitorId = alert.monitorId,
                monitorName = alert.monitorName,
                kind = LocalEventKind.PUSH_CORRELATED,
                detail = applicationContext.getString(
                    dev.astoris.ursa.R.string.push_timeline_correlated,
                    result.count,
                ),
                alertId = alert.id,
                alertDecision = PushAlertTimeline.decision(
                    outcome = PushAlertTimeline.CORRELATED,
                    mode = mode,
                    configuredSeverity = alert.severity,
                    effectiveSeverity = deliverySeverity,
                    timing = alert.timing,
                    deliveredCount = updated.deliveredCount,
                    correlation = result,
                ),
            )
        }
        when (
            val next = PushAlertLifecycle.afterDelivery(
                timing = updated.timing,
                nowMillis = System.currentTimeMillis(),
                deliveredRepeats = (updated.deliveredCount - 1).coerceAtLeast(0),
            )
        ) {
            is PushAlertDecision.WaitUntil -> schedule(applicationContext, updated, next.atMillis)
            else -> Unit
        }
        return Result.success()
    }

    companion object {
        private const val INPUT_ALERT_ID = "alert_id"

        fun schedule(context: Context, alert: PushPendingAlert, atMillis: Long) {
            val identity = PushAlertWork.identity(alert.serverId, alert.monitorId) ?: return
            PushPendingAlertStore(context).save(alert)
            val request = OneTimeWorkRequestBuilder<PushAlertWorker>()
                .setInputData(Data.Builder().putString(INPUT_ALERT_ID, alert.id).build())
                .setInitialDelay((atMillis - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(identity.tag)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                identity.workName(alert.deliveredCount),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context, serverId: String, monitorId: Int, removePending: Boolean = true) {
            val identity = PushAlertWork.identity(serverId, monitorId) ?: return
            WorkManager.getInstance(context).cancelAllWorkByTag(identity.tag)
            NotificationManagerCompat.from(context).cancel(identity.notificationId)
            PushNotificationGroups.refresh(context, serverId)
            if (removePending) PushPendingAlertStore(context).removeActive(serverId, monitorId)
        }

        fun beginDown(
            context: Context,
            notice: PushNotice,
            severity: PushSeverity,
            timing: PushAlertTiming,
            snoozedUntilMillis: Long?,
            alertId: String,
            correlationSink: ((PushCorrelationResult) -> Unit)? = null,
        ): PushAlertStart? {
            val serverId = notice.serverId ?: return null
            val monitorId = notice.monitorId ?: return null
            val identity = PushAlertWork.identity(serverId, monitorId) ?: return null
            cancel(context, serverId, monitorId)
            val now = System.currentTimeMillis()
            val alert = PushPendingAlert(
                id = alertId,
                serverId = serverId,
                monitorId = monitorId,
                monitorName = notice.monitorName,
                title = notice.title,
                body = notice.body,
                severity = severity,
                timing = timing.normalized(),
                deliveredCount = 0,
            )
            val store = PushPendingAlertStore(context)
            store.save(alert)
            val decision = PushAlertLifecycle.onDown(timing, now, snoozedUntilMillis)
            val result = when (decision) {
                PushAlertDecision.Deliver -> {
                    val deliverySeverity = PushQuietHoursPolicy.effectiveSeverity(
                        severity,
                        PushQuietHoursStore(context).load(),
                    )
                    val result = UrsaPushService.postNotification(
                        context,
                        notice,
                        idOverride = identity.notificationId,
                        severity = deliverySeverity,
                        correlationSink = correlationSink,
                    )
                    if (result == PushLocalTestResult.POSTED) {
                        val delivered = alert.copy(deliveredCount = 1)
                        store.save(delivered)
                        val next = PushAlertLifecycle.afterDelivery(timing, now, deliveredRepeats = 0)
                        if (next is PushAlertDecision.WaitUntil) schedule(context, delivered, next.atMillis)
                    }
                    result
                }
                is PushAlertDecision.WaitUntil -> {
                    schedule(context, alert, decision.atMillis)
                    null
                }
                else -> null
            }
            return PushAlertStart(alert, decision, result)
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
    }
}
