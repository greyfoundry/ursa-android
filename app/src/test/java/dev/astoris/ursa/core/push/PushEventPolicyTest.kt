package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushEventPolicyTest {

    @Test
    fun statusMapsToStableAlertCategories() {
        assertEquals(PushEventCategory.DOWN, PushEventPolicy.category(0))
        assertEquals(PushEventCategory.RECOVERY, PushEventPolicy.category(1))
        assertEquals(PushEventCategory.PENDING, PushEventPolicy.category(2))
        assertEquals(PushEventCategory.MAINTENANCE, PushEventPolicy.category(3))
        assertEquals(PushEventCategory.OTHER, PushEventPolicy.category(null))
    }

    @Test
    fun categoryOptOutsDoNotDisableDownOrPendingAlerts() {
        val preferences = PushEventPreferences(recoveryEnabled = false, maintenanceEnabled = false)

        assertTrue(PushEventPolicy.shouldNotify(0, preferences))
        assertFalse(PushEventPolicy.shouldNotify(1, preferences))
        assertTrue(PushEventPolicy.shouldNotify(2, preferences))
        assertFalse(PushEventPolicy.shouldNotify(3, preferences))
        assertTrue(PushEventPolicy.shouldNotify(null, preferences))
    }

    @Test
    fun recoveryAndMaintenanceUseTheirOwnStableChannels() {
        assertEquals(
            PushChannelRoute("ursa_monitors_recovery", false, true, false),
            PushEventPolicy.route(1, PushSeverity.CRITICAL),
        )
        assertEquals(
            PushChannelRoute("ursa_monitors_maintenance", false, false, false),
            PushEventPolicy.route(3, PushSeverity.CRITICAL),
        )
        assertEquals(
            PushSeverityPolicy.route(PushSeverity.STANDARD),
            PushEventPolicy.route(0, PushSeverity.STANDARD),
        )
    }

    @Test
    fun exactDuplicateInsideWindowIsSuppressedButRealTransitionsAlwaysPass() {
        val down = PushTransitionDedup.evaluate(previous = null, status = 0, nowMillis = NOW)
        assertTrue(down.deliver)

        val duplicate = PushTransitionDedup.evaluate(down.next, status = 0, nowMillis = NOW + 30_000)
        assertFalse(duplicate.deliver)

        val recovery = PushTransitionDedup.evaluate(down.next, status = 1, nowMillis = NOW + 31_000)
        assertTrue(recovery.deliver)

        val downAgain = PushTransitionDedup.evaluate(recovery.next, status = 0, nowMillis = NOW + 32_000)
        assertTrue(downAgain.deliver)
    }

    @Test
    fun sameStatusAfterWindowAndClockRollbackAreDelivered() {
        val previous = PushTransitionRecord(status = 0, atMillis = NOW)

        assertTrue(PushTransitionDedup.evaluate(previous, 0, NOW + 120_001).deliver)
        assertTrue(PushTransitionDedup.evaluate(previous, 0, NOW - 1).deliver)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
