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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorListScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val showingCache by vm.showingCache.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()
    val certs by vm.certs.collectAsStateWithLifecycle()
    val localEvents by vm.localEvents.collectAsStateWithLifecycle()
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val activeConnection = connections.firstOrNull { it.url == activeUrl }

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<MonitorStatus?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var activityFilter by remember { mutableStateOf(MonitorActivityFilter.ACTIVE) }
    var filterOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<MonitorOverlay?>(null) }
    var sortMode by remember { mutableStateOf(MonitorSort.ATTENTION) }
    val searchFocusRequester = remember { FocusRequester() }
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    val activeMonitors = monitors.filter { it.active }
    val downCount = activeMonitors.count { it.status == MonitorStatus.DOWN }
    val upCount = activeMonitors.count { it.status == MonitorStatus.UP }
    val pendingCount = activeMonitors.count { it.status == MonitorStatus.PENDING }
    val pausedCount = monitors.count { !it.active }
    val availableTags = monitors.flatMap { it.tags }.distinct().sorted()
    val shown = monitors
        .filter { m ->
                (query.isBlank() || m.name.contains(query, ignoreCase = true)) &&
                (statusFilter == null || m.status == statusFilter) &&
                (tagFilter?.let { it in m.tags } ?: true) &&
                when (activityFilter) {
                    MonitorActivityFilter.ACTIVE -> m.active
                    MonitorActivityFilter.PAUSED -> !m.active
                    MonitorActivityFilter.ALL -> true
                }
        }
        .sortedWith(sortMode.comparator(favorites))

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                            text = stringResource(R.string.connection_status, stringResource(state.labelRes)),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (
                                state == ConnectionState.AuthenticationFailed || state == ConnectionState.Error
                            ) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.servers_manage),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
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
                            statusFilter = null
                            tagFilter = null
                            activityFilter = MonitorActivityFilter.ALL
                        },
                        onActive = { activityFilter = MonitorActivityFilter.ACTIVE },
                        onPaused = {
                            activityFilter = MonitorActivityFilter.PAUSED
                            statusFilter = null
                        },
                        onStatus = { statusFilter = it },
                        onTag = { tagFilter = if (tagFilter == it) null else it },
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
            when (overlay) {
                MonitorOverlay.INCIDENTS -> FleetIncidentCenter(
                        monitors = monitors,
                        history = history,
                        onClose = { overlay = null },
                        onIncidentClick = vm::select,
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
                null -> if (monitors.isNotEmpty()) {
                    MonitorOverview(
                        upCount = upCount,
                        downCount = downCount,
                        pendingCount = pendingCount,
                        pausedCount = pausedCount,
                        onStatusClick = { status ->
                            statusFilter = status
                            activityFilter = MonitorActivityFilter.ACTIVE
                        },
                        onPausedClick = {
                            statusFilter = null
                            activityFilter = MonitorActivityFilter.PAUSED
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(shown, key = { it.id }) { monitor ->
                        MonitorRow(
                            monitor = monitor,
                            beats = history[monitor.id].orEmpty(),
                            onClick = { vm.select(monitor.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorFilterMenu(
    filterOpen: Boolean,
    onFilterOpenChange: (Boolean) -> Unit,
    statusFilter: MonitorStatus?,
    tagFilter: String?,
    activityFilter: MonitorActivityFilter,
    availableTags: List<String>,
    onAll: () -> Unit,
    onActive: () -> Unit,
    onPaused: () -> Unit,
    onStatus: (MonitorStatus) -> Unit,
    onTag: (String) -> Unit,
) {
    Box {
        IconButton(onClick = { onFilterOpenChange(true) }) {
            Icon(
                painter = painterResource(R.drawable.ic_filter),
                contentDescription = stringResource(R.string.action_filter),
                tint = if (
                    statusFilter != null || tagFilter != null ||
                    activityFilter != MonitorActivityFilter.ACTIVE
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
        }
    }
}

private enum class MonitorActivityFilter { ACTIVE, PAUSED, ALL }

private enum class MonitorOverlay { INCIDENTS, CERTIFICATES, DOMAINS, EVENTS }

private enum class MonitorSort {
    ATTENTION,
    FAVORITES,
    NAME;

    fun comparator(favorites: Set<Int>): Comparator<Monitor> = when (this) {
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
private fun MonitorRow(monitor: Monitor, beats: List<Heartbeat>, onClick: () -> Unit) {
    UrsaPressableCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MonitorLeadingIcon(monitor)
            Column(Modifier.weight(1f)) {
                Text(monitor.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                monitor.url?.let {
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
