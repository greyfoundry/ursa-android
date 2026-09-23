package dev.astoris.ursa.core.access

import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.data.model.ServerConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {

    @Test fun manage_allows_every_current_capability() {
        val connection = connection(AccessProfile.MANAGE)

        AccessCapability.entries.forEach { capability ->
            assertSame(AccessDecision.Allowed, AccessPolicy.evaluate(connection, capability))
        }
        assertEquals(AccessCapability.entries.toSet(), AccessPolicy.effectiveCapabilities(connection))
    }

    @Test fun view_only_denies_every_remote_capability_in_stable_enum_order() {
        val connection = connection(
            profile = AccessProfile.VIEW_ONLY,
            custom = AccessCapability.entries.toSet(),
        )

        val decision = AccessPolicy.evaluate(connection, *AccessCapability.entries.toTypedArray())

        assertEquals(
            AccessDecision.Denied(AccessProfile.VIEW_ONLY, AccessCapability.entries.toSet()),
            decision,
        )
        assertTrue(AccessPolicy.effectiveCapabilities(connection).isEmpty())
    }

    @Test fun custom_allows_only_explicit_capabilities() {
        val connection = connection(
            profile = AccessProfile.CUSTOM,
            custom = setOf(AccessCapability.MONITOR_STATE, AccessCapability.MAINTENANCE_WRITE),
        )

        assertSame(
            AccessDecision.Allowed,
            AccessPolicy.evaluate(connection, AccessCapability.MONITOR_STATE),
        )
        assertEquals(
            AccessDecision.Denied(
                AccessProfile.CUSTOM,
                setOf(AccessCapability.MONITOR_EDIT, AccessCapability.MONITOR_DELETE),
            ),
            AccessPolicy.evaluate(
                connection,
                AccessCapability.MONITOR_STATE,
                AccessCapability.MONITOR_EDIT,
                AccessCapability.MONITOR_DELETE,
            ),
        )
    }

    @Test fun empty_requirement_is_allowed_even_for_view_only() {
        assertSame(
            AccessDecision.Allowed,
            AccessPolicy.evaluate(AccessProfile.VIEW_ONLY, emptySet(), emptySet()),
        )
    }

    @Test fun saved_custom_choices_are_ignored_outside_custom_without_being_destroyed() {
        val selected = setOf(AccessCapability.MONITOR_STATE)
        val manage = connection(AccessProfile.MANAGE, selected)
        val viewOnly = manage.copy(accessProfile = AccessProfile.VIEW_ONLY)
        val restored = viewOnly.copy(accessProfile = AccessProfile.CUSTOM)

        assertEquals(AccessCapability.entries.toSet(), AccessPolicy.effectiveCapabilities(manage))
        assertTrue(AccessPolicy.effectiveCapabilities(viewOnly).isEmpty())
        assertEquals(selected, AccessPolicy.effectiveCapabilities(restored))
        assertEquals(selected, restored.customCapabilities)
    }

    private fun connection(
        profile: AccessProfile,
        custom: Set<AccessCapability> = emptySet(),
    ) = ServerConnection(
        url = "https://kuma.example.test",
        username = "operator",
        accessProfile = profile,
        customCapabilities = custom,
    )
}
