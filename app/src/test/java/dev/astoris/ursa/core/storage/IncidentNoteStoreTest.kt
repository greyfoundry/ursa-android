package dev.astoris.ursa.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentNoteStoreTest {

    @Test
    fun codecRoundTripsAndRejectsCorruptInput() {
        val now = 2_000_000_000_000L
        val note = IncidentNote(
            serverUrl = "https://kuma.example",
            monitorId = 7,
            startedAt = "2026-08-26 10:00:00",
            text = "Investigating upstream timeout",
            updatedAtMillis = now,
        )

        assertEquals(listOf(note), IncidentNoteCodec.decode(IncidentNoteCodec.encode(listOf(note), now), now))
        assertTrue(IncidentNoteCodec.decode("not-json", now).isEmpty())
    }

    @Test
    fun normalizationBoundsTextCountAndKeepsNewestDuplicate() {
        val now = 2_000_000_000_000L
        val duplicate = IncidentNote("https://kuma.example", 1, "start", "old", now - 10)
        val newest = duplicate.copy(text = " new ", updatedAtMillis = now)
        val notes = (2..IncidentNoteCodec.MAX_NOTES + 10).map { index ->
            IncidentNote(
                serverUrl = "https://kuma.example",
                monitorId = index,
                startedAt = "start-$index",
                text = "x".repeat(IncidentNoteCodec.MAX_TEXT_LENGTH + 20),
                updatedAtMillis = now - index,
            )
        } + duplicate + newest + IncidentNote("", 9, "start", "invalid", now)

        val normalized = IncidentNoteCodec.normalized(notes, now)

        assertEquals(IncidentNoteCodec.MAX_NOTES, normalized.size)
        assertEquals("new", normalized.first().text)
        assertTrue(normalized.all { it.text.length <= IncidentNoteCodec.MAX_TEXT_LENGTH })
        assertTrue(normalized.none { it.serverUrl.isBlank() })
    }
}
