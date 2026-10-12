package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.MonitorEditorRegistry
import dev.astoris.ursa.core.storage.MonitorPreset
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.allows
import dev.astoris.ursa.ui.components.OperationalStateKind
import dev.astoris.ursa.ui.components.OperationalStatePanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorPresetsScreen(
    vm: UrsaViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets by vm.monitorPresets.collectAsStateWithLifecycle()
    val compatibility by vm.kumaCompatibility.collectAsStateWithLifecycle()
    val activeConnection by vm.activeConnection.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<MonitorPreset?>(null) }
    BackHandler(onBack = onClose)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.monitor_presets_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.monitor_presets_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (presets.isEmpty()) {
                item {
                    OperationalStatePanel(
                        kind = OperationalStateKind.EMPTY,
                        title = stringResource(R.string.monitor_presets_empty_title),
                        message = stringResource(R.string.monitor_presets_empty_message),
                    )
                }
            } else {
                items(presets, key = MonitorPreset::id) { preset ->
                    val definition = MonitorEditorRegistry.find(preset.type)
                    val canUse = activeConnection.allows(AccessCapability.MONITOR_CREATE) &&
                        MonitorEditorRegistry.createVerified(preset.type, compatibility)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(preset.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                definition?.label ?: preset.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.monitor_preset_inactive_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { vm.createMonitorFromPreset(preset.id) },
                                enabled = canUse,
                            ) { Text(stringResource(R.string.monitor_preset_use)) }
                            OutlinedButton(onClick = { deleteTarget = preset }) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.monitor_preset_delete_title)) },
            text = { Text(stringResource(R.string.monitor_preset_delete_message, preset.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteMonitorPreset(preset.id)
                        deleteTarget = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
