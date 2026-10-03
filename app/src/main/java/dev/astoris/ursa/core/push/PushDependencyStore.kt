package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import dev.astoris.ursa.core.storage.Crypto
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.data.model.ManagedPushNotification
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class PushDependencyGraph(
    val parentsByMonitor: Map<Int, Set<Int>> = emptyMap(),
) {
    fun parentsOf(monitorId: Int): Set<Int> = parentsByMonitor[monitorId].orEmpty()
}

data class PushDependencySuppression(val parentId: Int)

object PushDependencyPolicy {
    fun updated(
        graph: PushDependencyGraph,
        monitorId: Int,
        parentIds: Set<Int>,
        validMonitorIds: Set<Int>,
    ): PushDependencyGraph? {
        if (monitorId !in validMonitorIds) return null
        val updated = graph.parentsByMonitor.toMutableMap().apply {
            if (parentIds.isEmpty()) remove(monitorId) else put(monitorId, parentIds)
        }
        return validated(updated, validMonitorIds)
    }

    fun validated(
        parentsByMonitor: Map<Int, Set<Int>>,
        validMonitorIds: Set<Int>? = null,
    ): PushDependencyGraph? {
        if (parentsByMonitor.size > MAX_MONITORS) return null
        val normalized = linkedMapOf<Int, Set<Int>>()
        for ((monitorId, rawParents) in parentsByMonitor.toSortedMap()) {
            if (monitorId <= 0 || validMonitorIds != null && monitorId !in validMonitorIds) return null
            if (rawParents.size > MAX_PARENTS_PER_MONITOR) return null
            val parents = rawParents.toSortedSet()
            if (monitorId in parents || parents.any { it <= 0 || validMonitorIds != null && it !in validMonitorIds }) {
                return null
            }
            if (parents.isNotEmpty()) normalized[monitorId] = parents
        }
        return PushDependencyGraph(normalized).takeUnless(::hasCycle)
    }

    fun suppression(
        graph: PushDependencyGraph,
        monitorId: Int,
        statusByMonitor: Map<Int, Int?>,
    ): PushDependencySuppression? {
        val safeGraph = validated(graph.parentsByMonitor) ?: return null
        return safeGraph.parentsOf(monitorId)
            .firstOrNull { statusByMonitor[it] == 0 }
            ?.let(::PushDependencySuppression)
    }

    private fun hasCycle(graph: PushDependencyGraph): Boolean {
        val nodes = graph.parentsByMonitor.keys + graph.parentsByMonitor.values.flatten()
        if (nodes.isEmpty()) return false
        val incoming = nodes.associateWithTo(mutableMapOf()) { 0 }
        graph.parentsByMonitor.values.forEach { parents ->
            parents.forEach { parent -> incoming[parent] = incoming.getValue(parent) + 1 }
        }
        val queue = ArrayDeque(incoming.filterValues { it == 0 }.keys)
        var visited = 0
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            visited++
            graph.parentsOf(node).forEach { parent ->
                val remaining = incoming.getValue(parent) - 1
                incoming[parent] = remaining
                if (remaining == 0) queue.addLast(parent)
            }
        }
        return visited != nodes.size
    }

    private const val MAX_MONITORS = 10_000
    private const val MAX_PARENTS_PER_MONITOR = 32
}

object PushDependencyGraphCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(graph: PushDependencyGraph): String = json.encodeToString(
        PersistedGraph(
            version = VERSION,
            dependencies = graph.parentsByMonitor.map { (monitorId, parentIds) ->
                PersistedDependency(monitorId, parentIds.sorted())
            },
        ),
    )

    fun decode(raw: String?): PushDependencyGraph? {
        val saved = raw?.let { runCatching { json.decodeFromString<PersistedGraph>(it) }.getOrNull() }
            ?: return null
        if (saved.version != VERSION) return null
        val rows = linkedMapOf<Int, Set<Int>>()
        for (row in saved.dependencies) {
            if (row.monitorId in rows) return null
            rows[row.monitorId] = row.parentIds.toSet()
        }
        return PushDependencyPolicy.validated(rows)
    }

    @Serializable
    private data class PersistedGraph(
        val version: Int,
        val dependencies: List<PersistedDependency>,
    )

    @Serializable
    private data class PersistedDependency(
        val monitorId: Int,
        val parentIds: List<Int>,
    )

    private const val VERSION = 1
}

class PushDependencyStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val crypto = Crypto(appContext)

    fun load(serverId: String?): PushDependencyGraph {
        val validServerId = serverId?.takeIf(ManagedPushNotification::isValidServerId)
            ?: return PushDependencyGraph()
        val cipher = prefs.getString(key(validServerId), null) ?: return PushDependencyGraph()
        return crypto.decrypt(cipher)?.let(PushDependencyGraphCodec::decode) ?: PushDependencyGraph()
    }

    fun save(serverId: String, graph: PushDependencyGraph, validMonitorIds: Set<Int>): Boolean {
        if (!ManagedPushNotification.isValidServerId(serverId)) return false
        val safeGraph = PushDependencyPolicy.validated(graph.parentsByMonitor, validMonitorIds) ?: return false
        return runCatching {
            prefs.edit {
                if (safeGraph.parentsByMonitor.isEmpty()) remove(key(serverId))
                else putString(key(serverId), crypto.encrypt(PushDependencyGraphCodec.encode(safeGraph)))
            }
            true
        }.getOrDefault(false)
    }

    fun suppression(
        serverId: String,
        monitorId: Int,
        statusStore: PushTransitionStore = PushTransitionStore(appContext),
    ): PushDependencySuppression? {
        val graph = load(serverId)
        val parents = graph.parentsOf(monitorId)
        if (parents.isEmpty()) return null
        return PushDependencyPolicy.suppression(graph, monitorId, statusStore.statuses(serverId, parents))
    }

    fun clearServer(serverId: String?) {
        val validServerId = serverId?.takeIf(ManagedPushNotification::isValidServerId) ?: return
        prefs.edit { remove(key(validServerId)) }
    }

    private companion object {
        const val PREFS = "ursa_push_dependencies"
        fun key(serverId: String) = "dependencies:$serverId"
    }
}

suspend fun recordPushDependencySuppression(
    context: Context,
    scope: ManagedPushScope,
    monitorId: Int,
    monitorName: String,
    suppression: PushDependencySuppression,
    alertId: String? = null,
) {
    val parentName = scope.monitor(suppression.parentId)?.name
        ?: context.getString(dev.astoris.ursa.R.string.monitor_fallback_name, suppression.parentId)
    EventLogStore(context).append(
        serverUrl = scope.serverUrl,
        monitorId = monitorId,
        monitorName = monitorName,
        kind = LocalEventKind.PUSH_SUPPRESSED,
        detail = context.getString(dev.astoris.ursa.R.string.push_dependency_suppressed_detail, parentName),
        alertId = alertId,
        alertDecision = PushAlertTimeline.decision(
            outcome = PushAlertTimeline.SUPPRESSED,
            reason = PushAlertTimeline.REASON_DEPENDENCY,
        ),
    )
}
