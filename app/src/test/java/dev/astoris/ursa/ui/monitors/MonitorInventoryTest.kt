package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorInventoryTest {
    private val monitors = listOf(
        monitor(1, "Production", "group", MonitorStatus.UP, weight = 10),
        monitor(2, "Website", "http", MonitorStatus.UP, parentId = 1, tags = listOf("public")),
        monitor(3, "Database", "postgres", MonitorStatus.DOWN, tags = listOf("internal")),
        monitor(4, "Paused API", "http", MonitorStatus.UP, active = false),
    )

    @Test
    fun queryMatchesOperationalMetadataAndParentName() {
        assertTrue(monitors[1].matchesInventoryQuery("public", monitors))
        assertTrue(monitors[1].matchesInventoryQuery("production", monitors))
        assertTrue(monitors[2].matchesInventoryQuery("postgres", monitors))
        assertFalse(monitors[2].matchesInventoryQuery("website", monitors))
    }

    @Test
    fun inventoryComposesQueryFilterHierarchyAndSort() {
        val rows = monitorInventoryRows(
            monitors = monitors,
            query = "",
            filter = MonitorViewFilter(activity = ActivityFilter.ACTIVE),
            certificateIds = emptySet(),
            sort = MonitorSort.ATTENTION,
            favorites = setOf(2),
        )

        assertEquals(listOf(3, 1, 2), rows.map { it.monitor.id })
        assertEquals(listOf(0, 0, 1), rows.map { it.depth })
    }

    @Test
    fun serverOrderDoesNotDependOnLiveStatus() {
        val before = monitorInventoryRows(
            monitors = monitors,
            query = "",
            filter = MonitorViewFilter(activity = ActivityFilter.ACTIVE),
            certificateIds = emptySet(),
            sort = MonitorSort.SERVER,
            favorites = emptySet(),
        )
        val after = monitorInventoryRows(
            monitors = monitors.map { it.copy(status = MonitorStatus.DOWN) },
            query = "",
            filter = MonitorViewFilter(activity = ActivityFilter.ACTIVE),
            certificateIds = emptySet(),
            sort = MonitorSort.SERVER,
            favorites = emptySet(),
        )

        assertEquals(before.map { it.monitor.id }, after.map { it.monitor.id })
    }

    private fun monitor(
        id: Int,
        name: String,
        type: String,
        status: MonitorStatus,
        active: Boolean = true,
        parentId: Int? = null,
        weight: Int = 0,
        tags: List<String> = emptyList(),
    ) = Monitor(
        id = id,
        name = name,
        url = if (type == "http") "https://${name.lowercase().replace(' ', '-')}.example" else null,
        type = type,
        active = active,
        tags = tags,
        parentId = parentId,
        weight = weight,
        status = status,
    )
}
