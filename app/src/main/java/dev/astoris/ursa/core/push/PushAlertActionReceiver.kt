package dev.astoris.ursa.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.LocalEventKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PushAlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return
        val monitorId = intent.getIntExtra(EXTRA_MONITOR_ID, -1)
        if (PushAlertWork.identity(serverId, monitorId) == null) return
        if (intent.action !in setOf(ACTION_ACKNOWLEDGE, ACTION_SNOOZE)) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val store = PushPendingAlertStore(appContext)
                val alert = store.loadActive(serverId, monitorId) ?: return@launch
                val scope = ManagedPushScopeStore(appContext).load(serverId) ?: return@launch
                val modeStore = PushAlertModeStore(appContext)
                val mode = modeStore.mode(serverId, monitorId)
                val eventStore = EventLogStore(appContext)
                when (intent.action) {
                    ACTION_ACKNOWLEDGE -> {
                        modeStore.setSnoozedUntil(serverId, monitorId, null)
                        PushAlertWorker.cancel(appContext, serverId, monitorId)
                        eventStore.append(
                            serverUrl = scope.serverUrl,
                            monitorId = monitorId,
                            monitorName = alert.monitorName,
                            kind = LocalEventKind.PUSH_ACKNOWLEDGED,
                            detail = appContext.getString(dev.astoris.ursa.R.string.push_timeline_acknowledged),
                            alertId = alert.id,
                            alertDecision = PushAlertTimeline.decision(
                                outcome = PushAlertTimeline.ACKNOWLEDGED,
                                mode = mode,
                                configuredSeverity = alert.severity,
                                timing = alert.timing,
                                deliveredCount = alert.deliveredCount,
                            ),
                        )
                    }
                    ACTION_SNOOZE -> {
                        val until = System.currentTimeMillis() + SNOOZE_MILLIS
                        modeStore.setSnoozedUntil(serverId, monitorId, until)
                        PushAlertWorker.cancel(appContext, serverId, monitorId, removePending = false)
                        PushAlertWorker.schedule(appContext, alert, until)
                        eventStore.append(
                            serverUrl = scope.serverUrl,
                            monitorId = monitorId,
                            monitorName = alert.monitorName,
                            kind = LocalEventKind.PUSH_SNOOZED,
                            detail = appContext.getString(dev.astoris.ursa.R.string.push_timeline_snoozed),
                            alertId = alert.id,
                            alertDecision = PushAlertTimeline.decision(
                                outcome = PushAlertTimeline.SNOOZED,
                                mode = mode,
                                configuredSeverity = alert.severity,
                                timing = alert.timing,
                                scheduledAtMillis = until,
                                deliveredCount = alert.deliveredCount,
                            ),
                        )
                    }
                    else -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACKNOWLEDGE = "dev.astoris.ursa.action.ACKNOWLEDGE_ALERT"
        const val ACTION_SNOOZE = "dev.astoris.ursa.action.SNOOZE_ALERT"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_MONITOR_ID = "monitor_id"
        private const val SNOOZE_MILLIS = 60 * 60 * 1_000L
    }
}
