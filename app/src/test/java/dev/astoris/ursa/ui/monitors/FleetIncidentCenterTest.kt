package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetIncidentCenterTest {

    @Test
    fun buildsActiveAndResolvedIncidentsAcrossMonitors() {
        val active = monitor(1, "API", MonitorStatus.DOWN)
        val recovered = monitor(2, "Database", MonitorStatus.UP)

        val incidents = fleetIncidents(
            listOf(active, recovered),
            mapOf(
                1 to listOf(beat(1, MonitorStatus.UP, "10:00"), beat(1, MonitorStatus.DOWN, "10:05")),
                2 to listOf(
                    beat(2, MonitorStatus.UP, "09:00"),
                    beat(2, MonitorStatus.DOWN, "09:05", "Timeout"),
                    beat(2, MonitorStatus.UP, "09:10"),
                ),
            ),
        )

        assertEquals(2, incidents.size)
        assertTrue(incidents.first().active)
        assertEquals("API", incidents.first().monitorName)
        assertFalse(incidents.last().active)
        assertEquals("09:10", incidents.last().resolvedAt)
        assertEquals("Timeout", incidents.last().message)
    }

    @Test
    fun currentDownMonitorWithoutHistoryStillAppears() {
        val incident = fleetIncidents(listOf(monitor(3, "Unknown start", MonitorStatus.DOWN)), emptyMap()).single()

        assertTrue(incident.active)
        assertNull(incident.startedAt)
    }

    private fun monitor(id: Int, name: String, status: MonitorStatus) = Monitor(
        id = id,
        name = name,
        url = null,
        type = "http",
        active = true,
        status = status,
    )

    private fun beat(id: Int, status: MonitorStatus, time: String, message: String? = null) = Heartbeat(
        monitorId = id,
        status = status,
        time = time,
        msg = message,
        ping = null,
        important = true,
    )
}
