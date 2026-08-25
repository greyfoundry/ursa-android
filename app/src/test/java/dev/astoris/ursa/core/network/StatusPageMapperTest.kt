package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.StatusBeatDto
import dev.astoris.ursa.data.model.StatusGroupDto
import dev.astoris.ursa.data.model.StatusHeartbeatResponse
import dev.astoris.ursa.data.model.StatusIncidentDto
import dev.astoris.ursa.data.model.StatusIncidentHistoryResponse
import dev.astoris.ursa.data.model.StatusMonitorDto
import dev.astoris.ursa.data.model.StatusPageResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPageMapperTest {

    @Test
    fun map_keepsRecentChecksAndDeduplicatesActiveIncidentHistory() {
        val active = incident(1, active = true)
        val resolved = incident(2, active = false)
        val page = StatusPageResponse(
            incidents = listOf(active),
            publicGroupList = listOf(StatusGroupDto("Core", listOf(StatusMonitorDto(7, "API")))),
        )
        val heartbeat = StatusHeartbeatResponse(
            heartbeatList = mapOf(
                "7" to listOf(StatusBeatDto(0, "first", 50), StatusBeatDto(1, "latest", 30)),
            ),
            uptimeList = mapOf("7_24" to 0.99),
        )
        val history = StatusIncidentHistoryResponse(
            ok = true,
            incidents = listOf(active, resolved),
            total = 2,
        )

        val view = StatusPageMapper.map(page, heartbeat, history, 123L)

        val monitor = view.groups.single().monitors.single()
        assertEquals(MonitorStatus.UP, monitor.status)
        assertEquals(listOf(MonitorStatus.DOWN, MonitorStatus.UP), monitor.recentChecks.map { it.status })
        assertEquals(listOf(1, 2), view.incidents.map { it.id })
        assertTrue(view.incidents.first().active)
        assertEquals(2, view.incidentTotal)
        assertEquals(123L, view.refreshedAtMillis)
    }

    private fun incident(id: Int, active: Boolean) = StatusIncidentDto(
        id = id,
        title = "Incident $id",
        active = active,
        pin = active,
        createdDate = "2026-08-2$id 12:00:00",
    )
}
