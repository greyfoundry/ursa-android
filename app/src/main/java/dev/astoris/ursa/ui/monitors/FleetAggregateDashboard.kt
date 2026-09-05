package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorChartPoint
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.components.UrsaPressableCard
import java.text.NumberFormat
import kotlin.math.roundToInt

internal data class MonitorAggregate(
    val monitorId: Int,
    val monitorName: String,
    val uptime: Double,
    val avgPing: Double?,
    val checkCount: Long,
)

internal data class FleetAggregateSummary(
    val fleetUptime: Double?,
    val fleetAvgPing: Double?,
    val monitors: List<MonitorAggregate>,
    val activeMonitorCount: Int,
    val latencyMonitorCount: Int,
    val noDataCount: Int,
    val requestFailedCount: Int,
    val pausedMonitorCount: Int,
)

/** Equal-weights monitor-level results so short check intervals cannot dominate the fleet. */
internal fun fleetAggregateSummary(
    monitors: List<Monitor>,
    chartData: Map<Int, List<MonitorChartPoint>?>,
): FleetAggregateSummary {
    val active = monitors.filter { it.active }
    var noData = 0
    var failed = 0
    val aggregates = active.mapNotNull { monitor ->
        val points = chartData[monitor.id]
        if (points == null) {
            failed += 1
            return@mapNotNull null
        }
        val up = points.sumOf { it.up }
        val down = points.sumOf { it.down }
        val checks = up + down
        if (checks <= 0L) {
            noData += 1
            return@mapNotNull null
        }
        var pingWeight = 0L
        var pingTotal = 0.0
        points.forEach { point ->
            if (point.up > 0L && point.avgPing != null) {
                pingWeight += point.up
                pingTotal += point.avgPing * point.up
            }
        }
        MonitorAggregate(
            monitorId = monitor.id,
            monitorName = monitor.name,
            uptime = up.toDouble() / checks.toDouble(),
            avgPing = if (pingWeight > 0L) pingTotal / pingWeight else null,
            checkCount = checks,
        )
    }.sortedWith(compareBy<MonitorAggregate> { it.uptime }.thenBy { it.monitorName.lowercase() })
    val latencies = aggregates.mapNotNull { it.avgPing }
    return FleetAggregateSummary(
        fleetUptime = aggregates.takeIf { it.isNotEmpty() }?.map { it.uptime }?.average(),
        fleetAvgPing = latencies.takeIf { it.isNotEmpty() }?.average(),
        monitors = aggregates,
        activeMonitorCount = active.size,
        latencyMonitorCount = latencies.size,
        noDataCount = noData,
        requestFailedCount = failed,
        pausedMonitorCount = monitors.size - active.size,
    )
}

private sealed interface FleetAggregateUiState {
    data object Loading : FleetAggregateUiState
    data class Loaded(val summary: FleetAggregateSummary) : FleetAggregateUiState
}

@Composable
internal fun FleetAggregateDashboard(
    monitors: List<Monitor>,
    loadChartData: suspend (List<Int>) -> Map<Int, List<MonitorChartPoint>?>,
    onClose: () -> Unit,
    onMonitorClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val activeIds = remember(monitors) { monitors.filter { it.active }.map { it.id } }
    var refreshKey by remember { mutableIntStateOf(0) }
    var uiState by remember { mutableStateOf<FleetAggregateUiState>(FleetAggregateUiState.Loading) }
    LaunchedEffect(activeIds, refreshKey) {
        uiState = FleetAggregateUiState.Loading
        uiState = FleetAggregateUiState.Loaded(
            fleetAggregateSummary(monitors, loadChartData(activeIds)),
        )
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.fleet_aggregate_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { refreshKey += 1 }) {
                Text(stringResource(R.string.statuspage_refresh))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.incident_center_close)) }
        }
        when (val state = uiState) {
            FleetAggregateUiState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        stringResource(R.string.fleet_aggregate_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            is FleetAggregateUiState.Loaded -> FleetAggregateContent(
                summary = state.summary,
                monitors = monitors,
                onMonitorClick = onMonitorClick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FleetAggregateContent(
    summary: FleetAggregateSummary,
    monitors: List<Monitor>,
    onMonitorClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 2
        }
    }
    val monitorById = remember(monitors) { monitors.associateBy { it.id } }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AggregateMetric(
                            value = summary.fleetUptime?.let(percent::format)
                                ?: stringResource(R.string.reliability_unavailable),
                            label = stringResource(R.string.fleet_aggregate_uptime),
                            modifier = Modifier.weight(1f),
                        )
                        AggregateMetric(
                            value = summary.fleetAvgPing?.roundToInt()?.let {
                                stringResource(R.string.response_time_milliseconds, it)
                            }
                                ?: stringResource(R.string.reliability_unavailable),
                            label = stringResource(R.string.fleet_aggregate_latency),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        pluralStringResource(
                            R.plurals.fleet_aggregate_coverage,
                            summary.monitors.size,
                            summary.monitors.size,
                            summary.activeMonitorCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            R.string.fleet_aggregate_excluded,
                            summary.pausedMonitorCount,
                            summary.noDataCount,
                            summary.requestFailedCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.fleet_aggregate_method),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.latencyMonitorCount != summary.monitors.size) {
                        Text(
                            pluralStringResource(
                                R.plurals.fleet_aggregate_latency_coverage,
                                summary.monitors.size,
                                summary.latencyMonitorCount,
                                summary.monitors.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (summary.monitors.isEmpty()) {
            item {
                Box(
                    Modifier.fillParentMaxHeight(0.55f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.fleet_aggregate_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(summary.monitors, key = { it.monitorId }) { aggregate ->
                val monitor = monitorById[aggregate.monitorId]
                UrsaPressableCard(onClick = { onMonitorClick(aggregate.monitorId) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(10.dp),
                            shape = CircleShape,
                            color = monitor?.let { StatusUi.color(it.status) }
                                ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            content = {},
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                aggregate.monitorName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                pluralStringResource(
                                    R.plurals.fleet_aggregate_checks,
                                    aggregate.checkCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                                    aggregate.checkCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(percent.format(aggregate.uptime), style = MaterialTheme.typography.labelLarge)
                            Text(
                                aggregate.avgPing?.roundToInt()?.let {
                                    stringResource(R.string.response_time_milliseconds, it)
                                }
                                    ?: stringResource(R.string.reliability_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AggregateMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
