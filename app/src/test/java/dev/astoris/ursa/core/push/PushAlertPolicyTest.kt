package dev.astoris.ursa.core.push

import dev.astoris.ursa.data.model.ManagedPushNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushAlertPolicyTest {

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
