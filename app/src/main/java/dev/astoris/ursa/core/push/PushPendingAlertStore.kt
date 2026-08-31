package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import dev.astoris.ursa.core.storage.Crypto
import dev.astoris.ursa.data.model.ManagedPushNotification
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class PushPendingAlert(
    val id: String,
    val serverId: String,
    val monitorId: Int,
    val monitorName: String,
    val title: String,
    val body: String,
    val severity: PushSeverity,
    val timing: PushAlertTiming,
    val deliveredCount: Int,
) {
    fun asNotice(): PushNotice = PushNotice(
        monitorId = monitorId,
        monitorName = monitorName,
        title = title,
        body = body,
        important = severity == PushSeverity.CRITICAL,
        status = 0,
        serverId = serverId,
    )
}

object PushPendingAlertCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(alert: PushPendingAlert): String = json.encodeToString(
        PersistedPushAlert(
            id = alert.id,
            serverId = alert.serverId,
            monitorId = alert.monitorId,
            monitorName = alert.monitorName,
            title = alert.title,
            body = alert.body,
            severity = alert.severity.name,
            firstDelayMinutes = alert.timing.firstDelayMinutes,
            repeatMinutes = alert.timing.repeatMinutes,
            maxRepeats = alert.timing.maxRepeats,
            deliveredCount = alert.deliveredCount,
        ),
    )

    fun decode(value: String): PushPendingAlert? {
        val saved = runCatching { json.decodeFromString<PersistedPushAlert>(value) }.getOrNull()
            ?: return null
        val validId = runCatching { UUID.fromString(saved.id).toString() }.getOrNull() ?: return null
        if (!ManagedPushNotification.isValidServerId(saved.serverId) || saved.monitorId <= 0) return null
        val severity = runCatching { PushSeverity.valueOf(saved.severity) }.getOrNull() ?: return null
        val timing = PushAlertTiming(
            saved.firstDelayMinutes,
            saved.repeatMinutes,
            saved.maxRepeats,
        ).normalized()
        return PushPendingAlert(
            id = validId,
            serverId = saved.serverId,
            monitorId = saved.monitorId,
            monitorName = saved.monitorName.take(120),
            title = saved.title.take(160),
            body = saved.body.take(1_000),
            severity = severity,
            timing = timing,
            deliveredCount = saved.deliveredCount.coerceIn(0, timing.maxRepeats + 1),
        )
    }

    @Serializable
    private data class PersistedPushAlert(
        val id: String,
        val serverId: String,
        val monitorId: Int,
        val monitorName: String,
        val title: String,
        val body: String,
        val severity: String,
        val firstDelayMinutes: Int,
        val repeatMinutes: Int,
        val maxRepeats: Int,
        val deliveredCount: Int,
    )
}

/** Stores rendered delayed/repeating alerts encrypted at rest. WorkManager sees only an opaque id. */
class PushPendingAlertStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val crypto = Crypto(context.applicationContext)

    fun save(alert: PushPendingAlert) {
        val activeKey = activeKey(alert.serverId, alert.monitorId)
        val previousId = prefs.getString(activeKey, null)
        prefs.edit {
            putString(alertKey(alert.id), crypto.encrypt(PushPendingAlertCodec.encode(alert)))
            putString(activeKey, alert.id)
            if (previousId != null && previousId != alert.id) remove(alertKey(previousId))
        }
    }

    fun load(id: String): PushPendingAlert? {
        val cipher = prefs.getString(alertKey(id), null) ?: return null
        val plain = crypto.decrypt(cipher) ?: return null
        return PushPendingAlertCodec.decode(plain)?.takeIf { it.id == id }
    }

    fun loadActive(serverId: String, monitorId: Int): PushPendingAlert? {
        val id = prefs.getString(activeKey(serverId, monitorId), null) ?: return null
        return load(id)
    }

    fun isActive(alert: PushPendingAlert): Boolean =
        prefs.getString(activeKey(alert.serverId, alert.monitorId), null) == alert.id

    fun removeActive(serverId: String, monitorId: Int): PushPendingAlert? {
        val key = activeKey(serverId, monitorId)
        val id = prefs.getString(key, null) ?: return null
        val alert = load(id)
        prefs.edit {
            remove(key)
            remove(alertKey(id))
        }
        return alert
    }

    private companion object {
        const val PREFS = "ursa_pending_push_alerts"

        fun alertKey(id: String) = "alert:$id"

        fun activeKey(serverId: String, monitorId: Int) = "active:$serverId:$monitorId"
    }
}
