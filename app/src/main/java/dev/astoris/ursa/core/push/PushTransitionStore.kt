package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import dev.astoris.ursa.data.model.ManagedPushNotification

class PushTransitionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun shouldDeliver(
        serverId: String?,
        monitorId: Int?,
        status: Int?,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val key = key(serverId, monitorId) ?: return true
        val previous = decode(prefs.getString(key, null))
        val decision = PushTransitionDedup.evaluate(previous, status, nowMillis)
        if (decision.deliver) prefs.edit { putString(key, encode(decision.next)) }
        return decision.deliver
    }

    fun clearServer(serverId: String?) {
        val valid = serverId?.takeIf(ManagedPushNotification::isValidServerId) ?: return
        val prefix = "$valid:"
        prefs.edit { prefs.all.keys.filter { it.startsWith(prefix) }.forEach(::remove) }
    }

    private fun key(serverId: String?, monitorId: Int?): String? {
        if (!ManagedPushNotification.isValidServerId(serverId) || monitorId == null || monitorId <= 0) return null
        return "$serverId:$monitorId"
    }

    private fun encode(record: PushTransitionRecord): String =
        "${record.status?.toString() ?: "null"}:${record.atMillis}"

    private fun decode(value: String?): PushTransitionRecord? {
        val parts = value?.split(':') ?: return null
        if (parts.size != 2) return null
        val status = if (parts[0] == "null") null else parts[0].toIntOrNull() ?: return null
        val atMillis = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return PushTransitionRecord(status, atMillis)
    }

    private companion object {
        const val PREFS = "ursa_push_transition_dedup"
    }
}
