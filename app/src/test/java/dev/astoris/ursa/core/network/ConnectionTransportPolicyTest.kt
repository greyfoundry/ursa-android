package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.CleartextPolicy
import dev.astoris.ursa.data.model.ServerConnection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTransportPolicyTest {
    @Test fun deny_blocks_http_but_never_https() {
        assertFalse(ConnectionTransportPolicy.allows("http://kuma.test", CleartextPolicy.DENY))
        assertTrue(ConnectionTransportPolicy.allows("https://kuma.test", CleartextPolicy.DENY))
    }

    @Test fun legacy_and_explicit_allow_preserve_http_access() {
        assertTrue(ConnectionTransportPolicy.allows("http://kuma.test", CleartextPolicy.LEGACY))
        assertTrue(ConnectionTransportPolicy.allows("http://kuma.test", CleartextPolicy.ALLOW))
    }

    @Test fun connection_overload_uses_persisted_policy() {
        assertFalse(
            ConnectionTransportPolicy.allows(
                ServerConnection(
                    url = "http://kuma.test",
                    username = "",
                    cleartextPolicy = CleartextPolicy.DENY,
                ),
            ),
        )
    }
}
