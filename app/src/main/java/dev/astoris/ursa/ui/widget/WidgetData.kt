package dev.astoris.ursa.ui.widget

import dev.astoris.ursa.core.storage.MonitorSnapshot
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
    val pingMs: Int? = null,
    val uptimePercent: Double? = null,
    val recentStatuses: List<MonitorStatus> = emptyList(),
)

@Serializable
data class WidgetSnapshot(
    val title: String,
    val rows: List<WidgetRow>,
    val updatedAt: Long,
)

object WidgetData {
    const val MAX_ROWS = 6
    const val MAX_RECENT_STATUSES = 12

    fun privateSnapshot(
        title: String,
        snapshot: MonitorSnapshot,
        selectedIds: Set<Int>,
    ): WidgetSnapshot = WidgetSnapshot(
        title = title,
        rows = chooseIds(snapshot.monitors, selectedIds) { it.id }.map { monitor ->
            WidgetRow(
                id = monitor.id,
                name = monitor.name,
                status = monitor.status,
                pingMs = monitor.ping ?: monitor.avgPing,
                uptimePercent = monitor.uptime24h?.times(100.0),
            )
        },
        updatedAt = snapshot.updatedAt,
    )

    fun publicSnapshot(view: StatusPageView, selectedIds: Set<Int>): WidgetSnapshot {
        val monitors = view.groups.flatMap { it.monitors }
        return WidgetSnapshot(
            title = view.title,
            rows = chooseIds(monitors, selectedIds) { it.id }.map { monitor ->
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
        )
    }

    private fun <T> chooseIds(values: List<T>, selectedIds: Set<Int>, id: (T) -> Int): List<T> =
        if (selectedIds.isEmpty()) {
            values.take(MAX_ROWS)
        } else {
            values.filter { id(it) in selectedIds }.take(MAX_ROWS)
        }
}
