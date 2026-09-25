package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus

internal enum class MonitorSort {
    SERVER,
    ATTENTION,
    FAVORITES,
    NAME;

    fun comparator(favorites: Set<Int>): Comparator<Monitor> = when (this) {
        SERVER -> compareBy<Monitor> { !it.active }
            .thenByDescending(Monitor::weight)
            .thenBy { it.name.lowercase() }
        ATTENTION -> compareBy<Monitor> { it.status.attentionPriority }
            .thenBy { if (it.id in favorites) 0 else 1 }
            .thenBy { it.name.lowercase() }
        FAVORITES -> compareBy<Monitor> { if (it.id in favorites) 0 else 1 }
            .thenBy { it.status.attentionPriority }
            .thenBy { it.name.lowercase() }
        NAME -> compareBy { it.name.lowercase() }
    }
}

internal fun monitorInventoryRows(
    monitors: List<Monitor>,
    query: String,
    filter: MonitorViewFilter,
    certificateIds: Set<Int>,
    sort: MonitorSort,
    favorites: Set<Int>,
): List<MonitorHierarchyRow> {
    val visibleIds = monitors.asSequence()
        .filter { it.matchesInventoryQuery(query, monitors) }
        .filter { filter.matches(it, monitors, certificateIds) }
        .mapTo(mutableSetOf(), Monitor::id)
    return monitorHierarchy(monitors, sort.comparator(favorites))
        .filter { it.monitor.id in visibleIds }
}

internal fun Monitor.matchesInventoryQuery(query: String, monitors: List<Monitor>): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    val parentName = parentId?.let { id -> monitors.firstOrNull { it.id == id }?.name }
    return sequenceOf(name, url, type, parentName)
        .filterNotNull()
        .any { it.contains(normalized, ignoreCase = true) } ||
        tags.any { it.contains(normalized, ignoreCase = true) }
}

private val MonitorStatus.attentionPriority: Int
    get() = when (this) {
        MonitorStatus.DOWN -> 0
        MonitorStatus.PENDING -> 1
        MonitorStatus.MAINTENANCE -> 2
        MonitorStatus.UP -> 3
    }
