package dev.astoris.ursa.ui.home

import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetMonitorCounts
import dev.astoris.ursa.data.model.FleetRefreshError
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerSnapshot
import dev.astoris.ursa.data.model.FleetSnapshot
import dev.astoris.ursa.data.model.FleetSnapshotSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FleetHomeDataTest {
    @Test
    fun summarySeparatesUrgentMaintenanceAndCoverage() {
        val snapshot = FleetSnapshot(
            servers = listOf(
                available("one", down = 2, pending = 1, maintenance = 3, capturedAt = 100),
                available("two", up = 4, capturedAt = 200, freshness = FleetFreshness.STALE),
                FleetServerSnapshot(
                    serverUrl = "three",
                    displayName = "Three",
                    isActiveServer = false,
                    availability = FleetServerAvailability.NO_CACHE,
                    refreshAttemptedAtMillis = 300,
                    refreshError = FleetRefreshError.TIMED_OUT,
                ),
            ),
            counts = FleetMonitorCounts(
                total = 10,
                active = 10,
                up = 4,
                down = 2,
                pending = 1,
                maintenance = 3,
                paused = 0,
            ),
            loadedAtMillis = 400,
        )

        val summary = fleetHomeSummary(snapshot)

        assertEquals(3, summary.totalServers)
        assertEquals(2, summary.availableServers)
        assertEquals(1, summary.unavailableServers)
        assertEquals(1, summary.staleServers)
        assertEquals(2, summary.affectedServers)
        assertEquals(3, summary.urgentMonitors)
        assertEquals(3, summary.maintenanceMonitors)
        assertEquals(10, summary.totalMonitors)
        assertEquals(300L, summary.lastRefreshAtMillis)
        assertEquals(200L, summary.newestSnapshotAtMillis)
    }

    @Test
    fun summaryDoesNotPresentMissingDataAsZeroMonitors() {
        val summary = fleetHomeSummary(
            FleetSnapshot(
                servers = listOf(
                    FleetServerSnapshot(
                        serverUrl = "missing",
                        displayName = "Missing",
                        isActiveServer = true,
                        availability = FleetServerAvailability.NO_CACHE,
                    ),
                ),
                counts = null,
                loadedAtMillis = 100,
            ),
        )

        assertNull(summary.totalMonitors)
        assertEquals(1, summary.affectedServers)
    }

    private fun available(
        url: String,
        up: Int = 0,
        down: Int = 0,
        pending: Int = 0,
        maintenance: Int = 0,
        capturedAt: Long,
        freshness: FleetFreshness = FleetFreshness.RECENT,
    ): FleetServerSnapshot {
        val active = up + down + pending + maintenance
        return FleetServerSnapshot(
            serverUrl = url,
            displayName = url.replaceFirstChar(Char::uppercase),
            isActiveServer = false,
            availability = FleetServerAvailability.AVAILABLE,
            source = FleetSnapshotSource.CACHE,
            freshness = freshness,
            capturedAtMillis = capturedAt,
            ageMillis = 0,
            counts = FleetMonitorCounts(active, active, up, down, pending, maintenance, 0),
        )
    }
}
