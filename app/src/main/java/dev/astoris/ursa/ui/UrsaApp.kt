package dev.astoris.ursa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.connections.LoginScreen
import dev.astoris.ursa.ui.connections.ConnectionManagerScreen
import dev.astoris.ursa.ui.lock.LockScreen
import dev.astoris.ursa.ui.monitors.MonitorDetailScreen
import dev.astoris.ursa.ui.statuspage.StatusPageScreen

@Composable
fun UrsaApp(vm: UrsaViewModel = viewModel()) {
    val startupReady by vm.startupReady.collectAsStateWithLifecycle()
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
        !startupReady -> StartupLoadingScreen()
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

@Composable
private fun StartupLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = CircleShape,
            ) {
                Icon(
                    painter = painterResource(R.mipmap.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp).size(34.dp),
                )
            }
            Text(
                text = stringResource(R.string.startup_loading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
