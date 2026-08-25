package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.StatusCheckView
import dev.astoris.ursa.data.model.StatusGroupView
import dev.astoris.ursa.data.model.StatusHeartbeatResponse
import dev.astoris.ursa.data.model.StatusIncidentHistoryResponse
import dev.astoris.ursa.data.model.StatusIncidentDto
import dev.astoris.ursa.data.model.StatusIncidentView
import dev.astoris.ursa.data.model.StatusMaintenanceView
import dev.astoris.ursa.data.model.StatusMonitorView
import dev.astoris.ursa.data.model.StatusPageResponse
import dev.astoris.ursa.data.model.StatusPageView

/** Maps the three public Kuma responses into one UI-safe snapshot. */
object StatusPageMapper {
    fun map(
        page: StatusPageResponse,
        heartbeat: StatusHeartbeatResponse,
        history: StatusIncidentHistoryResponse?,
        refreshedAtMillis: Long,
    ): StatusPageView {
        val groups = page.publicGroupList.map { group ->
            StatusGroupView(
                name = group.name,
                monitors = group.monitorList.map { monitor ->
                    val checks = heartbeat.heartbeatList[monitor.id.toString()].orEmpty().map { beat ->
                        StatusCheckView(MonitorStatus.from(beat.status), beat.time, beat.ping)
                    }
                    StatusMonitorView(
                        id = monitor.id,
                        name = monitor.name,
                        status = checks.lastOrNull()?.status ?: MonitorStatus.PENDING,
                        uptime24h = heartbeat.uptimeList["${monitor.id}_24"],
                        recentChecks = checks,
                    )
                },
            )
        }
        val incidents = (page.incidents + history?.incidents.orEmpty())
            .distinctBy { it.id }
            .sortedWith(compareByDescending<StatusIncidentDto> { it.active }
                .thenByDescending { it.createdDate })
            .map { incident ->
                StatusIncidentView(
                    id = incident.id,
                    style = incident.style,
                    title = incident.title,
                    content = incident.content,
                    active = incident.active,
                    pinned = incident.pin,
                    createdDate = incident.createdDate,
                    lastUpdatedDate = incident.lastUpdatedDate,
                )
            }
        return StatusPageView(
            title = page.config.title,
            description = page.config.description,
            groups = groups,
            incidents = incidents,
            incidentTotal = maxOf(history?.total ?: 0, incidents.size),
            incidentHasMore = history?.hasMore == true,
            maintenances = page.maintenanceList.map { maintenance ->
                StatusMaintenanceView(maintenance.id, maintenance.title, maintenance.description.orEmpty())
            },
            refreshedAtMillis = refreshedAtMillis,
        )
    }
}
