package dev.astoris.ursa.core.push

import java.time.DayOfWeek
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushEffectivePolicyTest {
    private val quietOff = PushQuietHours(enabled = false)
    private val events = PushEventPreferences()

    @Test
    fun defaultsPreserveImmediateCriticalAllTransitionDelivery() {
        val policy = resolve()

        assertEquals(PushAlertMode.ALL_TRANSITIONS, policy.mode.value)
        assertEquals(PushPolicyScope.DEFAULT, policy.mode.source)
        assertEquals(PushSeverity.CRITICAL, policy.effectiveSeverity)
        assertEquals(PushDownDelivery.IMMEDIATE, policy.downDelivery)
        assertNull(policy.scheduledAtMillis)
        assertTrue(policy.recoveryWillNotify)
        assertTrue(policy.maintenanceWillNotify)
    }

    @Test
    fun precedenceIsMonitorThenTagThenGroupThenServerThenDefault() {
        val layers = listOf(
            PushPolicyLayer(PushPolicyScope.SERVER, "server", mode = PushAlertMode.MUTED),
            PushPolicyLayer(PushPolicyScope.GROUP, "10", mode = PushAlertMode.DOWN_ONLY),
            PushPolicyLayer(
                PushPolicyScope.TAG,
                "1",
                mode = PushAlertMode.DOWN_AND_RECOVERY,
                severity = PushSeverity.STANDARD,
            ),
            PushPolicyLayer(PushPolicyScope.TAG, "2", severity = PushSeverity.SILENT),
            PushPolicyLayer(
                PushPolicyScope.MONITOR,
                "42",
                mode = PushAlertMode.ALL_TRANSITIONS,
                timing = PushAlertTiming(firstDelayMinutes = 5),
            ),
        )

        val policy = resolve(layers)

        assertEquals(PushAlertMode.MUTED, resolve(layers.take(1)).mode.value)
        assertEquals(PushAlertMode.DOWN_ONLY, resolve(layers.take(2)).mode.value)
        assertEquals(PushAlertMode.DOWN_AND_RECOVERY, resolve(layers.take(3)).mode.value)
        assertEquals(PushAlertMode.ALL_TRANSITIONS, policy.mode.value)
        assertEquals(PushPolicyScope.MONITOR, policy.mode.source)
        assertEquals(PushSeverity.SILENT, policy.severity.value)
        assertEquals(PushPolicyScope.TAG, policy.severity.source)
        assertEquals("2", policy.severity.sourceKey)
        assertEquals(5, policy.timing.value.firstDelayMinutes)
        assertEquals(PushPolicyScope.MONITOR, policy.timing.source)
    }

    @Test
    fun quietHoursAndSnoozeExplainTheActualScheduledOutcome() {
        val quiet = PushQuietHours(
            enabled = true,
            startMinute = 0,
            endMinute = 60,
            daysMask = PushQuietHours.dayMask(DayOfWeek.FRIDAY),
        )
        val snoozedUntil = NOW + 60 * 60_000L

        val policy = resolve(quietHours = quiet, snoozedUntilMillis = snoozedUntil)

        assertTrue(policy.quietHoursActive)
        assertEquals(PushSeverity.SILENT, policy.effectiveSeverity)
        assertEquals(PushDownDelivery.SNOOZED, policy.downDelivery)
        assertEquals(snoozedUntil, policy.scheduledAtMillis)
    }

    @Test
    fun setupAssignmentDeviceAndModeBlockersAreReportedInPipelineOrder() {
        assertEquals(PushDownDelivery.SETUP_REQUIRED, resolve(setupCurrent = false).downDelivery)
        assertEquals(PushDownDelivery.NOT_ASSIGNED, resolve(providerAssigned = false).downDelivery)
        assertEquals(PushDownDelivery.DEVICE_BLOCKED, resolve(notificationsAllowed = false).downDelivery)
        assertEquals(
            PushDownDelivery.MODE_BLOCKED,
            resolve(listOf(PushPolicyLayer(PushPolicyScope.MONITOR, "42", mode = PushAlertMode.MUTED))).downDelivery,
        )
    }

    @Test
    fun downParentSuppressesBeforeDelayAndNamesItsStableIdentity() {
        val policy = PushEffectivePolicyResolver.resolve(
            layers = emptyList(),
            quietHours = quietOff,
            eventPreferences = events,
            setupCurrent = true,
            providerAssigned = true,
            notificationsAllowed = true,
            snoozedUntilMillis = null,
            dependencyGraph = PushDependencyGraph(mapOf(42 to setOf(7))),
            monitorId = 42,
            dependencyStatuses = mapOf(7 to 0),
            nowMillis = NOW,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(PushDownDelivery.DEPENDENCY_SUPPRESSED, policy.downDelivery)
        assertEquals(7, policy.dependencySuppression?.parentId)
        assertEquals(setOf(7), policy.dependencyParentIds)
        assertNull(policy.scheduledAtMillis)
    }

    @Test
    fun recoveryAndMaintenanceRespectModeAndGlobalOptIns() {
        val policy = resolve(
            layers = listOf(
                PushPolicyLayer(PushPolicyScope.MONITOR, "42", mode = PushAlertMode.DOWN_AND_RECOVERY),
            ),
            eventPreferences = PushEventPreferences(recoveryEnabled = false, maintenanceEnabled = true),
        )

        assertFalse(policy.recoveryWillNotify)
        assertFalse(policy.maintenanceWillNotify)
    }

    private fun resolve(
        layers: List<PushPolicyLayer> = emptyList(),
        quietHours: PushQuietHours = quietOff,
        eventPreferences: PushEventPreferences = events,
        setupCurrent: Boolean = true,
        providerAssigned: Boolean = true,
        notificationsAllowed: Boolean = true,
        snoozedUntilMillis: Long? = null,
    ): PushEffectivePolicy = PushEffectivePolicyResolver.resolve(
        layers = layers,
        quietHours = quietHours,
        eventPreferences = eventPreferences,
        setupCurrent = setupCurrent,
        providerAssigned = providerAssigned,
        notificationsAllowed = notificationsAllowed,
        snoozedUntilMillis = snoozedUntilMillis,
        nowMillis = NOW,
        zoneId = ZoneOffset.UTC,
    )

    private companion object {
        const val NOW = 1_798_761_600_000L // 2027-01-01T00:00:00Z, Friday
    }
}
