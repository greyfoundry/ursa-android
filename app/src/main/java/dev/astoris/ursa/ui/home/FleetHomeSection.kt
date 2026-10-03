package dev.astoris.ursa.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetRefreshError
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerError
import dev.astoris.ursa.data.model.FleetServerSnapshot
import dev.astoris.ursa.data.model.FleetSnapshotSource
import dev.astoris.ursa.ui.FleetHomeUiState
import dev.astoris.ursa.ui.components.OperationalStateKind
import dev.astoris.ursa.ui.components.OperationalStatePanel
import dev.astoris.ursa.ui.components.SectionHeading

internal fun LazyListScope.fleetHomeItems(
    state: FleetHomeUiState,
    nowMillis: Long,
    onRefresh: () -> Unit,
    onOpenMonitors: (String) -> Unit,
    onOpenIssues: (String) -> Unit,
    onOpenIncidents: (String) -> Unit,
) {
    item(key = "all-servers-heading") {
        SectionHeading(stringResource(R.string.home_all_servers_title))
    }

    if (state.failed) {
        item(key = "fleet-load-failed") {
            OperationalStatePanel(
                kind = if (state.snapshot == null) {
                    OperationalStateKind.ERROR
                } else {
                    OperationalStateKind.PARTIAL
                },
                title = stringResource(R.string.home_fleet_load_failed),
                message = stringResource(R.string.home_fleet_load_failed_desc),
                actionLabel = stringResource(R.string.home_retry),
                onAction = onRefresh,
            )
        }
    }

    val snapshot = state.snapshot
    if (snapshot == null) {
        if (!state.failed) {
            item(key = "fleet-loading") {
                OperationalStatePanel(
                    kind = OperationalStateKind.LOADING,
                    title = stringResource(R.string.home_fleet_loading),
                    message = stringResource(R.string.home_fleet_loading_desc),
                )
            }
        }
        return
    }

    if (snapshot.isLocked) {
        item(key = "fleet-locked") {
            OperationalStatePanel(
                kind = OperationalStateKind.EMPTY,
                title = stringResource(R.string.home_fleet_locked),
                message = stringResource(R.string.home_fleet_locked_desc),
            )
        }
        return
    }

    if (snapshot.servers.isEmpty()) {
        item(key = "fleet-empty") {
            OperationalStatePanel(
                kind = OperationalStateKind.EMPTY,
                title = stringResource(R.string.home_fleet_empty),
                message = stringResource(R.string.home_fleet_empty_desc),
            )
        }
        return
    }

    item(key = "fleet-overview") {
        FleetOverviewCard(
            summary = fleetHomeSummary(snapshot),
            nowMillis = nowMillis,
            refreshing = state.refreshing,
            onRefresh = onRefresh,
        )
    }

    itemsIndexed(
        items = snapshot.servers,
        key = { index, _ -> "fleet-server-$index" },
    ) { _, server ->
        FleetServerCard(
            server = server,
            nowMillis = nowMillis,
            onOpenMonitors = { onOpenMonitors(server.serverUrl) },
            onOpenIssues = { onOpenIssues(server.serverUrl) },
            onOpenIncidents = { onOpenIncidents(server.serverUrl) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetOverviewCard(
    summary: FleetHomeSummary,
    nowMillis: Long,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val hasUrgent = summary.urgentMonitors > 0
    val contentColor = if (hasUrgent) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (hasUrgent) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (hasUrgent) {
                    pluralStringResource(
                        R.plurals.home_fleet_urgent,
                        summary.urgentMonitors,
                        summary.urgentMonitors,
                    )
                } else {
                    stringResource(R.string.home_fleet_no_urgent)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.home_fleet_affected_servers,
                    summary.affectedServers,
                    summary.affectedServers,
                    summary.totalServers,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.home_fleet_coverage,
                    summary.availableServers,
                    summary.totalServers,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.78f),
            )
            if (summary.unavailableServers > 0 || summary.staleServers > 0) {
                Text(
                    text = stringResource(
                        R.string.home_fleet_data_health,
                        summary.unavailableServers,
                        summary.staleServers,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
            Text(
                text = fleetOverviewTimeLabel(summary, nowMillis),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.78f),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(onClick = onRefresh, enabled = !refreshing) {
                    Text(
                        if (refreshing) {
                            stringResource(R.string.home_fleet_refreshing)
                        } else {
                            stringResource(R.string.home_fleet_refresh_all)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetServerCard(
    server: FleetServerSnapshot,
    nowMillis: Long,
    onOpenMonitors: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenIncidents: () -> Unit,
) {
    val urgent = server.urgentMonitorCount()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(server.displayName, style = MaterialTheme.typography.titleMedium)
            if (server.isActiveServer) {
                Text(
                    stringResource(R.string.home_fleet_current_server),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = fleetServerStateLabel(server, nowMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    server.refreshError != null ||
                        server.availability != FleetServerAvailability.AVAILABLE ||
                        server.freshness == FleetFreshness.STALE -> MaterialTheme.colorScheme.error
                    server.source == FleetSnapshotSource.LIVE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            server.counts?.let { counts ->
                Text(
                    text = listOf(
                        pluralStringResource(R.plurals.fleet_up_count, counts.up, counts.up),
                        pluralStringResource(R.plurals.fleet_down_count, counts.down, counts.down),
                        pluralStringResource(R.plurals.fleet_pending_count, counts.pending, counts.pending),
                        pluralStringResource(
                            R.plurals.home_maintenance_count,
                            counts.maintenance,
                            counts.maintenance,
                        ),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOpenMonitors) {
                    Text(stringResource(R.string.home_open_monitors))
                }
                if (urgent > 0 || (server.counts?.maintenance ?: 0) > 0) {
                    TextButton(onClick = onOpenIssues) {
                        Text(stringResource(R.string.home_fleet_view_issues))
                    }
                }
                if (urgent > 0) {
                    TextButton(onClick = onOpenIncidents) {
                        Text(stringResource(R.string.home_fleet_open_incidents))
                    }
                }
            }
        }
    }
}

@Composable
private fun fleetOverviewTimeLabel(summary: FleetHomeSummary, nowMillis: Long): String {
    summary.lastRefreshAtMillis?.let {
        return stringResource(R.string.home_fleet_refreshed, relativeTime(it, nowMillis))
    }
    summary.newestSnapshotAtMillis?.let {
        return stringResource(R.string.home_fleet_newest_snapshot, relativeTime(it, nowMillis))
    }
    return stringResource(R.string.home_fleet_no_snapshot_time)
}

@Composable
private fun fleetServerStateLabel(server: FleetServerSnapshot, nowMillis: Long): String {
    server.refreshError?.let { error ->
        return if (server.counts != null) {
            stringResource(
                R.string.home_fleet_refresh_failed_cached,
                fleetRefreshErrorLabel(error),
            )
        } else {
            fleetRefreshErrorLabel(error)
        }
    }
    if (server.availability == FleetServerAvailability.NO_CACHE) {
        return stringResource(R.string.home_fleet_no_cache)
    }
    server.error?.let { return fleetCacheErrorLabel(it) }
    val capturedAt = server.capturedAtMillis ?: return stringResource(R.string.home_fleet_no_cache)
    val relative = relativeTime(capturedAt, nowMillis)
    return when {
        server.freshness == FleetFreshness.STALE -> {
            stringResource(R.string.home_fleet_stale_snapshot, relative)
        }
        server.source == FleetSnapshotSource.LIVE -> stringResource(R.string.home_data_live)
        server.source == FleetSnapshotSource.REFRESHED -> {
            stringResource(R.string.home_fleet_refreshed, relative)
        }
        else -> stringResource(R.string.home_data_cached, relative)
    }
}

@Composable
private fun fleetRefreshErrorLabel(error: FleetRefreshError): String = stringResource(
    when (error) {
        FleetRefreshError.MISSING_SESSION -> R.string.home_fleet_error_sign_in
        FleetRefreshError.CLEARTEXT_BLOCKED -> R.string.home_fleet_error_cleartext
        FleetRefreshError.DEVICE_OFFLINE -> R.string.home_fleet_error_offline
        FleetRefreshError.SERVER_UNREACHABLE -> R.string.home_fleet_error_unreachable
        FleetRefreshError.AUTHENTICATION_FAILED -> R.string.home_fleet_error_sign_in
        FleetRefreshError.CERTIFICATE -> R.string.home_fleet_error_certificate
        FleetRefreshError.INCOMPATIBLE_RESPONSE -> R.string.home_fleet_error_incompatible
        FleetRefreshError.TIMED_OUT -> R.string.home_fleet_error_timeout
        FleetRefreshError.CACHE_WRITE_FAILED -> R.string.home_fleet_error_cache_write
        FleetRefreshError.UNKNOWN -> R.string.home_fleet_error_unknown
    },
)

@Composable
private fun fleetCacheErrorLabel(error: FleetServerError): String = stringResource(
    when (error) {
        FleetServerError.CACHE_STORE_UNAVAILABLE -> R.string.home_fleet_cache_unavailable
        FleetServerError.CACHE_DECRYPTION_FAILED -> R.string.home_fleet_cache_decrypt_failed
        FleetServerError.INVALID_CACHE -> R.string.home_fleet_cache_invalid
    },
)

private fun relativeTime(atMillis: Long, nowMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        minOf(atMillis, nowMillis),
        nowMillis,
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
