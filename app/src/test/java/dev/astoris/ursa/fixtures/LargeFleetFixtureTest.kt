package dev.astoris.ursa.fixtures

import dev.astoris.ursa.core.storage.SnapshotCodec
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFleetFixtureTest {

    @Test fun reference_sizes_are_deterministic_and_round_trip() {
        LargeFleetFixtures.REFERENCE_SIZES.forEach { size ->
            val first = LargeFleetFixtures.snapshot(size)
            val second = LargeFleetFixtures.snapshot(size)
            val decoded = SnapshotCodec.decode(SnapshotCodec.encode(first))

            assertEquals(first, second)
            assertEquals(first, decoded)
            assertEquals(size, first.monitors.size)
            assertEquals(size, first.monitors.map { it.id }.distinct().size)
            assertTrue(first.monitors.any { it.status == MonitorStatus.DOWN })
            assertTrue(first.monitors.any { !it.active })
            assertTrue(first.monitors.any { it.type == "group" })
        }
    }

    @Test fun multi_server_reference_has_scoped_ids_and_mixed_freshness() {
        val fleet = LargeFleetFixtures.multiServer()

        assertEquals(4, fleet.size)
        assertEquals(1_000, fleet.values.sumOf { it.monitors.size })
        assertEquals(4, fleet.values.map { it.updatedAt }.distinct().size)
        fleet.forEach { (url, snapshot) ->
            assertTrue(url.startsWith("https://kuma-"))
            assertEquals((1..250).toList(), snapshot.monitors.map { it.id })
        }
    }

    @Test fun group_relationships_always_target_a_present_group() {
        val snapshot = LargeFleetFixtures.snapshot(1_000)
        val groupIds = snapshot.monitors.filter { it.type == "group" }.mapTo(mutableSetOf()) { it.id }

        snapshot.monitors.filter { it.parentId != null }.forEach { monitor ->
            assertTrue("Missing parent for monitor ${monitor.id}", monitor.parentId in groupIds)
        }
    }
}
