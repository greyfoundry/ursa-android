package dev.astoris.ursa.core.work

import java.util.concurrent.TimeUnit

/**
 * Pure logic for local "slow response" alerting (upstream Uptime Kuma #1813). Kuma has
 * no server-side slow-response trigger, so URSA evaluates response time itself - live
 * while connected and from a periodic background worker. Everything here is free of
 * Android and network dependencies so it can be unit-tested directly.
 */
object ResponseAlertUtil {

    const val DEFAULT_GLOBAL_THRESHOLD_MS = 1000

    /** Do not re-alert the same monitor within this window, even if it stays slow. */
    val COOLDOWN_MS: Long = TimeUnit.HOURS.toMillis(1)

    /** Stable per-monitor key across servers, matching the cert-expiry convention. */
    fun monitorKey(serverUrl: String, monitorId: Int): String = "$serverUrl:$monitorId"

    /** Per-monitor override when set to a positive value, otherwise the global default. */
    fun effectiveThreshold(perMonitorMs: Int?, globalMs: Int): Int =
        perMonitorMs?.takeIf { it > 0 } ?: globalMs

    /**
     * Alert only when the monitor is UP but slow: a positive threshold, status UP,
     * a ping above the threshold, and no alert for this monitor within the cooldown.
     * A non-positive threshold disables alerting.
     */
    fun shouldAlert(
        status: Int,
        pingMs: Int?,
        thresholdMs: Int,
        lastAlertedAt: Long?,
        now: Long,
        cooldownMs: Long = COOLDOWN_MS,
    ): Boolean {
        if (thresholdMs <= 0) return false
        if (status != STATUS_UP) return false
        val ping = pingMs ?: return false
        if (ping <= thresholdMs) return false
        if (lastAlertedAt != null && now - lastAlertedAt < cooldownMs) return false
        return true
    }

    /** Serialize a per-monitor map. Newline/tab delimiters never appear in the keys
     *  (server URL + numeric id) or the numeric values, so parsing stays unambiguous. */
    fun encodeMap(map: Map<String, Long>): String =
        map.entries.joinToString(RECORD_SEP) { "${it.key}$FIELD_SEP${it.value}" }

    fun decodeMap(encoded: String): Map<String, Long> {
        if (encoded.isEmpty()) return emptyMap()
        return encoded.split(RECORD_SEP).mapNotNull { record ->
            val i = record.lastIndexOf(FIELD_SEP)
            if (i < 0) return@mapNotNull null
            val value = record.substring(i + 1).toLongOrNull() ?: return@mapNotNull null
            record.substring(0, i) to value
        }.toMap()
    }

    private const val STATUS_UP = 1
    private const val RECORD_SEP = "\n"
    private const val FIELD_SEP = "\t"
}
