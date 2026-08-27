package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.ManagedPushNotification
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorChartPoint
import dev.astoris.ursa.data.model.MonitorStatus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Pure normalization of Uptime Kuma wire payloads into domain models. Kept free of
 * Android/Socket.IO types (operates on kotlinx JsonObject) so it is unit-testable on
 * the JVM. Shapes verified live against Kuma 2.5.3 - see docs/references/uptime-kuma-api.mdx.
 */
object KumaParse {

    /** Kuma serializes some positional Socket.IO IDs as JSON strings. */
    fun positionalInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    /** Kuma positional metrics may arrive as either JSON numbers or numeric strings. */
    fun positionalDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

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

    /** Bean-serialized heartbeat arrays such as `monitorImportantHeartbeatListPaged`. */
    fun heartbeatRows(arr: JsonArray): List<Heartbeat> =
        arr.mapNotNull { (it as? JsonObject)?.let(::heartbeat) }

    /** Keeps only webhook providers explicitly marked as managed by URSA. */
    fun managedPushNotifications(arr: JsonArray): List<ManagedPushNotification> =
        arr.mapNotNull { value ->
            val obj = value as? JsonObject ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val rawConfig = obj["config"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val config = runCatching { Json.parseToJsonElement(rawConfig).jsonObject }.getOrNull()
                ?: return@mapNotNull null
            if (
                config[ManagedPushNotification.MANAGED_MARKER]?.jsonPrimitive?.booleanOrNull != true
            ) return@mapNotNull null
            if (config["type"]?.jsonPrimitive?.contentOrNull != "webhook") return@mapNotNull null
            val webhookUrl = config["webhookURL"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ManagedPushNotification(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: config["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                webhookUrl = webhookUrl,
                isDefault = obj["isDefault"]?.jsonPrimitive?.booleanOrNull
                    ?: config["isDefault"]?.jsonPrimitive?.booleanOrNull
                    ?: false,
            )
        }.sortedBy(ManagedPushNotification::id)

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

    /** `getMonitorChartData` buckets, aggregated by Kuma's uptime calculator. */
    fun chartRows(arr: JsonArray): List<MonitorChartPoint> = arr.mapNotNull { value ->
        val obj = value as? JsonObject ?: return@mapNotNull null
        val timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
        val up = obj["up"]?.jsonPrimitive?.longOrNull ?: 0L
        val down = obj["down"]?.jsonPrimitive?.longOrNull ?: 0L
        if (up < 0L || down < 0L) return@mapNotNull null
        val avgPing = obj["avgPing"]?.jsonPrimitive?.doubleOrNull
            ?.takeIf { it.isFinite() && it >= 0.0 }
        MonitorChartPoint(up, down, avgPing, timestamp)
    }

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
