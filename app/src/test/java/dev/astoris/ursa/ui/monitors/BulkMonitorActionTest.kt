package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkMonitorActionTest {

    @Test
    fun pauseTargetsOnlySelectedActiveMonitors() {
        val plan = planBulkMonitorAction(
            monitors = listOf(monitor(1, active = true), monitor(2, active = false), monitor(3, active = true)),
            selectedIds = setOf(1, 2, 999),
            action = BulkMonitorAction.PAUSE,
        )

        assertEquals(listOf(1), plan.targetIds)
        assertEquals(2, plan.selectedCount)
        assertFalse(plan.fleetWide)
    }

    @Test
    fun resumeTargetsOnlySelectedPausedMonitors() {
        val plan = planBulkMonitorAction(
            monitors = listOf(monitor(1, active = true), monitor(2, active = false)),
            selectedIds = setOf(1, 2),
            action = BulkMonitorAction.RESUME,
        )

        assertEquals(listOf(2), plan.targetIds)
        assertFalse(plan.fleetWide)
    }

    @Test
    fun actionIsFleetWideOnlyWhenEveryMonitorWillChange() {
        val plan = planBulkMonitorAction(
            monitors = listOf(monitor(1, active = true), monitor(2, active = true)),
            selectedIds = setOf(1, 2),
            action = BulkMonitorAction.PAUSE,
        )

        assertTrue(plan.fleetWide)
    }

    @Test
    fun emptyFleetIsNeverTreatedAsFleetWide() {
        val plan = planBulkMonitorAction(emptyList(), setOf(1), BulkMonitorAction.PAUSE)

        assertTrue(plan.targetIds.isEmpty())
        assertEquals(0, plan.selectedCount)
        assertFalse(plan.fleetWide)
    }

    private fun monitor(id: Int, active: Boolean) = Monitor(
        id = id,
        name = "Monitor $id",
        url = null,
        type = "manual",
        active = active,
        status = MonitorStatus.PENDING,
    )
}
