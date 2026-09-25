package dev.astoris.ursa.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.astoris.ursa.ui.navigation.LegacyRouteState
import dev.astoris.ursa.ui.navigation.UrsaNavHost

@Composable
fun UrsaApp(vm: UrsaViewModel = viewModel()) {
    val startupReady by vm.startupReady.collectAsStateWithLifecycle()
    val selected by vm.selectedMonitor.collectAsStateWithLifecycle()
    val selectedId by vm.selectedId.collectAsStateWithLifecycle()
    val statusPageMode by vm.statusPageMode.collectAsStateWithLifecycle()
    val selectedStatusPageId by vm.selectedStatusPageId.collectAsStateWithLifecycle()
    val hasSession by vm.hasSession.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val connectionManagerMode by vm.connectionManagerMode.collectAsStateWithLifecycle()
    val addingConnection by vm.addingConnection.collectAsStateWithLifecycle()
    val editingConnection by vm.editingConnection.collectAsStateWithLifecycle()
    val monitorEditor by vm.monitorEditor.collectAsStateWithLifecycle()
    val kioskMode by vm.kioskMode.collectAsStateWithLifecycle()
    val mainTab by vm.tab.collectAsStateWithLifecycle()
    val accessDenial by vm.accessDenial.collectAsStateWithLifecycle()

    // Re-lock when the app goes to the background (if the lock is enabled).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.relock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = AdaptiveLayout.isExpanded(maxWidth.value)
        val routeState = LegacyRouteState(
            locked = locked,
            startupReady = startupReady,
            statusPageMode = statusPageMode,
            selectedStatusPageId = selectedStatusPageId,
            connectionManagerMode = connectionManagerMode,
            addingConnection = addingConnection,
            editingConnection = editingConnection != null,
            hasSession = hasSession,
            monitorEditorOpen = monitorEditor !is MonitorEditorUiState.Idle,
            monitorEditorId = monitorEditor.monitorId(),
            kioskMode = kioskMode,
            selectedMonitorId = selectedId,
            expanded = expanded,
            mainTab = mainTab,
        )
        UrsaNavHost(
            vm = vm,
            routeState = routeState,
            selected = selected,
            editingConnection = editingConnection,
            monitorEditor = monitorEditor,
            expanded = expanded,
            modifier = Modifier.fillMaxSize(),
        )
    }

    accessDenial?.let { denial ->
        AlertDialog(
            onDismissRequest = vm::dismissAccessDenial,
            title = { Text(stringResource(R.string.access_denied_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.access_denied_message,
                        stringResource(denial.profile.labelRes()),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = vm::dismissAccessDenial) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

private fun MonitorEditorUiState.monitorId(): Int? = when (this) {
    is MonitorEditorUiState.Ready -> draft.id
    is MonitorEditorUiState.Saving -> draft.id
    is MonitorEditorUiState.Error -> draft?.id
    MonitorEditorUiState.Idle,
    MonitorEditorUiState.Loading,
    -> null
}

@Composable
internal fun StartupLoadingScreen(modifier: Modifier = Modifier) {
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
