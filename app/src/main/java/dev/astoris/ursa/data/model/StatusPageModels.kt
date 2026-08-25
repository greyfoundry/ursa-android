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
    val publicGroupList: List<StatusGroupDto> = emptyList(),
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
data class StatusBeatDto(
    val status: Int = 2,
    val time: String = "",
    val ping: Int? = null,
)

// ---- Domain view (flattened for the UI) ----

data class StatusPageView(
    val title: String,
    val description: String?,
    val groups: List<StatusGroupView>,
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
)
