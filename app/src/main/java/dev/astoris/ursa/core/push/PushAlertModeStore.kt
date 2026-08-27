package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import dev.astoris.ursa.data.model.ManagedPushNotification

enum class PushAlertMode {
    MUTED,
    DOWN_ONLY,
    DOWN_AND_RECOVERY,
    ALL_TRANSITIONS,
}

object PushAlertPolicy {
    fun shouldNotify(mode: PushAlertMode, status: Int?): Boolean = when (mode) {
        PushAlertMode.MUTED -> false
        PushAlertMode.DOWN_ONLY -> status == 0
        PushAlertMode.DOWN_AND_RECOVERY -> status == 0 || status == 1
        PushAlertMode.ALL_TRANSITIONS -> true
    }
}

/** Synchronous, non-sensitive policy lookup for the push service receive path. */
class PushAlertModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(serverId: String?, monitorId: Int?): PushAlertMode {
        val key = key(serverId, monitorId) ?: return PushAlertMode.ALL_TRANSITIONS
        return prefs.getString(key, null)?.let { value ->
            runCatching { PushAlertMode.valueOf(value) }.getOrNull()
        } ?: PushAlertMode.ALL_TRANSITIONS
    }

    fun setMode(serverId: String, monitorId: Int, mode: PushAlertMode): Boolean {
        val key = key(serverId, monitorId) ?: return false
        prefs.edit {
            if (mode == PushAlertMode.ALL_TRANSITIONS) remove(key) else putString(key, mode.name)
        }
        return true
    }

    fun modes(serverId: String?, monitorIds: Iterable<Int>): Map<Int, PushAlertMode> =
        monitorIds.associateWith { mode(serverId, it) }

    fun clearServer(serverId: String?): Boolean {
        if (!ManagedPushNotification.isValidServerId(serverId)) return false
        val prefix = "$serverId:"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return true
        prefs.edit { keys.forEach(::remove) }
        return true
    }

    private fun key(serverId: String?, monitorId: Int?): String? {
        if (!ManagedPushNotification.isValidServerId(serverId) || monitorId == null || monitorId <= 0) {
            return null
        }
        return "$serverId:$monitorId"
    }

    private companion object {
        const val PREFS = "ursa_push_alert_modes"
    }
}
