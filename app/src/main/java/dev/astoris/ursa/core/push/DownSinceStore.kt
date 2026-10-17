package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import dev.astoris.ursa.data.model.ManagedPushNotification

/**
 * Remembers when each monitor was last seen going down, so a later recovery
 * notification can report how long it was down (upstream #177). Kuma's webhook carries
 * no cumulative downtime, so URSA derives it from the down -> up transition it observes.
 * Plain preferences: a timestamp is not sensitive.
 */
class DownSinceStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("ursa_down_since", Context.MODE_PRIVATE)

    /** Record the moment a monitor went down, keeping the earliest if already down. */
    fun markDown(serverId: String?, monitorId: Int, atMillis: Long) {
        val key = key(serverId, monitorId)
        if (!prefs.contains(key)) prefs.edit { putLong(key, atMillis) }
    }

    /** Return and clear the down-since time, or null if we never saw it go down. */
    fun takeDown(serverId: String?, monitorId: Int): Long? {
        val key = key(serverId, monitorId)
        if (!prefs.contains(key)) return null
        val value = prefs.getLong(key, 0L)
        prefs.edit { remove(key) }
        return value
    }

    companion object {
        internal fun key(serverId: String?, monitorId: Int): String {
            val scope = serverId?.takeIf(ManagedPushNotification::isValidServerId)
            return if (scope == null) "down_$monitorId" else "down_${scope}_$monitorId"
        }
    }
}
