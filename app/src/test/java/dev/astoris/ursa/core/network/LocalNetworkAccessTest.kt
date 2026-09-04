package dev.astoris.ursa.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun android17RecognizesDirectLanAddresses() {
        assertTrue(LocalNetworkAccess.requiresPermission("http://10.0.2.2:3001", 37))
        assertTrue(LocalNetworkAccess.requiresPermission("https://192.168.1.20", 37))
        assertTrue(LocalNetworkAccess.requiresPermission("https://kuma.local", 37))
        assertTrue(LocalNetworkAccess.requiresPermission("https://monitor", 37))
        assertTrue(LocalNetworkAccess.requiresPermission("https://[fd00::20]:3001", 37))
    }

    @Test
    fun publicLoopbackAndOlderAndroidDoNotPrompt() {
        assertFalse(LocalNetworkAccess.requiresPermission("https://status.example.com", 37))
        assertFalse(LocalNetworkAccess.requiresPermission("http://127.0.0.1:3001", 37))
        assertFalse(LocalNetworkAccess.requiresPermission("http://10.0.2.2:3001", 36))
        assertFalse(LocalNetworkAccess.requiresPermission("not a url", 37))
    }
}
