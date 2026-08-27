package dev.astoris.ursa.ui.push

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.KumaPushSetupError
import dev.astoris.ursa.ui.KumaPushSetupUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val distributors by vm.distributors.collectAsStateWithLifecycle()
    val distributor by vm.pushDistributor.collectAsStateWithLifecycle()
    val endpoint by vm.pushEndpoint.collectAsStateWithLifecycle()
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val kumaSetup by vm.kumaPushSetup.collectAsStateWithLifecycle()
    var selectedMonitorIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var defaultForNew by remember { mutableStateOf(true) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(endpoint) {
        if (endpoint != null) vm.refreshKumaPushSetup()
    }
    LaunchedEffect(kumaSetup) {
        (kumaSetup as? KumaPushSetupUiState.Ready)?.let { ready ->
            selectedMonitorIds = ready.selectedMonitorIds
            defaultForNew = ready.isDefault
        }
    }

    // Notification permission (API 33+). Below 33 it is granted at install time.
    fun notifGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(notifGranted()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_notifications)) },
                actions = { TextButton(onClick = { vm.refreshDistributors() }) { Text(stringResource(R.string.push_refresh)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.push_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!granted) {
                Section(stringResource(R.string.push_section_allow)) {
                    Text(
                        stringResource(R.string.push_allow_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.push_allow_button))
                    }
                }
            }

            Section(stringResource(R.string.push_section_distributor)) {
                if (distributors.isEmpty()) {
                    Text(
                        stringResource(R.string.push_no_distributor),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    distributors.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(d, style = MaterialTheme.typography.bodyMedium)
                            if (d == distributor) {
                                Text(stringResource(R.string.push_selected), color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = { vm.registerPush(d) }) { Text(stringResource(R.string.push_use)) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            val ep = endpoint
            if (ep != null) {
                Section(stringResource(R.string.push_section_endpoint)) {
                    Text(
                        stringResource(R.string.push_endpoint_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            ep,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.push_ntfy_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("UnifiedPush endpoint", ep)))
                                }
                            },
                        ) { Text(stringResource(R.string.push_copy)) }
                        OutlinedButton(onClick = { vm.unregisterPush() }) { Text(stringResource(R.string.push_disconnect)) }
                    }
                }

                Section(stringResource(R.string.push_kuma_section)) {
                    Text(
                        stringResource(R.string.push_kuma_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val setup = kumaSetup) {
                        KumaPushSetupUiState.Idle -> Button(onClick = vm::refreshKumaPushSetup) {
                            Text(stringResource(R.string.push_kuma_check))
                        }
                        KumaPushSetupUiState.Loading -> Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(stringResource(R.string.push_kuma_loading))
                        }
                        is KumaPushSetupUiState.Error -> {
                            Text(
                                stringResource(setup.reason.messageRes),
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = vm::refreshKumaPushSetup) {
                                Text(stringResource(R.string.push_kuma_retry))
                            }
                        }
                        is KumaPushSetupUiState.Ready -> {
                            Card(Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(
                                        when {
                                            setup.notificationId == null -> R.string.push_kuma_not_configured
                                            !setup.endpointCurrent -> R.string.push_kuma_update_needed
                                            else -> R.string.push_kuma_configured
                                        },
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (setup.notificationId != null && setup.endpointCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            if (setup.recentlySaved) {
                                Text(
                                    stringResource(R.string.push_kuma_saved),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = defaultForNew,
                                        role = Role.Checkbox,
                                        onValueChange = { defaultForNew = it },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = defaultForNew,
                                    onCheckedChange = null,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.push_kuma_default))
                                    Text(
                                        stringResource(R.string.push_kuma_default_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.push_kuma_existing),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row {
                                    TextButton(onClick = { selectedMonitorIds = monitors.mapTo(mutableSetOf()) { it.id } }) {
                                        Text(stringResource(R.string.push_kuma_all))
                                    }
                                    TextButton(onClick = { selectedMonitorIds = emptySet() }) {
                                        Text(stringResource(R.string.push_kuma_none))
                                    }
                                }
                            }
                            monitors.forEach { monitor ->
                                val selected = monitor.id in selectedMonitorIds
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = selected,
                                            role = Role.Checkbox,
                                            onValueChange = { checked ->
                                                selectedMonitorIds = if (checked) {
                                                    selectedMonitorIds + monitor.id
                                                } else {
                                                    selectedMonitorIds - monitor.id
                                                }
                                            },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = null,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(monitor.name)
                                        Text(
                                            stringResource(
                                                if (monitor.active) R.string.filter_active else R.string.filter_paused,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (setup.unavailableMonitorIds.isNotEmpty()) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.push_kuma_partial,
                                        setup.unavailableMonitorIds.size,
                                        setup.unavailableMonitorIds.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { vm.saveKumaPushSetup(selectedMonitorIds, defaultForNew) },
                                ) {
                                    Text(
                                        stringResource(
                                            if (setup.notificationId == null) R.string.push_kuma_create
                                            else R.string.push_kuma_update,
                                        ),
                                    )
                                }
                                if (setup.notificationId != null) {
                                    OutlinedButton(onClick = { confirmRemove = true }) {
                                        Text(stringResource(R.string.push_kuma_remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.push_kuma_remove_title)) },
            text = { Text(stringResource(R.string.push_kuma_remove_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        vm.deleteKumaPushSetup()
                    },
                ) { Text(stringResource(R.string.push_kuma_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private val KumaPushSetupError.messageRes: Int
    get() = when (this) {
        KumaPushSetupError.INVALID_ENDPOINT -> R.string.push_kuma_invalid_endpoint
        KumaPushSetupError.SERVER_UNAVAILABLE -> R.string.push_kuma_server_unavailable
        KumaPushSetupError.SAVE_FAILED -> R.string.push_kuma_save_failed
        KumaPushSetupError.DELETE_FAILED -> R.string.push_kuma_delete_failed
    }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
