package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedLivePanelTest {

    @Test
    fun includesOnlyExistingPinnedMonitorsAndOrdersAttentionFirst() {
        val monitors = listOf(
            monitor(1, "Healthy", MonitorStatus.UP),
            monitor(2, "Down", MonitorStatus.DOWN),
            monitor(3, "Paused", MonitorStatus.DOWN, active = false),
            monitor(4, "Not pinned", MonitorStatus.DOWN),
        )

        val pinned = pinnedLiveMonitors(monitors, emptyMap(), setOf(1, 2, 3, 999))

        assertEquals(listOf(2, 1, 3), pinned.map { it.monitor.id })
        assertTrue(pinned.none { it.monitor.id == 999 })
    }

    @Test
    fun keepsOnlyNewestRequestedHeartbeatSamples() {
        val history = (1..20).map { index ->
            beat(index, if (index == 20) MonitorStatus.DOWN else MonitorStatus.UP)
        }

        val pinned = pinnedLiveMonitors(
            monitors = listOf(monitor(1, "API", MonitorStatus.DOWN)),
            history = mapOf(1 to history),
            pinnedIds = setOf(1),
            sampleCount = 4,
        ).single()

        assertEquals(listOf("17", "18", "19", "20"), pinned.recentBeats.map { it.time })
        assertEquals(MonitorStatus.DOWN, pinned.latestBeat?.status)
    }

    @Test
    fun emptyFavoritesProduceEmptyPanelWithoutRequests() {
        assertTrue(
            pinnedLiveMonitors(
                listOf(monitor(1, "API", MonitorStatus.UP)),
                mapOf(1 to listOf(beat(1, MonitorStatus.UP))),
                emptySet(),
            ).isEmpty(),
        )
    }

    private fun monitor(
        id: Int,
        name: String,
        status: MonitorStatus,
        active: Boolean = true,
    ) = Monitor(id, name, null, "http", active, status = status)

    private fun beat(index: Int, status: MonitorStatus) = Heartbeat(
        monitorId = 1,
        status = status,
        time = index.toString(),
        msg = null,
        ping = index,
        important = false,
    )
}
