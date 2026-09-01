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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import dev.astoris.ursa.core.network.MonitorDraft
import dev.astoris.ursa.core.network.MonitorDraftCodec
import dev.astoris.ursa.core.network.MonitorDraftError
import dev.astoris.ursa.core.network.MonitorEndpointKind
import dev.astoris.ursa.core.network.MonitorTypeCatalog
import dev.astoris.ursa.data.model.KumaNotification
import dev.astoris.ursa.ui.MonitorEditorUiState
import dev.astoris.ursa.ui.UrsaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorEditorScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val state by vm.monitorEditor.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val stateDraft = when (val current = state) {
        is MonitorEditorUiState.Ready -> current.draft
        is MonitorEditorUiState.Saving -> current.draft
        is MonitorEditorUiState.Error -> current.draft
        else -> null
    }
    var draft by remember(stateDraft?.id, stateDraft?.type) {
        mutableStateOf(stateDraft ?: MonitorDraft.create())
    }
    val saving = state is MonitorEditorUiState.Saving
    val serverError = (state as? MonitorEditorUiState.Error)?.message
    BackHandler(enabled = !saving) { vm.closeMonitorEditor() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (draft.isNew) R.string.monitor_add_title else R.string.monitor_edit_title))
                },
            )
        },
    ) { padding ->
        when {
            state is MonitorEditorUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.monitor_loading_details), modifier = Modifier.padding(top = 12.dp))
            }
            stateDraft == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(serverError ?: stringResource(R.string.monitor_load_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::closeMonitorEditor) { Text(stringResource(R.string.action_back)) }
            }
            else -> MonitorForm(
                draft = draft,
                onDraftChange = { draft = it },
                saving = saving,
                serverError = serverError,
                notifications = notifications,
                onCancel = vm::closeMonitorEditor,
                onSave = { vm.saveMonitor(draft) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorForm(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
    saving: Boolean,
    serverError: String?,
    notifications: List<KumaNotification>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val option = MonitorTypeCatalog.find(draft.type)
    val validation = MonitorDraftCodec.validate(draft)
    var typeMenuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it.take(250))) },
            label = { Text(stringResource(R.string.monitor_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft.isNew) {
            ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
                OutlinedTextField(
                    value = option?.label.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.monitor_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuOpen) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    MonitorTypeCatalog.creatable.forEach { next ->
                        DropdownMenuItem(
                            text = { Text(next.label) },
                            onClick = {
                                val defaults = MonitorDraft.create(next.key)
                                onDraftChange(
                                    defaults.copy(
                                        name = draft.name,
                                        description = draft.description,
                                        intervalSeconds = draft.intervalSeconds,
                                        retryIntervalSeconds = draft.retryIntervalSeconds,
                                        resendIntervalSeconds = draft.resendIntervalSeconds,
                                        maxRetries = draft.maxRetries,
                                        active = draft.active,
                                        notificationIds = draft.notificationIds,
                                    ),
                                )
                                typeMenuOpen = false
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.monitor_create_types_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.detail_type, option?.label ?: draft.type))
            Text(
                stringResource(R.string.monitor_advanced_preserved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (option?.endpointKind != MonitorEndpointKind.NONE) {
            OutlinedTextField(
                value = draft.endpoint,
                onValueChange = { onDraftChange(draft.copy(endpoint = it.take(2_048))) },
                label = {
                    Text(
                        stringResource(
                            if (option?.endpointKind == MonitorEndpointKind.URL) {
                                R.string.monitor_url_label
                            } else {
                                R.string.monitor_host_label
                            },
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (option?.endpointKind == MonitorEndpointKind.HOST_PORT) {
            NumberField(
                value = draft.port,
                onValueChange = { onDraftChange(draft.copy(port = it)) },
                label = stringResource(R.string.monitor_port_label),
                maxDigits = 5,
            )
        }
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChange(draft.copy(description = it.take(2_000))) },
            label = { Text(stringResource(R.string.monitor_description_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(
            value = draft.intervalSeconds,
            onValueChange = { onDraftChange(draft.copy(intervalSeconds = it ?: 0)) },
            label = stringResource(R.string.monitor_interval_label),
        )
        NumberField(
            value = draft.retryIntervalSeconds,
            onValueChange = { onDraftChange(draft.copy(retryIntervalSeconds = it ?: 0)) },
            label = stringResource(R.string.monitor_retry_interval_label),
        )
        NumberField(
            value = draft.maxRetries,
            onValueChange = { onDraftChange(draft.copy(maxRetries = it ?: 0)) },
            label = stringResource(R.string.monitor_retries_label),
            maxDigits = 3,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.active,
                onCheckedChange = { onDraftChange(draft.copy(active = it)) },
            )
            Text(stringResource(R.string.monitor_active_label))
        }
        Text(stringResource(R.string.monitor_notifications_title), style = MaterialTheme.typography.titleSmall)
        if (notifications.isEmpty()) {
            Text(
                stringResource(R.string.monitor_notifications_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.monitor_notifications_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val defaultSuffix = stringResource(R.string.monitor_notification_default_suffix)
            notifications.forEach { notification ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = notification.id in draft.notificationIds,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draft.copy(
                                    notificationIds = if (checked) {
                                        draft.notificationIds + notification.id
                                    } else {
                                        draft.notificationIds - notification.id
                                    },
                                ),
                            )
                        },
                    )
                    Column {
                        Text(notification.name)
                        Text(
                            buildString {
                                append(notification.type)
                                if (notification.isDefault) append(defaultSuffix)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        validation?.let { Text(validationMessage(it), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onSave, enabled = !saving && validation == null) {
                Text(stringResource(if (saving) R.string.action_saving else R.string.action_save))
            }
        }
    }
}

@Composable
private fun NumberField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    label: String,
    maxDigits: Int = 7,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(maxDigits).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun validationMessage(error: MonitorDraftError): String = stringResource(
    when (error) {
        MonitorDraftError.NAME_REQUIRED -> R.string.monitor_error_name
        MonitorDraftError.TYPE_UNAVAILABLE -> R.string.monitor_error_type
        MonitorDraftError.ENDPOINT_REQUIRED -> R.string.monitor_error_endpoint
        MonitorDraftError.INVALID_URL -> R.string.monitor_error_url
        MonitorDraftError.PORT_REQUIRED -> R.string.monitor_error_port
        MonitorDraftError.INVALID_INTERVAL -> R.string.monitor_error_interval
        MonitorDraftError.INVALID_RETRIES -> R.string.monitor_error_retries
    },
)
