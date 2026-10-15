package dev.astoris.ursa.core.push

import dev.astoris.ursa.data.model.ManagedPushNotification
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A rendered monitor notification: what to show in the system tray. */
data class PushNotice(
    val monitorId: Int?,
    val monitorName: String,
    val title: String,
    val body: String,
    val important: Boolean,
    /** Kuma heartbeat status (0=down, 1=up, 2=pending, 3=maintenance), when known. */
    val status: Int? = null,
    /** Random scope from URSA's managed provider; absent for legacy/manual Webhooks. */
    val serverId: String? = null,
)

/**
 * Pure parser for the JSON a Kuma "Webhook" notification POSTs to a UnifiedPush
 * endpoint. Body shape (verified against Kuma source, notification-providers/webhook.js):
 * `{ heartbeat, monitor, msg }`, where `msg` is ready-made text like
 * "[name] [🔴 Down] connect ECONNREFUSED ...".
 *
 * Kept free of Android types so it is unit-testable on the JVM. The push payload is
 * untrusted input (MASVS-PLATFORM-1) - parsing is tolerant and never throws; a body
 * that isn't the expected JSON yields null so the caller can fall back to raw text.
 */
object PushParse {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): PushNotice? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val monitor = root["monitor"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val heartbeat = root["heartbeat"]?.let { runCatching { it.jsonObject }.getOrNull() }

        val name = monitor?.get("name")?.jsonPrimitive?.contentOrNull?.ifBlank { null }.orEmpty()
        val status = heartbeat?.get("status")?.jsonPrimitive?.intOrNull
        val monitorId = heartbeat?.get("monitorID")?.jsonPrimitive?.intOrNull
            ?: monitor?.get("id")?.jsonPrimitive?.intOrNull
        val serverId = root[ManagedPushNotification.SERVER_ID_FIELD]
            ?.jsonPrimitive?.contentOrNull
            ?.takeIf(ManagedPushNotification::isValidServerId)

        val message = root["msg"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: heartbeat?.get("msg")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: ""

        // `important` can be a real boolean (heartbeat event) or 1/0 (beat rows).
        val important = heartbeat?.get("important")?.let { el ->
            el.jsonPrimitive.booleanOrNull ?: (el.jsonPrimitive.intOrNull?.let { it != 0 })
        } ?: (status == 0)

        return PushNotice(
            monitorId = monitorId,
            monitorName = name,
            title = "",
            body = message,
            important = important,
            status = status,
            serverId = serverId,
        )
    }

}
