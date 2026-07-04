package dev.astoris.ursa.core.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * The expiry of one monitored TLS certificate, captured while the app was connected.
 * Stored so a background job can remind about upcoming expiry without reconnecting.
 * [validToMillis] is absolute so the day count stays accurate over time.
 */
@Serializable
data class CertExpiry(
    val serverUrl: String,
    val monitorId: Int,
    val monitorName: String,
    val validToMillis: Long,
)

/** Pure helpers for cert-expiry data. No Android types, so unit-testable on the JVM. */
object CertExpiryUtil {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: List<CertExpiry>): String = json.encodeToString(list)

    fun decode(raw: String?): List<CertExpiry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CertExpiry>>(raw) }.getOrDefault(emptyList())
    }

    /**
     * Resolve an absolute expiry instant (epoch ms) from what the server reported.
     * Prefers the ISO-8601 `validTo`; otherwise derives it from `daysRemaining` at
     * capture time. Returns null when neither is usable.
     */
    fun resolveValidToMillis(validTo: String?, daysRemaining: Int?, nowMillis: Long): Long? {
        if (!validTo.isNullOrBlank()) {
            runCatching { Instant.parse(validTo).toEpochMilli() }.getOrNull()?.let { return it }
        }
        if (daysRemaining != null) return nowMillis + daysRemaining * DAY_MS
        return null
    }

    /** Whole days from [nowMillis] until [validToMillis] (negative if already expired). */
    fun daysUntil(validToMillis: Long, nowMillis: Long): Long =
        Math.floorDiv(validToMillis - nowMillis, DAY_MS)

    const val DAY_MS = 86_400_000L
    const val DEFAULT_THRESHOLD_DAYS = 14L
}
