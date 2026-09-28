package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun detachedChildRetainsMembershipForTheNextAlert() {
        val expected = "ursa-push-group-deadbeef"

        assertTrue(PushNotificationGrouping.belongsToGroup(expected, null, expected))
        assertTrue(PushNotificationGrouping.belongsToGroup(null, expected, expected))
        assertFalse(PushNotificationGrouping.belongsToGroup(null, null, expected))
        assertFalse(PushNotificationGrouping.belongsToGroup("other", "other", expected))
    }

    @Test
    fun correlationRequiresRecentDownEvidenceAndReportsItsBasis() {
        val scope = ManagedPushScope(
            serverId = "0123456789abcdef0123456789abcdef",
            serverUrl = "https://kuma.example.test",
            monitors = (1..11).map { id ->
                ManagedPushMonitor(
                    id = id,
                    name = "Monitor $id",
                    type = "http",
                    parentId = 99.takeIf { id in 1..3 },
                    tags = if (id in 4..6) listOf("edge") else emptyList(),
                    active = true,
                )
            },
            updatedAtMillis = 1L,
        )

        assertEquals(
            PushCorrelationResult(3, PushCorrelationBasis.SHARED_PARENT),
            PushStormCorrelation.evaluate(candidates(1..3) + candidates(7..7), scope),
        )
        assertEquals(
            PushCorrelationResult(3, PushCorrelationBasis.SHARED_TAG),
            PushStormCorrelation.evaluate(candidates(4..7), scope),
        )
        assertEquals(
            PushCorrelationResult(5, PushCorrelationBasis.SERVER_BURST),
            PushStormCorrelation.evaluate(candidates(7..11), scope),
        )
        assertNull(PushStormCorrelation.evaluate(candidates(7..10), scope))
        assertNull(
            PushStormCorrelation.evaluate(
                (7..11).mapIndexed { index, id ->
                    PushCorrelationCandidate(id, 0, index * 130_000L)
                },
                scope,
            ),
        )
        assertNull(
            PushStormCorrelation.evaluate(
                (1..5).map { PushCorrelationCandidate(it, 1, 1_000L) },
                scope,
            ),
        )
    }

    private fun candidates(ids: IntRange): List<PushCorrelationCandidate> =
        ids.map { PushCorrelationCandidate(it, 0, 1_000L) }
}
