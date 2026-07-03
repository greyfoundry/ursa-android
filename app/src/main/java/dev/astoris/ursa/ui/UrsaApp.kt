package dev.astoris.ursa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.astoris.ursa.ui.connections.LoginScreen
import dev.astoris.ursa.ui.monitors.MonitorDetailScreen
import dev.astoris.ursa.ui.monitors.MonitorListScreen
import dev.astoris.ursa.ui.statuspage.StatusPageScreen

@Composable
fun UrsaApp(vm: UrsaViewModel = viewModel()) {
    val selected by vm.selectedMonitor.collectAsStateWithLifecycle()
    val statusPageMode by vm.statusPageMode.collectAsStateWithLifecycle()
    val hasSession by vm.hasSession.collectAsStateWithLifecycle()

    when {
        statusPageMode -> StatusPageScreen(vm)
        !hasSession -> LoginScreen(vm)
        selected != null -> MonitorDetailScreen(vm, selected!!)
        else -> MonitorListScreen(vm)
    }
}
