package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Test

class PushAlertTimelineTest {

    @Test
    fun decisionSnapshotKeepsInputsNeededToExplainDelivery() {
        val snapshot = PushAlertTimeline.decision(
            outcome = PushAlertTimeline.CORRELATED,
            mode = PushAlertMode.DOWN_AND_RECOVERY,
            configuredSeverity = PushSeverity.CRITICAL,
            effectiveSeverity = PushSeverity.SILENT,
            timing = PushAlertTiming(firstDelayMinutes = 5, repeatMinutes = 15, maxRepeats = 3),
            scheduledAtMillis = 2_000_000_000_000L,
            deliveredCount = 2,
            correlation = PushCorrelationResult(3, PushCorrelationBasis.SHARED_PARENT),
        )

        assertEquals("CORRELATED", snapshot.outcome)
        assertEquals("DOWN_AND_RECOVERY", snapshot.mode)
        assertEquals("CRITICAL", snapshot.configuredSeverity)
        assertEquals("SILENT", snapshot.effectiveSeverity)
        assertEquals(5, snapshot.firstDelayMinutes)
        assertEquals(15, snapshot.repeatMinutes)
        assertEquals(3, snapshot.maxRepeats)
        assertEquals(2_000_000_000_000L, snapshot.scheduledAtMillis)
        assertEquals(2, snapshot.deliveredCount)
        assertEquals("SHARED_PARENT", snapshot.correlationBasis)
        assertEquals(3, snapshot.correlationCount)
    }
}
