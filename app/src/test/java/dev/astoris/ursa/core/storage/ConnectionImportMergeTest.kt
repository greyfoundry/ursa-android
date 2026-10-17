package dev.astoris.ursa.core.storage

import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.data.model.CleartextPolicy
import dev.astoris.ursa.data.model.ServerConnection
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionImportMergeTest {

    private val restricted = ServerConnection(
        url = "https://existing.example.test",
        username = "local",
        jwt = "local-session",
        accessProfile = AccessProfile.CUSTOM,
        customCapabilities = setOf(AccessCapability.MONITOR_STATE),
        cleartextPolicy = CleartextPolicy.DENY,
    )

    @Test fun default_merge_preserves_existing_access_and_omitted_session() {
        val imported = restricted.copy(
            username = "imported",
            jwt = null,
            accessProfile = AccessProfile.MANAGE,
            customCapabilities = emptySet(),
            cleartextPolicy = CleartextPolicy.ALLOW,
        )

        val merged = ConnectionImportMerge.merge(listOf(restricted), listOf(imported)).single()

        assertEquals("imported", merged.username)
        assertEquals("local-session", merged.jwt)
        assertEquals(AccessProfile.CUSTOM, merged.accessProfile)
        assertEquals(setOf(AccessCapability.MONITOR_STATE), merged.customCapabilities)
        assertEquals(CleartextPolicy.DENY, merged.cleartextPolicy)
    }

    @Test fun explicit_restore_replaces_access_and_included_session() {
        val imported = restricted.copy(
            jwt = "backup-session",
            accessProfile = AccessProfile.VIEW_ONLY,
            customCapabilities = setOf(AccessCapability.MONITOR_DELETE),
        )

        val merged = ConnectionImportMerge.merge(
            existing = listOf(restricted),
            imported = listOf(imported),
            restoreAccessProfiles = true,
        ).single()

        assertEquals("backup-session", merged.jwt)
        assertEquals(AccessProfile.VIEW_ONLY, merged.accessProfile)
        assertEquals(setOf(AccessCapability.MONITOR_DELETE), merged.customCapabilities)
    }

    @Test fun new_connection_uses_imported_access_profile() {
        val imported = ServerConnection(
            url = "https://new.example.test",
            username = "viewer",
            accessProfile = AccessProfile.VIEW_ONLY,
        )

        assertEquals(imported, ConnectionImportMerge.merge(listOf(restricted), listOf(imported)).last())
    }
}
