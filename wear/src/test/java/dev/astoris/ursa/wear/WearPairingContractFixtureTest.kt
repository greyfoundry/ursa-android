package dev.astoris.ursa.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WearPairingContractFixtureTest {

    @Test fun released_pairing_transport_identifiers_remain_stable() {
        assertEquals("/ursa/session/v1", WearPairingPayload.MESSAGE_PATH)
        assertEquals("ursa_session_receiver", WearPairingPayload.CAPABILITY)
        assertEquals("pauseMonitor", WearMonitorAction.PAUSE.eventName)
        assertEquals("resumeMonitor", WearMonitorAction.RESUME.eventName)
    }

    @Test fun released_v1_pairing_payload_remains_readable() {
        val bytes = requireNotNull(
            javaClass.getResource("/fixtures/wear_pairing_v1.json"),
        ).readBytes()

        val payload = WearPairingPayload.parse(bytes)

        assertNotNull(payload)
        assertEquals("https://minimal.example.test", payload!!.serverUrl)
        assertEquals("fixture-wear-session", payload.sessionToken)
        assertEquals("Home", payload.serverName)
        assertEquals(listOf(WearActionHeader("X-Fixture", "fixture-header")), payload.headers)
        assertEquals(WearPairingPayload.LEGACY_PROTOCOL_VERSION, payload.protocolVersion)
        assertEquals(WearAccessCapability.entries.toSet(), payload.allowedCapabilities)
        assertEquals(payload, WearPairingPayload.parse(payload.encode()))
    }

    @Test fun version2_restricted_pairing_fixture_is_fail_closed() {
        val bytes = requireNotNull(
            javaClass.getResource("/fixtures/wear_pairing_v2_restricted.json"),
        ).readBytes()

        val payload = WearPairingPayload.parseVersion2(bytes)

        assertNotNull(payload)
        assertEquals(WearPairingPayload.CURRENT_PROTOCOL_VERSION, payload!!.protocolVersion)
        assertEquals(WearPairingPayload.CURRENT_POLICY_VERSION, payload.policyVersion)
        assertEquals(emptySet<WearAccessCapability>(), payload.allowedCapabilities)
        assertEquals(payload, WearPairingPayload.parse(payload.encode()))
    }
}
