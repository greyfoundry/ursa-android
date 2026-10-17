package dev.astoris.ursa.fixtures

import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus

/** Deterministic large-fleet data shared by compatibility, UI, and future benchmark tests. */
object LargeFleetFixtures {
    const val BASE_UPDATED_AT = 1_800_000_000_000L
    val REFERENCE_SIZES = listOf(100, 500, 1_000)

    fun snapshot(
        monitorCount: Int,
        serverIndex: Int = 1,
    ): MonitorSnapshot {
        require(monitorCount >= 0)
        require(serverIndex > 0)
        return MonitorSnapshot(
            monitors = (1..monitorCount).map { id -> monitor(id, serverIndex) },
            updatedAt = BASE_UPDATED_AT + serverIndex * 60_000L,
        )
    }

    fun multiServer(
        serverCount: Int = 4,
        monitorsPerServer: Int = 250,
    ): Map<String, MonitorSnapshot> {
        require(serverCount > 0)
        require(monitorsPerServer >= 0)
        return (1..serverCount).associate { serverIndex ->
            "https://kuma-$serverIndex.example.test" to snapshot(monitorsPerServer, serverIndex)
        }
    }

    private fun monitor(id: Int, serverIndex: Int): Monitor {
        val groupId = id - ((id - 1) % GROUP_SIZE)
        val isGroup = id == groupId
        val active = isGroup || id % 37 != 0
        val status = when {
            id % 17 == 0 -> MonitorStatus.DOWN
            id % 13 == 0 -> MonitorStatus.PENDING
            id % 11 == 0 -> MonitorStatus.MAINTENANCE
            else -> MonitorStatus.UP
        }
        val type = if (isGroup) "group" else TYPES[(id + serverIndex) % TYPES.size]
        return Monitor(
            id = id,
            name = if (isGroup) "Group ${groupId / GROUP_SIZE + 1}" else "Monitor $serverIndex-$id",
            url = if (type == "http") "https://service-$serverIndex-$id.example.test/health" else null,
            type = type,
            active = active,
            tags = listOf(if (id % 2 == 0) "production" else "internal", "server-$serverIndex"),
            parentId = groupId.takeUnless { isGroup },
            weight = id,
            status = status,
            ping = (id * 7 + serverIndex).mod(500),
            avgPing = (id * 5 + serverIndex).mod(400),
            uptime24h = when (status) {
                MonitorStatus.UP -> 0.999
                MonitorStatus.DOWN -> 0.94
                MonitorStatus.PENDING -> null
                MonitorStatus.MAINTENANCE -> 1.0
            },
        )
    }

    private const val GROUP_SIZE = 25
    private val TYPES = listOf("http", "port", "ping", "dns", "push", "keyword", "json-query")
}
