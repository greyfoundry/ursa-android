package dev.astoris.ursa.wear

import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class WearMonitorStatus {
    UP,
    DOWN,
    PENDING,
    MAINTENANCE,
    UNKNOWN,
}

data class WearMonitor(
    val id: Int,
    val name: String,
    val group: String?,
    val status: WearMonitorStatus,
    val pingMs: Int?,
    val uptime24h: Double?,
    val tags: List<String>,
)

data class WearSnapshot(
    val title: String,
    val monitors: List<WearMonitor>,
) {
    val up: Int get() = monitors.count { it.status == WearMonitorStatus.UP }
    val down: Int get() = monitors.count { it.status == WearMonitorStatus.DOWN }
    val pending: Int get() = monitors.count { it.status == WearMonitorStatus.PENDING }
    val maintenance: Int get() = monitors.count { it.status == WearMonitorStatus.MAINTENANCE }

    fun attentionFirst(): List<WearMonitor> = monitors.sortedWith(
        compareBy<WearMonitor>({ statusPriority(it.status) }, { it.name.lowercase(Locale.ROOT) }),
    )

    private fun statusPriority(status: WearMonitorStatus): Int = when (status) {
        WearMonitorStatus.DOWN -> 0
        WearMonitorStatus.PENDING -> 1
        WearMonitorStatus.MAINTENANCE -> 2
        WearMonitorStatus.UNKNOWN -> 3
        WearMonitorStatus.UP -> 4
    }
}

data class StatusPageAddress(
    val baseUrl: String,
    val slug: String,
) {
    val configUrl: String get() = "$baseUrl/api/status-page/$slug"
    val heartbeatUrl: String get() = "$baseUrl/api/status-page/heartbeat/$slug"

    companion object {
        fun parse(value: String): StatusPageAddress? {
            val uri = safeHttpUri(value) ?: return null
            val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotBlank)
            val slug = segments.lastOrNull()?.takeIf(::safePathSegment) ?: return null
            val baseSegments = if (segments.size >= 2 && segments[segments.lastIndex - 1] == "status") {
                segments.dropLast(2)
            } else {
                emptyList()
            }
            if (baseSegments.any { !safePathSegment(it) }) return null
            val basePath = baseSegments.joinToString(separator = "/", prefix = "/")
                .takeIf { baseSegments.isNotEmpty() }
                .orEmpty()
            return StatusPageAddress(httpOrigin(uri) + basePath, slug)
        }
    }
}

data class WearActionConfig(
    val serverUrl: String,
    val sessionToken: String,
    val headers: List<WearActionHeader> = emptyList(),
) {
    val normalizedServerUrl: String? get() {
        val uri = safeHttpUri(serverUrl) ?: return null
        val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotBlank)
        if (segments.any { !safePathSegment(it) }) return null
        val path = segments.joinToString(separator = "/", prefix = "/")
            .takeIf { segments.isNotEmpty() }
            .orEmpty()
        return httpOrigin(uri) + path
    }

    val isReady: Boolean
        get() = sessionToken.isNotBlank() && sessionToken.length <= MAX_TOKEN_LENGTH &&
            normalizedServerUrl != null && headers.size <= MAX_HEADERS &&
            headers.all { it.normalizedOrNull() != null } &&
            headers.map { it.name.trim().lowercase(Locale.ROOT) }.distinct().size == headers.size

    companion object {
        const val MAX_TOKEN_LENGTH = 8_192
        const val MAX_HEADERS = 8
    }
}

object WearStatusParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(configBody: String, heartbeatBody: String): WearSnapshot {
        val configRoot = parseObject(configBody)
        val heartbeatRoot = parseObject(heartbeatBody)
        val beats = heartbeatRoot["heartbeatList"] as? JsonObject ?: JsonObject(emptyMap())
        val uptimes = heartbeatRoot["uptimeList"] as? JsonObject ?: JsonObject(emptyMap())
        val monitors = linkedMapOf<Int, WearMonitor>()
        val groups = configRoot["publicGroupList"] as? JsonArray ?: JsonArray(emptyList())

        groups.forEach { groupElement ->
            val group = groupElement as? JsonObject ?: return@forEach
            val groupName = group.string("name")
            val monitorList = group["monitorList"] as? JsonArray ?: return@forEach
            monitorList.forEach monitorLoop@{ monitorElement ->
                val monitor = monitorElement as? JsonObject ?: return@monitorLoop
                val id = monitor["id"]?.jsonPrimitive?.intOrNull ?: return@monitorLoop
                if (id <= 0 || monitors.containsKey(id)) return@monitorLoop
                val latest = (beats[id.toString()] as? JsonArray)?.lastOrNull() as? JsonObject
                monitors[id] = WearMonitor(
                    id = id,
                    name = monitor.string("name") ?: "Monitor $id",
                    group = groupName,
                    status = latest.status(),
                    pingMs = latest?.get("ping")?.jsonPrimitive?.doubleOrNull?.toInt(),
                    uptime24h = uptimes["${id}_24"]?.jsonPrimitive?.doubleOrNull
                        ?.takeIf { it in 0.0..1.0 },
                    tags = monitor.tags(),
                )
            }
        }
        val title = (configRoot["config"] as? JsonObject)?.string("title") ?: "URSA"
        return WearSnapshot(title = title, monitors = monitors.values.toList())
    }

    private fun parseObject(body: String): JsonObject = runCatching {
        json.parseToJsonElement(body).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
        ?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_TEXT_LENGTH)

    private fun JsonObject?.status(): WearMonitorStatus = when (
        this?.get("status")?.jsonPrimitive?.intOrNull
    ) {
        0 -> WearMonitorStatus.DOWN
        1 -> WearMonitorStatus.UP
        2 -> WearMonitorStatus.PENDING
        3 -> WearMonitorStatus.MAINTENANCE
        else -> WearMonitorStatus.UNKNOWN
    }

    private fun JsonObject.tags(): List<String> = (this["tags"] as? JsonArray).orEmpty()
        .mapNotNull { element ->
            val tag = element as? JsonObject ?: return@mapNotNull null
            val name = tag.string("name") ?: return@mapNotNull null
            val value = tag.string("value")
            if (value == null) name else "$name: $value"
        }
        .distinct()
        .take(MAX_TAGS)

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private const val MAX_TEXT_LENGTH = 80
    private const val MAX_TAGS = 8
}

object WearDisplay {
    fun fleetSummary(snapshot: WearSnapshot): String {
        if (snapshot.monitors.isEmpty()) return "No monitors"
        val unknown = snapshot.monitors.size - snapshot.up - snapshot.down -
            snapshot.pending - snapshot.maintenance
        return buildList {
            if (snapshot.down > 0) add("${snapshot.down} down")
            if (snapshot.pending > 0) add("${snapshot.pending} pending")
            if (snapshot.maintenance > 0) add("${snapshot.maintenance} maintenance")
            if (unknown > 0) add("$unknown unknown")
            if (snapshot.up > 0) add("${snapshot.up} up")
        }.joinToString(" · ")
    }

    fun complicationText(snapshot: WearSnapshot): String = when {
        snapshot.monitors.isEmpty() -> "No monitors"
        snapshot.down > 0 -> "${snapshot.down} down"
        snapshot.pending > 0 -> "${snapshot.pending} pending"
        snapshot.maintenance > 0 -> "Maintenance"
        else -> "All clear"
    }

    fun metrics(monitor: WearMonitor): String = buildList {
        monitor.pingMs?.let { add("$it ms") }
        monitor.uptime24h?.let { add(String.format(Locale.US, "%.1f%%", it * 100.0)) }
    }.joinToString(" · ").ifEmpty { "No recent metrics" }

    fun statusLabel(status: WearMonitorStatus): String = when (status) {
        WearMonitorStatus.UP -> "Up"
        WearMonitorStatus.DOWN -> "Down"
        WearMonitorStatus.PENDING -> "Pending"
        WearMonitorStatus.MAINTENANCE -> "Maintenance"
        WearMonitorStatus.UNKNOWN -> "Unknown"
    }
}

private fun safeHttpUri(value: String): URI? {
    if (value.length > 2_048) return null
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
    return uri
}

private fun safePathSegment(value: String): Boolean =
    value.matches(Regex("[A-Za-z0-9._~-]+")) && value != "." && value != ".."

private fun httpOrigin(uri: URI): String {
    val host = if (uri.host.contains(':')) "[${uri.host}]" else uri.host
    val port = if (uri.port == -1) "" else ":${uri.port}"
    return "${uri.scheme.lowercase(Locale.ROOT)}://$host$port"
}
