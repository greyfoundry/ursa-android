package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushAlertWorkTest {

    @Test
    fun workIdentityIsStableScopedAndDoesNotExposeServerScope() {
        val server = "0123456789abcdef0123456789abcdef"
        val identity = PushAlertWork.identity(server, 42)

        assertEquals(identity, PushAlertWork.identity(server, 42))
        assertNotEquals(identity, PushAlertWork.identity(server, 43))
        assertTrue(identity!!.tag.startsWith("ursa-push-alert-"))
        assertTrue(identity.tag.length < 64)
        assertTrue(server !in identity.tag)
        assertNotEquals(identity.workName(0), identity.workName(1))
        assertEquals(identity.notificationId, PushAlertWork.identity(server, 42)?.notificationId)
    }

    @Test
    fun workIdentityRejectsUnmanagedOrInvalidMonitors() {
        assertNull(PushAlertWork.identity(null, 42))
        assertNull(PushAlertWork.identity("bad", 42))
        assertNull(PushAlertWork.identity("0123456789abcdef0123456789abcdef", 0))
    }
}
