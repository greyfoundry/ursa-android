package dev.astoris.ursa.ui.browse

import dev.astoris.ursa.ui.monitors.MonitorViewFilter

internal sealed interface BrowseSearchAction {
    data class Monitor(val id: Int) : BrowseSearchAction
    data class MonitorFilter(val filter: MonitorViewFilter) : BrowseSearchAction
    data object Monitors : BrowseSearchAction
    data object Maintenance : BrowseSearchAction
    data object StatusPages : BrowseSearchAction
    data class StatusPage(val id: String) : BrowseSearchAction
    data object Servers : BrowseSearchAction
    data class Tool(val tool: BrowseTool) : BrowseSearchAction
}

internal data class BrowseSearchEntry(
    val id: String,
    val title: String,
    val resourceType: String,
    val server: String,
    val terms: List<String>,
    val action: BrowseSearchAction,
)

internal fun browseSearchResults(
    entries: List<BrowseSearchEntry>,
    query: String,
    limit: Int = 50,
): List<BrowseSearchEntry> {
    val needle = query.trim()
    if (needle.isEmpty() || limit <= 0) return emptyList()
    return entries.asSequence()
        .mapNotNull { entry -> entry.searchRank(needle)?.let { rank -> rank to entry } }
        .sortedWith(compareBy<Pair<Int, BrowseSearchEntry>> { it.first }.thenBy { it.second.title.lowercase() })
        .take(limit)
        .map { it.second }
        .toList()
}

private fun BrowseSearchEntry.searchRank(query: String): Int? = when {
    title.equals(query, ignoreCase = true) -> 0
    title.startsWith(query, ignoreCase = true) -> 1
    title.contains(query, ignoreCase = true) -> 2
    resourceType.contains(query, ignoreCase = true) -> 3
    terms.any { it.contains(query, ignoreCase = true) } -> 4
    server.contains(query, ignoreCase = true) -> 5
    else -> null
}
