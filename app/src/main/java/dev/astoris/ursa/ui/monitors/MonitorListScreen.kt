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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.data.model.Heartbeat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import dev.astoris.ursa.core.network.FaviconCache
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
    var filterOpen by remember { mutableStateOf(false) }

    val downCount = monitors.count { it.status == MonitorStatus.DOWN }
    val availableTags = monitors.flatMap { it.tags }.distinct().sorted()
    val shown = monitors
        .filter { m ->
                (query.isBlank() || m.name.contains(query, ignoreCase = true)) &&
                (statusFilter == null || m.status == statusFilter) &&
                (tagFilter?.let { it in m.tags } ?: true)
        }
        .sortedBy { it.status.attentionPriority }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (searchActive) {
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                painter = painterResource(R.mipmap.ic_launcher_monochrome),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                            Text(stringResource(R.string.monitors_title))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searchActive) { searchActive = false; query = "" } else searchActive = true
                    }) {
                        Icon(
                            painter = painterResource(if (searchActive) R.drawable.ic_close else R.drawable.ic_search),
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    Box {
                        IconButton(onClick = { filterOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filter),
                                contentDescription = stringResource(R.string.action_filter),
                                tint = if (statusFilter != null || tagFilter != null) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        DropdownMenu(expanded = filterOpen, onDismissRequest = { filterOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_all)) },
                                onClick = { statusFilter = null; tagFilter = null; filterOpen = false },
                            )
                            listOf(
                                MonitorStatus.UP, MonitorStatus.DOWN,
                                MonitorStatus.PENDING, MonitorStatus.MAINTENANCE,
                            ).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(StatusUi.labelRes(s))) },
                                    onClick = { statusFilter = s; filterOpen = false },
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
                                        onClick = {
                                            tagFilter = if (tagFilter == tag) null else tag
                                            filterOpen = false
                                        },
                                    )
                                }
                            }
                        }
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
            if (downCount > 0) {
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

private val MonitorStatus.attentionPriority: Int
    get() = when (this) {
        MonitorStatus.DOWN -> 0
        MonitorStatus.PENDING -> 1
        MonitorStatus.MAINTENANCE -> 2
        MonitorStatus.UP -> 3
    }

/** Service favicon when one is reachable (#443), else the tinted status circle. Status
 *  is still conveyed by the pill on the right. */
@Composable
private fun MonitorLeadingIcon(monitor: Monitor) {
    var favicon by remember(monitor.url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(monitor.url) { favicon = monitor.url?.let { FaviconCache.get(it) } }
    val fav = favicon
    if (fav != null) {
        Image(bitmap = fav, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape))
    } else {
        StatusCircle(monitor.status)
    }
}

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
                if (monitor.tags.isNotEmpty()) {
                    Text(
                        text = monitor.tags.take(2).joinToString(separator = "  •  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
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
                    monitor.ping?.let {
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
