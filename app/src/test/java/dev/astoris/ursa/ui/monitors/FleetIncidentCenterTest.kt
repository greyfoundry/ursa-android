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

    @Test
    fun durationUsesUtcHeartbeatTimesAndLiveClock() {
        val resolved = FleetIncident(1, "API", "2026-08-25 10:00:00.000", "2026-08-25 10:01:05.000", null)
        val active = FleetIncident(1, "API", "2026-08-25 10:00:00", null, null)
        val now = kumaUtcMillisOrNull("2026-08-25 11:02:03")!!

        assertEquals(65_000L, incidentDurationMillis(resolved, now))
        assertEquals("1m 5s", compactDuration(incidentDurationMillis(resolved, now)!!))
        assertEquals("1h 2m", compactDuration(incidentDurationMillis(active, now)!!))
        assertNull(kumaUtcMillisOrNull("not a Kuma time"))
    }

    @Test
    fun reliabilityCalculatesDowntimeMttrAndFlakiestMonitorForWindow() {
        val now = kumaUtcMillisOrNull("2026-08-25 12:00:00")!!
        val api = monitor(1, "API", MonitorStatus.DOWN)
        val database = monitor(2, "Database", MonitorStatus.UP)
        val history = mapOf(
            1 to listOf(
                beat(1, MonitorStatus.UP, "2026-08-25 05:00:00"),
                beat(1, MonitorStatus.DOWN, "2026-08-25 07:00:00"),
                beat(1, MonitorStatus.UP, "2026-08-25 07:30:00"),
                beat(1, MonitorStatus.DOWN, "2026-08-25 10:00:00"),
            ),
            2 to listOf(
                beat(2, MonitorStatus.UP, "2026-08-25 05:00:00"),
                beat(2, MonitorStatus.DOWN, "2026-08-25 09:00:00"),
                beat(2, MonitorStatus.UP, "2026-08-25 09:15:00"),
            ),
        )

        val summary = fleetReliabilitySummary(
            monitors = listOf(api, database),
            history = history,
            incidents = fleetIncidents(listOf(api, database), history),
            windowHours = 6,
            nowMillis = now,
        )

        assertEquals(9_900_000L, summary.observedDowntimeMillis)
        assertEquals(1_350_000L, summary.meanTimeToRecoveryMillis)
        assertEquals(MonitorFlakiness(1, "API", 2), summary.flakiestMonitor)
        assertEquals(0, summary.incompleteMonitorCount)
        assertEquals(2, summary.activeMonitorCount)
    }

    @Test
    fun reliabilityClipsCarryInOutageAndMarksPartialHistory() {
        val now = kumaUtcMillisOrNull("2026-08-25 12:00:00")!!
        val api = monitor(1, "API", MonitorStatus.UP)
        val history = mapOf(
            1 to listOf(
                beat(1, MonitorStatus.DOWN, "2026-08-25 05:30:00"),
                beat(1, MonitorStatus.UP, "2026-08-25 06:30:00"),
            ),
        )

        val summary = fleetReliabilitySummary(
            monitors = listOf(api),
            history = history,
            incidents = fleetIncidents(listOf(api), history),
            windowHours = 6,
            nowMillis = now,
        )

        assertEquals(1_800_000L, summary.observedDowntimeMillis)
        assertNull(summary.meanTimeToRecoveryMillis)
        assertNull(summary.flakiestMonitor)
        assertEquals(0, summary.incompleteMonitorCount)
        assertTrue(incidentOverlapsWindow(fleetIncidents(listOf(api), history).single(), now - 21_600_000L, now))

        val recentOnly = mapOf(1 to listOf(beat(1, MonitorStatus.UP, "2026-08-25 11:00:00")))
        val partial = fleetReliabilitySummary(listOf(api), recentOnly, emptyList(), 6, now)
        assertEquals(1, partial.incompleteMonitorCount)

        val paused = api.copy(active = false)
        val pausedSummary = fleetReliabilitySummary(
            listOf(paused),
            history,
            fleetIncidents(listOf(paused), history),
            24 * 30,
            now,
        )
        assertEquals(0L, pausedSummary.observedDowntimeMillis)
        assertEquals(0, pausedSummary.activeMonitorCount)
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
