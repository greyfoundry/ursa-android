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
import dev.astoris.ursa.ui.lock.LockScreen
import dev.astoris.ursa.ui.monitors.MonitorDetailScreen
import dev.astoris.ursa.ui.monitors.MonitorListScreen
import dev.astoris.ursa.ui.push.PushScreen
import dev.astoris.ursa.ui.settings.SettingsScreen
import dev.astoris.ursa.ui.statuspage.StatusPageScreen

@Composable
fun UrsaApp(vm: UrsaViewModel = viewModel()) {
    val selected by vm.selectedMonitor.collectAsStateWithLifecycle()
    val statusPageMode by vm.statusPageMode.collectAsStateWithLifecycle()
    val pushMode by vm.pushMode.collectAsStateWithLifecycle()
    val settingsMode by vm.settingsMode.collectAsStateWithLifecycle()
    val hasSession by vm.hasSession.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()

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
        !hasSession -> LoginScreen(vm)
        settingsMode -> SettingsScreen(vm)
        pushMode -> PushScreen(vm)
        selected != null -> MonitorDetailScreen(vm, selected!!)
        else -> MonitorListScreen(vm)
    }
}
