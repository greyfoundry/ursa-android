package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor

enum class BulkMonitorAction { PAUSE, RESUME }

data class BulkMonitorPlan(
    val targetIds: List<Int>,
    val selectedCount: Int,
    val fleetWide: Boolean,
)

fun planBulkMonitorAction(
    monitors: List<Monitor>,
    selectedIds: Set<Int>,
    action: BulkMonitorAction,
): BulkMonitorPlan {
    val selected = monitors.filter { it.id in selectedIds }
    val targetIds = selected
        .filter { monitor ->
            when (action) {
                BulkMonitorAction.PAUSE -> monitor.active
                BulkMonitorAction.RESUME -> !monitor.active
            }
        }
        .map(Monitor::id)
        .sorted()
    return BulkMonitorPlan(
        targetIds = targetIds,
        selectedCount = selected.size,
        fleetWide = monitors.isNotEmpty() && targetIds.size == monitors.size,
    )
}
