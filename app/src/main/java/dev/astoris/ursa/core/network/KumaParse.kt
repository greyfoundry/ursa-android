package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure normalization of Uptime Kuma wire payloads into domain models. Kept free of
 * Android/Socket.IO types (operates on kotlinx JsonObject) so it is unit-testable on
 * the JVM. Shapes verified live against Kuma 2.4.0 - see docs/references/uptime-kuma-api.mdx.
 */
object KumaParse {

    /** `monitorList` / `updateMonitorIntoList`: id-keyed object of monitors. */
    fun monitorList(obj: JsonObject): Map<Int, Monitor> = buildMap {
        for ((_, value) in obj) {
            (value as? JsonObject)?.let(::monitor)?.let { put(it.id, it) }
        }
    }

    fun monitor(obj: JsonObject): Monitor? {
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        return Monitor(
            id = id,
            name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
            url = obj["url"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
            type = obj["type"]?.jsonPrimitive?.contentOrNull ?: "",
            active = obj["active"]?.jsonPrimitive?.booleanOrNull ?: true,
            tags = tags(obj),
        )
    }

    fun tags(obj: JsonObject): List<String> =
        obj["tags"]?.jsonArray?.mapNotNull { el ->
            (el as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.ifEmpty { null }
        } ?: emptyList()

    /** `heartbeat` event (object, camelCase). */
    fun heartbeat(obj: JsonObject): Heartbeat? {
        val id = obj["monitorID"]?.jsonPrimitive?.intOrNull ?: return null
        return Heartbeat(
            monitorId = id,
            status = MonitorStatus.from(obj["status"]?.jsonPrimitive?.intOrNull ?: 2),
            time = obj["time"]?.jsonPrimitive?.contentOrNull ?: "",
            msg = obj["msg"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
            ping = obj["ping"]?.jsonPrimitive?.intOrNull,
            important = obj["important"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    /** `getMonitorBeats` rows - SNAKE_CASE, `important` is 1/0 not a boolean. */
    fun beatRow(obj: JsonObject): Heartbeat? {
        val id = obj["monitor_id"]?.jsonPrimitive?.intOrNull ?: return null
        return Heartbeat(
            monitorId = id,
            status = MonitorStatus.from(obj["status"]?.jsonPrimitive?.intOrNull ?: 2),
            time = obj["time"]?.jsonPrimitive?.contentOrNull ?: "",
            msg = obj["msg"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
            ping = obj["ping"]?.jsonPrimitive?.intOrNull,
            important = (obj["important"]?.jsonPrimitive?.intOrNull ?: 0) != 0,
        )
    }

    fun beatRows(arr: JsonArray): List<Heartbeat> =
        arr.mapNotNull { (it as? JsonObject)?.let(::beatRow) }

    /**
     * `certInfo` payload: `{ valid, certInfo: { subject:{CN}, issuer:{CN}, validTo,
     * daysRemaining } }`. `validTo`/`daysRemaining` drive the local expiry reminder.
     */
    fun cert(obj: JsonObject): CertInfo {
        val info = obj["certInfo"] as? JsonObject
        return CertInfo(
            valid = obj["valid"]?.jsonPrimitive?.booleanOrNull ?: false,
            subject = (info?.get("subject") as? JsonObject)?.get("CN")?.jsonPrimitive?.contentOrNull,
            issuer = (info?.get("issuer") as? JsonObject)?.get("CN")?.jsonPrimitive?.contentOrNull,
            validTo = info?.get("validTo")?.jsonPrimitive?.contentOrNull?.ifEmpty { null },
            daysRemaining = info?.get("daysRemaining")?.jsonPrimitive?.intOrNull,
        )
    }
}
