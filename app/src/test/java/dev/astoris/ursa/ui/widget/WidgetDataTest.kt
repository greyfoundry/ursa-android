package dev.astoris.ursa.ui.widget

import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.StatusCheckView
import dev.astoris.ursa.data.model.StatusGroupView
import dev.astoris.ursa.data.model.StatusMonitorView
import dev.astoris.ursa.data.model.StatusPageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataTest {
    @Test
    fun latestHeartbeatReplacesPlaceholderStatusAndPing() {
        val monitors = listOf(monitor(1, "API", MonitorStatus.PENDING))
        val result = WidgetData.mergeLatestHeartbeats(
            monitors,
            mapOf(
                1 to listOf(
                    Heartbeat(1, MonitorStatus.UP, "2026-10-14T12:00:00Z", null, 42, false),
                ),
            ),
        )

        assertEquals(MonitorStatus.UP, result.single().status)
        assertEquals(42, result.single().ping)
    }

    @Test
    fun privateWidgetShowsChosenAttentionRowsFirst() {
        val monitors = listOf(
            monitor(1, "First", MonitorStatus.UP),
            monitor(2, "Second", MonitorStatus.DOWN),
            monitor(3, "Third", MonitorStatus.PENDING),
        )
        val result = WidgetData.privateSnapshot(
            title = "Home",
            snapshot = MonitorSnapshot(monitors, 123L),
            selectedIds = setOf(3, 1),
        )

        assertEquals(listOf(3, 1), result.rows.map(WidgetRow::id))
        assertEquals(1, result.urgentCount)
        assertEquals(123L, result.updatedAt)
    }

    @Test
    fun publicWidgetIncludesUptimeAndBoundedHeartbeatHistory() {
        val checks = (0 until 20).map { index ->
            StatusCheckView(if (index % 2 == 0) MonitorStatus.UP else MonitorStatus.DOWN, "$index", index)
        }
        val view = StatusPageView(
            title = "Public",
            description = null,
            groups = listOf(
                StatusGroupView(
                    "Core",
                    listOf(StatusMonitorView(9, "API", MonitorStatus.UP, 0.9994, checks)),
                ),
            ),
            incidents = emptyList(),
            incidentTotal = 0,
            incidentHasMore = false,
            maintenances = emptyList(),
            refreshedAtMillis = 456L,
        )

        val result = WidgetData.publicSnapshot(view, setOf(9))

        assertEquals(99.94, result.rows.single().uptimePercent!!, 0.001)
        assertEquals(12, result.rows.single().recentStatuses.size)
        assertTrue(result.rows.single().recentStatuses.first() == MonitorStatus.UP)
    }

    @Test
    fun emptySelectionUsesABoundedDefaultSet() {
        val monitors = (1..20).map { monitor(it, "Monitor $it", MonitorStatus.UP) }
        val result = WidgetData.privateSnapshot(
            title = "Fleet",
            snapshot = MonitorSnapshot(monitors, 1L),
            selectedIds = emptySet(),
        )
        assertEquals(WidgetData.MAX_ROWS, result.rows.size)
        assertEquals(12, WidgetData.MAX_ROWS)
    }

    @Test
    fun privateSnapshotKeepsAllNineReportedRows() {
        val monitors = (1..9).map { monitor(it, "Monitor $it", MonitorStatus.UP) }

        val result = WidgetData.privateSnapshot(
            title = "Fleet",
            snapshot = MonitorSnapshot(monitors, 1L),
            selectedIds = monitors.map(Monitor::id).toSet(),
        )

        assertEquals(9, result.rows.size)
    }

    @Test
    fun visibleRowCountKeepsCompactRowsSafeAndTallRowsComplete() {
        assertEquals(1, WidgetData.visibleRowCount(heightDp = 80f, showHistory = false))
        assertEquals(7, WidgetData.visibleRowCount(heightDp = 180f, showHistory = false))
        assertEquals(9, WidgetData.visibleRowCount(heightDp = 220f, showHistory = false))
        assertEquals(9, WidgetData.visibleRowCount(heightDp = 300f, showHistory = true))
        assertEquals(WidgetData.MAX_ROWS, WidgetData.visibleRowCount(heightDp = 400f, showHistory = false))
    }

    @Test
    fun rowHistoryYieldsToMonitorCapacityForLargerWidgets() {
        assertTrue(WidgetData.showsRowHistory(rowCount = 4, hasHistory = true, spacious = true))
        assertTrue(!WidgetData.showsRowHistory(rowCount = 5, hasHistory = true, spacious = true))
        assertTrue(!WidgetData.showsRowHistory(rowCount = 3, hasHistory = false, spacious = true))
        assertTrue(!WidgetData.showsRowHistory(rowCount = 3, hasHistory = true, spacious = false))
    }

    @Test
    fun denseRowsRequireBothMoreThanSixRowsAndEnoughHeight() {
        assertTrue(!WidgetData.usesDenseRows(rowCount = 6, heightDp = 400f))
        assertTrue(!WidgetData.usesDenseRows(rowCount = 9, heightDp = 95f))
        assertTrue(WidgetData.usesDenseRows(rowCount = 7, heightDp = 180f))
        assertTrue(WidgetData.usesDenseRows(rowCount = WidgetData.MAX_ROWS, heightDp = 400f))
    }

    @Test
    fun pausedAndMaintenanceRowsAreNotUrgent() {
        val monitors = listOf(
            monitor(1, "Paused down", MonitorStatus.DOWN, active = false),
            monitor(2, "Maintenance", MonitorStatus.MAINTENANCE),
            monitor(3, "Down", MonitorStatus.DOWN),
        )

        val result = WidgetData.privateSnapshot(
            title = "Fleet",
            snapshot = MonitorSnapshot(monitors, 1L),
            selectedIds = emptySet(),
        )

        assertEquals(1, result.urgentCount)
        assertEquals(listOf(3, 2, 1), result.rows.map(WidgetRow::id))
    }

    private fun monitor(id: Int, name: String, status: MonitorStatus, active: Boolean = true) = Monitor(
        id = id,
        name = name,
        url = null,
        type = "http",
        active = active,
        status = status,
        ping = id * 10,
        uptime24h = 0.98,
    )
}
