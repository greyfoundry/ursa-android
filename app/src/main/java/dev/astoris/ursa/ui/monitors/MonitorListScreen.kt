package dev.astoris.ursa.ui.monitors

import android.text.format.DateUtils
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.Sparkline
import dev.astoris.ursa.ui.StatusCircle
import dev.astoris.ursa.ui.StatusPill
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorListScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val showingCache by vm.showingCache.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<MonitorStatus?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var activityFilter by remember { mutableStateOf(MonitorActivityFilter.ACTIVE) }
    var filterOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var showOverview by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

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
        .sortedBy { it.status.attentionPriority }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painter = painterResource(R.mipmap.ic_launcher_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.selectTab(dev.astoris.ursa.ui.MainTab.NOTIFICATIONS) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_notifications),
                            contentDescription = stringResource(R.string.nav_notifications),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state != ConnectionState.Authenticated) {
                Text(
                    text = stringResource(R.string.connection_status, state.toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
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
                            contentDescription = stringResource(R.string.action_search),
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
                                text = {
                                    Text(
                                        stringResource(
                                            if (showOverview) R.string.action_hide_overview
                                            else R.string.action_show_overview,
                                        ),
                                    )
                                },
                                onClick = { showOverview = !showOverview; moreOpen = false },
                            )
                        }
                    }
                }
            }
            if (showOverview && downCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatusPill(MonitorStatus.DOWN)
                        Text(
                            text = pluralStringResource(R.plurals.monitors_need_attention, downCount, downCount),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            if (showOverview && monitors.isNotEmpty()) {
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

@Composable
private fun MonitorOverview(
    upCount: Int,
    downCount: Int,
    pendingCount: Int,
    pausedCount: Int,
    onStatusClick: (MonitorStatus) -> Unit,
    onPausedClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.monitor_overview),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth()) {
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
            }
            if (pausedCount > 0) {
                TextButton(
                    onClick = onPausedClick,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(pluralStringResource(R.plurals.monitor_paused_count, pausedCount, pausedCount))
                }
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
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
private fun MonitorLeadingIcon(monitor: Monitor) = StatusCircle(monitor.status)

@Composable
private fun MonitorRow(monitor: Monitor, beats: List<Heartbeat>, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                Text(monitor.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                monitor.url?.let {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sparkline(
                        beats = beats,
                        color = StatusUi.color(monitor.status),
                        modifier = Modifier.width(64.dp).height(18.dp),
                    )
                    (monitor.avgPing ?: monitor.ping)?.let {
                        Text(
                            "${it}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    monitor.uptime24h?.let {
                        Text(
                            "${(it * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
