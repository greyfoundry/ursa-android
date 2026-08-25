package dev.astoris.ursa.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.astoris.ursa.core.network.KumaClient
import dev.astoris.ursa.core.storage.ConnectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the Pause / Resume actions on a monitor notification. Not exported: it is
 * only ever triggered by the app's own notification PendingIntents. Because a push can
 * arrive while the app is closed, this opens a short-lived connection to the active
 * server, applies the action, and disconnects.
 */
class MonitorActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_MONITOR_ID, -1)
        if (id < 0) return
        val pause = intent.action == ACTION_PAUSE
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                perform(appContext, id, pause)
            } catch (e: Exception) {
                Log.w(TAG, "Monitor action failed: ${e.message}")
            } finally {
                NotificationManagerCompat.from(appContext).cancel(id)
                pending.finish()
            }
        }
    }

    private suspend fun perform(context: Context, id: Int, pause: Boolean) {
        val store = ConnectionStore(context)
        val conns = store.connections.first()
        val activeUrl = store.activeUrl.first()
        val conn = conns.firstOrNull { it.url == activeUrl } ?: conns.firstOrNull()
        val jwt = conn?.jwt ?: return

        val client = KumaClient(conn.url, conn.insecure, conn.headers)
        try {
            client.connect()
            // loginByToken suspends until the server acks auth, so the pause/resume
            // that follows is authorized. Bounded so we never hang the receiver.
            val authed = withTimeoutOrNull(AUTH_TIMEOUT_MS) { client.loginByToken(jwt) } == true
            if (authed) {
                withTimeoutOrNull(ACTION_TIMEOUT_MS) {
                    if (pause) client.pauseMonitor(id) else client.resumeMonitor(id)
                }
            }
        } finally {
            client.disconnect()
        }
    }

    companion object {
        private const val TAG = "UrsaMonitorAction"
        const val ACTION_PAUSE = "dev.astoris.ursa.action.PAUSE"
        const val ACTION_RESUME = "dev.astoris.ursa.action.RESUME"
        const val EXTRA_MONITOR_ID = "monitor_id"
        private const val AUTH_TIMEOUT_MS = 8_000L
        private const val ACTION_TIMEOUT_MS = 5_000L
    }
}
