package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushPendingAlertCodecTest {

    @Test
    fun pendingAlertRoundTripsWithoutLosingRenderedNotification() {
        val pending = PushPendingAlert(
            id = "d4c59da1-87db-49a3-9477-7bdcb747d754",
            serverId = "0123456789abcdef0123456789abcdef",
            monitorId = 42,
            monitorName = "Home API",
            title = "Home API is Down",
            body = "Connection refused",
            severity = PushSeverity.STANDARD,
            timing = PushAlertTiming(firstDelayMinutes = 5, repeatMinutes = 15, maxRepeats = 3),
            deliveredCount = 1,
        )

        assertEquals(pending, PushPendingAlertCodec.decode(PushPendingAlertCodec.encode(pending)))
    }

    @Test
    fun malformedOrOutOfScopePendingAlertIsRejected() {
        assertNull(PushPendingAlertCodec.decode("not json"))
        assertNull(
            PushPendingAlertCodec.decode(
                """{"id":"x","serverId":"bad","monitorId":42,"monitorName":"M","title":"T","body":"B"}""",
            ),
        )
    }
}
