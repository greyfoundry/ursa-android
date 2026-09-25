package dev.astoris.ursa.ui.components

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationalUiModelTest {
    @Test
    fun freshnessUsesConservativeBoundaries() {
        assertEquals(FreshnessState.LIVE, resolveFreshness(0, 60_000, isOnline = true))
        assertEquals(FreshnessState.LIVE, resolveFreshness(-1, 60_000, isOnline = true))
        assertEquals(FreshnessState.RECENT, resolveFreshness(15_000, 60_000, isOnline = true))
        assertEquals(FreshnessState.STALE, resolveFreshness(60_000, 60_000, isOnline = true))
        assertEquals(FreshnessState.OFFLINE, resolveFreshness(0, 60_000, isOnline = false))
    }

    @Test
    fun freshnessRejectsInvalidPolicy() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveFreshness(ageMillis = 0, staleAfterMillis = 0, isOnline = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveFreshness(
                ageMillis = 0,
                staleAfterMillis = 1,
                isOnline = true,
                liveWindowMillis = -1,
            )
        }
    }

    @Test
    fun timelineGroupingUsesLocalCalendarBoundaries() {
        val zone = ZoneId.of("Europe/London")
        val now = Instant.parse("2026-09-25T00:30:00Z").toEpochMilli()

        assertEquals(
            TimelineDayGroup.TODAY,
            timelineDayGroup(Instant.parse("2026-09-25T00:05:00Z").toEpochMilli(), now, zone),
        )
        assertEquals(
            TimelineDayGroup.YESTERDAY,
            timelineDayGroup(Instant.parse("2026-09-24T22:55:00Z").toEpochMilli(), now, zone),
        )
        assertEquals(
            TimelineDayGroup.EARLIER,
            timelineDayGroup(Instant.parse("2026-09-23T22:55:00Z").toEpochMilli(), now, zone),
        )
    }
}
