package dev.astoris.ursa.core.push

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OverallStatusSummaryTest {

    @Test
    fun inactiveMonitorsAreExcludedFromOperationalCounts() {
        val summary = OverallStatusSummary.from(
            listOf(
                monitor(1, MonitorStatus.UP),
                monitor(2, MonitorStatus.DOWN),
                monitor(3, MonitorStatus.PENDING),
                monitor(4, MonitorStatus.MAINTENANCE),
                monitor(5, MonitorStatus.DOWN, active = false),
            ),
        )

        assertEquals(4, summary.total)
        assertEquals(1, summary.up)
        assertEquals(1, summary.down)
        assertEquals(1, summary.pending)
        assertEquals(1, summary.maintenance)
    }

    @Test
    fun emptyAndHealthyFleetsHaveUnambiguousStates() {
        assertEquals(OverallFleetState.NO_DATA, OverallStatusSummary.from(emptyList()).state)
        assertEquals(
            OverallFleetState.HEALTHY,
            OverallStatusSummary.from(listOf(monitor(1, MonitorStatus.UP))).state,
        )
    }

    @Test
    fun downTakesPriorityOverPendingAndMaintenance() {
        assertEquals(
            OverallFleetState.ATTENTION,
            OverallStatusSummary.from(
                listOf(
                    monitor(1, MonitorStatus.PENDING),
                    monitor(2, MonitorStatus.MAINTENANCE),
                    monitor(3, MonitorStatus.DOWN),
                ),
            ).state,
        )
    }

    private fun monitor(id: Int, status: MonitorStatus, active: Boolean = true) = Monitor(
        id = id,
        name = "Monitor $id",
        url = null,
        type = "http",
        active = active,
        status = status,
    )
}
