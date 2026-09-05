package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ActivityFilter { ACTIVE, PAUSED, ALL }

@Serializable
enum class CertificateFilter { ANY, HAS_CERTIFICATE, NO_CERTIFICATE }

@Serializable
data class MonitorViewFilter(
    val statuses: Set<MonitorStatus> = emptySet(),
    val tags: Set<String> = emptySet(),
    val groups: Set<Int> = emptySet(),
    val types: Set<String> = emptySet(),
    val certificate: CertificateFilter = CertificateFilter.ANY,
    val activity: ActivityFilter = ActivityFilter.ALL,
) {
    val isDefault: Boolean get() = this == MonitorViewFilter()

    fun matches(monitor: Monitor, all: List<Monitor>, certificateIds: Set<Int>): Boolean {
        if (statuses.isNotEmpty() && monitor.status !in statuses) return false
        if (tags.isNotEmpty() && monitor.tags.none(tags::contains)) return false
        if (types.isNotEmpty() && monitor.type !in types) return false
        if (groups.isNotEmpty() && !belongsToAnyGroup(monitor, all, groups)) return false
        if (certificate == CertificateFilter.HAS_CERTIFICATE && monitor.id !in certificateIds) return false
        if (certificate == CertificateFilter.NO_CERTIFICATE && monitor.id in certificateIds) return false
        return when (activity) {
            ActivityFilter.ACTIVE -> monitor.active
            ActivityFilter.PAUSED -> !monitor.active
            ActivityFilter.ALL -> true
        }
    }

    private fun belongsToAnyGroup(monitor: Monitor, all: List<Monitor>, targets: Set<Int>): Boolean {
        val byId = all.associateBy(Monitor::id)
        var parent = monitor.parentId
        val visited = mutableSetOf<Int>()
        while (parent != null && visited.add(parent)) {
            if (parent in targets) return true
            parent = byId[parent]?.parentId
        }
        return false
    }
}

@Serializable
data class SavedMonitorView(val name: String, val filter: MonitorViewFilter)

object MonitorViewCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun isValidName(name: String) = name.trim().length in 1..40
    fun encode(view: SavedMonitorView): String = json.encodeToString(view.copy(name = view.name.trim()))
    fun decode(raw: String): SavedMonitorView? = runCatching { json.decodeFromString<SavedMonitorView>(raw) }
        .getOrNull()?.takeIf { isValidName(it.name) }
}
