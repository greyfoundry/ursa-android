package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FleetAggregateDashboardTest {

    @Test
    fun equalWeightsActiveMonitorsAndExcludesPausedMissingAndFailed() {
        val monitors = listOf(
            monitor(1, "Fast interval"),
            monitor(2, "Slow interval"),
            monitor(3, "No checks"),
            monitor(4, "Unavailable"),
            monitor(5, "Paused", active = false),
        )
        val summary = fleetAggregateSummary(
            monitors,
            mapOf(
                1 to listOf(point(up = 4, down = 1, ping = 100.0), point(up = 5, ping = 200.0)),
                2 to listOf(point(up = 1, down = 1, ping = 300.0)),
                3 to emptyList(),
                4 to null,
                5 to listOf(point(up = 100, ping = 1.0)),
            ),
        )

        assertEquals(0.7, summary.fleetUptime!!, 0.0001)
        assertEquals((1400.0 / 9.0 + 300.0) / 2.0, summary.fleetAvgPing!!, 0.0001)
        assertEquals(listOf(2, 1), summary.monitors.map { it.monitorId })
        assertEquals(4, summary.activeMonitorCount)
        assertEquals(2, summary.latencyMonitorCount)
        assertEquals(1, summary.noDataCount)
        assertEquals(1, summary.requestFailedCount)
        assertEquals(1, summary.pausedMonitorCount)
    }

    @Test
    fun downOnlyMonitorContributesUptimeButNotLatency() {
        val summary = fleetAggregateSummary(
            listOf(monitor(1, "Down")),
            mapOf(1 to listOf(point(up = 0, down = 8, ping = null))),
        )

        assertEquals(0.0, summary.fleetUptime!!, 0.0)
        assertNull(summary.fleetAvgPing)
        assertEquals(0, summary.latencyMonitorCount)
        assertEquals(8L, summary.monitors.single().checkCount)
    }

    @Test
    fun noContributorsLeavesAggregateUnavailable() {
        val summary = fleetAggregateSummary(
            listOf(monitor(1, "Paused", active = false), monitor(2, "New")),
            mapOf(2 to emptyList()),
        )

        assertNull(summary.fleetUptime)
        assertNull(summary.fleetAvgPing)
        assertEquals(1, summary.noDataCount)
        assertEquals(1, summary.pausedMonitorCount)
    }

    private fun monitor(id: Int, name: String, active: Boolean = true) = Monitor(
        id = id,
        name = name,
        url = null,
        type = "http",
        active = active,
    )

    private fun point(
        up: Long,
        down: Long = 0,
        ping: Double?,
        timestamp: Long = 1,
    ) = MonitorChartPoint(up, down, ping, timestamp)
}
