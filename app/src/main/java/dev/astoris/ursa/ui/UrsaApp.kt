package dev.astoris.ursa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.ui.connections.LoginScreen
import dev.astoris.ursa.ui.monitors.MonitorListScreen

@Composable
fun UrsaApp(vm: UrsaViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (state) {
        ConnectionState.Authenticated -> MonitorListScreen(vm)
        else -> LoginScreen(vm)
    }
}
