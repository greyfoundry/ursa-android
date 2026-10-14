package dev.astoris.ursa.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportSafetyTest {
    private val scope = "0376a9af36e38c3e"

    @Test
    fun friendlyDisplayNameIsRetained() {
        assertEquals("Home Kuma", credentialFreeServerLabel(" Home Kuma ", scope))
    }

    @Test
    fun addressLikeDisplayNamesFallBackToOneWayScope() {
        val expected = "Server 0376a9af36e3"
        assertEquals(expected, credentialFreeServerLabel("10.0.2.2:3001", scope))
        assertEquals(expected, credentialFreeServerLabel("https://kuma.example.com", scope))
        assertEquals(expected, credentialFreeServerLabel("kuma.example.com:3001", scope))
        assertEquals(expected, credentialFreeServerLabel("user@kuma", scope))
    }

    @Test
    fun missingScopeUsesGenericFallback() {
        assertEquals("Saved server", credentialFreeServerLabel("192.168.1.5", ""))
    }
}
