package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.storage.MonitorCacheFailure
import dev.astoris.ursa.core.storage.MonitorCacheRead
import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetAuthenticationState
import dev.astoris.ursa.data.model.FleetMonitorCounts
import dev.astoris.ursa.data.model.FleetPushBindingState
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerError
import dev.astoris.ursa.data.model.FleetSnapshotSource
import dev.astoris.ursa.data.model.FleetTransportState
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.fixtures.LargeFleetFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetRepositoryTest {

    @Test fun cached_fleet_preserves_per_server_availability_and_aggregates_only_known_data() {
        val now = 1_800_000_000_000L
        val connections = listOf(
            connection("https://one.example", alias = "Primary"),
            connection("https://two.example"),
            connection("https://three.example"),
            connection("https://four.example"),
        )
        val fresh = MonitorSnapshot(
            monitors = listOf(
                monitor(1, MonitorStatus.UP),
                monitor(2, MonitorStatus.DOWN),
                monitor(3, MonitorStatus.PENDING),
                monitor(4, MonitorStatus.MAINTENANCE),
                monitor(5, MonitorStatus.DOWN, active = false),
            ),
            updatedAt = now - 60_000L,
        )
        val stale = MonitorSnapshot(
            monitors = listOf(monitor(6, MonitorStatus.UP)),
            updatedAt = now - FleetRepository.DEFAULT_STALE_AFTER_MILLIS,
        )

        val fleet = buildCachedFleetSnapshot(
            connections = connections,
            activeUrl = connections[1].url,
            cacheReads = mapOf(
                connections[0].url to MonitorCacheRead.Available(fresh),
                connections[1].url to MonitorCacheRead.Available(stale),
                connections[2].url to MonitorCacheRead.Missing,
                connections[3].url to MonitorCacheRead.Unavailable(
                    MonitorCacheFailure.INVALID_SNAPSHOT,
                ),
            ),
            pushBindings = mapOf(
                connections[0].url to FleetPushBindingState.CONFIGURED,
                connections[1].url to FleetPushBindingState.NOT_CONFIGURED,
            ),
            contentUnlocked = true,
            nowMillis = now,
        )

        assertEquals(4, fleet.servers.size)
        assertEquals("Primary", fleet.servers[0].displayName)
        assertFalse(fleet.servers[0].isActiveServer)
        assertTrue(fleet.servers[1].isActiveServer)
        assertEquals(FleetServerAvailability.AVAILABLE, fleet.servers[0].availability)
        assertEquals(FleetSnapshotSource.CACHE, fleet.servers[0].source)
        assertEquals(FleetFreshness.RECENT, fleet.servers[0].freshness)
        assertEquals(FleetTransportState.HTTPS, fleet.servers[0].transport)
        assertEquals(FleetAuthenticationState.SIGN_IN_REQUIRED, fleet.servers[0].authentication)
        assertEquals(FleetPushBindingState.CONFIGURED, fleet.servers[0].pushBinding)
        assertEquals(60_000L, fleet.servers[0].ageMillis)
        assertEquals(
            FleetMonitorCounts(total = 5, active = 4, up = 1, down = 1,
                pending = 1, maintenance = 1, paused = 1),
            fleet.servers[0].counts,
        )
        assertEquals(FleetFreshness.STALE, fleet.servers[1].freshness)
        assertEquals(FleetServerAvailability.NO_CACHE, fleet.servers[2].availability)
        assertNull(fleet.servers[2].counts)
        assertEquals(FleetServerAvailability.UNAVAILABLE, fleet.servers[3].availability)
        assertEquals(FleetServerError.INVALID_CACHE, fleet.servers[3].error)
        assertNull(fleet.servers[3].counts)
        assertEquals(2, fleet.availableServerCount)
        assertEquals(2, fleet.unavailableServerCount)
        assertTrue(fleet.hasPartialData)
        assertEquals(1, fleet.affectedServerCount)
        assertEquals(
            FleetMonitorCounts(total = 6, active = 5, up = 2, down = 1,
                pending = 1, maintenance = 1, paused = 1),
            fleet.counts,
        )
    }

    @Test fun locked_fleet_excludes_cached_content_instead_of_reporting_zeroes() {
        val connection = connection("https://locked.example")
        val fleet = buildCachedFleetSnapshot(
            connections = listOf(connection),
            activeUrl = connection.url,
            cacheReads = mapOf(
                connection.url to MonitorCacheRead.Available(
                    MonitorSnapshot(listOf(monitor(1, MonitorStatus.DOWN)), updatedAt = 1L),
                ),
            ),
            contentUnlocked = false,
            nowMillis = 10L,
        )

        assertTrue(fleet.isLocked)
        assertTrue(fleet.servers.isEmpty())
        assertEquals(1, fleet.hiddenServerCount)
        assertEquals(1, fleet.totalServerCount)
        assertNull(fleet.counts)
        assertEquals(0, fleet.availableServerCount)
        assertEquals(0, fleet.unavailableServerCount)
        assertFalse(fleet.hasPartialData)
    }

    @Test fun available_empty_snapshot_remains_distinct_from_missing_data() {
        val connection = connection("https://empty.example")
        val fleet = buildCachedFleetSnapshot(
            connections = listOf(connection),
            activeUrl = connection.url,
            cacheReads = mapOf(
                connection.url to MonitorCacheRead.Available(MonitorSnapshot(emptyList(), 1L)),
            ),
            contentUnlocked = true,
            nowMillis = 1L,
        )

        assertEquals(FleetMonitorCounts.EMPTY, fleet.counts)
        assertEquals(FleetMonitorCounts.EMPTY, fleet.servers.single().counts)
        assertEquals(1, fleet.availableServerCount)
    }

    @Test fun unavailable_only_fleet_never_synthesizes_healthy_aggregate_counts() {
        val connections = listOf(
            connection("https://missing.example"),
            connection("https://store.example"),
            connection("https://decrypt.example"),
        )
        val fleet = buildCachedFleetSnapshot(
            connections = connections,
            activeUrl = connections.first().url,
            cacheReads = mapOf(
                connections[0].url to MonitorCacheRead.Missing,
                connections[1].url to MonitorCacheRead.Unavailable(
                    MonitorCacheFailure.STORE_UNAVAILABLE,
                ),
                connections[2].url to MonitorCacheRead.Unavailable(
                    MonitorCacheFailure.DECRYPTION_FAILED,
                ),
            ),
            contentUnlocked = true,
            nowMillis = 1L,
        )

        assertNull(fleet.counts)
        assertEquals(0, fleet.availableServerCount)
        assertEquals(3, fleet.unavailableServerCount)
        assertEquals(FleetServerAvailability.NO_CACHE, fleet.servers[0].availability)
        assertEquals(FleetServerError.CACHE_STORE_UNAVAILABLE, fleet.servers[1].error)
        assertEquals(FleetServerError.CACHE_DECRYPTION_FAILED, fleet.servers[2].error)
        assertEquals(0, fleet.affectedServerCount)
    }

    @Test fun future_timestamps_are_clamped_and_large_fleet_counts_stay_server_scoped() {
        val snapshots = LargeFleetFixtures.multiServer()
        val connections = snapshots.keys.map(::connection)
        val fleet = buildCachedFleetSnapshot(
            connections = connections,
            activeUrl = connections.first().url,
            cacheReads = snapshots.mapValues { MonitorCacheRead.Available(it.value) },
            contentUnlocked = true,
            nowMillis = LargeFleetFixtures.BASE_UPDATED_AT,
        )

        assertEquals(1_000, fleet.counts?.total)
        assertEquals(4, fleet.availableServerCount)
        assertEquals(4, fleet.servers.map { it.serverUrl }.distinct().size)
        assertTrue(fleet.servers.all { it.ageMillis == 0L })
        assertTrue(fleet.servers.all { it.freshness == FleetFreshness.RECENT })
    }

    @Test fun active_live_snapshot_replaces_only_current_server_and_recomputes_totals() {
        val connections = listOf(
            connection("https://one.example"),
            connection("https://two.example"),
        )
        val cached = buildCachedFleetSnapshot(
            connections = connections,
            activeUrl = connections.first().url,
            cacheReads = connections.associate { connection ->
                connection.url to MonitorCacheRead.Available(
                    MonitorSnapshot(listOf(monitor(1, MonitorStatus.UP)), updatedAt = 100L),
                )
            },
            contentUnlocked = true,
            nowMillis = 200L,
        )

        val live = mergeActiveFleetSnapshot(
            snapshot = cached,
            activeUrl = connections[1].url,
            monitors = listOf(
                monitor(2, MonitorStatus.DOWN),
                monitor(3, MonitorStatus.PENDING),
            ),
            capturedAtMillis = 250L,
            loadedAtMillis = 300L,
            reportedVersion = "2.5.5",
        )

        assertFalse(live.servers[0].isActiveServer)
        assertEquals(FleetSnapshotSource.CACHE, live.servers[0].source)
        assertTrue(live.servers[1].isActiveServer)
        assertEquals(FleetSnapshotSource.LIVE, live.servers[1].source)
        assertEquals(FleetFreshness.LIVE, live.servers[1].freshness)
        assertEquals(FleetAuthenticationState.AUTHENTICATED, live.servers[1].authentication)
        assertEquals("2.5.5", live.servers[1].reportedVersion)
        assertEquals(50L, live.servers[1].ageMillis)
        assertEquals(3, live.counts?.total)
        assertEquals(1, live.counts?.up)
        assertEquals(1, live.counts?.down)
        assertEquals(1, live.counts?.pending)
    }

    @Test fun transport_and_saved_session_health_are_explicit_without_cache_data() {
        val blocked = ServerConnection(
            url = "http://blocked.example",
            username = "operator",
            jwt = "session",
            cleartextPolicy = dev.astoris.ursa.data.model.CleartextPolicy.DENY,
        )
        val fleet = buildCachedFleetSnapshot(
            connections = listOf(blocked),
            activeUrl = blocked.url,
            cacheReads = emptyMap(),
            pushBindings = mapOf(blocked.url to FleetPushBindingState.NOT_CONFIGURED),
            contentUnlocked = true,
            nowMillis = 1L,
        )

        val server = fleet.servers.single()
        assertEquals(FleetTransportState.HTTP_BLOCKED, server.transport)
        assertEquals(FleetAuthenticationState.SESSION_SAVED, server.authentication)
        assertEquals(FleetPushBindingState.NOT_CONFIGURED, server.pushBinding)
        assertNull(server.reportedVersion)
    }

    private fun connection(url: String, alias: String? = null) = ServerConnection(
        url = url,
        username = "operator",
        alias = alias,
    )

    private fun monitor(
        id: Int,
        status: MonitorStatus,
        active: Boolean = true,
    ) = Monitor(
        id = id,
        name = "Monitor $id",
        url = null,
        type = "http",
        active = active,
        status = status,
    )
}
