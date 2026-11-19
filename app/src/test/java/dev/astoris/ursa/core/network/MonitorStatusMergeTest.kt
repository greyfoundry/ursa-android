package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MonitorStatusMergeTest {
    private fun monitor(id: Int, status: MonitorStatus = MonitorStatus.PENDING) =
        Monitor(id = id, name = "M$id", url = null, type = "http", active = true, status = status)

    private fun beat(id: Int, status: MonitorStatus, ping: Int? = null, time: String = "2026-11-19 05:37:45") =
        Heartbeat(id, status, time, null, ping, false)

    @Test
    fun newestBeatSetsStatusAndPing() {
        val beats = listOf(beat(1, MonitorStatus.DOWN, 10), beat(1, MonitorStatus.UP, 42))

        val result = MonitorStatusMerge.withNewestBeat(monitor(1), beats)

        assertEquals(MonitorStatus.UP, result.status)
        assertEquals(42, result.ping)
    }

    @Test
    fun newestBeatWithoutPingKeepsKnownPing() {
        val known = monitor(1).copy(ping = 30)

        val result = MonitorStatusMerge.withNewestBeat(known, listOf(beat(1, MonitorStatus.UP, ping = null)))

        assertEquals(30, result.ping)
    }

    @Test
    fun emptyHistoryLeavesMonitorUnchanged() {
        val known = monitor(1, MonitorStatus.DOWN)

        assertSame(known, MonitorStatusMerge.withNewestBeat(known, emptyList()))
    }

    @Test
    fun freshListWithoutKnownStateStaysPending() {
        val result = MonitorStatusMerge.withKnownStatus(mapOf(1 to monitor(1)), emptyMap(), emptyMap())

        assertEquals(MonitorStatus.PENDING, result.getValue(1).status)
    }

    @Test
    fun historyThatArrivedBeforeTheListSetsStatus() {
        val result = MonitorStatusMerge.withKnownStatus(
            fresh = mapOf(1 to monitor(1)),
            previous = emptyMap(),
            history = mapOf(1 to listOf(beat(1, MonitorStatus.UP, 52))),
        )

        assertEquals(MonitorStatus.UP, result.getValue(1).status)
        assertEquals(52, result.getValue(1).ping)
    }

    @Test
    fun repeatedListKeepsKnownStateWhenNoHistory() {
        val known = monitor(1, MonitorStatus.DOWN).copy(ping = 12, avgPing = 15, uptime24h = 0.98)
        val fresh = monitor(1).copy(name = "Renamed")

        val result = MonitorStatusMerge.withKnownStatus(mapOf(1 to fresh), mapOf(1 to known), emptyMap())

        val merged = result.getValue(1)
        assertEquals("Renamed", merged.name)
        assertEquals(MonitorStatus.DOWN, merged.status)
        assertEquals(12, merged.ping)
        assertEquals(15, merged.avgPing)
        assertEquals(0.98, merged.uptime24h!!, 0.0)
    }

    @Test
    fun newestBeatWinsOverCachedStatus() {
        val cached = monitor(1, MonitorStatus.UP)

        val result = MonitorStatusMerge.withKnownStatus(
            fresh = mapOf(1 to monitor(1)),
            previous = mapOf(1 to cached),
            history = mapOf(1 to listOf(beat(1, MonitorStatus.DOWN))),
        )

        assertEquals(MonitorStatus.DOWN, result.getValue(1).status)
    }

    @Test
    fun addedMonitorTakesStatusFromHistory() {
        val previous = mapOf(1 to monitor(1, MonitorStatus.UP))

        val result = MonitorStatusMerge.withKnownStatus(
            fresh = mapOf(2 to monitor(2)),
            previous = previous,
            history = mapOf(2 to listOf(beat(2, MonitorStatus.MAINTENANCE))),
        )

        assertEquals(setOf(2), result.keys)
        assertEquals(MonitorStatus.MAINTENANCE, result.getValue(2).status)
    }
}
