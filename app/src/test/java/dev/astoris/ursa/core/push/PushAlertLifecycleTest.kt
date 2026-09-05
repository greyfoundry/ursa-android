package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Test

class PushAlertLifecycleTest {

    @Test
    fun downAlertDeliversImmediatelyByDefault() {
        assertEquals(
            PushAlertDecision.Deliver,
            PushAlertLifecycle.onDown(PushAlertTiming(), NOW),
        )
    }

    @Test
    fun downAlertWaitsForConfiguredDelay() {
        assertEquals(
            PushAlertDecision.WaitUntil(NOW + 5 * MINUTE),
            PushAlertLifecycle.onDown(PushAlertTiming(firstDelayMinutes = 5), NOW),
        )
    }

    @Test
    fun activeSnoozeTakesPrecedenceOverInitialDelay() {
        assertEquals(
            PushAlertDecision.WaitUntil(NOW + 60 * MINUTE),
            PushAlertLifecycle.onDown(
                timing = PushAlertTiming(firstDelayMinutes = 5),
                nowMillis = NOW,
                snoozedUntilMillis = NOW + 60 * MINUTE,
            ),
        )
    }

    @Test
    fun expiredSnoozeDoesNotDelayDelivery() {
        assertEquals(
            PushAlertDecision.Deliver,
            PushAlertLifecycle.onDown(
                timing = PushAlertTiming(),
                nowMillis = NOW,
                snoozedUntilMillis = NOW - 1,
            ),
        )
    }

    @Test
    fun recoveryAndAcknowledgementCancelPendingAlert() {
        assertEquals(PushAlertDecision.Cancel, PushAlertLifecycle.onRecovery())
        assertEquals(PushAlertDecision.Cancel, PushAlertLifecycle.onAcknowledge())
    }

    @Test
    fun repeatsStopAtTheConfiguredBound() {
        val timing = PushAlertTiming(repeatMinutes = 15, maxRepeats = 3)

        assertEquals(
            PushAlertDecision.WaitUntil(NOW + 15 * MINUTE),
            PushAlertLifecycle.afterDelivery(timing, NOW, deliveredRepeats = 0),
        )
        assertEquals(
            PushAlertDecision.WaitUntil(NOW + 15 * MINUTE),
            PushAlertLifecycle.afterDelivery(timing, NOW, deliveredRepeats = 2),
        )
        assertEquals(
            PushAlertDecision.Complete,
            PushAlertLifecycle.afterDelivery(timing, NOW, deliveredRepeats = 3),
        )
    }

    @Test
    fun disabledRepeatsCompleteAfterInitialDelivery() {
        assertEquals(
            PushAlertDecision.Complete,
            PushAlertLifecycle.afterDelivery(PushAlertTiming(), NOW, deliveredRepeats = 0),
        )
    }

    @Test
    fun timingValuesAreConstrainedToSupportedSafeChoices() {
        assertEquals(
            PushAlertTiming(firstDelayMinutes = 0, repeatMinutes = 0, maxRepeats = 0),
            PushAlertTiming(firstDelayMinutes = -1, repeatMinutes = 2, maxRepeats = -4).normalized(),
        )
        assertEquals(
            PushAlertTiming(firstDelayMinutes = 10, repeatMinutes = 30, maxRepeats = 5),
            PushAlertTiming(firstDelayMinutes = 99, repeatMinutes = 99, maxRepeats = 99).normalized(),
        )
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
    }
}
