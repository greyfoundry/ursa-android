package dev.astoris.ursa.ui.widget

import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.StatusPageView
import kotlinx.serialization.Serializable

@Serializable
enum class WidgetSource { PRIVATE_SERVER, PUBLIC_PAGE }

@Serializable
data class WidgetConfig(
    val source: WidgetSource,
    val sourceId: String,
    val selectedMonitorIds: Set<Int> = emptySet(),
)

@Serializable
data class WidgetRow(
    val id: Int,
    val name: String,
    val status: MonitorStatus,
    val active: Boolean = true,
    val pingMs: Int? = null,
    val uptimePercent: Double? = null,
    val recentStatuses: List<MonitorStatus> = emptyList(),
)

@Serializable
data class WidgetSnapshot(
    val title: String,
    val rows: List<WidgetRow>,
    val updatedAt: Long,
    val urgentCount: Int = rows.count { it.active && it.status.isUrgent },
)

object WidgetData {
    const val MAX_ROWS = 12
    const val MAX_RECENT_STATUSES = 12

    fun visibleRowCount(heightDp: Float, showHistory: Boolean): Int =
        ((heightDp - 48f) / if (showHistory) 26f else 18f).toInt().coerceIn(1, MAX_ROWS)

    fun showsRowHistory(rowCount: Int, hasHistory: Boolean, spacious: Boolean): Boolean =
        spacious && hasHistory && rowCount in 2..4

    fun usesDenseRows(rowCount: Int, heightDp: Float): Boolean =
        rowCount > 6 && visibleRowCount(heightDp, showHistory = false) > 6

    fun mergeLatestHeartbeats(
        monitors: List<Monitor>,
        history: Map<Int, List<Heartbeat>>,
    ): List<Monitor> = monitors.map { monitor ->
        history[monitor.id]?.lastOrNull()?.let { beat ->
            monitor.copy(status = beat.status, ping = beat.ping)
        } ?: monitor
    }

    fun privateSnapshot(
        title: String,
        snapshot: MonitorSnapshot,
        selectedIds: Set<Int>,
    ): WidgetSnapshot = WidgetSnapshot(
        title = title,
        rows = chooseIds(snapshot.monitors, selectedIds, { it.id }, { it.active }, { it.status }).map { monitor ->
            WidgetRow(
                id = monitor.id,
                name = monitor.name,
                status = monitor.status,
                active = monitor.active,
                pingMs = monitor.ping ?: monitor.avgPing,
                uptimePercent = monitor.uptime24h?.times(100.0),
            )
        },
        updatedAt = snapshot.updatedAt,
        urgentCount = snapshot.monitors.count {
            (selectedIds.isEmpty() || it.id in selectedIds) && it.active && it.status.isUrgent
        },
    )

    fun publicSnapshot(view: StatusPageView, selectedIds: Set<Int>): WidgetSnapshot {
        val monitors = view.groups.flatMap { it.monitors }
        return WidgetSnapshot(
            title = view.title,
            rows = chooseIds(monitors, selectedIds, { it.id }, { true }, { it.status }).map { monitor ->
                WidgetRow(
                    id = monitor.id,
                    name = monitor.name,
                    status = monitor.status,
                    pingMs = monitor.recentChecks.lastOrNull()?.ping,
                    uptimePercent = monitor.uptime24h?.times(100.0),
                    recentStatuses = monitor.recentChecks.takeLast(MAX_RECENT_STATUSES).map { it.status },
                )
            },
            updatedAt = view.refreshedAtMillis,
            urgentCount = monitors.count {
                (selectedIds.isEmpty() || it.id in selectedIds) && it.status.isUrgent
            },
        )
    }

    private fun <T> chooseIds(
        values: List<T>,
        selectedIds: Set<Int>,
        id: (T) -> Int,
        active: (T) -> Boolean,
        status: (T) -> MonitorStatus,
    ): List<T> = values
        .filter { selectedIds.isEmpty() || id(it) in selectedIds }
        .sortedWith(compareBy<T> { if (active(it)) status(it).widgetPriority else Int.MAX_VALUE })
        .take(MAX_ROWS)
}

private val MonitorStatus.isUrgent: Boolean
    get() = this == MonitorStatus.DOWN || this == MonitorStatus.PENDING

private val MonitorStatus.widgetPriority: Int
    get() = when (this) {
        MonitorStatus.DOWN -> 0
        MonitorStatus.PENDING -> 1
        MonitorStatus.MAINTENANCE -> 2
        MonitorStatus.UP -> 3
    }
