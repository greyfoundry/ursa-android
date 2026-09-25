package dev.astoris.ursa.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.CompactStatusRow
import dev.astoris.ursa.ui.components.FreshnessState
import dev.astoris.ursa.ui.components.OperationalStateKind
import dev.astoris.ursa.ui.components.OperationalStatePanel
import dev.astoris.ursa.ui.components.ServerContextHeader
import dev.astoris.ursa.ui.components.resolveFreshness
import dev.astoris.ursa.ui.monitors.ActivityFilter
import dev.astoris.ursa.ui.monitors.MonitorViewFilter
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: UrsaViewModel,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()
    val localEvents by vm.localEvents.collectAsStateWithLifecycle()
    val savedViews by vm.savedViews.collectAsStateWithLifecycle()
    val connection by vm.activeConnection.collectAsStateWithLifecycle()
    val connectionState by vm.state.collectAsStateWithLifecycle()
    val showingCache by vm.showingCache.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(DateUtils.MINUTE_IN_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    val summary = remember(monitors) { homeFleetSummary(monitors) }
    val recent = remember(monitors, history, localEvents) {
        recentMonitorChanges(monitors, history, localEvents)
    }
    val serverName = connection?.displayName ?: stringResource(R.string.home_server_unknown)
    val serverAddress = connection?.url ?: stringResource(R.string.home_server_not_selected)
    val freshness = homeFreshness(connectionState, showingCache, lastUpdated, nowMillis)
    val freshnessLabel = homeFreshnessLabel(
        connectionState = connectionState,
        showingCache = showingCache,
        lastUpdated = lastUpdated,
        nowMillis = nowMillis,
    )

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
                .widthIn(max = HomeMaxContentWidth)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "home-heading") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item(key = "attention-heading") {
            SectionHeading(stringResource(R.string.home_attention_title))
        }
        if (monitors.isEmpty()) {
            item(key = "home-data-state") {
                HomeDataState(
                    state = connectionState,
                    onRetry = vm::refreshActiveServer,
                )
            }
        } else if (summary.attention.isEmpty()) {
            item(key = "attention-empty") {
                OperationalStatePanel(
                    kind = OperationalStateKind.EMPTY,
                    title = stringResource(R.string.home_attention_clear),
                    message = stringResource(R.string.home_attention_clear_desc),
                    announcement = null,
                )
            }
        } else {
            items(
                items = summary.attention.take(HOME_ATTENTION_LIMIT),
                key = { "attention-${it.id}" },
            ) { monitor ->
                CompactStatusRow(
                    title = monitor.name,
                    status = monitor.status,
                    subtitle = monitor.type,
                    metadata = monitorTelemetry(monitor.ping, monitor.avgPing, monitor.uptime24h),
                    onClick = { vm.openMonitor(monitor.id) },
                )
            }
            if (summary.attention.size > HOME_ATTENTION_LIMIT) {
                item(key = "attention-more") {
                    TextButton(
                        onClick = {
                            vm.openMonitors(
                                MonitorViewFilter(
                                    statuses = setOf(
                                        MonitorStatus.DOWN,
                                        MonitorStatus.PENDING,
                                        MonitorStatus.MAINTENANCE,
                                    ),
                                    activity = ActivityFilter.ACTIVE,
                                ),
                            )
                        },
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.home_attention_more,
                                summary.attention.size - HOME_ATTENTION_LIMIT,
                                summary.attention.size - HOME_ATTENTION_LIMIT,
                            ),
                        )
                    }
                }
            }
        }

        item(key = "fleet-heading") {
            SectionHeading(stringResource(R.string.home_fleet_title))
        }
        item(key = "fleet-summary") {
            HomeFleetCard(
                summary = summary,
                onOpenMonitors = { vm.openMonitors() },
            )
        }

        item(key = "server-heading") {
            SectionHeading(stringResource(R.string.home_server_title))
        }
        item(key = "server-context") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ServerContextHeader(
                    serverName = serverName,
                    serverAddress = serverAddress,
                    connectionLabel = freshnessLabel,
                    freshnessState = freshness,
                    onClick = vm::enterConnectionManager,
                )
                TextButton(
                    onClick = vm::refreshActiveServer,
                    enabled = connection != null && connectionState != ConnectionState.Connecting,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.home_refresh))
                }
            }
        }

        item(key = "recent-heading") {
            SectionHeading(stringResource(R.string.home_recent_title))
        }
        if (recent.isEmpty()) {
            item(key = "recent-empty") {
                OperationalStatePanel(
                    kind = OperationalStateKind.EMPTY,
                    title = stringResource(R.string.home_recent_empty),
                    message = stringResource(R.string.home_recent_empty_desc),
                    announcement = null,
                )
            }
        } else {
            items(recent, key = { "recent-${it.monitor.id}" }) { change ->
                CompactStatusRow(
                    title = change.monitor.name,
                    status = change.monitor.status,
                    subtitle = change.monitor.type,
                    metadata = DateUtils.getRelativeTimeSpanString(
                        change.atMillis,
                        nowMillis,
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    onClick = { vm.openMonitor(change.monitor.id) },
                )
            }
        }

        if (savedViews.isNotEmpty()) {
            item(key = "saved-heading") {
                SectionHeading(stringResource(R.string.home_saved_views_title))
            }
            item(key = "saved-views") {
                LazyRow(
                    contentPadding = PaddingValues(end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(savedViews, key = { it.name }) { view ->
                        AssistChip(
                            onClick = { vm.openMonitors(view.filter) },
                            label = { Text(view.name) },
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun HomeFleetCard(
    summary: HomeFleetSummary,
    onOpenMonitors: () -> Unit,
) {
    val attentionCount = summary.down + summary.pending
    val container = when {
        summary.active == 0 -> MaterialTheme.colorScheme.surfaceContainerLow
        summary.down > 0 -> MaterialTheme.colorScheme.errorContainer
        summary.pending > 0 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        summary.active == 0 -> MaterialTheme.colorScheme.onSurface
        summary.down > 0 -> MaterialTheme.colorScheme.onErrorContainer
        summary.pending > 0 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, content.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    summary.active == 0 -> stringResource(R.string.home_no_active_checks)
                    summary.down > 0 -> pluralStringResource(
                        R.plurals.home_fleet_attention,
                        attentionCount,
                        attentionCount,
                    )
                    summary.pending > 0 -> stringResource(R.string.fleet_checking)
                    else -> stringResource(R.string.fleet_healthy)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = listOf(
                    pluralStringResource(R.plurals.fleet_up_count, summary.up, summary.up),
                    pluralStringResource(R.plurals.fleet_down_count, summary.down, summary.down),
                    pluralStringResource(R.plurals.fleet_pending_count, summary.pending, summary.pending),
                    pluralStringResource(
                        R.plurals.home_maintenance_count,
                        summary.maintenance,
                        summary.maintenance,
                    ),
                    pluralStringResource(R.plurals.fleet_paused_count, summary.paused, summary.paused),
                ).joinToString(" • "),
                color = content.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onOpenMonitors, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.home_open_monitors), color = content)
            }
        }
    }
}

@Composable
private fun HomeDataState(
    state: ConnectionState,
    onRetry: () -> Unit,
) {
    val kind = when (state) {
        ConnectionState.Connecting,
        ConnectionState.Connected,
        -> OperationalStateKind.LOADING
        ConnectionState.Disconnected -> OperationalStateKind.OFFLINE
        ConnectionState.AuthenticationFailed,
        ConnectionState.Error,
        -> OperationalStateKind.ERROR
        ConnectionState.Authenticated -> OperationalStateKind.EMPTY
    }
    val title = when (kind) {
        OperationalStateKind.LOADING -> stringResource(R.string.home_loading)
        OperationalStateKind.OFFLINE -> stringResource(R.string.home_offline)
        OperationalStateKind.ERROR -> stringResource(R.string.home_unavailable)
        OperationalStateKind.EMPTY -> stringResource(R.string.monitors_empty)
        OperationalStateKind.PARTIAL -> stringResource(R.string.home_unavailable)
    }
    val message = when (kind) {
        OperationalStateKind.LOADING -> stringResource(R.string.home_loading_desc)
        OperationalStateKind.OFFLINE -> stringResource(R.string.home_offline_desc)
        OperationalStateKind.ERROR -> stringResource(R.string.home_unavailable_desc)
        OperationalStateKind.EMPTY -> stringResource(R.string.home_empty_desc)
        OperationalStateKind.PARTIAL -> stringResource(R.string.home_unavailable_desc)
    }
    OperationalStatePanel(
        kind = kind,
        title = title,
        message = message,
        actionLabel = if (kind == OperationalStateKind.EMPTY || kind == OperationalStateKind.LOADING) {
            null
        } else {
            stringResource(R.string.home_retry)
        },
        onAction = if (kind == OperationalStateKind.EMPTY || kind == OperationalStateKind.LOADING) {
            null
        } else {
            onRetry
        },
    )
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun homeFreshnessLabel(
    connectionState: ConnectionState,
    showingCache: Boolean,
    lastUpdated: Long?,
    nowMillis: Long,
): String = when {
    connectionState == ConnectionState.Authenticated && !showingCache -> {
        stringResource(R.string.home_data_live)
    }
    lastUpdated != null -> stringResource(
        R.string.home_data_cached,
        DateUtils.getRelativeTimeSpanString(
            lastUpdated,
            nowMillis,
            DateUtils.MINUTE_IN_MILLIS,
        ).toString(),
    )
    else -> stringResource(connectionState.homeLabelRes)
}

private fun homeFreshness(
    connectionState: ConnectionState,
    showingCache: Boolean,
    lastUpdated: Long?,
    nowMillis: Long,
): FreshnessState {
    if (connectionState == ConnectionState.Authenticated && !showingCache) return FreshnessState.LIVE
    val updated = lastUpdated ?: return FreshnessState.OFFLINE
    val online = connectionState == ConnectionState.Connecting ||
        connectionState == ConnectionState.Connected ||
        connectionState == ConnectionState.Authenticated
    return resolveFreshness(
        ageMillis = nowMillis - updated,
        staleAfterMillis = HOME_STALE_AFTER_MILLIS,
        isOnline = online,
    )
}

private val ConnectionState.homeLabelRes: Int
    get() = when (this) {
        ConnectionState.Disconnected -> R.string.connection_offline
        ConnectionState.Connecting -> R.string.connection_connecting
        ConnectionState.Connected -> R.string.connection_signing_in
        ConnectionState.Authenticated -> R.string.connection_online
        ConnectionState.AuthenticationFailed -> R.string.connection_auth_failed
        ConnectionState.Error -> R.string.connection_unavailable
    }

private fun monitorTelemetry(ping: Int?, avgPing: Int?, uptime: Double?): String? {
    val parts = buildList {
        (avgPing ?: ping)?.let { add("${it}ms") }
        uptime?.let { add("${(it * 100).coerceIn(0.0, 100.0).toInt()}%") }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

private const val HOME_ATTENTION_LIMIT = 4
private const val HOME_STALE_AFTER_MILLIS = 5L * DateUtils.MINUTE_IN_MILLIS
private val HomeMaxContentWidth = 840.dp
