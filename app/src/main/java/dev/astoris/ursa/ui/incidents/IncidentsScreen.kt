package dev.astoris.ursa.ui.incidents

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.monitors.FleetIncidentCenter

@Composable
fun IncidentsScreen(
    vm: UrsaViewModel,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()
    val notes by vm.incidentNotes.collectAsStateWithLifecycle()
    val serverUrl by vm.activeUrl.collectAsStateWithLifecycle()

    FleetIncidentCenter(
        monitors = monitors,
        history = history,
        notes = notes,
        serverUrl = serverUrl,
        loadImportantHeartbeats = vm::importantHeartbeatHistory,
        onIncidentClick = vm::openMonitor,
        onSaveNote = vm::saveIncidentNote,
        modifier = modifier.statusBarsPadding().fillMaxSize(),
    )
}
