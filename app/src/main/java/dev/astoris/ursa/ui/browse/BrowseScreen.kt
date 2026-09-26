package dev.astoris.ursa.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.MaintenanceDraft
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.SavedStatusPage
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.SectionHeading
import dev.astoris.ursa.ui.components.OperationalStateKind
import dev.astoris.ursa.ui.components.OperationalStatePanel
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.monitors.ActivityFilter
import dev.astoris.ursa.ui.monitors.CertificateDashboard
import dev.astoris.ursa.ui.monitors.DomainDashboard
import dev.astoris.ursa.ui.monitors.FleetAggregateDashboard
import dev.astoris.ursa.ui.monitors.GlobalEventLog
import dev.astoris.ursa.ui.monitors.MaintenanceScreen
import dev.astoris.ursa.ui.monitors.MonitorViewFilter
import dev.astoris.ursa.ui.monitors.PinnedLivePanel
import dev.astoris.ursa.ui.monitors.SavedMonitorView
import dev.astoris.ursa.ui.monitors.fleetIncidents
import dev.astoris.ursa.ui.push.PushScreen
import dev.astoris.ursa.ui.settings.SettingsScreen
import java.net.URI

internal enum class BrowseTool {
    MAINTENANCE,
    NOTIFICATIONS,
    CERTIFICATES,
    DOMAINS,
    EVENTS,
    FLEET,
    LIVE,
    SETTINGS,
}

@Composable
fun BrowseScreen(
    vm: UrsaViewModel,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()
    val certs by vm.certs.collectAsStateWithLifecycle()
    val localEvents by vm.localEvents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val maintenances by vm.maintenances.collectAsStateWithLifecycle()
    val statusPages by vm.savedStatusPages.collectAsStateWithLifecycle()
    val connections by vm.connections.collectAsStateWithLifecycle()
    val savedViews by vm.savedViews.collectAsStateWithLifecycle()
    val activeConnection by vm.activeConnection.collectAsStateWithLifecycle()
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationsFromSettings by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = BrowseTool.entries.firstOrNull { it.name == selectedName }
    val activeServer = activeConnection?.displayName ?: stringResource(R.string.browse_current_server)
    val searchCopy = BrowseSearchCopy(
        monitor = stringResource(R.string.browse_resource_monitor),
        group = stringResource(R.string.browse_resource_group),
        tag = stringResource(R.string.browse_resource_tag),
        incident = stringResource(R.string.browse_resource_incident),
        maintenance = stringResource(R.string.browse_resource_maintenance),
        statusPage = stringResource(R.string.browse_resource_status_page),
        server = stringResource(R.string.browse_resource_server),
        savedView = stringResource(R.string.browse_resource_saved_view),
        tool = stringResource(R.string.browse_resource_tool),
        publicPage = stringResource(R.string.browse_public_status_page),
    )
    val catalogEntries = listOf(
        BrowseCatalogEntry(
            BrowseSection.OPERATIONS,
            "maintenance",
            stringResource(R.string.status_maintenance),
            stringResource(R.string.browse_maintenance_desc),
            BrowseSearchAction.Maintenance,
        ),
        BrowseCatalogEntry(
            BrowseSection.OPERATIONS,
            "status-pages",
            stringResource(R.string.statuspage_title),
            stringResource(R.string.settings_status_pages_desc),
            BrowseSearchAction.StatusPages,
        ),
        BrowseCatalogEntry(
            BrowseSection.OPERATIONS,
            "notifications",
            stringResource(R.string.nav_notifications),
            stringResource(R.string.settings_notifications_desc),
            BrowseSearchAction.Tool(BrowseTool.NOTIFICATIONS),
        ),
        BrowseCatalogEntry(
            BrowseSection.INSIGHTS,
            "certificates",
            stringResource(R.string.certificate_dashboard_title),
            stringResource(R.string.browse_certificates_desc),
            BrowseSearchAction.Tool(BrowseTool.CERTIFICATES),
        ),
        BrowseCatalogEntry(
            BrowseSection.INSIGHTS,
            "domains",
            stringResource(R.string.domain_dashboard_title),
            stringResource(R.string.browse_domains_desc),
            BrowseSearchAction.Tool(BrowseTool.DOMAINS),
        ),
        BrowseCatalogEntry(
            BrowseSection.INSIGHTS,
            "events",
            stringResource(R.string.event_log_title),
            stringResource(R.string.browse_events_desc),
            BrowseSearchAction.Tool(BrowseTool.EVENTS),
        ),
        BrowseCatalogEntry(
            BrowseSection.INSIGHTS,
            "fleet",
            stringResource(R.string.fleet_aggregate_title),
            stringResource(R.string.browse_fleet_desc),
            BrowseSearchAction.Tool(BrowseTool.FLEET),
        ),
        BrowseCatalogEntry(
            BrowseSection.INSIGHTS,
            "live",
            stringResource(R.string.pinned_live_title),
            stringResource(R.string.browse_live_desc),
            BrowseSearchAction.Tool(BrowseTool.LIVE),
        ),
        BrowseCatalogEntry(
            BrowseSection.MANAGE,
            "monitor-views",
            stringResource(R.string.browse_monitors_title),
            stringResource(R.string.browse_monitors_desc),
            BrowseSearchAction.Monitors,
        ),
        BrowseCatalogEntry(
            BrowseSection.MANAGE,
            "servers",
            stringResource(R.string.servers_title),
            stringResource(R.string.browse_servers_desc),
            BrowseSearchAction.Servers,
        ),
        BrowseCatalogEntry(
            BrowseSection.MANAGE,
            "settings",
            stringResource(R.string.browse_settings_title),
            stringResource(R.string.browse_settings_desc),
            BrowseSearchAction.Tool(BrowseTool.SETTINGS),
        ),
    )
    val toolEntries = catalogEntries.map { it.searchEntry(activeServer, searchCopy.tool) }
    val searchEntries = remember(
        monitors,
        history,
        maintenances,
        statusPages,
        connections,
        savedViews,
        activeServer,
        searchCopy,
        toolEntries,
    ) {
        buildBrowseSearchEntries(
            monitors = monitors,
            history = history,
            maintenances = maintenances,
            statusPages = statusPages,
            connections = connections,
            savedViews = savedViews,
            activeServer = activeServer,
            copy = searchCopy,
            tools = toolEntries,
        )
    }
    val searchResults = remember(searchEntries, query) { browseSearchResults(searchEntries, query) }
    val close = {
        selectedName = if (selected == BrowseTool.NOTIFICATIONS && notificationsFromSettings) {
            BrowseTool.SETTINGS.name
        } else {
            null
        }
        notificationsFromSettings = false
    }
    val openMonitor: (Int) -> Unit = { id ->
        selectedName = null
        notificationsFromSettings = false
        vm.openMonitor(id)
    }
    val openAction: (BrowseSearchAction) -> Unit = { action ->
        notificationsFromSettings = false
        when (action) {
            is BrowseSearchAction.Monitor -> vm.openMonitor(action.id)
            is BrowseSearchAction.MonitorFilter -> vm.openMonitors(action.filter)
            BrowseSearchAction.Monitors -> vm.openMonitors()
            BrowseSearchAction.Maintenance -> selectedName = BrowseTool.MAINTENANCE.name
            BrowseSearchAction.StatusPages -> vm.enterStatusPage()
            is BrowseSearchAction.StatusPage -> statusPages.firstOrNull { it.id == action.id }?.let { page ->
                vm.enterStatusPage()
                vm.openStatusPage(page)
            }
            BrowseSearchAction.Servers -> vm.enterConnectionManager()
            is BrowseSearchAction.Tool -> selectedName = action.tool.name
        }
    }

    when (selected) {
        null -> BrowseIndex(
            query = query,
            searchResults = searchResults,
            onQueryChange = { query = it },
            onSearchEntry = { entry ->
                query = ""
                openAction(entry.action)
            },
            catalogEntries = catalogEntries,
            onCatalogEntry = { openAction(it.action) },
            modifier = modifier,
        )
        BrowseTool.NOTIFICATIONS -> {
            BackHandler(onBack = close)
            PushScreen(vm, modifier)
        }
        BrowseTool.SETTINGS -> {
            BackHandler(onBack = close)
            SettingsScreen(
                vm = vm,
                modifier = modifier,
                onNotificationsClick = {
                    notificationsFromSettings = true
                    selectedName = BrowseTool.NOTIFICATIONS.name
                },
            )
        }
        else -> Box(
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        ) {
            when (selected) {
                BrowseTool.MAINTENANCE -> MaintenanceScreen(vm, close, Modifier.fillMaxSize())
                BrowseTool.CERTIFICATES -> CertificateDashboard(
                    monitors, certs, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.DOMAINS -> DomainDashboard(
                    monitors, certs, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.EVENTS -> GlobalEventLog(
                    monitors, history, localEvents, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.FLEET -> FleetAggregateDashboard(
                    monitors, vm::fleetChartData, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.LIVE -> PinnedLivePanel(
                    monitors = monitors,
                    history = history,
                    pinnedIds = favorites,
                    onClose = close,
                    onMonitorClick = openMonitor,
                    onUnpin = vm::toggleFavorite,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BrowseIndex(
    query: String,
    searchResults: List<BrowseSearchEntry>,
    onQueryChange: (String) -> Unit,
    onSearchEntry: (BrowseSearchEntry) -> Unit,
    catalogEntries: List<BrowseCatalogEntry>,
    onCatalogEntry: (BrowseCatalogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.browse_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.browse_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.browse_search_hint)) },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = null)
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.action_close_search),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotBlank()) {
                item { SectionHeading(stringResource(R.string.browse_search_results)) }
                if (searchResults.isEmpty()) {
                    item {
                        OperationalStatePanel(
                            kind = OperationalStateKind.EMPTY,
                            title = stringResource(R.string.browse_search_empty_title),
                            message = stringResource(R.string.browse_search_empty_message, query.trim()),
                        )
                    }
                } else {
                    items(searchResults, key = BrowseSearchEntry::id) { entry ->
                        BrowseSearchItem(entry, onClick = { onSearchEntry(entry) })
                    }
                }
            } else {
                BrowseSection.entries.forEach { section ->
                    item(key = "section-${section.name}") {
                        SectionHeading(stringResource(section.labelRes))
                    }
                    items(
                        catalogEntries.filter { it.section == section },
                        key = { "catalog-${it.id}" },
                    ) { entry ->
                        BrowseItem(entry.title, entry.description) { onCatalogEntry(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseSearchItem(entry: BrowseSearchEntry, onClick: () -> Unit) {
    BrowseItem(
        title = entry.title,
        description = stringResource(R.string.browse_result_context, entry.resourceType, entry.server),
        onClick = onClick,
    )
}

@Composable
private fun BrowseItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    UrsaPressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class BrowseSection(val labelRes: Int) {
    OPERATIONS(R.string.browse_operations),
    INSIGHTS(R.string.browse_insights),
    MANAGE(R.string.browse_manage),
}

private data class BrowseCatalogEntry(
    val section: BrowseSection,
    val id: String,
    val title: String,
    val description: String,
    val action: BrowseSearchAction,
) {
    fun searchEntry(server: String, resourceType: String) = BrowseSearchEntry(
        id = "tool-$id",
        title = title,
        resourceType = resourceType,
        server = server,
        terms = listOf(description),
        action = action,
    )
}

internal data class BrowseSearchCopy(
    val monitor: String,
    val group: String,
    val tag: String,
    val incident: String,
    val maintenance: String,
    val statusPage: String,
    val server: String,
    val savedView: String,
    val tool: String,
    val publicPage: String,
)

internal fun buildBrowseSearchEntries(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    maintenances: List<MaintenanceDraft>,
    statusPages: List<SavedStatusPage>,
    connections: List<ServerConnection>,
    savedViews: List<SavedMonitorView>,
    activeServer: String,
    copy: BrowseSearchCopy,
    tools: List<BrowseSearchEntry>,
): List<BrowseSearchEntry> = buildList {
    val monitorNames = monitors.associate { it.id to it.name }
    monitors.forEach { monitor ->
        val isGroup = monitor.type.equals("group", ignoreCase = true)
        add(
            BrowseSearchEntry(
                id = "monitor-${monitor.id}",
                title = monitor.name,
                resourceType = if (isGroup) copy.group else copy.monitor,
                server = activeServer,
                terms = buildList {
                    add(monitor.type)
                    monitor.parentId?.let(monitorNames::get)?.let(::add)
                    addAll(monitor.tags)
                },
                action = BrowseSearchAction.Monitor(monitor.id),
            ),
        )
    }
    monitors.asSequence().flatMap { it.tags.asSequence() }.distinct().sortedBy { it.lowercase() }.forEach { tag ->
        add(
            BrowseSearchEntry(
                id = "tag-$tag",
                title = tag,
                resourceType = copy.tag,
                server = activeServer,
                terms = emptyList(),
                action = BrowseSearchAction.MonitorFilter(
                    MonitorViewFilter(tags = setOf(tag), activity = ActivityFilter.ALL),
                ),
            ),
        )
    }
    fleetIncidents(monitors, history).distinctBy { it.monitorId }.forEach { incident ->
        add(
            BrowseSearchEntry(
                id = "incident-${incident.monitorId}",
                title = incident.monitorName,
                resourceType = copy.incident,
                server = activeServer,
                terms = emptyList(),
                action = BrowseSearchAction.Monitor(incident.monitorId),
            ),
        )
    }
    maintenances.forEachIndexed { index, maintenance ->
        add(
            BrowseSearchEntry(
                id = "maintenance-${maintenance.id ?: "new-$index"}",
                title = maintenance.title,
                resourceType = copy.maintenance,
                server = activeServer,
                terms = listOf(maintenance.strategy.wireValue, maintenance.status),
                action = BrowseSearchAction.Maintenance,
            ),
        )
    }
    statusPages.forEach { page ->
        add(
            BrowseSearchEntry(
                id = "status-page-${page.id}",
                title = page.name,
                resourceType = copy.statusPage,
                server = runCatching { URI(page.url).host }.getOrNull().orEmpty().ifBlank { copy.publicPage },
                terms = listOf(page.slug),
                action = BrowseSearchAction.StatusPage(page.id),
            ),
        )
    }
    connections.forEachIndexed { index, connection ->
        add(
            BrowseSearchEntry(
                id = "server-$index",
                title = connection.displayName,
                resourceType = copy.server,
                server = connection.displayName,
                terms = emptyList(),
                action = BrowseSearchAction.Servers,
            ),
        )
    }
    savedViews.forEach { view ->
        add(
            BrowseSearchEntry(
                id = "saved-view-${view.name}",
                title = view.name,
                resourceType = copy.savedView,
                server = activeServer,
                terms = emptyList(),
                action = BrowseSearchAction.MonitorFilter(view.filter),
            ),
        )
    }
    addAll(tools)
}
