package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.theme.KumaGreen
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal data class FleetIncident(
    val monitorId: Int,
    val monitorName: String,
    val startedAt: String?,
    val resolvedAt: String?,
    val message: String?,
) {
    val active: Boolean get() = resolvedAt == null
}

internal enum class FleetIncidentFilter { ALL, ACTIVE, RESOLVED }

internal data class MonitorFlakiness(
    val monitorId: Int,
    val monitorName: String,
    val incidents: Int,
)

internal data class FleetReliabilitySummary(
    val observedDowntimeMillis: Long,
    val meanTimeToRecoveryMillis: Long?,
    val flakiestMonitor: MonitorFlakiness?,
    val incompleteMonitorCount: Int,
    val activeMonitorCount: Int,
)

private val kumaHeartbeatTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]")

internal fun kumaUtcMillisOrNull(value: String?): Long? = runCatching {
    value?.let { LocalDateTime.parse(it, kumaHeartbeatTime).toInstant(ZoneOffset.UTC).toEpochMilli() }
}.getOrNull()

internal fun incidentDurationMillis(incident: FleetIncident, nowMillis: Long): Long? {
    val start = kumaUtcMillisOrNull(incident.startedAt) ?: return null
    val end = kumaUtcMillisOrNull(incident.resolvedAt) ?: nowMillis
    return (end - start).coerceAtLeast(0L)
}

internal fun compactDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

internal fun incidentOverlapsWindow(incident: FleetIncident, cutoffMillis: Long, nowMillis: Long): Boolean {
    val start = kumaUtcMillisOrNull(incident.startedAt)
    val end = kumaUtcMillisOrNull(incident.resolvedAt) ?: nowMillis
    return when {
        incident.active && start == null -> true
        start == null -> false
        else -> start <= nowMillis && end >= cutoffMillis
    }
}

internal fun fleetReliabilitySummary(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    incidents: List<FleetIncident>,
    windowHours: Int,
    nowMillis: Long,
): FleetReliabilitySummary {
    val cutoff = nowMillis - windowHours * 3_600_000L
    val activeMonitors = monitors.filter { it.active }
    val activeMonitorIds = activeMonitors.mapTo(mutableSetOf()) { it.id }
    val incompleteMonitorCount = activeMonitors.count { monitor ->
        history[monitor.id].orEmpty().mapNotNull { kumaUtcMillisOrNull(it.time) }.minOrNull()
            ?.let { it > cutoff } != false
    }
    var observedDowntime = 0L
    val recoveryDurations = mutableListOf<Long>()
    val outageCounts = mutableMapOf<Int, Int>()

    incidents.forEach { incident ->
        if (incident.monitorId !in activeMonitorIds) return@forEach
        val start = kumaUtcMillisOrNull(incident.startedAt) ?: return@forEach
        val end = (kumaUtcMillisOrNull(incident.resolvedAt) ?: nowMillis).coerceAtMost(nowMillis)
        if (end < cutoff || start > nowMillis) return@forEach
        observedDowntime += (end - start.coerceAtLeast(cutoff)).coerceAtLeast(0L)
        if (start >= cutoff) {
            outageCounts[incident.monitorId] = outageCounts.getOrDefault(incident.monitorId, 0) + 1
            if (!incident.active) recoveryDurations += (end - start).coerceAtLeast(0L)
        }
    }

    val names = monitors.associate { it.id to it.name }
    val flakiest = outageCounts.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { names[it.key].orEmpty() })
        .firstOrNull()
        ?.let { MonitorFlakiness(it.key, names[it.key].orEmpty(), it.value) }
    return FleetReliabilitySummary(
        observedDowntimeMillis = observedDowntime,
        meanTimeToRecoveryMillis = recoveryDurations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        flakiestMonitor = flakiest,
        incompleteMonitorCount = incompleteMonitorCount,
        activeMonitorCount = activeMonitors.size,
    )
}

internal fun fleetIncidents(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
): List<FleetIncident> = monitors.flatMap { monitor ->
    val incidents = mutableListOf<FleetIncident>()
    var downStart: Heartbeat? = null
    history[monitor.id].orEmpty().forEach { beat ->
        if (beat.status == MonitorStatus.DOWN && downStart == null) {
            downStart = beat
        } else if (beat.status != MonitorStatus.DOWN && downStart != null) {
            val start = downStart
            incidents += FleetIncident(
                monitorId = monitor.id,
                monitorName = monitor.name,
                startedAt = start.time,
                resolvedAt = beat.time,
                message = start.msg,
            )
            downStart = null
        }
    }
    if (downStart != null || (monitor.status == MonitorStatus.DOWN && incidents.none { it.active })) {
        incidents += FleetIncident(
            monitorId = monitor.id,
            monitorName = monitor.name,
            startedAt = downStart?.time,
            resolvedAt = null,
            message = downStart?.msg,
        )
    }
    incidents
}.sortedWith(
    compareByDescending<FleetIncident> { it.active }
        .thenByDescending { it.startedAt.orEmpty() },
)

@Composable
internal fun FleetIncidentCenter(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    onClose: () -> Unit,
    onIncidentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val incidents = remember(monitors, history) { fleetIncidents(monitors, history) }
    var filter by remember { mutableStateOf(FleetIncidentFilter.ALL) }
    var window by remember { mutableStateOf(HeartbeatRange.DAY) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val cutoffMillis = nowMillis - window.hours * 3_600_000L
    val windowedIncidents = incidents.filter { incidentOverlapsWindow(it, cutoffMillis, nowMillis) }
    val reliability = fleetReliabilitySummary(monitors, history, incidents, window.hours, nowMillis)
    val shown = windowedIncidents.filter { incident ->
        when (filter) {
            FleetIncidentFilter.ALL -> true
            FleetIncidentFilter.ACTIVE -> incident.active
            FleetIncidentFilter.RESOLVED -> !incident.active
        }
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.incident_center_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.incident_center_close)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FleetIncidentFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                        selectedLabelColor = KumaGreen,
                    ),
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    FleetIncidentFilter.ALL -> R.string.filter_all
                                    FleetIncidentFilter.ACTIVE -> R.string.incident_filter_active
                                    FleetIncidentFilter.RESOLVED -> R.string.incident_filter_resolved
                                },
                            ),
                        )
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeartbeatRange.entries.forEach { option ->
                FilterChip(
                    selected = window == option,
                    onClick = { window = option },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                        selectedLabelColor = KumaGreen,
                    ),
                    label = { Text(option.label) },
                )
            }
        }
        if (shown.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { ReliabilitySummaryCard(window, reliability) }
                item {
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.incident_center_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ReliabilitySummaryCard(window, reliability) }
                items(shown, key = { "${it.monitorId}-${it.startedAt}-${it.resolvedAt}" }) { incident ->
                    UrsaPressableCard(onClick = { onIncidentClick(incident.monitorId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(
                                        StatusUi.color(if (incident.active) MonitorStatus.DOWN else MonitorStatus.UP),
                                    ),
                                )
                                Text(incident.monitorName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    stringResource(
                                        if (incident.active) R.string.statuspage_incident_active
                                        else R.string.statuspage_incident_resolved,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                incident.startedAt?.let { stringResource(R.string.incident_started_at, it) }
                                    ?: stringResource(R.string.incident_start_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            incident.resolvedAt?.let {
                                Text(
                                    stringResource(R.string.incident_resolved_at, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            incidentDurationMillis(incident, nowMillis)?.let { duration ->
                                Text(
                                    stringResource(R.string.incident_duration, compactDuration(duration)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (incident.active) StatusUi.color(MonitorStatus.DOWN)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            incident.message?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReliabilitySummaryCard(
    window: HeartbeatRange,
    summary: FleetReliabilitySummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.reliability_title, window.label),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReliabilityMetric(
                    value = compactDuration(summary.observedDowntimeMillis),
                    label = stringResource(R.string.reliability_downtime),
                    modifier = Modifier.weight(1f),
                )
                ReliabilityMetric(
                    value = summary.meanTimeToRecoveryMillis?.let(::compactDuration)
                        ?: stringResource(R.string.reliability_unavailable),
                    label = stringResource(R.string.reliability_mttr),
                    modifier = Modifier.weight(1f),
                )
                ReliabilityMetric(
                    value = summary.flakiestMonitor?.monitorName
                        ?: stringResource(R.string.reliability_unavailable),
                    label = summary.flakiestMonitor?.let {
                        pluralStringResource(R.plurals.reliability_incidents, it.incidents, it.incidents)
                    } ?: stringResource(R.string.reliability_no_outages),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (summary.activeMonitorCount == 0) {
                    stringResource(R.string.reliability_no_active_monitors)
                } else if (summary.incompleteMonitorCount == 0) {
                    pluralStringResource(
                        R.plurals.reliability_complete,
                        summary.activeMonitorCount,
                        summary.activeMonitorCount,
                    )
                } else {
                    pluralStringResource(
                        R.plurals.reliability_incomplete,
                        summary.activeMonitorCount,
                        summary.incompleteMonitorCount,
                        summary.activeMonitorCount,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReliabilityMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
