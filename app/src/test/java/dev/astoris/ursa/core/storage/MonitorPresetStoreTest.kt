package dev.astoris.ursa.core.storage

import dev.astoris.ursa.core.network.MonitorDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPresetStoreTest {

    @Test
    fun codecRoundTripsCanonicalCredentialFreeDraft() {
        val now = 2_000_000_000_000L
        val source = MonitorDraft.create("smtp").copy(
            id = 7,
            name = "Mail relay",
            endpoint = "mail.example",
            port = 587,
            active = true,
            notificationIds = setOf(4),
            parentId = 3,
        )

        val preset = MonitorPresetCodec.create("preset-1", "Mail", source, now)!!
        val restored = MonitorPresetCodec.draft(preset)!!

        assertEquals("preset-1", preset.id)
        assertEquals("Mail", preset.title)
        assertEquals("smtp", restored.type)
        assertEquals("Mail relay", restored.name)
        assertEquals("mail.example", restored.endpoint)
        assertEquals(587, restored.port)
        assertNull(restored.id)
        assertFalse(restored.active)
        assertTrue(restored.notificationIds.isEmpty())
        assertNull(restored.parentId)
    }

    @Test
    fun codecRejectsSecretBearingAndTamperedRecords() {
        assertNull(
            MonitorPresetCodec.create(
                "mqtt",
                "MQTT",
                MonitorDraft.create("mqtt").copy(mqttPassword = "secret"),
            ),
        )

        val valid = MonitorPresetCodec.create("smtp", "SMTP", MonitorDraft.create("smtp"), 10L)!!
        assertNull(MonitorPresetCodec.draft(valid.copy(type = "mqtt")))
        assertNull(MonitorPresetCodec.draft(valid.copy(payload = "not-json")))
    }

    @Test
    fun normalizationKeepsNewestBoundedUniqueRecords() {
        val now = 2_000_000_000_000L
        val draft = MonitorDraft.create("smtp")
        val records = (0..MonitorPresetCodec.MAX_PRESETS + 5).map { index ->
            MonitorPresetCodec.create("preset-$index", "Preset $index", draft, now - index)!!
        } + MonitorPresetCodec.create("preset-0", "Older duplicate", draft, now - 1_000)!!

        val normalized = MonitorPresetCodec.normalized(records, now)

        assertEquals(MonitorPresetCodec.MAX_PRESETS, normalized.size)
        assertEquals("preset-0", normalized.first().id)
        assertEquals(normalized.size, normalized.map(MonitorPreset::id).distinct().size)
    }
}
