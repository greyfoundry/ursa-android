package dev.astoris.ursa.ui.incidents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.monitors.FleetIncidentCenter
import dev.astoris.ursa.ui.theme.KumaGreen

private enum class IncidentSource { KUMA_HISTORY, DEVICE_ALERTS }

@Composable
fun IncidentsScreen(
    vm: UrsaViewModel,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()
    val notes by vm.incidentNotes.collectAsStateWithLifecycle()
    val serverUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val localEvents by vm.localEvents.collectAsStateWithLifecycle()
    val validMonitorIds = remember(monitors) { monitors.mapTo(mutableSetOf()) { it.id } }
    var source by rememberSaveable { mutableStateOf(IncidentSource.KUMA_HISTORY) }

    Column(modifier.statusBarsPadding().fillMaxSize()) {
        Text(
            stringResource(R.string.incident_center_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        Text(
            stringResource(
                if (source == IncidentSource.KUMA_HISTORY) R.string.incident_center_source
                else R.string.incident_alert_source,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(IncidentSource.entries) { option ->
                FilterChip(
                    selected = source == option,
                    onClick = { source = option },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                        selectedLabelColor = KumaGreen,
                    ),
                    label = {
                        Text(
                            stringResource(
                                if (option == IncidentSource.KUMA_HISTORY) R.string.incident_source_kuma
                                else R.string.incident_source_device_alerts,
                            ),
                        )
                    },
                )
            }
        }
        when (source) {
            IncidentSource.KUMA_HISTORY -> FleetIncidentCenter(
                monitors = monitors,
                history = history,
                notes = notes,
                serverUrl = serverUrl,
                loadImportantHeartbeats = vm::importantHeartbeatHistory,
                onIncidentClick = vm::openMonitor,
                onSaveNote = vm::saveIncidentNote,
                modifier = Modifier.fillMaxSize(),
                showHeader = false,
            )
            IncidentSource.DEVICE_ALERTS -> LocalAlertGroups(
                events = localEvents,
                validMonitorIds = validMonitorIds,
                onMonitorClick = vm::openMonitor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
