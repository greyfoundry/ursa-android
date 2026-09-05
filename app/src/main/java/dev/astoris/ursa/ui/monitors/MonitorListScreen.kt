package dev.astoris.ursa.ui.monitors

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.FaviconCache
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.Sparkline
import dev.astoris.ursa.ui.StatusCircle
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.UptimeRing
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.labelRes
import dev.astoris.ursa.ui.actionRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorListScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val connectionFailure by vm.connectionFailure.collectAsStateWithLifecycle()
    val showingCache by vm.showingCache.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()
    val certs by vm.certs.collectAsStateWithLifecycle()
    val localEvents by vm.localEvents.collectAsStateWithLifecycle()
    val incidentNotes by vm.incidentNotes.collectAsStateWithLifecycle()
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val compactDisplay by vm.compactDisplayEnabled.collectAsStateWithLifecycle()
    val savedViews by vm.savedViews.collectAsStateWithLifecycle()
    val incidentOpenRequest by vm.incidentOpenRequest.collectAsStateWithLifecycle()
    val activeConnection = connections.firstOrNull { it.url == activeUrl }

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var viewFilter by remember { mutableStateOf(MonitorViewFilter(activity = ActivityFilter.ACTIVE)) }
    var filterOpen by remember { mutableStateOf(false) }
    var advancedFilterOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<MonitorOverlay?>(null) }
    var sortMode by remember { mutableStateOf(MonitorSort.SERVER) }
    var bulkMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pendingBulkAction by remember { mutableStateOf<BulkMonitorAction?>(null) }
    var bulkInFlight by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    val activeMonitors = monitors.filter { it.active }
    val downCount = activeMonitors.count { it.status == MonitorStatus.DOWN }
    val upCount = activeMonitors.count { it.status == MonitorStatus.UP }
    val pendingCount = activeMonitors.count { it.status == MonitorStatus.PENDING }
    val pausedCount = monitors.count { !it.active }
    val availableTags = monitors.flatMap { it.tags }.distinct().sorted()
    val statusFilter = viewFilter.statuses.singleOrNull()
    val tagFilter = viewFilter.tags.singleOrNull()
    val activityFilter = viewFilter.activity
    val visibleIds = monitors
        .filter { m ->
                (query.isBlank() || m.name.contains(query, ignoreCase = true)) &&
                viewFilter.matches(m, monitors, certs.keys)
        }.mapTo(mutableSetOf(), Monitor::id)
    val shown = monitorHierarchy(monitors, sortMode.comparator(favorites))
        .filter { it.monitor.id in visibleIds }
    val pausePlan = planBulkMonitorAction(monitors, selectedIds, BulkMonitorAction.PAUSE)
    val resumePlan = planBulkMonitorAction(monitors, selectedIds, BulkMonitorAction.RESUME)

    LaunchedEffect(monitors) {
        selectedIds = selectedIds.intersect(monitors.mapTo(mutableSetOf(), Monitor::id))
    }
    LaunchedEffect(incidentOpenRequest) {
        if (incidentOpenRequest != null) {
            overlay = MonitorOverlay.INCIDENTS
            vm.consumeIncidentOpenRequest()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Surface(
                        onClick = { vm.enterConnectionManager() },
                        color = Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.mipmap.ic_launcher_monochrome),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                            Column {
                                Text(
                                    activeConnection?.displayName ?: stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.servers_switch),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state != ConnectionState.Authenticated) {
                Surface(
                    onClick = { vm.enterConnectionManager() },
                    color = when (state) {
                        ConnectionState.AuthenticationFailed, ConnectionState.Error ->
                            MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = connectionFailure?.takeIf {
                                state == ConnectionState.Error || state == ConnectionState.AuthenticationFailed
                            }?.let { stringResource(it.actionRes) }
                                ?: stringResource(R.string.connection_status, stringResource(state.labelRes)),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            color = if (
                                state == ConnectionState.AuthenticationFailed || state == ConnectionState.Error
                            ) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.servers_manage),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            if (showingCache) {
                val whenText = lastUpdated?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    )
                }
                Text(
                    text = whenText?.let { stringResource(R.string.cache_banner_updated, it) }
                        ?: stringResource(R.string.cache_banner),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (overlay == null) {
                if (searchActive) {
                LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                    )
                    IconButton(onClick = { searchActive = false; query = "" }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close_search),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bulkMode) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.bulk_monitors_selected,
                                selectedIds.size,
                                selectedIds.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { bulkMode = false; selectedIds = emptySet() },
                            enabled = !bulkInFlight,
                        ) { Text(stringResource(R.string.action_cancel)) }
                    } else {
                        Text(
                            text = stringResource(R.string.monitors_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = stringResource(R.string.action_search),
                            )
                        }
                        MonitorFilterMenu(
                            filterOpen = filterOpen,
                            onFilterOpenChange = { filterOpen = it },
                            statusFilter = statusFilter,
                            tagFilter = tagFilter,
                            activityFilter = activityFilter,
                            availableTags = availableTags,
                            onAll = {
                                viewFilter = MonitorViewFilter()
                            },
                            onActive = { viewFilter = viewFilter.copy(activity = ActivityFilter.ACTIVE) },
                            onPaused = {
                                viewFilter = viewFilter.copy(activity = ActivityFilter.PAUSED, statuses = emptySet())
                            },
                            onStatus = { viewFilter = viewFilter.copy(statuses = setOf(it)) },
                            onTag = {
                                viewFilter = viewFilter.copy(tags = if (tagFilter == it) emptySet() else setOf(it))
                            },
                            onAdvanced = { advancedFilterOpen = true },
                        )
                        Box {
                            IconButton(onClick = { moreOpen = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vertical),
                                    contentDescription = stringResource(R.string.action_more),
                                )
                            }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.monitor_add_title)) },
                                onClick = { vm.createMonitor(); moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bulk_manage_monitors)) },
                                onClick = { bulkMode = true; selectedIds = emptySet(); moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.status_maintenance)) },
                                onClick = { overlay = MonitorOverlay.MAINTENANCE; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.incident_center_title)) },
                                onClick = { overlay = MonitorOverlay.INCIDENTS; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.certificate_dashboard_title)) },
                                onClick = { overlay = MonitorOverlay.CERTIFICATES; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.domain_dashboard_title)) },
                                onClick = { overlay = MonitorOverlay.DOMAINS; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.event_log_title)) },
                                onClick = { overlay = MonitorOverlay.EVENTS; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fleet_aggregate_title)) },
                                onClick = { overlay = MonitorOverlay.FLEET_SUMMARY; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pinned_live_title)) },
                                onClick = { overlay = MonitorOverlay.PINNED_LIVE; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_server)) },
                                onClick = { sortMode = MonitorSort.SERVER; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_attention)) },
                                onClick = { sortMode = MonitorSort.ATTENTION; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_favorites)) },
                                onClick = { sortMode = MonitorSort.FAVORITES; moreOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sort_name)) },
                                onClick = { sortMode = MonitorSort.NAME; moreOpen = false },
                            )
                            }
                        }
                    }
                }
                }
            }
            if (overlay == null && bulkMode) {
                val shownIds = shown.mapTo(mutableSetOf()) { it.monitor.id }
                val allShownSelected = shownIds.isNotEmpty() && shownIds.all { it in selectedIds }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            selectedIds = if (allShownSelected) selectedIds - shownIds else selectedIds + shownIds
                        },
                        enabled = shownIds.isNotEmpty() && !bulkInFlight,
                    ) {
                        Text(
                            stringResource(
                                if (allShownSelected) R.string.bulk_clear_visible else R.string.bulk_select_visible,
                            ),
                        )
                    }
                    TextButton(
                        onClick = { pendingBulkAction = BulkMonitorAction.PAUSE },
                        enabled = pausePlan.targetIds.isNotEmpty() && !bulkInFlight,
                    ) { Text(stringResource(R.string.action_pause)) }
                    TextButton(
                        onClick = { pendingBulkAction = BulkMonitorAction.RESUME },
                        enabled = resumePlan.targetIds.isNotEmpty() && !bulkInFlight,
                    ) { Text(stringResource(R.string.action_resume)) }
                }
            }
            when (overlay) {
                MonitorOverlay.MAINTENANCE -> MaintenanceScreen(
                    vm = vm,
                    onClose = { overlay = null },
                    modifier = Modifier.fillMaxSize(),
                )
                MonitorOverlay.INCIDENTS -> FleetIncidentCenter(
                        monitors = monitors,
                        history = history,
                        notes = incidentNotes,
                        serverUrl = activeUrl,
                        loadImportantHeartbeats = vm::importantHeartbeatHistory,
                        onClose = { overlay = null },
                        onIncidentClick = vm::select,
                        onSaveNote = vm::saveIncidentNote,
                        modifier = Modifier.fillMaxSize(),
                    )
                MonitorOverlay.CERTIFICATES -> CertificateDashboard(
                    monitors = monitors,
                    certs = certs,
                    onClose = { overlay = null },
                    onCertificateClick = vm::select,
                    modifier = Modifier.fillMaxSize(),
                )
                MonitorOverlay.DOMAINS -> DomainDashboard(
                    monitors = monitors,
                    certs = certs,
                    onClose = { overlay = null },
                    onMonitorClick = vm::select,
                    modifier = Modifier.fillMaxSize(),
                )
                MonitorOverlay.EVENTS -> GlobalEventLog(
                    monitors = monitors,
                    history = history,
                    localEvents = localEvents,
                    onClose = { overlay = null },
                    onMonitorClick = vm::select,
                    modifier = Modifier.fillMaxSize(),
                )
                MonitorOverlay.FLEET_SUMMARY -> FleetAggregateDashboard(
                    monitors = monitors,
                    loadChartData = vm::fleetChartData,
                    onClose = { overlay = null },
                    onMonitorClick = vm::select,
                    modifier = Modifier.fillMaxSize(),
                )
                MonitorOverlay.PINNED_LIVE -> PinnedLivePanel(
                    monitors = monitors,
                    history = history,
                    pinnedIds = favorites,
                    onClose = { overlay = null },
                    onMonitorClick = vm::select,
                    onUnpin = vm::toggleFavorite,
                    modifier = Modifier.fillMaxSize(),
                )
                null -> if (monitors.isNotEmpty()) {
                    MonitorOverview(
                        upCount = upCount,
                        downCount = downCount,
                        pendingCount = pendingCount,
                        pausedCount = pausedCount,
                        onStatusClick = { status ->
                            viewFilter = viewFilter.copy(statuses = setOf(status), activity = ActivityFilter.ACTIVE)
                        },
                        onPausedClick = {
                            viewFilter = viewFilter.copy(statuses = emptySet(), activity = ActivityFilter.PAUSED)
                        },
                    )
                }
            }
            if (overlay == null && monitors.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.monitors_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (overlay == null && shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.monitors_none_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (overlay == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compactDisplay) 8.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compactDisplay) 4.dp else 10.dp),
                ) {
                    items(shown, key = { it.monitor.id }) { row ->
                        val monitor = row.monitor
                        MonitorRow(
                            monitor = monitor,
                            beats = history[monitor.id].orEmpty(),
                            selected = selectedIds.contains(monitor.id).takeIf { bulkMode },
                            depth = row.depth,
                            compact = compactDisplay,
                            onClick = {
                                if (bulkMode) {
                                    selectedIds = if (monitor.id in selectedIds) {
                                        selectedIds - monitor.id
                                    } else {
                                        selectedIds + monitor.id
                                    }
                                } else {
                                    vm.select(monitor.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    pendingBulkAction?.let { action ->
        val plan = if (action == BulkMonitorAction.PAUSE) pausePlan else resumePlan
        val actionLabel = stringResource(
            if (action == BulkMonitorAction.PAUSE) R.string.action_pause else R.string.action_resume,
        )
        AlertDialog(
            onDismissRequest = { if (!bulkInFlight) pendingBulkAction = null },
            title = { Text(stringResource(R.string.bulk_confirm_title, actionLabel)) },
            text = {
                Text(
                    if (plan.fleetWide) {
                        pluralStringResource(
                            R.plurals.bulk_confirm_fleet,
                            plan.targetIds.size,
                            actionLabel,
                            plan.targetIds.size,
                        )
                    } else {
                        pluralStringResource(
                            R.plurals.bulk_confirm_selected,
                            plan.targetIds.size,
                            actionLabel,
                            plan.targetIds.size,
                        )
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        bulkInFlight = true
                        vm.setMonitorsActive(
                            ids = plan.targetIds.toSet(),
                            active = action == BulkMonitorAction.RESUME,
                        ) { result ->
                            bulkInFlight = false
                            pendingBulkAction = null
                            selectedIds = result.failedIds
                            if (result.failedIds.isEmpty()) {
                                bulkMode = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = resources.getQuantityString(
                                            R.plurals.bulk_monitors_updated,
                                            result.succeededIds.size,
                                            result.succeededIds.size,
                                        ),
                                    )
                                }
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = resources.getQuantityString(
                                            R.plurals.bulk_monitors_partial,
                                            result.failedIds.size,
                                            result.succeededIds.size,
                                            result.failedIds.size,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    enabled = !bulkInFlight && plan.targetIds.isNotEmpty(),
                ) { Text(actionLabel) }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingBulkAction = null },
                    enabled = !bulkInFlight,
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (advancedFilterOpen) {
        AdvancedFilterDialog(
            filter = viewFilter,
            monitors = monitors,
            savedViews = savedViews,
            onApply = { viewFilter = it; advancedFilterOpen = false },
            onSave = vm::saveMonitorView,
            onDelete = vm::deleteMonitorView,
            onDismiss = { advancedFilterOpen = false },
        )
    }
}

@Composable
private fun MonitorFilterMenu(
    filterOpen: Boolean,
    onFilterOpenChange: (Boolean) -> Unit,
    statusFilter: MonitorStatus?,
    tagFilter: String?,
    activityFilter: ActivityFilter,
    availableTags: List<String>,
    onAll: () -> Unit,
    onActive: () -> Unit,
    onPaused: () -> Unit,
    onStatus: (MonitorStatus) -> Unit,
    onTag: (String) -> Unit,
    onAdvanced: () -> Unit,
) {
    Box {
        IconButton(onClick = { onFilterOpenChange(true) }) {
            Icon(
                painter = painterResource(R.drawable.ic_filter),
                contentDescription = stringResource(R.string.action_filter),
                tint = if (
                    statusFilter != null || tagFilter != null ||
                    activityFilter != ActivityFilter.ACTIVE
                ) {
                    MaterialTheme.colorScheme.primary
                } else {
                    LocalContentColor.current
                },
            )
        }
        DropdownMenu(expanded = filterOpen, onDismissRequest = { onFilterOpenChange(false) }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, onClick = {
                onAll()
                onFilterOpenChange(false)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.filter_active)) }, onClick = {
                onActive()
                onFilterOpenChange(false)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.filter_paused)) }, onClick = {
                onPaused()
                onFilterOpenChange(false)
            })
            listOf(
                MonitorStatus.UP, MonitorStatus.DOWN,
                MonitorStatus.PENDING, MonitorStatus.MAINTENANCE,
            ).forEach { status ->
                DropdownMenuItem(
                    text = { Text(stringResource(StatusUi.labelRes(status))) },
                    onClick = { onStatus(status); onFilterOpenChange(false) },
                )
            }
            if (availableTags.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.filter_tags)) },
                    onClick = {},
                    enabled = false,
                )
                availableTags.forEach { tag ->
                    DropdownMenuItem(
                        text = { Text(tag) },
                        onClick = { onTag(tag); onFilterOpenChange(false) },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_advanced)) },
                onClick = { onFilterOpenChange(false); onAdvanced() },
            )
        }
    }
}

private enum class MonitorOverlay {
    MAINTENANCE,
    INCIDENTS,
    CERTIFICATES,
    DOMAINS,
    EVENTS,
    FLEET_SUMMARY,
    PINNED_LIVE,
}

private enum class MonitorSort {
    SERVER,
    ATTENTION,
    FAVORITES,
    NAME;

    fun comparator(favorites: Set<Int>): Comparator<Monitor> = when (this) {
        SERVER -> compareBy<Monitor> { !it.active }
            .thenByDescending(Monitor::weight)
            .thenBy { it.name.lowercase() }
        ATTENTION -> compareBy<Monitor> { it.status.attentionPriority }
            .thenBy { if (it.id in favorites) 0 else 1 }
            .thenBy { it.name.lowercase() }
        FAVORITES -> compareBy<Monitor> { if (it.id in favorites) 0 else 1 }
            .thenBy { it.status.attentionPriority }
            .thenBy { it.name.lowercase() }
        NAME -> compareBy { it.name.lowercase() }
    }
}

@Composable
private fun MonitorOverview(
    upCount: Int,
    downCount: Int,
    pendingCount: Int,
    pausedCount: Int,
    onStatusClick: (MonitorStatus) -> Unit,
    onPausedClick: () -> Unit,
) {
    val healthy = downCount == 0 && pendingCount == 0
    val containerColor = if (downCount > 0) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }
    val contentColor = if (downCount > 0) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (healthy) {
                            stringResource(R.string.fleet_healthy)
                        } else if (downCount > 0) {
                            pluralStringResource(R.plurals.monitors_need_attention, downCount, downCount)
                        } else {
                            stringResource(R.string.fleet_checking)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = listOf(
                            pluralStringResource(R.plurals.fleet_up_count, upCount, upCount),
                            pluralStringResource(R.plurals.fleet_down_count, downCount, downCount),
                            pluralStringResource(R.plurals.fleet_pending_count, pendingCount, pendingCount),
                            pluralStringResource(R.plurals.fleet_paused_count, pausedCount, pausedCount),
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                }
                Surface(
                    color = if (healthy) StatusUi.color(MonitorStatus.UP) else contentColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                ) {
                    Icon(
                        painter = painterResource(
                            if (healthy) R.drawable.ic_status_up else R.drawable.ic_status_down,
                        ),
                        contentDescription = null,
                        tint = if (healthy) MaterialTheme.colorScheme.onPrimary else contentColor,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                MonitorMetric(
                    count = upCount,
                    label = stringResource(R.string.status_up),
                    color = StatusUi.color(MonitorStatus.UP),
                    onClick = { onStatusClick(MonitorStatus.UP) },
                    modifier = Modifier.weight(1f),
                )
                MonitorMetric(
                    count = downCount,
                    label = stringResource(R.string.status_down),
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onStatusClick(MonitorStatus.DOWN) },
                    modifier = Modifier.weight(1f),
                )
                MonitorMetric(
                    count = pendingCount,
                    label = stringResource(R.string.status_pending),
                    color = StatusUi.color(MonitorStatus.PENDING),
                    onClick = { onStatusClick(MonitorStatus.PENDING) },
                    modifier = Modifier.weight(1f),
                )
                MonitorMetric(
                    count = pausedCount,
                    label = stringResource(R.string.filter_paused),
                    color = contentColor.copy(alpha = 0.72f),
                    onClick = onPausedClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonitorMetric(
    count: Int,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = count.toString(), style = MaterialTheme.typography.titleMedium, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.82f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val MonitorStatus.attentionPriority: Int
    get() = when (this) {
        MonitorStatus.DOWN -> 0
        MonitorStatus.PENDING -> 1
        MonitorStatus.MAINTENANCE -> 2
        MonitorStatus.UP -> 3
    }

@Composable
private fun MonitorLeadingIcon(monitor: Monitor) {
    var favicon by remember(monitor.url) { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current.applicationContext
    LaunchedEffect(monitor.url) { favicon = monitor.url?.let { FaviconCache.get(context, it) } }
    val icon = favicon
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(CircleShape),
        )
    } else {
        StatusCircle(monitor.status)
    }
}

@Composable
private fun MonitorRow(
    monitor: Monitor,
    beats: List<Heartbeat>,
    selected: Boolean?,
    depth: Int,
    compact: Boolean,
    onClick: () -> Unit,
) {
    UrsaPressableCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(4) * 16).dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 12.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            selected?.let {
                Checkbox(checked = it, onCheckedChange = null)
            }
            MonitorLeadingIcon(monitor)
            Column(Modifier.weight(1f)) {
                Text(monitor.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                monitor.url?.takeUnless { compact }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(StatusUi.color(monitor.status)),
                    )
                    Text(
                        stringResource(StatusUi.labelRes(monitor.status)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Sparkline(
                    beats = beats,
                    color = StatusUi.color(monitor.status),
                    modifier = Modifier.width(72.dp).height(20.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (monitor.avgPing ?: monitor.ping)?.let { Text("${it}ms", style = MaterialTheme.typography.labelMedium) }
                    monitor.uptime24h?.let {
                        UptimeRing(it, StatusUi.color(monitor.status))
                    }
                }
            }
        }
    }
}
