package dev.astoris.ursa.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerWebLinkTest {

    @Test
    fun monitorLinkKeepsReverseProxyBasePath() {
        assertEquals(
            "https://status.example.com/kuma/dashboard/42",
            ServerWebLink.monitor("https://status.example.com/kuma/", 42),
        )
    }

    @Test
    fun monitorLinkRejectsUnsafeOrAmbiguousServerAddresses() {
        assertNull(ServerWebLink.monitor("ftp://status.example.com", 42))
        assertNull(ServerWebLink.monitor("https://user:secret@status.example.com", 42))
        assertNull(ServerWebLink.monitor("https://status.example.com?token=secret", 42))
        assertNull(ServerWebLink.monitor("https://status.example.com/#inside", 42))
        assertNull(ServerWebLink.monitor("https://status.example.com", 0))
    }

    @Test
    fun dashboardLinkNeverCopiesConnectionMetadata() {
        assertEquals(
            "http://10.0.2.2:3001/dashboard",
            ServerWebLink.dashboard("http://10.0.2.2:3001"),
        )
    }
}
