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
