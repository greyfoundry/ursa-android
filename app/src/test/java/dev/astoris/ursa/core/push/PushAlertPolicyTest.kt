package dev.astoris.ursa.core.push

import dev.astoris.ursa.data.model.ManagedPushNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushAlertPolicyTest {

    @Test
    fun severitiesRouteToStableAndroidChannelBehavior() {
        assertEquals(
            PushChannelRoute("ursa_monitors_critical", highPriority = true, sound = true, vibration = true),
            PushSeverityPolicy.route(PushSeverity.CRITICAL),
        )
        assertEquals(
            PushChannelRoute("ursa_monitors_standard", highPriority = false, sound = true, vibration = false),
            PushSeverityPolicy.route(PushSeverity.STANDARD),
        )
        assertEquals(
            PushChannelRoute("ursa_monitors_silent", highPriority = false, sound = false, vibration = false),
            PushSeverityPolicy.route(PushSeverity.SILENT),
        )
    }

    @Test
    fun missingOrInvalidSavedSeverityKeepsExistingCriticalBehavior() {
        assertEquals(PushSeverity.CRITICAL, PushSeverityPolicy.decode(null))
        assertEquals(PushSeverity.CRITICAL, PushSeverityPolicy.decode(""))
        assertEquals(PushSeverity.CRITICAL, PushSeverityPolicy.decode("future-value"))
        assertEquals(PushSeverity.STANDARD, PushSeverityPolicy.decode("STANDARD"))
        assertEquals(PushSeverity.SILENT, PushSeverityPolicy.decode("SILENT"))
    }

    @Test
    fun preferenceKeysKeepModeAndSeverityScopedToOneManagedServer() {
        val serverId = "0123456789abcdef0123456789abcdef"
        assertEquals("$serverId:42", PushAlertPreferenceKey.mode(serverId, 42))
        assertEquals("severity:$serverId:42", PushAlertPreferenceKey.severity(serverId, 42))
        assertTrue(PushAlertPreferenceKey.belongsToServer("$serverId:42", serverId))
        assertTrue(PushAlertPreferenceKey.belongsToServer("severity:$serverId:42", serverId))
        assertFalse(PushAlertPreferenceKey.belongsToServer("other:$serverId:42", serverId))
        assertNull(PushAlertPreferenceKey.mode("not-valid", 42))
        assertNull(PushAlertPreferenceKey.severity(serverId, 0))
    }

    @Test
    fun modesApplyOnlyTheirDocumentedTransitions() {
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.MUTED, 0))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.MUTED, 1))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.MUTED, 2))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.MUTED, 3))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.MUTED, null))

        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_ONLY, 0))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_ONLY, 1))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_ONLY, 2))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_ONLY, 3))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_ONLY, null))

        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_AND_RECOVERY, 0))
        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_AND_RECOVERY, 1))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_AND_RECOVERY, 2))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_AND_RECOVERY, 3))
        assertFalse(PushAlertPolicy.shouldNotify(PushAlertMode.DOWN_AND_RECOVERY, null))

        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.ALL_TRANSITIONS, 0))
        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.ALL_TRANSITIONS, 1))
        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.ALL_TRANSITIONS, 2))
        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.ALL_TRANSITIONS, 3))
        assertTrue(PushAlertPolicy.shouldNotify(PushAlertMode.ALL_TRANSITIONS, null))
    }

    @Test
    fun managedTemplateRequiresServerScopeAndKeepsStandardFields() {
        val serverId = "0123456789abcdef0123456789abcdef"
        val body = ManagedPushNotification.customWebhookBody(serverId)
        assertNotNull(body)
        assertTrue(body!!.contains("{{ heartbeatJSON | json }}"))
        assertTrue(body.contains("{{ monitorJSON | json }}"))
        assertTrue(body.contains("{{ msg | json }}"))
        assertTrue(body.contains(serverId))
        assertNull(ManagedPushNotification.customWebhookBody("not-valid"))
    }
}
