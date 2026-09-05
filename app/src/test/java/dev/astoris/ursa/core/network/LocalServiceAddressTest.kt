package dev.astoris.ursa.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalServiceAddressTest {
    @Test
    fun formatsHttpAndHttpsEndpoints() {
        assertEquals("http://192.168.1.12:3001", localServiceUrl("http", "192.168.1.12", 3001))
        assertEquals("http://example.local", localServiceUrl("http", "example.local", 80))
        assertEquals("https://example.local", localServiceUrl("https", "example.local", 443))
    }

    @Test
    fun bracketsIpv6AndRemovesInterfaceScope() {
        assertEquals("https://[fe80::1234]:8443", localServiceUrl("https", "fe80::1234%wlan0", 8443))
    }

    @Test
    fun rejectsUnsafeOrInvalidAddresses() {
        assertNull(localServiceUrl("ftp", "example.local", 21))
        assertNull(localServiceUrl("http", "bad/host", 80))
        assertNull(localServiceUrl("http", "example.local", 0))
        assertNull(localServiceUrl("https", "example.local", 65_536))
    }
}
