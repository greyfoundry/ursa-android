package dev.astoris.ursa.core.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.storage.CertExpiryStore
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.data.repository.MonitorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** User-enabled live fleet status. Its visible ongoing notification is the feature and disclosure. */
class OverallStatusService : Service() {
    private var connectionScope: CoroutineScope? = null
    private var monitorJob: Job? = null
    private var repository: MonitorRepository? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startInForeground(buildNotification(OverallStatusSummary.from(emptyList()), ConnectionState.Connecting))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!OverallStatusStore(this).enabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (monitorJob == null || intent?.action == ACTION_REFRESH) restartMonitoring()
        return START_STICKY
    }

    private fun restartMonitoring() {
        repository?.disconnect()
        connectionScope?.cancel()
        val nextScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        connectionScope = nextScope
        monitorJob = nextScope.launch {
            try {
                monitorActiveServer(nextScope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                updateNotification(OverallStatusSummary.from(emptyList()), ConnectionState.Error)
            }
        }
    }

    private suspend fun monitorActiveServer(activeScope: CoroutineScope) {
        val connectionStore = ConnectionStore(this)
        val connections = connectionStore.connections.first()
        val activeUrl = connectionStore.activeUrl.first()
        val connection = connections.firstOrNull { it.url == activeUrl } ?: connections.firstOrNull()
        if (connection?.jwt == null) {
            updateNotification(OverallStatusSummary.from(emptyList()), ConnectionState.AuthenticationFailed)
            return
        }
        val repo = MonitorRepository(
            connectionStore,
            MonitorCacheStore(this),
            CertExpiryStore(this),
            activeScope,
        )
        repository = repo
        repo.switchTo(connection)
        combine(repo.monitors, repo.state) { monitors, state -> OverallStatusSummary.from(monitors) to state }
            .collect { (summary, state) -> updateNotification(summary, state) }
    }

    private fun updateNotification(summary: OverallStatusSummary, state: ConnectionState) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(summary, state))
    }

    private fun buildNotification(summary: OverallStatusSummary, connectionState: ConnectionState): Notification {
        val open = Intent()
        open.setClassName(packageName, MainActivity::class.java.name)
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val contentIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE)
        val title = when {
            connectionState == ConnectionState.AuthenticationFailed -> getString(R.string.overall_status_sign_in)
            connectionState != ConnectionState.Authenticated && summary.total == 0 ->
                getString(R.string.overall_status_connecting)
            summary.down > 0 -> resources.getQuantityString(
                R.plurals.overall_status_down_title,
                summary.down,
                summary.down,
            )
            summary.pending > 0 -> resources.getQuantityString(
                R.plurals.overall_status_pending_title,
                summary.pending,
                summary.pending,
            )
            else -> getString(R.string.overall_status_healthy)
        }
        val text = getString(
            R.string.overall_status_counts,
            resources.getQuantityString(R.plurals.overall_status_up_count, summary.up, summary.up),
            resources.getQuantityString(R.plurals.overall_status_down_count, summary.down, summary.down),
            resources.getQuantityString(R.plurals.overall_status_pending_count, summary.pending, summary.pending),
            resources.getQuantityString(
                R.plurals.overall_status_maintenance_count,
                summary.maintenance,
                summary.maintenance,
            ),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ursa)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        repository?.disconnect()
        connectionScope?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "ursa_overall_status"
        private const val NOTIFICATION_ID = 0x5552534F
        private const val ACTION_REFRESH = "dev.astoris.ursa.action.REFRESH_OVERALL_STATUS"

        fun setEnabled(context: Context, enabled: Boolean) {
            OverallStatusStore(context).setEnabled(enabled)
            if (enabled) {
                ContextCompat.startForegroundService(context, Intent(context, OverallStatusService::class.java))
            } else {
                context.stopService(Intent(context, OverallStatusService::class.java))
            }
        }

        fun restoreIfEnabled(context: Context) {
            if (OverallStatusStore(context).enabled()) setEnabled(context, true)
        }

        fun refreshIfEnabled(context: Context) {
            if (!OverallStatusStore(context).enabled()) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverallStatusService::class.java).setAction(ACTION_REFRESH),
            )
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.overall_status_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.overall_status_channel_desc)
                        setSound(null, null)
                        enableVibration(false)
                    },
                )
            }
        }
    }
}
