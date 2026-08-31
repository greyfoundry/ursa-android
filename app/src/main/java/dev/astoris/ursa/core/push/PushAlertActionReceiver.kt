package dev.astoris.ursa.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PushAlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: return
        val monitorId = intent.getIntExtra(EXTRA_MONITOR_ID, -1)
        if (PushAlertWork.identity(serverId, monitorId) == null) return
        when (intent.action) {
            ACTION_ACKNOWLEDGE -> {
                PushAlertModeStore(context).setSnoozedUntil(serverId, monitorId, null)
                PushAlertWorker.cancel(context, serverId, monitorId)
            }
            ACTION_SNOOZE -> {
                val store = PushPendingAlertStore(context)
                val alert = store.loadActive(serverId, monitorId) ?: return
                val until = System.currentTimeMillis() + SNOOZE_MILLIS
                PushAlertModeStore(context).setSnoozedUntil(serverId, monitorId, until)
                PushAlertWorker.cancel(context, serverId, monitorId, removePending = false)
                PushAlertWorker.schedule(context, alert, until)
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
