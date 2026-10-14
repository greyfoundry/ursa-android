package dev.astoris.ursa.data.repository

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorRepositoryStateTest {
    private val cached = listOf(
        Monitor(1, "Cached", null, "http", true, status = MonitorStatus.UP),
    )

    @Test
    fun cacheIsUsedOnlyUntilTheLiveMonitorListArrives() {
        assertEquals(cached, displayedMonitors(emptyList(), cached, liveListReady = false))
        assertTrue(isShowingMonitorCache(cached, liveListReady = false))

        assertEquals(emptyList<Monitor>(), displayedMonitors(emptyList(), cached, liveListReady = true))
        assertFalse(isShowingMonitorCache(cached, liveListReady = true))
    }
}
