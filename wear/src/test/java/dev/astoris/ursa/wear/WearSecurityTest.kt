package dev.astoris.ursa.wear

import com.google.crypto.tink.subtle.AesGcmJce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSecurityTest {
    @Test
    fun sessionTokenRoundTripsWithoutPlaintextStorage() {
        val aead = AesGcmJce(ByteArray(32) { it.toByte() })

        val encrypted = WearSecretCodec.encrypt(aead, "private-session-token")

        assertFalse(encrypted.contains("private-session-token"))
        assertEquals("private-session-token", WearSecretCodec.decrypt(aead, encrypted))
    }

    @Test
    fun tamperedOrWrongKeyCiphertextIsRejected() {
        val first = AesGcmJce(ByteArray(32) { it.toByte() })
        val second = AesGcmJce(ByteArray(32) { (it + 1).toByte() })
        val encrypted = WearSecretCodec.encrypt(first, "token")
        val replacement = if (encrypted.last() == 'A') 'B' else 'A'

        assertNull(WearSecretCodec.decrypt(first, encrypted.dropLast(1) + replacement))
        assertNull(WearSecretCodec.decrypt(second, encrypted))
    }

    @Test
    fun monitorActionsMapOnlyToSupportedKumaEvents() {
        assertEquals("pauseMonitor", WearMonitorAction.PAUSE.eventName)
        assertEquals("resumeMonitor", WearMonitorAction.RESUME.eventName)
        assertTrue(WearActionMessage.clean("  Updated\nmonitor  ").length <= 160)
        assertEquals("Updated monitor", WearActionMessage.clean("  Updated\nmonitor  "))
    }

    @Test
    fun pairingPayloadAcceptsOnlyBoundedValidSessions() {
        val encoded = WearPairingPayload(
            serverUrl = "https://kuma.example.com/base",
            sessionToken = "private-token",
            serverName = "Home\nserver",
            headers = listOf(WearActionHeader("CF-Access-Client-Id", "client-id")),
        ).encode()

        val decoded = WearPairingPayload.parse(encoded)

        assertEquals("https://kuma.example.com/base", decoded?.serverUrl)
        assertEquals("private-token", decoded?.sessionToken)
        assertEquals("Home server", decoded?.serverName)
        assertEquals(listOf(WearActionHeader("CF-Access-Client-Id", "client-id")), decoded?.headers)
        assertNull(WearPairingPayload.parse("{}".encodeToByteArray()))
        assertNull(
            WearPairingPayload.parse(
                WearPairingPayload("javascript://bad", "token", "Bad", emptyList()).encode(),
            ),
        )
        assertNull(
            WearPairingPayload.parse(
                WearPairingPayload(
                    "https://kuma.example.com",
                    "token",
                    "Bad header",
                    listOf(WearActionHeader("X-Test", "bad\nvalue")),
                ).encode(),
            ),
        )
        assertNull(
            WearPairingPayload.parse(
                WearPairingPayload(
                    "https://kuma.example.com",
                    "token",
                    "Too many headers",
                    List(9) { WearActionHeader("X-Test-$it", "value") },
                ).encode(),
            ),
        )
        assertNull(
            WearPairingPayload.parse(
                """{"serverUrl":[],"sessionToken":"token"}""".encodeToByteArray(),
            ),
        )
        assertNull(WearPairingPayload.parse(ByteArray(20_000)))
    }

    @Test fun version2PairingEnforcesTransferredCapabilities() {
        val encoded = WearPairingPayload(
            serverUrl = "https://kuma.example.com",
            sessionToken = "private-token",
            serverName = "Restricted",
            protocolVersion = WearPairingPayload.CURRENT_PROTOCOL_VERSION,
            policyVersion = WearPairingPayload.CURRENT_POLICY_VERSION,
            allowedCapabilities = emptySet(),
        ).encode()

        val decoded = WearPairingPayload.parseVersion2(encoded)

        assertEquals(WearPairingPayload.CURRENT_PROTOCOL_VERSION, decoded?.protocolVersion)
        assertEquals(WearPairingPayload.CURRENT_POLICY_VERSION, decoded?.policyVersion)
        assertFalse(requireNotNull(decoded).allows(WearMonitorAction.PAUSE))
        assertFalse(
            WearActionConfig(
                decoded.serverUrl,
                decoded.sessionToken,
                decoded.headers,
                decoded.allowedCapabilities,
            ).allows(WearMonitorAction.RESUME),
        )
        assertNull(WearPairingPayload.parseVersion1(encoded))
    }

    @Test fun version2RejectsUnknownPolicyAndCapabilities() {
        assertNull(
            WearPairingPayload.parseVersion2(
                """{"protocolVersion":2,"policyVersion":99,"serverUrl":"https://kuma.example.com","sessionToken":"token","allowedCapabilities":[]}"""
                    .encodeToByteArray(),
            ),
        )
        assertNull(
            WearPairingPayload.parseVersion2(
                """{"protocolVersion":2,"policyVersion":1,"serverUrl":"https://kuma.example.com","sessionToken":"token","allowedCapabilities":["UNKNOWN"]}"""
                    .encodeToByteArray(),
            ),
        )
    }
}
