package dev.astoris.ursa.data.model

import kotlinx.serialization.Serializable

/** A locally saved public status page. Sensitive reverse-proxy headers stay on the matching server. */
@Serializable
data class SavedStatusPage(
    val id: String,
    val name: String,
    val url: String,
    val slug: String,
    val insecure: Boolean = false,
    val favorite: Boolean = false,
    val order: Int = 0,
)

// ---- Wire DTOs (verified against Uptime Kuma 2.5.0 REST) ----
// GET /api/status-page/<slug>  and  /api/status-page/heartbeat/<slug>

@Serializable
data class StatusPageResponse(
    val config: StatusPageConfig = StatusPageConfig(),
    val incidents: List<StatusIncidentDto> = emptyList(),
    val publicGroupList: List<StatusGroupDto> = emptyList(),
    val maintenanceList: List<StatusMaintenanceDto> = emptyList(),
)

@Serializable
data class StatusPageConfig(
    val title: String = "",
    val description: String? = null,
)

@Serializable
data class StatusGroupDto(
    val name: String = "",
    val monitorList: List<StatusMonitorDto> = emptyList(),
)

@Serializable
data class StatusMonitorDto(
    val id: Int,
    val name: String = "",
)

@Serializable
data class StatusHeartbeatResponse(
    val heartbeatList: Map<String, List<StatusBeatDto>> = emptyMap(),
    val uptimeList: Map<String, Double> = emptyMap(),
)

@Serializable
data class StatusPageEntryResponse(
    val type: String = "",
    val entryPage: String? = null,
    val statusPageSlug: String? = null,
)

@Serializable
data class StatusBeatDto(
    val status: Int = 2,
    val time: String = "",
    val ping: Int? = null,
)

@Serializable
data class StatusIncidentDto(
    val id: Int,
    val style: String = "warning",
    val title: String = "",
    val content: String = "",
    val pin: Boolean = false,
    val active: Boolean = false,
    val createdDate: String = "",
    val lastUpdatedDate: String? = null,
)

@Serializable
data class StatusIncidentHistoryResponse(
    val ok: Boolean = false,
    val incidents: List<StatusIncidentDto> = emptyList(),
    val total: Int = 0,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class StatusMaintenanceDto(
    val id: Int,
    val title: String = "",
    val description: String? = null,
)

// ---- Domain view (flattened for the UI) ----

data class StatusPageView(
    val title: String,
    val description: String?,
    val groups: List<StatusGroupView>,
    val incidents: List<StatusIncidentView>,
    val incidentTotal: Int,
    val incidentHasMore: Boolean,
    val maintenances: List<StatusMaintenanceView>,
    val refreshedAtMillis: Long,
)

data class StatusGroupView(
    val name: String,
    val monitors: List<StatusMonitorView>,
)

data class StatusMonitorView(
    val id: Int,
    val name: String,
    val status: MonitorStatus,
    val uptime24h: Double?,
    val recentChecks: List<StatusCheckView>,
)

data class StatusCheckView(
    val status: MonitorStatus,
    val time: String,
    val ping: Int?,
)

data class StatusIncidentView(
    val id: Int,
    val style: String,
    val title: String,
    val content: String,
    val active: Boolean,
    val pinned: Boolean,
    val createdDate: String,
    val lastUpdatedDate: String?,
)

data class StatusMaintenanceView(
    val id: Int,
    val title: String,
    val description: String,
)
