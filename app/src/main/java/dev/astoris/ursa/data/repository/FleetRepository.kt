package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheFailure
import dev.astoris.ursa.core.storage.MonitorCacheRead
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetMonitorCounts
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerError
import dev.astoris.ursa.data.model.FleetServerSnapshot
import dev.astoris.ursa.data.model.FleetSnapshot
import dev.astoris.ursa.data.model.FleetSnapshotSource
import dev.astoris.ursa.data.model.ServerConnection

/**
 * Builds a cross-server view from encrypted last-known snapshots only. Network
 * refresh is deliberately a separate operation so opening Fleet cannot create
 * background sockets for inactive connections.
 */
class FleetRepository(
    private val connectionStore: ConnectionStore,
    private val monitorCacheStore: MonitorCacheStore,
) {
    suspend fun loadCached(
        contentUnlocked: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): FleetSnapshot {
        val selection = connectionStore.selectionSnapshot()
        if (!contentUnlocked) {
            return buildCachedFleetSnapshot(
                connections = selection.connections,
                activeUrl = selection.activeUrl,
                cacheReads = emptyMap(),
                contentUnlocked = false,
                nowMillis = nowMillis,
            )
        }
        val cacheReads = monitorCacheStore.readAll(selection.connections.map(ServerConnection::url))
        return buildCachedFleetSnapshot(
            connections = selection.connections,
            activeUrl = selection.activeUrl,
            cacheReads = cacheReads,
            contentUnlocked = true,
            nowMillis = nowMillis,
        )
    }

    companion object {
        const val DEFAULT_STALE_AFTER_MILLIS = 5L * 60L * 1_000L
    }
}

internal fun buildCachedFleetSnapshot(
    connections: List<ServerConnection>,
    activeUrl: String?,
    cacheReads: Map<String, MonitorCacheRead>,
    contentUnlocked: Boolean,
    nowMillis: Long,
    staleAfterMillis: Long = FleetRepository.DEFAULT_STALE_AFTER_MILLIS,
): FleetSnapshot {
    require(staleAfterMillis > 0L)
    if (!contentUnlocked) {
        return FleetSnapshot(
            servers = emptyList(),
            counts = null,
            loadedAtMillis = nowMillis,
            isLocked = true,
            hiddenServerCount = connections.size,
        )
    }

    val servers = connections.map { connection ->
        when (val read = cacheReads[connection.url] ?: MonitorCacheRead.Missing) {
            is MonitorCacheRead.Available -> {
                val ageMillis = (nowMillis - read.snapshot.updatedAt).coerceAtLeast(0L)
                FleetServerSnapshot(
                    serverUrl = connection.url,
                    displayName = connection.displayName,
                    isActiveServer = connection.url == activeUrl,
                    availability = FleetServerAvailability.AVAILABLE,
                    source = FleetSnapshotSource.CACHE,
                    freshness = if (ageMillis < staleAfterMillis) {
                        FleetFreshness.RECENT
                    } else {
                        FleetFreshness.STALE
                    },
                    capturedAtMillis = read.snapshot.updatedAt,
                    ageMillis = ageMillis,
                    counts = FleetMonitorCounts.from(read.snapshot.monitors),
                )
            }
            MonitorCacheRead.Missing -> FleetServerSnapshot(
                serverUrl = connection.url,
                displayName = connection.displayName,
                isActiveServer = connection.url == activeUrl,
                availability = FleetServerAvailability.NO_CACHE,
            )
            is MonitorCacheRead.Unavailable -> FleetServerSnapshot(
                serverUrl = connection.url,
                displayName = connection.displayName,
                isActiveServer = connection.url == activeUrl,
                availability = FleetServerAvailability.UNAVAILABLE,
                error = read.reason.toFleetError(),
            )
        }
    }
    val availableCounts = servers.mapNotNull(FleetServerSnapshot::counts)
    return FleetSnapshot(
        servers = servers,
        counts = availableCounts.takeIf { it.isNotEmpty() }
            ?.fold(FleetMonitorCounts.EMPTY, FleetMonitorCounts::plus),
        loadedAtMillis = nowMillis,
    )
}

private fun MonitorCacheFailure.toFleetError(): FleetServerError = when (this) {
    MonitorCacheFailure.STORE_UNAVAILABLE -> FleetServerError.CACHE_STORE_UNAVAILABLE
    MonitorCacheFailure.DECRYPTION_FAILED -> FleetServerError.CACHE_DECRYPTION_FAILED
    MonitorCacheFailure.INVALID_SNAPSHOT -> FleetServerError.INVALID_CACHE
}
