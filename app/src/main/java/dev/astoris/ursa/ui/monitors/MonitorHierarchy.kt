package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor

data class MonitorHierarchyRow(val monitor: Monitor, val depth: Int)

private val serverMonitorOrder = compareBy<Monitor> { !it.active }
    .thenByDescending(Monitor::weight)
    .thenBy { it.name.lowercase() }

fun monitorHierarchy(
    monitors: List<Monitor>,
    comparator: Comparator<Monitor> = serverMonitorOrder,
): List<MonitorHierarchyRow> {
    val ids = monitors.mapTo(mutableSetOf(), Monitor::id)
    val children = monitors.groupBy { monitor ->
        monitor.parentId?.takeIf { it in ids && it != monitor.id }
    }
    val seen = mutableSetOf<Int>()
    val rows = mutableListOf<MonitorHierarchyRow>()

    fun append(monitor: Monitor, depth: Int) {
        if (!seen.add(monitor.id)) return
        rows += MonitorHierarchyRow(monitor, depth)
        children[monitor.id].orEmpty().sortedWith(comparator).forEach { append(it, depth + 1) }
    }

    children[null].orEmpty().sortedWith(comparator).forEach { append(it, 0) }
    monitors.filterNot { it.id in seen }.sortedWith(comparator).forEach { append(it, 0) }
    return rows
}

fun eligibleParentGroups(monitors: List<Monitor>, monitorId: Int?): List<Monitor> {
    if (monitorId == null) return monitors.filter { it.type == "group" }.sortedWith(serverMonitorOrder)
    val descendants = mutableSetOf<Int>()
    var frontier = setOf(monitorId)
    while (frontier.isNotEmpty()) {
        val next = monitors.filter { it.parentId in frontier }.mapTo(mutableSetOf(), Monitor::id) - descendants
        descendants += next
        frontier = next
    }
    return monitors.filter { it.type == "group" && it.id != monitorId && it.id !in descendants }
        .sortedWith(serverMonitorOrder)
}
