package dev.astoris.ursa.core.push

import dev.astoris.ursa.core.network.ConnectionFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPathHealthTest {
    @Test
    fun explicitDisconnectStaysQuietWhileUnexpectedLossIsActionable() {
        val unexpected = health(
            hasEndpoint = false,
            diagnostics = PushDiagnostics(
                lastRegistrationAtMs = 1_000L,
                lastUnexpectedUnregisterAtMs = 2_000L,
            ),
        )
        val explicit = PushPathHealthResolver.resolve(
            hasDistributor = false,
            hasEndpoint = false,
            diagnostics = unexpectedDiagnostics(),
            setup = PushSetupSignal.UNCHECKED,
            connectionFailure = null,
            nowMillis = 3_000L,
        )

        assertEquals(PushPathState.DISTRIBUTOR_DISCONNECTED, unexpected.state)
        assertEquals(PushPathState.NOT_CONNECTED, explicit.state)
        assertTrue(PushUnregisterPolicy.isExpected(1_000L, 121_000L))
        assertFalse(PushUnregisterPolicy.isExpected(1_000L, 121_001L))
        assertFalse(PushUnregisterPolicy.isExpected(null, 1_000L))
    }

    @Test
    fun newestRegistrationSignalWinsWithoutDiscardingHistory() {
        val failed = health(
            hasEndpoint = false,
            diagnostics = PushDiagnostics(
                lastRegistrationAtMs = 1_000L,
                lastErrorAtMs = 2_000L,
                lastError = PushRegistrationError.NETWORK,
            ),
        )
        val recovered = health(
            diagnostics = PushDiagnostics(
                lastRegistrationAtMs = 3_000L,
                lastErrorAtMs = 2_000L,
                lastError = PushRegistrationError.NETWORK,
            ),
        )

        assertEquals(PushPathState.REGISTRATION_FAILED, failed.state)
        assertEquals(PushRegistrationError.NETWORK, failed.registrationError)
        assertEquals(PushPathState.REGISTERED, recovered.state)
    }

    @Test
    fun setupSignalsRemainDistinctFromMonitorState() {
        assertEquals(PushPathState.PROVIDER_MISSING, health(setup = PushSetupSignal.MISSING).state)
        assertEquals(PushPathState.BINDING_STALE, health(setup = PushSetupSignal.STALE).state)
        assertEquals(PushPathState.SETUP_ERROR, health(setup = PushSetupSignal.SETUP_ERROR).state)

        val unavailable = health(
            setup = PushSetupSignal.UNAVAILABLE,
            connectionFailure = ConnectionFailureReason.CERTIFICATE,
        )
        assertEquals(PushPathState.KUMA_UNAVAILABLE, unavailable.state)
        assertEquals(ConnectionFailureReason.CERTIFICATE, unavailable.connectionFailure)
    }

    @Test
    fun deliveryTestBecomesTimeoutOnlyAfterItsEvidenceWindow() {
        val diagnostics = PushDiagnostics(deliveryTestRequestedAtMs = 1_000L)

        assertEquals(
            PushPathState.DELIVERY_WAITING,
            health(diagnostics = diagnostics, nowMillis = 30_999L).state,
        )
        assertEquals(
            PushPathState.DELIVERY_TIMEOUT,
            health(diagnostics = diagnostics, nowMillis = 31_000L).state,
        )
        assertEquals(
            PushPathState.VERIFIED,
            health(diagnostics = diagnostics.copy(deliveryTestReceivedAtMs = 2_000L)).state,
        )
        assertEquals(
            PushPathState.DELIVERY_REJECTED,
            health(diagnostics = diagnostics.copy(deliveryTestRejectedAtMs = 2_000L)).state,
        )
    }

    private fun health(
        hasEndpoint: Boolean = true,
        diagnostics: PushDiagnostics = PushDiagnostics(),
        setup: PushSetupSignal = PushSetupSignal.CURRENT,
        connectionFailure: ConnectionFailureReason? = null,
        nowMillis: Long = 10_000L,
    ): PushPathHealth = PushPathHealthResolver.resolve(
        hasDistributor = true,
        hasEndpoint = hasEndpoint,
        diagnostics = diagnostics,
        setup = setup,
        connectionFailure = connectionFailure,
        nowMillis = nowMillis,
    )

    private fun unexpectedDiagnostics() = PushDiagnostics(
        lastRegistrationAtMs = 1_000L,
        lastUnexpectedUnregisterAtMs = 2_000L,
    )
}
