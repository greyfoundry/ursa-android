package dev.astoris.ursa.ui.tile

import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetMonitorCounts
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerSnapshot
import dev.astoris.ursa.data.model.FleetSnapshot
import dev.astoris.ursa.data.model.FleetSnapshotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusTileDataTest {
    @Test
    fun urgentFleetTargetsFirstAffectedServer() {
        val first = available("one", counts(down = 1))
        val second = available("two", counts(pending = 2))

        val result = statusTileData(snapshot(first, second))

        assertEquals(StatusTileKind.URGENT, result.kind)
        assertEquals(3, result.urgentMonitors)
        assertEquals(2, result.affectedServers)
        assertEquals("one", result.targetServerUrl)
    }

    @Test
    fun lockedFleetExposesNoCountsOrDestination() {
        val result = statusTileData(
            FleetSnapshot(emptyList(), null, loadedAtMillis = 1L, isLocked = true, hiddenServerCount = 2),
        )

        assertEquals(StatusTileKind.LOCKED, result.kind)
        assertEquals(0, result.urgentMonitors)
        assertNull(result.targetServerUrl)
    }

    @Test
    fun staleAndMissingDataStayDistinct() {
        val stale = available("stale", counts(up = 1), FleetFreshness.STALE)
        assertEquals(StatusTileKind.STALE, statusTileData(snapshot(stale)).kind)

        val missing = FleetServerSnapshot(
            serverUrl = "missing",
            displayName = "Missing",
            isActiveServer = false,
            availability = FleetServerAvailability.NO_CACHE,
        )
        assertEquals(
            StatusTileKind.NO_DATA,
            statusTileData(FleetSnapshot(listOf(missing), null, loadedAtMillis = 1L)).kind,
        )
    }

    private fun snapshot(vararg servers: FleetServerSnapshot): FleetSnapshot = FleetSnapshot(
        servers = servers.toList(),
        counts = servers.mapNotNull(FleetServerSnapshot::counts)
            .fold(FleetMonitorCounts.EMPTY, FleetMonitorCounts::plus),
        loadedAtMillis = 1L,
    )

    private fun available(
        url: String,
        counts: FleetMonitorCounts,
        freshness: FleetFreshness = FleetFreshness.RECENT,
    ) = FleetServerSnapshot(
        serverUrl = url,
        displayName = url,
        isActiveServer = false,
        availability = FleetServerAvailability.AVAILABLE,
        source = FleetSnapshotSource.CACHE,
        freshness = freshness,
        capturedAtMillis = 1L,
        ageMillis = 0L,
        counts = counts,
    )

    private fun counts(
        up: Int = 0,
        down: Int = 0,
        pending: Int = 0,
        maintenance: Int = 0,
    ) = FleetMonitorCounts(
        total = up + down + pending + maintenance,
        active = up + down + pending + maintenance,
        up = up,
        down = down,
        pending = pending,
        maintenance = maintenance,
        paused = 0,
    )
}
