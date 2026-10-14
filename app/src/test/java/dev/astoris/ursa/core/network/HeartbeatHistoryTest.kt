package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.MonitorStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatHistoryTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()

    @Test
    fun rangeValidationAndLookbackAreBounded() {
        val valid = HeartbeatHistoryRange(
            Instant.parse("2026-10-14T09:30:00Z").toEpochMilli(),
            Instant.parse("2026-10-14T10:30:00Z").toEpochMilli(),
        )
        assertNull(valid.validationError(now))
        assertEquals(3, valid.requiredLookbackHours(now))

        assertEquals(
            HeartbeatHistoryRangeError.EMPTY,
            HeartbeatHistoryRange(now, now).validationError(now),
        )
        assertEquals(
            HeartbeatHistoryRangeError.FUTURE,
            HeartbeatHistoryRange(now - 1_000L, now + 120_000L).validationError(now),
        )
        assertEquals(
            HeartbeatHistoryRangeError.LOOKBACK_TOO_LARGE,
            HeartbeatHistoryRange(
                now - HeartbeatHistoryRange.MAX_LOOKBACK_MILLIS - 1L,
                now,
            ).validationError(now),
        )
    }

    @Test
    fun exactRangeFiltersDeduplicatesAndReportsTruncation() {
        val range = HeartbeatHistoryRange(
            Instant.parse("2026-10-14T09:00:00Z").toEpochMilli(),
            Instant.parse("2026-10-14T10:00:00Z").toEpochMilli(),
        )
        val before = beat("2026-10-14 08:59:59.000", 1)
        val first = beat("2026-10-14 09:15:00.000", 1)
        val duplicate = first.copy()
        val second = beat("2026-10-14 09:45:00", 0)
        val malformed = beat("not-a-time", 1)

        val result = filterHeartbeatHistory(
            listOf(second, malformed, duplicate, before, first),
            range,
            maxRows = 1,
        )

        assertEquals(listOf(second), result.beats)
        assertTrue(result.truncated)
        assertEquals(1, result.malformedRows)
        assertEquals(Instant.parse("2026-10-14T09:45:00Z").toEpochMilli(), result.observedStartMillis)
        assertEquals(result.observedStartMillis, result.observedEndMillis)
    }

    @Test
    fun emptyValidRangeIsNotReportedAsTruncated() {
        val range = HeartbeatHistoryRange.recent(24, now)
        val result = filterHeartbeatHistory(emptyList(), range)

        assertTrue(result.beats.isEmpty())
        assertFalse(result.truncated)
        assertNull(result.observedStartMillis)
        assertNull(result.observedEndMillis)
    }

    private fun beat(time: String, status: Int) = Heartbeat(
        monitorId = 7,
        status = MonitorStatus.from(status),
        time = time,
        msg = null,
        ping = 42,
        important = false,
    )
}
