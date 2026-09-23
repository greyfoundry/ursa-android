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
        assertEquals(payload, WearPairingPayload.parse(payload.encode()))
    }
}
