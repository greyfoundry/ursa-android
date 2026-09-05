package dev.astoris.ursa.core.update

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.R
import dev.astoris.ursa.core.push.PushEventPolicy

object UpdateNotifier {
    fun notifyOnce(context: Context, release: AvailableRelease): Boolean {
        val app = context.applicationContext
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val prefs = app.getSharedPreferences("ursa_update_notices", Context.MODE_PRIVATE)
        if (prefs.getString("last_version", null) == release.version.toString()) return false
        val open = Intent()
        open.setClassName(app.packageName, MainActivity::class.java.name)
        open.action = Intent.ACTION_VIEW
        open.data = "ursa://settings".toUri()
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val notification = NotificationCompat.Builder(app, PushEventPolicy.UPDATE_ROUTE.channelId)
            .setSmallIcon(R.drawable.ic_stat_ursa)
            .setContentTitle(app.getString(R.string.update_available_title, release.version.toString()))
            .setContentText(app.getString(R.string.update_available_body))
            .setContentIntent(PendingIntent.getActivity(app, 4400, open, PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        app.getSystemService(NotificationManager::class.java).notify(4400, notification)
        prefs.edit { putString("last_version", release.version.toString()) }
        return true
    }
}
