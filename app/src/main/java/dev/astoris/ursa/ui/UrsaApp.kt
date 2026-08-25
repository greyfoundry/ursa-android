package dev.astoris.ursa.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.astoris.ursa.ui.connections.LoginScreen
import dev.astoris.ursa.ui.connections.ConnectionManagerScreen
import dev.astoris.ursa.ui.lock.LockScreen
import dev.astoris.ursa.ui.monitors.MonitorDetailScreen
import dev.astoris.ursa.ui.statuspage.StatusPageScreen

@Composable
fun UrsaApp(vm: UrsaViewModel = viewModel()) {
    val selected by vm.selectedMonitor.collectAsStateWithLifecycle()
    val statusPageMode by vm.statusPageMode.collectAsStateWithLifecycle()
    val hasSession by vm.hasSession.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val connectionManagerMode by vm.connectionManagerMode.collectAsStateWithLifecycle()
    val addingConnection by vm.addingConnection.collectAsStateWithLifecycle()
    val editingConnection by vm.editingConnection.collectAsStateWithLifecycle()

    // Re-lock when the app goes to the background (if the lock is enabled).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.relock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        locked -> LockScreen(vm)
        statusPageMode -> StatusPageScreen(vm)
        connectionManagerMode && addingConnection -> LoginScreen(
            vm = vm,
            initialConnection = editingConnection,
            onBack = { vm.cancelAddingConnection() },
            onConnected = { vm.finishAddingConnection() },
        )
        connectionManagerMode -> ConnectionManagerScreen(vm)
        !hasSession -> LoginScreen(vm)
        selected != null -> MonitorDetailScreen(vm, selected!!)
        else -> MainShell(vm)
    }
}
