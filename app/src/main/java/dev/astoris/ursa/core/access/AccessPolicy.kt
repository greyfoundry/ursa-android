package dev.astoris.ursa.core.access

import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.data.model.ServerConnection

sealed interface AccessDecision {
    data object Allowed : AccessDecision

    data class Denied(
        val profile: AccessProfile,
        val missing: Set<AccessCapability>,
    ) : AccessDecision
}

/** Pure client-side policy evaluation. Kuma session tokens remain server-authoritative. */
object AccessPolicy {

    fun evaluate(
        connection: ServerConnection,
        vararg required: AccessCapability,
    ): AccessDecision = evaluate(
        profile = connection.accessProfile,
        customCapabilities = connection.customCapabilities,
        required = required.toSet(),
    )

    fun evaluate(
        profile: AccessProfile,
        customCapabilities: Set<AccessCapability>,
        required: Set<AccessCapability>,
    ): AccessDecision {
        if (required.isEmpty()) return AccessDecision.Allowed
        val allowed = effectiveCapabilities(profile, customCapabilities)
        val missing = AccessCapability.entries.filterTo(linkedSetOf()) {
            it in required && it !in allowed
        }
        return if (missing.isEmpty()) {
            AccessDecision.Allowed
        } else {
            AccessDecision.Denied(profile, missing)
        }
    }

    fun effectiveCapabilities(connection: ServerConnection): Set<AccessCapability> =
        effectiveCapabilities(connection.accessProfile, connection.customCapabilities)

    fun effectiveCapabilities(
        profile: AccessProfile,
        customCapabilities: Set<AccessCapability>,
    ): Set<AccessCapability> = when (profile) {
        AccessProfile.VIEW_ONLY -> emptySet()
        AccessProfile.MANAGE -> ALL_CAPABILITIES
        AccessProfile.CUSTOM -> AccessCapability.entries.filterTo(linkedSetOf()) {
            it in customCapabilities
        }
    }

    private val ALL_CAPABILITIES = AccessCapability.entries.toSet()
}
