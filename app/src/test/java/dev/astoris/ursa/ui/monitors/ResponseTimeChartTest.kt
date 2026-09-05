package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResponseTimeChartTest {

    @Test
    fun samplesIgnoreMissingPingAndStatsRoundAverage() {
        val samples = responseTimeSamples(
            listOf(
                beat("first", 10),
                beat("missing", null),
                beat("invalid", -1),
                beat("last", 21),
            ),
        )

        assertEquals(listOf("first", "last"), samples.map { it.time })
        assertEquals(ResponseTimeStats(minimum = 10, average = 16, maximum = 21), responseTimeStats(samples))
        assertNull(responseTimeStats(emptyList()))
    }

    @Test
    fun sampleIndexClampsTouchPositionToSeries() {
        assertEquals(0, responseTimeSampleIndex(-10f, 100f, 5))
        assertEquals(2, responseTimeSampleIndex(50f, 100f, 5))
        assertEquals(4, responseTimeSampleIndex(150f, 100f, 5))
        assertEquals(0, responseTimeSampleIndex(50f, 0f, 5))
    }

    private fun beat(time: String, ping: Int?) = Heartbeat(
        monitorId = 1,
        status = MonitorStatus.UP,
        time = time,
        msg = null,
        ping = ping,
        important = false,
    )
}
