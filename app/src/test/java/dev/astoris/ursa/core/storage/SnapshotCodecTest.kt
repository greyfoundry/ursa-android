package dev.astoris.ursa.core.storage

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotCodecTest {

    private val sample = MonitorSnapshot(
        monitors = listOf(
            Monitor(id = 1, name = "API", url = "https://api.example.com", type = "http",
                active = true, tags = listOf("prod"), status = MonitorStatus.UP, ping = 42,
                avgPing = 40, uptime24h = 0.999),
            Monitor(id = 2, name = "DB", url = null, type = "port", active = false,
                status = MonitorStatus.DOWN),
        ),
        updatedAt = 1_720_000_000_000,
    )

    @Test fun round_trips() {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(sample))!!
        assertEquals(sample, decoded)
    }

    @Test fun preserves_status_and_nullable_url() {
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(sample))!!
        assertEquals(MonitorStatus.DOWN, decoded.monitors[1].status)
        assertNull(decoded.monitors[1].url)
        assertEquals(1_720_000_000_000, decoded.updatedAt)
    }

    @Test fun empty_monitor_list_round_trips() {
        val empty = MonitorSnapshot(emptyList(), 0)
        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(empty))!!
        assertTrue(decoded.monitors.isEmpty())
    }

    @Test fun garbage_decodes_to_null() {
        assertNull(SnapshotCodec.decode("not json"))
        assertNull(SnapshotCodec.decode("{}")) // missing required fields
    }

    @Test fun null_or_blank_decodes_to_null() {
        assertNull(SnapshotCodec.decode(null))
        assertNull(SnapshotCodec.decode(""))
        assertNull(SnapshotCodec.decode("   "))
    }
}
