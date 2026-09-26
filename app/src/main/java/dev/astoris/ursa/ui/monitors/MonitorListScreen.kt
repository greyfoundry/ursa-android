package dev.astoris.ursa.ui.monitors

import android.text.format.DateUtils
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.FaviconCache
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.Sparkline
import dev.astoris.ursa.ui.AccessProfileNotice
import dev.astoris.ursa.ui.StatusCircle
import dev.astoris.ursa.ui.StatusPill
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.OperationalControlsRow
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.labelRes
import dev.astoris.ursa.ui.actionRes
import dev.astoris.ursa.ui.allows
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
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val compactDisplay by vm.compactDisplayEnabled.collectAsStateWithLifecycle()
    val savedViews by vm.savedViews.collectAsStateWithLifecycle()
    val monitorFilterRequest by vm.monitorFilterRequest.collectAsStateWithLifecycle()
    val activeConnection = connections.firstOrNull { it.url == activeUrl }
    val canCreate = activeConnection.allows(AccessCapability.MONITOR_CREATE)
    val canBulk = activeConnection.allows(AccessCapability.BULK_WRITE)

    var query by remember { mutableStateOf("") }
    var viewFilter by remember { mutableStateOf(MonitorViewFilter(activity = ActivityFilter.ACTIVE)) }
    var filterOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var advancedFilterOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(MonitorSort.SERVER) }
    var bulkMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pendingBulkAction by remember { mutableStateOf<BulkMonitorAction?>(null) }
    var bulkInFlight by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    val availableTags = monitors.flatMap { it.tags }.distinct().sorted()
    val tagFilter = viewFilter.tags.singleOrNull()
    val shown = monitorInventoryRows(
        monitors = monitors,
        query = query,
        filter = viewFilter,
        certificateIds = certs.keys,
        sort = sortMode,
        favorites = favorites,
    )
    val defaultFilter = MonitorViewFilter(activity = ActivityFilter.ACTIVE)
    val pausePlan = planBulkMonitorAction(monitors, selectedIds, BulkMonitorAction.PAUSE)
    val resumePlan = planBulkMonitorAction(monitors, selectedIds, BulkMonitorAction.RESUME)

    LaunchedEffect(monitors) {
        selectedIds = selectedIds.intersect(monitors.mapTo(mutableSetOf(), Monitor::id))
    }
    LaunchedEffect(monitorFilterRequest) {
        monitorFilterRequest?.let { requested ->
            viewFilter = requested
            vm.consumeMonitorFilterRequest()
        }
    }
    LaunchedEffect(canBulk) {
        if (!canBulk) {
            bulkMode = false
            selectedIds = emptySet()
            pendingBulkAction = null
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
                actions = {
                    TextButton(
                        onClick = vm::createMonitor,
                        enabled = canCreate,
                    ) {
                        Text(stringResource(R.string.action_add))
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
            AccessProfileNotice(
                activeConnection,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
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
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
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
                                enabled = canBulk,
                                text = { Text(stringResource(R.string.bulk_manage_monitors)) },
                                onClick = { bulkMode = true; selectedIds = emptySet(); moreOpen = false },
                            )
                        }
                    }
                }
            }
            if (!bulkMode) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.action_close_search),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    OperationalControlsRow(
                        filterLabel = stringResource(R.string.inventory_filters),
                        sortLabel = stringResource(sortMode.labelRes),
                        savedViewLabel = stringResource(R.string.saved_views_title),
                        onFilterClick = { filterOpen = true },
                        onSortClick = { sortOpen = true },
                        onSavedViewClick = { advancedFilterOpen = true },
                        filterSelected = viewFilter != defaultFilter,
                        savedViewSelected = savedViews.any { it.filter == viewFilter },
                    )
                    MonitorFilterMenu(
                        filterOpen = filterOpen,
                        onFilterOpenChange = { filterOpen = it },
                        availableTags = availableTags,
                        onAll = { viewFilter = MonitorViewFilter() },
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
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        MonitorSort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sort.labelRes)) },
                                onClick = { sortMode = sort; sortOpen = false },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.inventory_result_count, shown.size, monitors.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            if (bulkMode) {
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
                        enabled = canBulk && pausePlan.targetIds.isNotEmpty() && !bulkInFlight,
                    ) { Text(stringResource(R.string.action_pause)) }
                    TextButton(
                        onClick = { pendingBulkAction = BulkMonitorAction.RESUME },
                        enabled = canBulk && resumePlan.targetIds.isNotEmpty() && !bulkInFlight,
                    ) { Text(stringResource(R.string.action_resume)) }
                }
            }
            if (monitors.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.monitors_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.monitors_none_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
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
                            serverName = activeConnection?.displayName ?: stringResource(R.string.app_name),
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
                    enabled = canBulk && !bulkInFlight && plan.targetIds.isNotEmpty(),
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
    availableTags: List<String>,
    onAll: () -> Unit,
    onActive: () -> Unit,
    onPaused: () -> Unit,
    onStatus: (MonitorStatus) -> Unit,
    onTag: (String) -> Unit,
    onAdvanced: () -> Unit,
) {
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

private val MonitorSort.labelRes: Int
    get() = when (this) {
        MonitorSort.SERVER -> R.string.action_sort_server
        MonitorSort.ATTENTION -> R.string.action_sort_attention
        MonitorSort.FAVORITES -> R.string.action_sort_favorites
        MonitorSort.NAME -> R.string.action_sort_name
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
    serverName: String,
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
                Text(
                    text = stringResource(R.string.monitor_row_context, monitor.type, serverName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                monitor.url?.takeUnless { compact }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatusPill(monitor.status)
                Sparkline(
                    beats = beats,
                    color = StatusUi.color(monitor.status),
                    modifier = Modifier.width(72.dp).height(20.dp),
                )
                val telemetry = buildList {
                    (monitor.avgPing ?: monitor.ping)?.let { add("${it}ms") }
                    monitor.uptime24h?.let { add("${(it * 100).coerceIn(0.0, 100.0).toInt()}%") }
                }.joinToString(" • ")
                if (telemetry.isNotEmpty()) {
                    Text(
                        text = telemetry,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
