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
import java.util.UUID
import java.util.concurrent.TimeUnit

class PushAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val alertId = inputData.getString(INPUT_ALERT_ID) ?: return Result.success()
        val store = PushPendingAlertStore(applicationContext)
        val alert = store.load(alertId)?.takeIf(store::isActive) ?: return Result.success()
        val deliverySeverity = PushQuietHoursPolicy.effectiveSeverity(
            alert.severity,
            PushQuietHoursStore(applicationContext).load(),
        )
        val result = UrsaPushService.postNotification(
            context = applicationContext,
            notice = alert.asNotice(),
            idOverride = PushAlertWork.identity(alert.serverId, alert.monitorId)?.notificationId,
            severity = deliverySeverity,
        )
        if (result != PushLocalTestResult.POSTED) return Result.success()

        val updated = alert.copy(deliveredCount = alert.deliveredCount + 1)
        store.save(updated)
        EventLogStore(applicationContext).append(
            serverUrl = null,
            monitorId = alert.monitorId,
            monitorName = alert.monitorName,
            kind = LocalEventKind.PUSH_ALERT,
            detail = alert.body,
        )
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
            if (removePending) PushPendingAlertStore(context).removeActive(serverId, monitorId)
        }

        fun beginDown(
            context: Context,
            notice: PushNotice,
            severity: PushSeverity,
            timing: PushAlertTiming,
            snoozedUntilMillis: Long?,
        ): PushLocalTestResult? {
            val serverId = notice.serverId ?: return null
            val monitorId = notice.monitorId ?: return null
            val identity = PushAlertWork.identity(serverId, monitorId) ?: return null
            cancel(context, serverId, monitorId)
            val now = System.currentTimeMillis()
            val alert = PushPendingAlert(
                id = UUID.randomUUID().toString(),
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
            return when (val decision = PushAlertLifecycle.onDown(timing, now, snoozedUntilMillis)) {
                PushAlertDecision.Deliver -> {
                    val result = UrsaPushService.postNotification(
                        context,
                        notice,
                        idOverride = identity.notificationId,
                        severity = severity,
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
        }
    }
}
