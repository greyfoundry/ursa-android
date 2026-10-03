package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.network.ConnectionFailureReason
import dev.astoris.ursa.core.network.ConnectionTransportPolicy
import dev.astoris.ursa.core.network.KumaClient
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheFailure
import dev.astoris.ursa.core.storage.MonitorCacheRead
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetMonitorCounts
import dev.astoris.ursa.data.model.FleetRefreshError
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerError
import dev.astoris.ursa.data.model.FleetServerSnapshot
import dev.astoris.ursa.data.model.FleetSnapshot
import dev.astoris.ursa.data.model.FleetSnapshotSource
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.ServerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Explicitly refreshes every saved server with small bounded concurrency.
     * The caller's coroutine owns cancellation; each temporary socket is closed.
     */
    suspend fun refresh(
        contentUnlocked: Boolean,
        nowMillis: () -> Long = System::currentTimeMillis,
    ): FleetSnapshot {
        val selection = connectionStore.selectionSnapshot()
        if (!contentUnlocked) {
            return buildCachedFleetSnapshot(
                connections = selection.connections,
                activeUrl = selection.activeUrl,
                cacheReads = emptyMap(),
                contentUnlocked = false,
                nowMillis = nowMillis(),
            )
        }
        val cacheReads = monitorCacheStore.readAll(selection.connections.map(ServerConnection::url))
        val cached = buildCachedFleetSnapshot(
            connections = selection.connections,
            activeUrl = selection.activeUrl,
            cacheReads = cacheReads,
            contentUnlocked = true,
            nowMillis = nowMillis(),
        )
        val outcomes = refreshFleetConnections(
            connections = selection.connections,
            nowMillis = nowMillis,
        )
        val cacheWriteFailures = mutableSetOf<String>()
        outcomes.forEach { outcome ->
            val success = outcome.result as? FleetRefreshResult.Success ?: return@forEach
            try {
                monitorCacheStore.save(
                    outcome.serverUrl,
                    MonitorSnapshot(
                        monitors = success.monitors,
                        updatedAt = outcome.completedAtMillis,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                cacheWriteFailures += outcome.serverUrl
            }
        }
        return mergeFleetRefresh(
            cached = cached,
            outcomes = outcomes,
            cacheWriteFailures = cacheWriteFailures,
            loadedAtMillis = nowMillis(),
        )
    }

    companion object {
        const val DEFAULT_STALE_AFTER_MILLIS = 5L * 60L * 1_000L
        const val MAX_REFRESH_CONCURRENCY = 2
        const val SERVER_REFRESH_TIMEOUT_MILLIS = 20_000L
    }
}

internal sealed interface FleetRefreshResult {
    data class Success(val monitors: List<Monitor>) : FleetRefreshResult
    data class Failure(val error: FleetRefreshError) : FleetRefreshResult
}

internal data class FleetRefreshOutcome(
    val serverUrl: String,
    val completedAtMillis: Long,
    val result: FleetRefreshResult,
)

internal interface FleetRefreshSession {
    suspend fun load(token: String): FleetRefreshResult
    fun close()
}

internal suspend fun refreshFleetConnections(
    connections: List<ServerConnection>,
    maxConcurrency: Int = FleetRepository.MAX_REFRESH_CONCURRENCY,
    nowMillis: () -> Long = System::currentTimeMillis,
    sessionFactory: (ServerConnection) -> FleetRefreshSession = ::KumaFleetRefreshSession,
): List<FleetRefreshOutcome> {
    require(maxConcurrency in 1..FleetRepository.MAX_REFRESH_CONCURRENCY)
    val permits = Semaphore(maxConcurrency)
    return supervisorScope {
        connections.map { connection ->
            async {
                permits.withPermit {
                    val result = refreshConnection(connection, sessionFactory)
                    FleetRefreshOutcome(
                        serverUrl = connection.url,
                        completedAtMillis = nowMillis(),
                        result = result,
                    )
                }
            }
        }.awaitAll()
    }
}

private suspend fun refreshConnection(
    connection: ServerConnection,
    sessionFactory: (ServerConnection) -> FleetRefreshSession,
): FleetRefreshResult {
    if (!ConnectionTransportPolicy.allows(connection)) {
        return FleetRefreshResult.Failure(FleetRefreshError.CLEARTEXT_BLOCKED)
    }
    val token = connection.jwt?.takeIf(String::isNotBlank)
        ?: return FleetRefreshResult.Failure(FleetRefreshError.MISSING_SESSION)
    var session: FleetRefreshSession? = null
    return try {
        session = sessionFactory(connection)
        session.load(token)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        FleetRefreshResult.Failure(FleetRefreshError.UNKNOWN)
    } finally {
        session?.let { runCatching(it::close) }
    }
}

private class KumaFleetRefreshSession(connection: ServerConnection) : FleetRefreshSession {
    private val client = KumaClient(connection.url, connection.insecure, connection.headers)

    override suspend fun load(token: String): FleetRefreshResult {
        val result = withTimeoutOrNull(FleetRepository.SERVER_REFRESH_TIMEOUT_MILLIS) {
            client.connect()
            if (!client.loginByToken(token)) {
                return@withTimeoutOrNull FleetRefreshResult.Failure(
                    client.failure.value.toFleetRefreshError(),
                )
            }
            client.monitorListReady.first { it }
            FleetRefreshResult.Success(client.monitors.value.values.toList())
        }
        return result ?: FleetRefreshResult.Failure(FleetRefreshError.TIMED_OUT)
    }

    override fun close() = client.disconnect()
}

internal fun mergeFleetRefresh(
    cached: FleetSnapshot,
    outcomes: List<FleetRefreshOutcome>,
    cacheWriteFailures: Set<String> = emptySet(),
    loadedAtMillis: Long,
): FleetSnapshot {
    require(!cached.isLocked)
    val byUrl = outcomes.associateBy(FleetRefreshOutcome::serverUrl)
    val servers = cached.servers.map { server ->
        val outcome = byUrl[server.serverUrl] ?: return@map server
        when (val result = outcome.result) {
            is FleetRefreshResult.Success -> server.copy(
                availability = FleetServerAvailability.AVAILABLE,
                source = FleetSnapshotSource.REFRESHED,
                freshness = FleetFreshness.RECENT,
                capturedAtMillis = outcome.completedAtMillis,
                ageMillis = 0L,
                counts = FleetMonitorCounts.from(result.monitors),
                error = null,
                refreshAttemptedAtMillis = outcome.completedAtMillis,
                refreshError = if (server.serverUrl in cacheWriteFailures) {
                    FleetRefreshError.CACHE_WRITE_FAILED
                } else {
                    null
                },
            )
            is FleetRefreshResult.Failure -> server.copy(
                refreshAttemptedAtMillis = outcome.completedAtMillis,
                refreshError = result.error,
            )
        }
    }
    val availableCounts = servers.mapNotNull(FleetServerSnapshot::counts)
    return FleetSnapshot(
        servers = servers,
        counts = availableCounts.takeIf { it.isNotEmpty() }
            ?.fold(FleetMonitorCounts.EMPTY, FleetMonitorCounts::plus),
        loadedAtMillis = loadedAtMillis,
    )
}

internal fun mergeActiveFleetSnapshot(
    snapshot: FleetSnapshot,
    activeUrl: String,
    monitors: List<Monitor>,
    capturedAtMillis: Long,
    loadedAtMillis: Long,
): FleetSnapshot {
    require(!snapshot.isLocked)
    val servers = snapshot.servers.map { server ->
        if (server.serverUrl == activeUrl) {
            server.copy(
                isActiveServer = true,
                availability = FleetServerAvailability.AVAILABLE,
                source = FleetSnapshotSource.LIVE,
                freshness = FleetFreshness.LIVE,
                capturedAtMillis = capturedAtMillis,
                ageMillis = (loadedAtMillis - capturedAtMillis).coerceAtLeast(0L),
                counts = FleetMonitorCounts.from(monitors),
                error = null,
                refreshError = null,
            )
        } else {
            server.copy(isActiveServer = false)
        }
    }
    val availableCounts = servers.mapNotNull(FleetServerSnapshot::counts)
    return snapshot.copy(
        servers = servers,
        counts = availableCounts.takeIf { it.isNotEmpty() }
            ?.fold(FleetMonitorCounts.EMPTY, FleetMonitorCounts::plus),
        loadedAtMillis = loadedAtMillis,
    )
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

internal fun ConnectionFailureReason?.toFleetRefreshError(): FleetRefreshError = when (this) {
    ConnectionFailureReason.DEVICE_OFFLINE -> FleetRefreshError.DEVICE_OFFLINE
    ConnectionFailureReason.SERVER_UNREACHABLE -> FleetRefreshError.SERVER_UNREACHABLE
    ConnectionFailureReason.AUTHENTICATION -> FleetRefreshError.AUTHENTICATION_FAILED
    ConnectionFailureReason.CERTIFICATE -> FleetRefreshError.CERTIFICATE
    ConnectionFailureReason.CLEARTEXT_BLOCKED -> FleetRefreshError.CLEARTEXT_BLOCKED
    ConnectionFailureReason.INCOMPATIBLE_RESPONSE -> FleetRefreshError.INCOMPATIBLE_RESPONSE
    ConnectionFailureReason.UNKNOWN, null -> FleetRefreshError.UNKNOWN
}
