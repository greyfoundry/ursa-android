package dev.astoris.ursa.core.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A rendered monitor notification: what to show in the system tray. */
data class PushNotice(
    val monitorId: Int?,
    val title: String,
    val body: String,
    val important: Boolean,
)

/**
 * Pure parser for the JSON a Kuma "Webhook" notification POSTs to a UnifiedPush
 * endpoint. Body shape (verified against Kuma source, notification-providers/webhook.js):
 * `{ heartbeat, monitor, msg }`, where `msg` is ready-made text like
 * "[name] [🔴 Down] connect ECONNREFUSED ...".
 *
 * Kept free of Android types so it is unit-testable on the JVM. The push payload is
 * untrusted input (MASVS-PLATFORM-1) — parsing is tolerant and never throws; a body
 * that isn't the expected JSON yields null so the caller can fall back to raw text.
 */
object PushParse {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): PushNotice? {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val monitor = root["monitor"]?.let { runCatching { it.jsonObject }.getOrNull() }
        val heartbeat = root["heartbeat"]?.let { runCatching { it.jsonObject }.getOrNull() }

        val name = monitor?.get("name")?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: "Monitor"
        val status = heartbeat?.get("status")?.jsonPrimitive?.intOrNull
        val monitorId = heartbeat?.get("monitorID")?.jsonPrimitive?.intOrNull
            ?: monitor?.get("id")?.jsonPrimitive?.intOrNull

        val message = root["msg"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: heartbeat?.get("msg")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            ?: statusPhrase(status)

        // `important` can be a real boolean (heartbeat event) or 1/0 (beat rows).
        val important = heartbeat?.get("important")?.let { el ->
            el.jsonPrimitive.booleanOrNull ?: (el.jsonPrimitive.intOrNull?.let { it != 0 })
        } ?: (status == 0)

        return PushNotice(
            monitorId = monitorId,
            title = "$name ${statusPhrase(status)}",
            body = message,
            important = important,
        )
    }

    private fun statusPhrase(status: Int?): String = when (status) {
        1 -> "is Up"
        0 -> "is Down"
        2 -> "is Pending"
        3 -> "in Maintenance"
        else -> "updated"
    }
}
