package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PushNotificationGroupingTest {
    @Test
    fun groupIdentityIsStableScopedAndDoesNotExposeServerId() {
        val firstServer = "0123456789abcdef0123456789abcdef"
        val secondServer = "fedcba9876543210fedcba9876543210"

        val first = PushNotificationGrouping.identity(firstServer)

        assertNotNull(first)
        assertEquals(first, PushNotificationGrouping.identity(firstServer))
        assertNotEquals(first, PushNotificationGrouping.identity(secondServer))
        assertFalse(first!!.key.contains(firstServer))
        assertNull(PushNotificationGrouping.identity("invalid"))
    }

    @Test
    fun firstAlertStaysIndividualAndSecondCreatesBoundedSummary() {
        assertNull(PushNotificationGrouping.summarize(listOf("API is Down")))

        val summary = PushNotificationGrouping.summarize(
            listOf(
                "API is Down",
                "Database is Down\ncheck failed",
                "Cache is Down",
                "Router is Down",
                "W".repeat(120),
                "Sixth is Down",
            ),
        )

        assertEquals(6, summary?.count)
        assertEquals(5, summary?.lines?.size)
        assertEquals("Database is Down check failed", summary?.lines?.get(1))
        assertEquals(96, summary?.lines?.last()?.length)
    }
}
