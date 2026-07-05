package dev.astoris.ursa.core.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.KumaClient
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.ResponseAlertStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Periodic background check for "slow but up" monitors (upstream #1813). Kuma has no
 * server-side slow-response trigger, so URSA briefly connects to each saved server with
 * its stored token, reads the latest heartbeat per monitor, and raises a local
 * notification when response time crosses the (global or per-monitor) threshold. A
 * per-monitor cooldown in [ResponseAlertStore] prevents repeat spam.
 */
class ResponseAlertWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = ResponseAlertStore(applicationContext)
        if (!store.isEnabled()) return Result.success()

        // POST_NOTIFICATIONS is a runtime permission on API 33+; without it, do nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        ResponseAlertNotifier.ensureChannel(applicationContext)

        val connections = ConnectionStore(applicationContext).connections.first()
        val globalThreshold = store.globalThresholdMs()
        val perMonitor = store.perMonitorThresholds()
        val lastAlerted = store.lastAlerted()
        val now = System.currentTimeMillis()

        for (conn in connections) {
            val token = conn.jwt ?: continue
            val client = KumaClient(conn.url, conn.insecure)
            try {
                client.connect()
                if (!client.loginByToken(token)) continue
                // Wait for the server to push the initial heartbeat snapshot.
                val ready = withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                    client.state.first { it == ConnectionState.Authenticated }
                    client.beatHistory.first { it.isNotEmpty() }
                } != null
                if (!ready) continue

                val beats = client.beatHistory.value
                val names = client.monitors.value
                for ((monitorId, history) in beats) {
                    val latest = history.lastOrNull() ?: continue
                    val key = ResponseAlertUtil.monitorKey(conn.url, monitorId)
                    val threshold = ResponseAlertUtil.effectiveThreshold(
                        perMonitor[key]?.toInt(), globalThreshold,
                    )
                    if (!ResponseAlertUtil.shouldAlert(
                            latest.status.code, latest.ping, threshold, lastAlerted[key], now,
                        )
                    ) {
                        continue
                    }
                    val name = names[monitorId]?.name ?: "Monitor $monitorId"
                    ResponseAlertNotifier.notify(applicationContext, name, latest.ping ?: 0, threshold, key)
                    store.markAlerted(key, now)
                }
            } catch (_: Exception) {
                // A single unreachable server should not fail the whole run.
            } finally {
                client.disconnect()
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "response_alert_check"
        private const val SETTLE_TIMEOUT_MS = 15_000L

        /** Schedule the periodic check once (kept if already scheduled). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ResponseAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
