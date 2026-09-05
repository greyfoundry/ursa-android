package dev.astoris.ursa.ui

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun expandedPaneStartsAtStableTabletWidth() {
        assertFalse(AdaptiveLayout.isExpanded(839.9f))
        assertTrue(AdaptiveLayout.isExpanded(840f))
    }

    @Test
    fun kioskSummaryCountsEveryOperationalState() {
        val summary = KioskData.summarize(
            listOf(
                monitor(1, "Healthy", MonitorStatus.UP),
                monitor(2, "Down", MonitorStatus.DOWN),
                monitor(3, "Pending", MonitorStatus.PENDING),
                monitor(4, "Maintenance", MonitorStatus.MAINTENANCE),
                monitor(5, "Paused", MonitorStatus.UP, active = false),
            ),
        )

        assertEquals(1, summary.up)
        assertEquals(1, summary.down)
        assertEquals(1, summary.pending)
        assertEquals(1, summary.maintenance)
        assertEquals(1, summary.paused)
        assertEquals(MonitorStatus.DOWN, summary.overallStatus)
    }

    @Test
    fun kioskRowsPutAttentionFirstAndStayBounded() {
        val monitors = (1..9).map { monitor(it, "Healthy $it", MonitorStatus.UP) } +
            monitor(20, "Pending", MonitorStatus.PENDING) +
            monitor(21, "Down", MonitorStatus.DOWN) +
            monitor(22, "Paused", MonitorStatus.DOWN, active = false)

        val rows = KioskData.summarize(monitors).rows

        assertEquals(KioskData.MAX_ROWS, rows.size)
        assertEquals(listOf(21, 20), rows.take(2).map(Monitor::id))
        assertEquals(22, rows.last().id)
    }

    @Test
    fun kioskSummaryDistinguishesPendingAndNoActiveFleet() {
        val pending = KioskData.summarize(listOf(monitor(1, "Pending", MonitorStatus.PENDING)))
        val pausedOnly = KioskData.summarize(
            listOf(monitor(2, "Paused", MonitorStatus.UP, active = false)),
        )

        assertEquals(MonitorStatus.PENDING, pending.overallStatus)
        assertEquals(1, pending.pending)
        assertEquals(MonitorStatus.PENDING, pausedOnly.overallStatus)
        assertEquals(0, pausedOnly.up + pausedOnly.down + pausedOnly.pending + pausedOnly.maintenance)
        assertEquals(1, pausedOnly.paused)
    }

    private fun monitor(
        id: Int,
        name: String,
        status: MonitorStatus,
        active: Boolean = true,
    ) = Monitor(
        id = id,
        name = name,
        url = null,
        type = "http",
        active = active,
        status = status,
    )
}
