package dev.astoris.ursa.ui.home

import dev.astoris.ursa.core.storage.LocalEvent
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeDataTest {
    @Test
    fun summarySeparatesActiveStatesFromPausedMonitors() {
        val summary = homeFleetSummary(
            listOf(
                monitor(1, "Up", MonitorStatus.UP),
                monitor(2, "Down", MonitorStatus.DOWN),
                monitor(3, "Pending", MonitorStatus.PENDING),
                monitor(4, "Maintenance", MonitorStatus.MAINTENANCE),
                monitor(5, "Paused", MonitorStatus.DOWN, active = false),
            ),
        )

        assertEquals(4, summary.active)
        assertEquals(1, summary.up)
        assertEquals(1, summary.down)
        assertEquals(1, summary.pending)
        assertEquals(1, summary.maintenance)
        assertEquals(1, summary.paused)
        assertEquals(listOf(2, 3, 4), summary.attention.map { it.id })
    }

    @Test
    fun recentChangesAreNewestDistinctKnownMonitors() {
        val monitors = listOf(
            monitor(1, "One", MonitorStatus.UP),
            monitor(2, "Two", MonitorStatus.DOWN),
        )
        val history = mapOf(
            1 to listOf(
                heartbeat(1, MonitorStatus.DOWN, "2026-09-25 10:00:00"),
                heartbeat(1, MonitorStatus.UP, "2026-09-25 10:05:00"),
            ),
        )
        val local = listOf(
            LocalEvent(
                id = "newer",
                serverUrl = "http://server",
                monitorId = 2,
                monitorName = "Two",
                kind = LocalEventKind.PUSH_ALERT,
                atMillis = 1_790_331_000_000L,
            ),
            LocalEvent(
                id = "unknown",
                monitorId = 99,
                monitorName = "Removed",
                kind = LocalEventKind.PAUSED,
                atMillis = 1_790_331_100_000L,
            ),
        )

        val recent = recentMonitorChanges(monitors, history, local)

        assertEquals(listOf(2, 1), recent.map { it.monitor.id })
    }

    @Test
    fun recentChangesRespectLimit() {
        val monitors = listOf(monitor(1, "One", MonitorStatus.UP))
        val events = listOf(
            LocalEvent(
                id = "event",
                serverUrl = "http://server",
                monitorId = 1,
                monitorName = "One",
                kind = LocalEventKind.RESUMED,
                atMillis = 100,
            ),
        )

        assertEquals(emptyList<RecentMonitorChange>(), recentMonitorChanges(monitors, emptyMap(), events, 0))
        assertEquals(1, recentMonitorChanges(monitors, emptyMap(), events, 1).size)
    }

    @Test
    fun providerNeutralEventsNeverAttachByNumericMonitorId() {
        val monitors = listOf(monitor(7, "Current server", MonitorStatus.UP))
        val providerNeutral = listOf(
            LocalEvent(
                id = "provider-neutral",
                serverUrl = null,
                monitorId = 7,
                monitorName = "Another server",
                kind = LocalEventKind.PUSH_ALERT,
                atMillis = 100,
            ),
        )

        assertEquals(
            emptyList<RecentMonitorChange>(),
            recentMonitorChanges(monitors, emptyMap(), providerNeutral),
        )
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

    private fun heartbeat(id: Int, status: MonitorStatus, time: String) = Heartbeat(
        monitorId = id,
        status = status,
        time = time,
        msg = null,
        ping = 10,
        important = true,
    )
}
