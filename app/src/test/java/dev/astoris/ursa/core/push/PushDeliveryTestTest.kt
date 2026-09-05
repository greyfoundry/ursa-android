package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDeliveryTestTest {

    @Test
    fun buildsAndMatchesExactKumaTestMessage() {
        val token = "a1b2c3d4e5f6"
        assertEquals("URSA delivery test $token", PushDeliveryTest.notificationName(token))
        assertTrue(PushDeliveryTest.matches("URSA delivery test $token Testing", token))
    }

    @Test
    fun rejectsInvalidTokensAndLookalikeMessages() {
        assertNull(PushDeliveryTest.notificationName("too-short"))
        assertFalse(PushDeliveryTest.matches("prefix URSA delivery test a1b2c3d4e5f6 Testing", "a1b2c3d4e5f6"))
        assertFalse(PushDeliveryTest.matches("URSA delivery test a1b2c3d4e5f6", "a1b2c3d4e5f6"))
    }
}
