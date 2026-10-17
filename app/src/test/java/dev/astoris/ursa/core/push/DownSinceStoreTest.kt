package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DownSinceStoreTest {

    @Test fun managedServersCannotShareDowntimeState() {
        val first = "0123456789abcdef0123456789abcdef"
        val second = "fedcba9876543210fedcba9876543210"

        assertNotEquals(DownSinceStore.key(first, 7), DownSinceStore.key(second, 7))
        assertEquals("down_7", DownSinceStore.key(null, 7))
        assertEquals("down_7", DownSinceStore.key("invalid", 7))
    }
}
