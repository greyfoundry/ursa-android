package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.network.ConnectionFailureReason
import dev.astoris.ursa.core.storage.MonitorCacheRead
import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.CleartextPolicy
import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetMonitorCounts
import dev.astoris.ursa.data.model.FleetRefreshError
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetSnapshotSource
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.ServerConnection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetRefreshTest {

    @Test fun network_failures_map_to_stable_fleet_failures() {
        val expected = mapOf(
            ConnectionFailureReason.DEVICE_OFFLINE to FleetRefreshError.DEVICE_OFFLINE,
            ConnectionFailureReason.SERVER_UNREACHABLE to FleetRefreshError.SERVER_UNREACHABLE,
            ConnectionFailureReason.AUTHENTICATION to FleetRefreshError.AUTHENTICATION_FAILED,
            ConnectionFailureReason.CERTIFICATE to FleetRefreshError.CERTIFICATE,
            ConnectionFailureReason.CLEARTEXT_BLOCKED to FleetRefreshError.CLEARTEXT_BLOCKED,
            ConnectionFailureReason.INCOMPATIBLE_RESPONSE to FleetRefreshError.INCOMPATIBLE_RESPONSE,
            ConnectionFailureReason.UNKNOWN to FleetRefreshError.UNKNOWN,
        )

        expected.forEach { (reason, fleetError) ->
            assertEquals(fleetError, reason.toFleetRefreshError())
        }
        assertEquals(FleetRefreshError.UNKNOWN, null.toFleetRefreshError())
    }

    @Test fun refresh_is_bounded_ordered_and_closes_every_temporary_session() = runBlocking {
        val connections = (1..5).map { connection("https://server-$it.example", session = "fixture-$it") }
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val closed = AtomicInteger()

        val outcomes = refreshFleetConnections(
            connections = connections,
            nowMillis = { 42L },
            sessionFactory = { connection ->
                object : FleetRefreshSession {
                    override suspend fun load(token: String): FleetRefreshResult {
                        val count = active.incrementAndGet()
                        maximum.updateAndGet { previous -> maxOf(previous, count) }
                        return try {
                            delay(20L)
                            FleetRefreshResult.Success(
                                listOf(monitor(connection.url.hashCode(), MonitorStatus.UP)),
                            )
                        } finally {
                            active.decrementAndGet()
                        }
                    }

                    override fun close() {
                        closed.incrementAndGet()
                    }
                }
            },
        )

        assertTrue(maximum.get() in 1..FleetRepository.MAX_REFRESH_CONCURRENCY)
        assertEquals(connections.map(ServerConnection::url), outcomes.map { it.serverUrl })
        assertTrue(outcomes.all { it.result is FleetRefreshResult.Success })
        assertEquals(connections.size, closed.get())
    }

    @Test fun policy_and_missing_session_fail_before_a_socket_is_created() = runBlocking {
        val blocked = connection(
            url = "http://blocked.example",
            session = "fixture",
            cleartextPolicy = CleartextPolicy.DENY,
        )
        val missing = connection("https://missing.example", session = null)
        val created = AtomicInteger()

        val outcomes = refreshFleetConnections(
            connections = listOf(blocked, missing),
            sessionFactory = {
                created.incrementAndGet()
                error("must not create session")
            },
        )

        assertEquals(0, created.get())
        assertEquals(
            listOf(FleetRefreshError.CLEARTEXT_BLOCKED, FleetRefreshError.MISSING_SESSION),
            outcomes.map { (it.result as FleetRefreshResult.Failure).error },
        )
    }

    @Test fun cancellation_propagates_and_still_closes_started_session() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val closed = AtomicBoolean(false)
        val job = launch {
            refreshFleetConnections(
                connections = listOf(connection("https://slow.example", session = "fixture")),
                sessionFactory = {
                    object : FleetRefreshSession {
                        override suspend fun load(token: String): FleetRefreshResult {
                            started.complete(Unit)
                            awaitCancellation()
                        }

                        override fun close() {
                            closed.set(true)
                        }
                    }
                },
            )
        }

        started.await()
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(closed.get())
    }

    @Test fun one_session_exception_becomes_a_scoped_failure_and_other_results_survive() = runBlocking {
        val first = connection("https://broken.example", session = "fixture-one")
        val second = connection("https://healthy.example", session = "fixture-two")
        val closed = AtomicInteger()

        val outcomes = refreshFleetConnections(
            connections = listOf(first, second),
            sessionFactory = { connection ->
                object : FleetRefreshSession {
                    override suspend fun load(token: String): FleetRefreshResult {
                        if (connection == first) error("broken session")
                        return FleetRefreshResult.Success(emptyList())
                    }

                    override fun close() {
                        closed.incrementAndGet()
                    }
                }
            },
        )

        assertEquals(FleetRefreshError.UNKNOWN, (outcomes[0].result as FleetRefreshResult.Failure).error)
        assertTrue(outcomes[1].result is FleetRefreshResult.Success)
        assertEquals(2, closed.get())
    }

    @Test fun merge_keeps_cached_success_when_refresh_fails_and_promotes_live_empty_server() {
        val cachedConnection = connection("https://cached.example", session = "fixture-one")
        val emptyConnection = connection("https://empty.example", session = "fixture-two")
        val cached = buildCachedFleetSnapshot(
            connections = listOf(cachedConnection, emptyConnection),
            activeUrl = cachedConnection.url,
            cacheReads = mapOf(
                cachedConnection.url to MonitorCacheRead.Available(
                    MonitorSnapshot(listOf(monitor(1, MonitorStatus.DOWN)), updatedAt = 10L),
                ),
                emptyConnection.url to MonitorCacheRead.Missing,
            ),
            contentUnlocked = true,
            nowMillis = 20L,
        )

        val refreshed = mergeFleetRefresh(
            cached = cached,
            outcomes = listOf(
                FleetRefreshOutcome(
                    cachedConnection.url,
                    30L,
                    FleetRefreshResult.Failure(FleetRefreshError.AUTHENTICATION_FAILED),
                ),
                FleetRefreshOutcome(
                    emptyConnection.url,
                    31L,
                    FleetRefreshResult.Success(emptyList(), reportedVersion = "2.5.5"),
                ),
            ),
            cacheWriteFailures = setOf(emptyConnection.url),
            loadedAtMillis = 32L,
        )

        val failed = refreshed.servers[0]
        assertEquals(FleetSnapshotSource.CACHE, failed.source)
        assertEquals(FleetFreshness.RECENT, failed.freshness)
        assertEquals(1, failed.counts?.down)
        assertEquals(FleetRefreshError.AUTHENTICATION_FAILED, failed.refreshError)
        assertEquals(30L, failed.refreshAttemptedAtMillis)

        val live = refreshed.servers[1]
        assertEquals(FleetServerAvailability.AVAILABLE, live.availability)
        assertEquals(FleetSnapshotSource.REFRESHED, live.source)
        assertEquals(FleetFreshness.RECENT, live.freshness)
        assertEquals(FleetMonitorCounts.EMPTY, live.counts)
        assertNull(live.error)
        assertEquals(FleetRefreshError.CACHE_WRITE_FAILED, live.refreshError)
        assertEquals("2.5.5", live.reportedVersion)
        assertEquals(31L, live.capturedAtMillis)
        assertEquals(1, refreshed.counts?.down)
        assertEquals(32L, refreshed.loadedAtMillis)
    }

    private fun connection(
        url: String,
        session: String?,
        cleartextPolicy: CleartextPolicy = CleartextPolicy.DENY,
    ) = ServerConnection(
        url = url,
        username = "operator",
        jwt = session,
        cleartextPolicy = cleartextPolicy,
    )

    private fun monitor(id: Int, status: MonitorStatus) = Monitor(
        id = id,
        name = "Monitor $id",
        url = null,
        type = "http",
        active = true,
        status = status,
    )
}
