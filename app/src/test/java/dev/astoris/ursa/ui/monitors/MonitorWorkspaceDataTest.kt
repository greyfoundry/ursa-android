package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.components.FreshnessState
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitorWorkspaceDataTest {
    @Test
    fun transitionsExcludeRepeatedHeartbeatsAndKeepWireOrder() {
        val transitions = monitorTransitionEvents(
            listOf(
                beat(MonitorStatus.UP, "10:00"),
                beat(MonitorStatus.UP, "10:01"),
                beat(MonitorStatus.DOWN, "10:02", "timeout"),
                beat(MonitorStatus.DOWN, "10:03"),
                beat(MonitorStatus.UP, "10:04"),
            ),
        )

        assertEquals(listOf(MonitorStatus.DOWN, MonitorStatus.UP), transitions.map { it.status })
        assertEquals("timeout", transitions.first().message)
        assertEquals("10:04", transitions.last().time)
    }

    @Test
    fun freshnessPrefersLiveAndFailsClosedWithoutCacheTimestamp() {
        assertEquals(
            FreshnessState.LIVE,
            monitorWorkspaceFreshness(
                connectionState = ConnectionState.Authenticated,
                showingCache = false,
                lastUpdated = null,
                nowMillis = 1_000,
            ),
        )
        assertEquals(
            FreshnessState.OFFLINE,
            monitorWorkspaceFreshness(
                connectionState = ConnectionState.Disconnected,
                showingCache = true,
                lastUpdated = null,
                nowMillis = 1_000,
            ),
        )
    }

    @Test
    fun cachedOnlineDataBecomesStaleAfterFiveMinutes() {
        assertEquals(
            FreshnessState.STALE,
            monitorWorkspaceFreshness(
                connectionState = ConnectionState.Connecting,
                showingCache = true,
                lastUpdated = 1_000,
                nowMillis = 301_001,
            ),
        )
    }

    private fun beat(status: MonitorStatus, time: String, message: String? = null) = Heartbeat(
        monitorId = 1,
        status = status,
        time = time,
        msg = message,
        ping = 10,
        important = true,
    )
}
