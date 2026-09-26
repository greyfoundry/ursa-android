package dev.astoris.ursa.ui.browse

import dev.astoris.ursa.core.network.MaintenanceDraft
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.data.model.SavedStatusPage
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.ui.monitors.MonitorViewFilter
import dev.astoris.ursa.ui.monitors.SavedMonitorView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseSearchTest {
    private val entries = listOf(
        entry("monitor", "API", "Monitor", listOf("http", "public")),
        entry("tag", "Public", "Tag"),
        entry("server", "Production", "Server"),
    )

    @Test
    fun searchRanksNamesBeforeMetadataAndMatchesTypeServerAndTerms() {
        assertEquals(listOf("tag", "monitor"), browseSearchResults(entries, "public").map { it.id })
        assertEquals(listOf("server"), browseSearchResults(entries, "server").map { it.id })
        assertEquals(listOf("server"), browseSearchResults(entries, "prod").map { it.id })
        assertEquals(listOf("monitor"), browseSearchResults(entries, "http").map { it.id })
    }

    @Test
    fun blankQueriesAndNonPositiveLimitsReturnNothing() {
        assertTrue(browseSearchResults(entries, "  ").isEmpty())
        assertTrue(browseSearchResults(entries, "public", limit = 0).isEmpty())
    }

    @Test
    fun indexCoversPermittedResourcesWithoutSecretsOrBodies() {
        val monitor = Monitor(
            id = 1,
            name = "Website",
            url = "https://monitor-secret@example.com/private?token=hidden",
            type = "http",
            active = true,
            tags = listOf("public"),
            status = MonitorStatus.UP,
        )
        val indexed = buildBrowseSearchEntries(
            monitors = listOf(
                Monitor(2, "Production", null, "group", true),
                monitor.copy(parentId = 2),
            ),
            history = mapOf(
                1 to listOf(
                    Heartbeat(1, MonitorStatus.DOWN, "2026-09-26 10:00:00", "private body", null, true),
                    Heartbeat(1, MonitorStatus.UP, "2026-09-26 10:01:00", null, 20, true),
                ),
            ),
            maintenances = listOf(MaintenanceDraft(id = 2, title = "Deploy window", description = "private body")),
            statusPages = listOf(
                SavedStatusPage("page", "Public page", "https://status.example/private?token=hidden", "public"),
            ),
            connections = listOf(
                ServerConnection(
                    url = "https://user:password@example.com/private",
                    username = "secret-user",
                    jwt = "secret-jwt",
                    alias = "Production",
                    headers = listOf(RequestHeader("Authorization", "secret-header")),
                ),
            ),
            savedViews = listOf(SavedMonitorView("Needs review", MonitorViewFilter())),
            activeServer = "Production",
            copy = BrowseSearchCopy(
                "Monitor", "Group", "Tag", "Incident", "Maintenance",
                "Status page", "Server", "Saved view", "Tool", "Public status page",
            ),
            tools = listOf(entry("tool", "Certificates", "Tool")),
        )

        assertEquals(
            setOf(
                "Monitor",
                "Group",
                "Tag",
                "Incident",
                "Maintenance",
                "Status page",
                "Server",
                "Saved view",
                "Tool",
            ),
            indexed.mapTo(mutableSetOf(), BrowseSearchEntry::resourceType),
        )
        val searchable = indexed.flatMap { listOf(it.title, it.server) + it.terms }.joinToString(" ")
        listOf("monitor-secret", "hidden", "private body", "password", "secret-user", "secret-jwt", "secret-header")
            .forEach { secret -> assertFalse(searchable.contains(secret, ignoreCase = true)) }
    }

    private fun entry(
        id: String,
        title: String,
        type: String,
        terms: List<String> = emptyList(),
    ) = BrowseSearchEntry(
        id = id,
        title = title,
        resourceType = type,
        server = if (id == "server") "Production" else "Primary",
        terms = terms,
        action = BrowseSearchAction.MonitorFilter(MonitorViewFilter()),
    )
}
