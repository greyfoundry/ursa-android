package dev.astoris.ursa.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLogStoreTest {

    @Test
    fun codecRoundTripsAndRejectsCorruptInput() {
        val now = 2_000_000_000_000L
        val event = LocalEvent(
            id = "event-1",
            serverUrl = "https://kuma.example",
            monitorId = 7,
            monitorName = "API",
            kind = LocalEventKind.SLOW_RESPONSE,
            atMillis = now,
            detail = "900ms response, 500ms limit",
        )

        assertEquals(listOf(event), LocalEventCodec.decode(LocalEventCodec.encode(listOf(event), now), now))
        assertTrue(LocalEventCodec.decode("not-json", now).isEmpty())
    }

    @Test
    fun codecReadsLegacyEventsWithoutAlertFields() {
        val now = 2_000_000_000_000L
        val raw = """[{"id":"legacy","monitorName":"API","kind":"PUSH_ALERT","atMillis":$now}]"""

        val event = LocalEventCodec.decode(raw, now).single()

        assertEquals("legacy", event.id)
        assertEquals(null, event.alertId)
        assertEquals(null, event.alertDecision)
    }

    @Test
    fun normalizationBoundsAlertDecisionAndRejectsInvalidOpaqueId() {
        val now = 2_000_000_000_000L
        val event = LocalEvent(
            id = "event",
            monitorName = "API",
            kind = LocalEventKind.PUSH_DELAYED,
            atMillis = now,
            alertId = "not-a-uuid",
            alertDecision = LocalAlertDecision(
                outcome = "DELAYED",
                mode = "DOWN_ONLY",
                configuredSeverity = "CRITICAL",
                firstDelayMinutes = 100_000,
                repeatMinutes = -1,
                maxRepeats = 999,
                scheduledAtMillis = -1,
                deliveredCount = 999,
            ),
        )

        val normalized = LocalEventCodec.normalized(listOf(event), now).single()

        assertEquals(null, normalized.alertId)
        assertEquals(1_440, normalized.alertDecision?.firstDelayMinutes)
        assertEquals(0, normalized.alertDecision?.repeatMinutes)
        assertEquals(100, normalized.alertDecision?.maxRepeats)
        assertEquals(null, normalized.alertDecision?.scheduledAtMillis)
        assertEquals(101, normalized.alertDecision?.deliveredCount)
    }

    @Test
    fun normalizationBoundsRetentionSizeAndUntrustedText() {
        val now = 2_000_000_000_000L
        val events = (0..LocalEventCodec.MAX_EVENTS + 10).map { index ->
            LocalEvent(
                id = "event-$index",
                monitorName = " x".repeat(100),
                kind = LocalEventKind.PUSH_ALERT,
                atMillis = now - index,
                detail = "d".repeat(300),
            )
        } + LocalEvent(
            id = "expired",
            monitorName = "Old",
            kind = LocalEventKind.PAUSED,
            atMillis = now - LocalEventCodec.RETENTION_MILLIS - 1,
        )

        val normalized = LocalEventCodec.normalized(events, now)

        assertEquals(LocalEventCodec.MAX_EVENTS, normalized.size)
        assertEquals("event-0", normalized.first().id)
        assertTrue(normalized.none { it.id == "expired" })
        assertTrue(normalized.all { it.monitorName.length <= 120 })
        assertTrue(normalized.all { (it.detail?.length ?: 0) <= 240 })
    }
}
