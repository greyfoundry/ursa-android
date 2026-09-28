package dev.astoris.ursa.core.push

import dev.astoris.ursa.core.network.ConnectionFailureReason

enum class PushSetupSignal {
    UNCHECKED,
    MISSING,
    CURRENT,
    STALE,
    UNAVAILABLE,
    SETUP_ERROR,
}

enum class PushPathState {
    NOT_CONNECTED,
    DISTRIBUTOR_DISCONNECTED,
    REGISTRATION_FAILED,
    REGISTERED,
    KUMA_UNAVAILABLE,
    PROVIDER_MISSING,
    BINDING_STALE,
    SETUP_ERROR,
    DELIVERY_REJECTED,
    DELIVERY_WAITING,
    DELIVERY_TIMEOUT,
    VERIFIED,
}

data class PushPathHealth(
    val state: PushPathState,
    val registrationError: PushRegistrationError? = null,
    val connectionFailure: ConnectionFailureReason? = null,
)

object PushPathHealthResolver {
    const val DELIVERY_TIMEOUT_MILLIS = 30_000L

    fun resolve(
        hasDistributor: Boolean,
        hasEndpoint: Boolean,
        diagnostics: PushDiagnostics,
        setup: PushSetupSignal,
        connectionFailure: ConnectionFailureReason?,
        nowMillis: Long,
    ): PushPathHealth {
        if (!hasDistributor) return PushPathHealth(PushPathState.NOT_CONNECTED)

        val latestHealthyAt = maxOf(
            diagnostics.lastRegistrationAtMs ?: 0L,
            diagnostics.lastMessageAtMs ?: 0L,
        )
        val disconnectedAt = diagnostics.lastUnexpectedUnregisterAtMs ?: 0L
        val errorAt = diagnostics.lastErrorAtMs ?: 0L
        if (!hasEndpoint && disconnectedAt > latestHealthyAt) {
            return PushPathHealth(PushPathState.DISTRIBUTOR_DISCONNECTED)
        }
        if (errorAt > latestHealthyAt) {
            return PushPathHealth(PushPathState.REGISTRATION_FAILED, diagnostics.lastError)
        }
        if (!hasEndpoint) return PushPathHealth(PushPathState.NOT_CONNECTED)

        return when (setup) {
            PushSetupSignal.UNCHECKED -> PushPathHealth(PushPathState.REGISTERED)
            PushSetupSignal.MISSING -> PushPathHealth(PushPathState.PROVIDER_MISSING)
            PushSetupSignal.STALE -> PushPathHealth(PushPathState.BINDING_STALE)
            PushSetupSignal.UNAVAILABLE -> PushPathHealth(
                PushPathState.KUMA_UNAVAILABLE,
                connectionFailure = connectionFailure,
            )
            PushSetupSignal.SETUP_ERROR -> PushPathHealth(PushPathState.SETUP_ERROR)
            PushSetupSignal.CURRENT -> deliveryHealth(diagnostics, nowMillis)
        }
    }

    private fun deliveryHealth(diagnostics: PushDiagnostics, nowMillis: Long): PushPathHealth {
        val requestedAt = diagnostics.deliveryTestRequestedAtMs
            ?: return PushPathHealth(PushPathState.REGISTERED)
        if ((diagnostics.deliveryTestRejectedAtMs ?: 0L) >= requestedAt) {
            return PushPathHealth(PushPathState.DELIVERY_REJECTED)
        }
        if ((diagnostics.deliveryTestReceivedAtMs ?: 0L) >= requestedAt) {
            return PushPathHealth(PushPathState.VERIFIED)
        }
        return PushPathHealth(
            if (nowMillis - requestedAt >= DELIVERY_TIMEOUT_MILLIS) {
                PushPathState.DELIVERY_TIMEOUT
            } else {
                PushPathState.DELIVERY_WAITING
            },
        )
    }
}

object PushUnregisterPolicy {
    const val EXPECTED_WINDOW_MILLIS = 2 * 60_000L

    fun isExpected(requestedAtMillis: Long?, nowMillis: Long): Boolean =
        requestedAtMillis != null && nowMillis - requestedAtMillis in 0..EXPECTED_WINDOW_MILLIS
}
