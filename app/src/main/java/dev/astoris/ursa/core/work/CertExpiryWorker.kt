package dev.astoris.ursa.core.work

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.core.push.PushEventPreferencesStore
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.ui.AppDeepLink
import androidx.core.net.toUri
import java.util.concurrent.TimeUnit

/**
 * Daily check that reminds about TLS certificates nearing expiry, using the expiry
 * data captured while the app was connected. Runs even when the app is closed; it
 * does not open any network connection.
 */
class CertExpiryWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!PushEventPreferencesStore(applicationContext).load().certificateEnabled) {
            return Result.success()
        }
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
        val eventLogStore = EventLogStore(applicationContext)
        val manager = NotificationManagerCompat.from(applicationContext)

        for (entry in entries) {
            val days = CertExpiryUtil.daysUntil(entry.validToMillis, now)
            if (days in 0..CertExpiryUtil.DEFAULT_THRESHOLD_DAYS) {
                val text = if (days == 0L) {
                    applicationContext.getString(
                        dev.astoris.ursa.R.string.cert_expiry_notification_today,
                        entry.monitorName,
                    )
                } else {
                    applicationContext.resources.getQuantityString(
                        dev.astoris.ursa.R.plurals.cert_expiry_notification_days,
                        days.toInt(),
                        entry.monitorName,
                        days.toInt(),
                    )
                }
                val notification = Notification.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(dev.astoris.ursa.R.drawable.ic_stat_ursa)
                    .setContentTitle(
                        applicationContext.getString(dev.astoris.ursa.R.string.cert_expiry_notification_title),
                    )
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            applicationContext,
                            "${entry.serverUrl}:${entry.monitorId}".hashCode(),
                            Intent().apply {
                                setClassName(applicationContext.packageName, MainActivity::class.java.name)
                                action = Intent.ACTION_VIEW
                                data = AppDeepLink.monitor(entry.serverUrl, entry.monitorId).toUri()
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            },
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    .build()
                manager.notify("${entry.serverUrl}:${entry.monitorId}".hashCode(), notification)
                eventLogStore.append(
                    serverUrl = entry.serverUrl,
                    monitorId = entry.monitorId,
                    monitorName = entry.monitorName,
                    kind = LocalEventKind.CERTIFICATE_EXPIRY,
                    detail = text,
                    atMillis = now,
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "ursa_cert_expiry"
        private const val WORK_NAME = "cert_expiry_check"

        fun ensureChannel(context: Context) {
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
