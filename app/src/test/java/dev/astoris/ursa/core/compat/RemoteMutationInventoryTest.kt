package dev.astoris.ursa.core.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMutationInventoryTest {

    @Test fun every_known_remote_mutation_has_a_policy_classification() {
        val rows = fixture("remote_mutations_v1.tsv")
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .map { line ->
                val columns = line.split('\t')
                require(columns.size == 4) { "Invalid mutation inventory row: $line" }
                MutationRow(columns[0], columns[1], columns[2], columns[3])
            }
            .toList()

        assertEquals(EXPECTED_IDS, rows.mapTo(linkedSetOf(), MutationRow::id))
        assertEquals(rows.size, rows.map(MutationRow::id).distinct().size)
        assertTrue(rows.all { row ->
            row.capability.split('+').all { it in ALLOWED_CAPABILITIES }
        })
        assertTrue(rows.all { it.wireEvents.split(',').all(String::isNotBlank) })
        assertTrue(rows.all { it.entryPoints.isNotBlank() })
        assertEquals(1, rows.count { it.capability == "BOOTSTRAP" })
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.getResource("/fixtures/$name"),
    ) { "Missing compatibility fixture: $name" }.readText()

    private data class MutationRow(
        val id: String,
        val capability: String,
        val wireEvents: String,
        val entryPoints: String,
    )

    private companion object {
        val ALLOWED_CAPABILITIES = setOf(
            "BOOTSTRAP",
            "MONITOR_STATE",
            "MONITOR_CREATE",
            "MONITOR_EDIT",
            "MONITOR_DELETE",
            "BULK_WRITE",
            "MAINTENANCE_WRITE",
            "PUSH_SETUP",
        )
        val EXPECTED_IDS = linkedSetOf(
            "server_setup",
            "monitor_pause",
            "monitor_resume",
            "bulk_monitor_pause",
            "bulk_monitor_resume",
            "monitor_create",
            "monitor_edit",
            "monitor_edit_active_state",
            "monitor_tag_assign",
            "monitor_delete",
            "maintenance_create",
            "maintenance_edit",
            "maintenance_pause",
            "maintenance_resume",
            "maintenance_delete",
            "managed_push_save",
            "managed_push_test",
            "managed_push_delete",
        )
    }
}
