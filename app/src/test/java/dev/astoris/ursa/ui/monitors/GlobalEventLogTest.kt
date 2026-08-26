package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.core.storage.LocalEvent
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalEventLogTest {

    @Test
    fun derivesChronologicalStateRecoveryAndMaintenanceTransitions() {
        val events = heartbeatEventLog(
            monitors = listOf(monitor()),
            history = mapOf(
                1 to listOf(
                    beat(MonitorStatus.UP, "2026-08-25 10:00:00"),
                    beat(MonitorStatus.DOWN, "2026-08-25 10:01:00", "timeout"),
                    beat(MonitorStatus.UP, "2026-08-25 10:02:00"),
                    beat(MonitorStatus.MAINTENANCE, "2026-08-25 10:03:00"),
                    beat(MonitorStatus.UP, "2026-08-25 10:04:00"),
                    beat(MonitorStatus.PENDING, "2026-08-25 10:05:00"),
                ),
            ),
        )

        assertEquals(
            listOf(
                EventLogKind.DOWN,
                EventLogKind.RECOVERED,
                EventLogKind.MAINTENANCE_STARTED,
                EventLogKind.MAINTENANCE_ENDED,
                EventLogKind.UP,
                EventLogKind.PENDING,
            ),
            events.map { it.kind },
        )
        assertEquals("timeout", events.first().detail)
        assertTrue(events.all { it.source == EventLogSource.KUMA })
    }

    @Test
    fun firstHeartbeatAndRepeatedStatusAreNotInventedAsEvents() {
        val events = heartbeatEventLog(
            listOf(monitor()),
            mapOf(
                1 to listOf(
                    beat(MonitorStatus.DOWN, "2026-08-25 10:00:00"),
                    beat(MonitorStatus.DOWN, "2026-08-25 10:01:00"),
                ),
            ),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun combinesDeviceEventsNewestFirstAndFiltersByCategory() {
        val local = listOf(
            LocalEvent(
                id = "pause",
                serverUrl = "https://kuma.example",
                monitorId = 1,
                monitorName = "API",
                kind = LocalEventKind.PAUSED,
                atMillis = 200,
            ),
            LocalEvent(
                id = "alert",
                monitorName = "API",
                kind = LocalEventKind.SLOW_RESPONSE,
                atMillis = 300,
            ),
        )

        val events = combinedEventLog(listOf(monitor()), emptyMap(), local)

        assertEquals(listOf(EventLogKind.SLOW_RESPONSE, EventLogKind.PAUSED), events.map { it.kind })
        assertTrue(events.first().matches(EventLogFilter.ALERTS))
        assertFalse(events.first().matches(EventLogFilter.ACTIONS))
        assertTrue(events.last().matches(EventLogFilter.ACTIONS))
        assertTrue(events.all { it.matches(EventLogFilter.ALL) })
    }

    private fun monitor() = Monitor(
        id = 1,
        name = "API",
        url = "https://api.example",
        type = "http",
        active = true,
    )

    private fun beat(status: MonitorStatus, time: String, message: String? = null) = Heartbeat(
        monitorId = 1,
        status = status,
        time = time,
        msg = message,
        ping = null,
        important = true,
    )
}
