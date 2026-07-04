package dev.astoris.ursa.core.storage

import dev.astoris.ursa.data.model.Monitor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The last-known monitor list for a server, with the time it was captured. Cached so
 * the app can paint immediately (offline or while the socket reconnects) instead of
 * showing an empty screen. Status/ping/uptime included as last seen live.
 */
@Serializable
data class MonitorSnapshot(
    val monitors: List<Monitor>,
    val updatedAt: Long,
)

/**
 * Pure encode/decode for a [MonitorSnapshot]. No Android types, so it is unit-testable
 * on the JVM. Decoding is tolerant: unparseable or empty input yields null rather than
 * throwing, so a corrupt or missing cache simply means "no cache".
 */
object SnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: MonitorSnapshot): String = json.encodeToString(snapshot)

    fun decode(raw: String?): MonitorSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<MonitorSnapshot>(raw) }.getOrNull()
    }
}
