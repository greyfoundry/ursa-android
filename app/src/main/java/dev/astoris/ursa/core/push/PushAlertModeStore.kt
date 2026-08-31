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

enum class PushSeverity {
    CRITICAL,
    STANDARD,
    SILENT,
}

data class PushChannelRoute(
    val channelId: String,
    val highPriority: Boolean,
    val sound: Boolean,
    val vibration: Boolean,
)

object PushSeverityPolicy {
    fun route(severity: PushSeverity): PushChannelRoute = when (severity) {
        PushSeverity.CRITICAL -> PushChannelRoute(
            channelId = "ursa_monitors_critical",
            highPriority = true,
            sound = true,
            vibration = true,
        )
        PushSeverity.STANDARD -> PushChannelRoute(
            channelId = "ursa_monitors_standard",
            highPriority = false,
            sound = true,
            vibration = false,
        )
        PushSeverity.SILENT -> PushChannelRoute(
            channelId = "ursa_monitors_silent",
            highPriority = false,
            sound = false,
            vibration = false,
        )
    }

    fun decode(value: String?): PushSeverity = value?.let {
        runCatching { PushSeverity.valueOf(it) }.getOrNull()
    } ?: PushSeverity.CRITICAL
}

object PushAlertPreferenceKey {
    fun mode(serverId: String?, monitorId: Int?): String? = scoped(serverId, monitorId)

    fun severity(serverId: String?, monitorId: Int?): String? =
        scoped(serverId, monitorId)?.let { "severity:$it" }

    fun belongsToServer(key: String, serverId: String): Boolean =
        key.startsWith("$serverId:") || key.startsWith("severity:$serverId:")

    private fun scoped(serverId: String?, monitorId: Int?): String? {
        if (!ManagedPushNotification.isValidServerId(serverId) || monitorId == null || monitorId <= 0) {
            return null
        }
        return "$serverId:$monitorId"
    }
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
        val key = PushAlertPreferenceKey.mode(serverId, monitorId) ?: return PushAlertMode.ALL_TRANSITIONS
        return prefs.getString(key, null)?.let { value ->
            runCatching { PushAlertMode.valueOf(value) }.getOrNull()
        } ?: PushAlertMode.ALL_TRANSITIONS
    }

    fun setMode(serverId: String, monitorId: Int, mode: PushAlertMode): Boolean {
        val key = PushAlertPreferenceKey.mode(serverId, monitorId) ?: return false
        prefs.edit {
            if (mode == PushAlertMode.ALL_TRANSITIONS) remove(key) else putString(key, mode.name)
        }
        return true
    }

    fun modes(serverId: String?, monitorIds: Iterable<Int>): Map<Int, PushAlertMode> =
        monitorIds.associateWith { mode(serverId, it) }

    fun severity(serverId: String?, monitorId: Int?): PushSeverity {
        val key = PushAlertPreferenceKey.severity(serverId, monitorId) ?: return PushSeverity.CRITICAL
        return PushSeverityPolicy.decode(prefs.getString(key, null))
    }

    fun setSeverity(serverId: String, monitorId: Int, severity: PushSeverity): Boolean {
        val key = PushAlertPreferenceKey.severity(serverId, monitorId) ?: return false
        prefs.edit {
            if (severity == PushSeverity.CRITICAL) remove(key) else putString(key, severity.name)
        }
        return true
    }

    fun severities(serverId: String?, monitorIds: Iterable<Int>): Map<Int, PushSeverity> =
        monitorIds.associateWith { severity(serverId, it) }

    fun clearServer(serverId: String?): Boolean {
        val validServerId = serverId?.takeIf(ManagedPushNotification::isValidServerId) ?: return false
        val keys = prefs.all.keys.filter { PushAlertPreferenceKey.belongsToServer(it, validServerId) }
        if (keys.isEmpty()) return true
        prefs.edit { keys.forEach(::remove) }
        return true
    }

    private companion object {
        const val PREFS = "ursa_push_alert_modes"
    }
}
