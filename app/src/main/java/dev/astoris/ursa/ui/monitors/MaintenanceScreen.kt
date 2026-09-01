package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.MaintenanceCodec
import dev.astoris.ursa.core.network.MaintenanceDraft
import dev.astoris.ursa.core.network.MaintenanceDraftError
import dev.astoris.ursa.core.network.MaintenanceStrategy
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.ui.MaintenanceEditorUiState
import dev.astoris.ursa.ui.UrsaViewModel

@Composable
fun MaintenanceScreen(vm: UrsaViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val maintenances by vm.maintenances.collectAsStateWithLifecycle()
    val editor by vm.maintenanceEditor.collectAsStateWithLifecycle()
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<MaintenanceDraft?>(null) }

    BackHandler {
        if (editor is MaintenanceEditorUiState.Idle) onClose() else vm.closeMaintenanceEditor()
    }

    val editorDraft = when (val state = editor) {
        is MaintenanceEditorUiState.Ready -> state.draft
        is MaintenanceEditorUiState.Saving -> state.draft
        is MaintenanceEditorUiState.Error -> state.draft
        else -> null
    }
    if (editorDraft != null) {
        MaintenanceEditor(
            initial = editorDraft,
            monitors = monitors,
            saving = editor is MaintenanceEditorUiState.Saving,
            error = (editor as? MaintenanceEditorUiState.Error)?.message,
            onSave = vm::saveMaintenance,
            onCancel = vm::closeMaintenanceEditor,
            modifier = modifier,
        )
    } else {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.status_maintenance), style = MaterialTheme.typography.headlineSmall)
                Row {
                    TextButton(onClick = vm::createMaintenance) { Text(stringResource(R.string.action_add)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
                }
            }
            if (editor is MaintenanceEditorUiState.Loading) Text(stringResource(R.string.maintenance_loading))
            (editor as? MaintenanceEditorUiState.Error)?.takeIf { it.draft == null }?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }
            if (maintenances.isEmpty()) {
                Text(stringResource(R.string.maintenance_empty))
            }
            maintenances.forEach { maintenance ->
                Card(onClick = { maintenance.id?.let(vm::editMaintenance) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(maintenance.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                R.string.maintenance_summary,
                                strategyLabel(maintenance.strategy),
                                maintenance.status,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        maintenance.resolvedTimezone.takeIf(String::isNotBlank)?.let {
                            Text(stringResource(R.string.maintenance_timezone_value, it), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                maintenance.id?.let { id -> vm.setMaintenanceActive(id, !maintenance.active) {} }
                            }) { Text(stringResource(if (maintenance.active) R.string.action_pause else R.string.action_resume)) }
                            TextButton(onClick = { confirmDelete = maintenance }) { Text(stringResource(R.string.action_delete)) }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { maintenance ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.maintenance_delete_title)) },
            text = { Text(stringResource(R.string.maintenance_delete_message, maintenance.title)) },
            confirmButton = {
                Button(onClick = {
                    maintenance.id?.let { id -> vm.deleteMaintenance(id) { if (it) confirmDelete = null } }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceEditor(
    initial: MaintenanceDraft,
    monitors: List<Monitor>,
    saving: Boolean,
    error: String?,
    onSave: (MaintenanceDraft) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initial.id, initial.strategy) { mutableStateOf(initial) }
    var strategyOpen by remember { mutableStateOf(false) }
    val validation = MaintenanceCodec.validate(draft)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (draft.isNew) R.string.maintenance_add_title else R.string.maintenance_edit_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft = draft.copy(title = it.take(250)) },
            label = { Text(stringResource(R.string.maintenance_title_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { draft = draft.copy(description = it.take(2_000)) },
            label = { Text(stringResource(R.string.monitor_description_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(expanded = strategyOpen, onExpandedChange = { strategyOpen = it }) {
            OutlinedTextField(
                value = strategyLabel(draft.strategy),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.maintenance_strategy_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyOpen) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = strategyOpen, onDismissRequest = { strategyOpen = false }) {
                MaintenanceStrategy.entries.forEach { strategy ->
                    DropdownMenuItem(
                        text = { Text(strategyLabel(strategy)) },
                        onClick = { draft = draft.copy(strategy = strategy); strategyOpen = false },
                    )
                }
            }
        }

        when (draft.strategy) {
            MaintenanceStrategy.MANUAL -> Text(stringResource(R.string.maintenance_manual_desc))
            MaintenanceStrategy.SINGLE -> DateRangeFields(draft) { draft = it }
            MaintenanceStrategy.CRON -> {
                OutlinedTextField(
                    value = draft.cron,
                    onValueChange = { draft = draft.copy(cron = it.take(200)) },
                    label = { Text(stringResource(R.string.maintenance_cron_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                IntegerField(draft.durationMinutes, stringResource(R.string.maintenance_duration_label)) {
                    draft = draft.copy(durationMinutes = it)
                }
                DateRangeFields(draft) { draft = it }
            }
            MaintenanceStrategy.RECURRING_INTERVAL -> {
                IntegerField(draft.intervalDay, stringResource(R.string.maintenance_interval_label)) { draft = draft.copy(intervalDay = it) }
                TimeRangeFields(draft) { draft = it }
                DateRangeFields(draft) { draft = it }
            }
            MaintenanceStrategy.RECURRING_WEEKDAY -> {
                Text(stringResource(R.string.maintenance_weekdays_label))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                        val day = index + 1
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label)
                            Checkbox(
                                checked = day in draft.weekdays,
                                onCheckedChange = { checked ->
                                    draft = draft.copy(
                                        weekdays = if (checked) draft.weekdays + day else draft.weekdays - day,
                                    )
                                },
                            )
                        }
                    }
                }
                TimeRangeFields(draft) { draft = it }
                DateRangeFields(draft) { draft = it }
            }
            MaintenanceStrategy.RECURRING_DAY_OF_MONTH -> {
                OutlinedTextField(
                    value = draft.daysOfMonth.joinToString(","),
                    onValueChange = { text ->
                        val values = text.split(',').map(String::trim)
                            .filter { it == "lastDay1" || it.toIntOrNull() in 1..31 }.toSet()
                        draft = draft.copy(daysOfMonth = values)
                    },
                    label = { Text(stringResource(R.string.maintenance_days_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeRangeFields(draft) { draft = it }
                DateRangeFields(draft) { draft = it }
            }
        }

        if (draft.strategy != MaintenanceStrategy.MANUAL) {
            OutlinedTextField(
                value = draft.timezoneOption,
                onValueChange = { draft = draft.copy(timezoneOption = it.take(100)) },
                label = { Text(stringResource(R.string.maintenance_timezone_label)) },
                supportingText = {
                    draft.resolvedTimezone.takeIf(String::isNotBlank)?.let {
                        Text(stringResource(R.string.maintenance_timezone_resolved, it))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.active, onCheckedChange = { draft = draft.copy(active = it) })
            Text(stringResource(R.string.maintenance_active_label))
        }
        Text(stringResource(R.string.maintenance_monitors_title), style = MaterialTheme.typography.titleSmall)
        if (monitors.isEmpty()) Text(stringResource(R.string.maintenance_monitors_empty))
        monitors.forEach { monitor ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = monitor.id in draft.monitorIds,
                    onCheckedChange = { checked ->
                        draft = draft.copy(
                            monitorIds = if (checked) draft.monitorIds + monitor.id else draft.monitorIds - monitor.id,
                        )
                    },
                )
                Text(monitor.name)
            }
        }
        validation?.let { Text(maintenanceError(it), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = { onSave(draft) }, enabled = !saving && validation == null) {
                Text(stringResource(if (saving) R.string.action_saving else R.string.action_save))
            }
        }
    }
}

@Composable
private fun DateRangeFields(draft: MaintenanceDraft, onChange: (MaintenanceDraft) -> Unit) {
    OutlinedTextField(
        value = draft.startDate,
        onValueChange = { onChange(draft.copy(startDate = it.take(30))) },
        label = { Text(stringResource(R.string.maintenance_start_date_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.endDate,
        onValueChange = { onChange(draft.copy(endDate = it.take(30))) },
        label = { Text(stringResource(R.string.maintenance_end_date_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimeRangeFields(draft: MaintenanceDraft, onChange: (MaintenanceDraft) -> Unit) {
    OutlinedTextField(
        value = draft.startTime,
        onValueChange = { onChange(draft.copy(startTime = it.take(5))) },
        label = { Text(stringResource(R.string.maintenance_start_time_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.endTime,
        onValueChange = { onChange(draft.copy(endTime = it.take(5))) },
        label = { Text(stringResource(R.string.maintenance_end_time_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntegerField(value: Int, label: String, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onChange(it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun maintenanceError(error: MaintenanceDraftError): String = stringResource(
    when (error) {
        MaintenanceDraftError.TITLE_REQUIRED -> R.string.maintenance_error_title
        MaintenanceDraftError.DATE_RANGE_REQUIRED -> R.string.maintenance_error_dates
        MaintenanceDraftError.CRON_REQUIRED -> R.string.maintenance_error_cron
        MaintenanceDraftError.DURATION_REQUIRED -> R.string.maintenance_error_duration
        MaintenanceDraftError.INTERVAL_REQUIRED -> R.string.maintenance_error_interval
        MaintenanceDraftError.WEEKDAYS_REQUIRED -> R.string.maintenance_error_weekdays
        MaintenanceDraftError.DAYS_OF_MONTH_REQUIRED -> R.string.maintenance_error_days
        MaintenanceDraftError.TIME_RANGE_REQUIRED -> R.string.maintenance_error_times
    },
)

@Composable
private fun strategyLabel(strategy: MaintenanceStrategy): String = stringResource(
    when (strategy) {
        MaintenanceStrategy.MANUAL -> R.string.maintenance_strategy_manual
        MaintenanceStrategy.SINGLE -> R.string.maintenance_strategy_single
        MaintenanceStrategy.CRON -> R.string.maintenance_strategy_cron
        MaintenanceStrategy.RECURRING_INTERVAL -> R.string.maintenance_strategy_interval
        MaintenanceStrategy.RECURRING_WEEKDAY -> R.string.maintenance_strategy_weekdays
        MaintenanceStrategy.RECURRING_DAY_OF_MONTH -> R.string.maintenance_strategy_days
    },
)
