package dev.astoris.ursa.ui.components

import java.time.Instant
import java.time.ZoneId

enum class FreshnessState {
    LIVE,
    RECENT,
    STALE,
    OFFLINE,
}

/**
 * Converts cache age and connectivity into one shared freshness vocabulary.
 * Threshold equality is deliberately stale so callers never overstate freshness.
 */
fun resolveFreshness(
    ageMillis: Long,
    staleAfterMillis: Long,
    isOnline: Boolean,
    liveWindowMillis: Long = 15_000L,
): FreshnessState {
    require(staleAfterMillis > 0) { "staleAfterMillis must be positive" }
    require(liveWindowMillis >= 0) { "liveWindowMillis must not be negative" }
    if (!isOnline) return FreshnessState.OFFLINE
    val safeAge = ageMillis.coerceAtLeast(0L)
    return when {
        safeAge < liveWindowMillis -> FreshnessState.LIVE
        safeAge < staleAfterMillis -> FreshnessState.RECENT
        else -> FreshnessState.STALE
    }
}

enum class OperationalStateKind {
    LOADING,
    EMPTY,
    OFFLINE,
    ERROR,
    PARTIAL,
}

enum class TimelineDayGroup {
    TODAY,
    YESTERDAY,
    EARLIER,
}

/** Groups events by the operator's local calendar rather than fixed 24-hour buckets. */
fun timelineDayGroup(
    timestampMillis: Long,
    nowMillis: Long,
    zoneId: ZoneId,
): TimelineDayGroup {
    val eventDate = Instant.ofEpochMilli(timestampMillis).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    return when (eventDate) {
        today -> TimelineDayGroup.TODAY
        today.minusDays(1) -> TimelineDayGroup.YESTERDAY
        else -> TimelineDayGroup.EARLIER
    }
}
