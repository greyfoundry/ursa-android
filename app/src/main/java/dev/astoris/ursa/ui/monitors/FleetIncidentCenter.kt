package dev.astoris.ursa.ui.monitors

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.theme.KumaGreen

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
    val incidents = remember(monitors, history) { fleetIncidents(monitors, history) }
    var filter by remember { mutableStateOf(FleetIncidentFilter.ALL) }
    val shown = incidents.filter { incident ->
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
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.incident_center_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
