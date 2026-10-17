package dev.astoris.ursa.ui.home

import dev.astoris.ursa.core.storage.LocalEvent
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.monitors.combinedEventLog

internal data class HomeFleetSummary(
    val up: Int,
    val down: Int,
    val pending: Int,
    val maintenance: Int,
    val paused: Int,
    val attention: List<Monitor>,
) {
    val active: Int get() = up + down + pending + maintenance
}

internal data class RecentMonitorChange(
    val monitor: Monitor,
    val atMillis: Long,
)

internal fun homeFleetSummary(monitors: List<Monitor>): HomeFleetSummary {
    val active = monitors.filter(Monitor::active)
    val attention = active.filter { it.status != MonitorStatus.UP }
        .sortedWith(compareBy<Monitor> { it.status.homePriority }.thenBy { it.name.lowercase() })
    return HomeFleetSummary(
        up = active.count { it.status == MonitorStatus.UP },
        down = active.count { it.status == MonitorStatus.DOWN },
        pending = active.count { it.status == MonitorStatus.PENDING },
        maintenance = active.count { it.status == MonitorStatus.MAINTENANCE },
        paused = monitors.count { !it.active },
        attention = attention,
    )
}

internal fun recentMonitorChanges(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    localEvents: List<LocalEvent>,
    limit: Int = 5,
): List<RecentMonitorChange> {
    if (limit <= 0) return emptyList()
    val byId = monitors.associateBy(Monitor::id)
    val scopedLocalEvents = localEvents.filter { it.serverUrl != null }
    return combinedEventLog(monitors, history, scopedLocalEvents)
        .asSequence()
        .mapNotNull { event ->
            val monitor = event.monitorId?.let(byId::get) ?: return@mapNotNull null
            RecentMonitorChange(monitor, event.atMillis)
        }
        .distinctBy { it.monitor.id }
        .take(limit)
        .toList()
}

private val MonitorStatus.homePriority: Int
    get() = when (this) {
        MonitorStatus.DOWN -> 0
        MonitorStatus.PENDING -> 1
        MonitorStatus.MAINTENANCE -> 2
        MonitorStatus.UP -> 3
    }
