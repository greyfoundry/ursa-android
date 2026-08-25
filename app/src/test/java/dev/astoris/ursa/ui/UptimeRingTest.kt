package dev.astoris.ursa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UptimeRingTest {

    @Test
    fun uptimeIsClampedAndRoundedForCompactDisplay() {
        assertEquals(0f, normalizedUptimeOrNull(-0.2)!!, 0f)
        assertEquals(0.999f, normalizedUptimeOrNull(0.999)!!, 0.0001f)
        assertEquals(100, uptimeDisplayPercentage(0.999))
        assertEquals(100, uptimeDisplayPercentage(1.4))
    }

    @Test
    fun invalidUptimeIsNotRendered() {
        assertNull(normalizedUptimeOrNull(Double.NaN))
        assertNull(normalizedUptimeOrNull(Double.POSITIVE_INFINITY))
        assertNull(uptimeDisplayPercentage(Double.NEGATIVE_INFINITY))
    }
}
