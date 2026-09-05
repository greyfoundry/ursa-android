package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorHierarchyTest {

    @Test
    fun serverHierarchyKeepsChildrenBelowParentsAndUsesWeight() {
        val rows = monitorHierarchy(
            listOf(
                monitor(1, "Group", "group", weight = 5),
                monitor(2, "Lower", "http", parent = 1, weight = 1),
                monitor(3, "Higher", "http", parent = 1, weight = 9),
                monitor(4, "Root", "http", weight = 2),
            ),
        )

        assertEquals(listOf(1, 3, 2, 4), rows.map { it.monitor.id })
        assertEquals(listOf(0, 1, 1, 0), rows.map(MonitorHierarchyRow::depth))
    }

    @Test
    fun eligibleParentsExcludeSelfAndDescendants() {
        val monitors = listOf(
            monitor(1, "Root", "group"),
            monitor(2, "Nested", "group", parent = 1),
            monitor(3, "Child", "http", parent = 2),
            monitor(4, "Other", "group"),
        )

        assertEquals(setOf(4), eligibleParentGroups(monitors, 1).mapTo(mutableSetOf(), Monitor::id))
        assertEquals(setOf(1, 2, 4), eligibleParentGroups(monitors, 3).mapTo(mutableSetOf(), Monitor::id))
    }

    @Test
    fun malformedParentCycleStillReturnsEveryMonitorOnce() {
        val rows = monitorHierarchy(
            listOf(
                monitor(1, "One", "group", parent = 2),
                monitor(2, "Two", "group", parent = 1),
            ),
        )

        assertEquals(setOf(1, 2), rows.mapTo(mutableSetOf()) { it.monitor.id })
        assertEquals(2, rows.size)
    }

    private fun monitor(
        id: Int,
        name: String,
        type: String,
        parent: Int? = null,
        weight: Int = 0,
    ) = Monitor(id, name, null, type, true, parentId = parent, weight = weight, status = MonitorStatus.UP)
}
