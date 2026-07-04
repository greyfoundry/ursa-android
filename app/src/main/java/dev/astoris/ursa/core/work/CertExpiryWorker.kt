package dev.astoris.ursa.core.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.astoris.ursa.core.storage.CertExpiryStore
import dev.astoris.ursa.core.storage.CertExpiryUtil
import java.util.concurrent.TimeUnit

/**
 * Daily check that reminds about TLS certificates nearing expiry, using the expiry
 * data captured while the app was connected. Runs even when the app is closed; it
 * does not open any network connection.
 */
class CertExpiryWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // POST_NOTIFICATIONS is a runtime permission on API 33+; without it, do nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        ensureChannel(applicationContext)

        val now = System.currentTimeMillis()
        val entries = CertExpiryStore(applicationContext).loadAll()
        val manager = NotificationManagerCompat.from(applicationContext)

        for (entry in entries) {
            val days = CertExpiryUtil.daysUntil(entry.validToMillis, now)
            if (days in 0..CertExpiryUtil.DEFAULT_THRESHOLD_DAYS) {
                val text = if (days == 0L) {
                    "The certificate for ${entry.monitorName} expires today."
                } else {
                    "The certificate for ${entry.monitorName} expires in $days day(s)."
                }
                val notification = Notification.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(dev.astoris.ursa.R.drawable.ic_stat_ursa)
                    .setContentTitle("Certificate expiring")
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .build()
                manager.notify("${entry.serverUrl}:${entry.monitorId}".hashCode(), notification)
            }
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "ursa_cert_expiry"
        private const val WORK_NAME = "cert_expiry_check"

        private fun ensureChannel(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Certificate expiry",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "Reminders before a monitored TLS certificate expires" },
                )
            }
        }

        /** Schedule the daily check once (kept if already scheduled). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CertExpiryWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
