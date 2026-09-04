package dev.astoris.ursa.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDeepLinkTest {
    @Test
    fun scopedRoutesRoundTripWithoutExposingServerUrl() {
        val scope = AppDeepLink.serverScope("https://kuma.example.com/base")
        val route = AppDeepLink.monitor("https://kuma.example.com/base", 42)

        assertEquals(16, scope.length)
        assertTrue(route.contains(scope))
        assertTrue("server URL must not be embedded", !route.contains("kuma.example.com"))
        assertEquals(AppRoute.Monitor(scope, 42), AppDeepLink.parse(route))
        assertEquals(AppRoute.Connection(scope), AppDeepLink.parse(AppDeepLink.connection("https://kuma.example.com/base")))
        assertEquals(AppRoute.Incident(scope, 42), AppDeepLink.parse(AppDeepLink.incident("https://kuma.example.com/base", 42)))
    }

    @Test
    fun staticRoutesRemainStable() {
        assertEquals(AppRoute.Push, AppDeepLink.parse("ursa://push"))
        assertEquals(AppRoute.Settings, AppDeepLink.parse("ursa://settings"))
    }

    @Test
    fun malformedOrAmbiguousRoutesAreRejected() {
        listOf(
            "https://monitor/0123456789abcdef/1",
            "ursa://monitor/0123456789abcdef/0",
            "ursa://monitor/0123456789abcdef/-1",
            "ursa://monitor/not-a-scope/1",
            "ursa://monitor/0123456789abcdef/1/extra",
            "ursa://monitor/0123456789abcdef/1?next=2",
            "ursa://user@monitor/0123456789abcdef/1",
            "ursa://incident/0123456789abcdef/%2F1",
        ).forEach { assertNull(it, AppDeepLink.parse(it)) }
    }
}
