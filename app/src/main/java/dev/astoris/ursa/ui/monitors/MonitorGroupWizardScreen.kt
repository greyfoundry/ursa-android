package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.MonitorEditorRegistry
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.repository.MonitorGroupMutationResult
import dev.astoris.ursa.ui.AccessProfileNotice
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.allows
import dev.astoris.ursa.ui.components.OperationalStateKind
import dev.astoris.ursa.ui.components.OperationalStatePanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorGroupWizardScreen(
    vm: UrsaViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val connection by vm.activeConnection.collectAsStateWithLifecycle()
    val compatibility by vm.kumaCompatibility.collectAsStateWithLifecycle()
    val candidates = remember(monitors) {
        monitors.filter { it.type != "group" }.sortedBy { it.name.lowercase() }
    }
    var name by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }
    var preview by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MonitorGroupMutationResult?>(null) }
    val visible = remember(candidates, query) {
        query.trim().lowercase().takeIf(String::isNotEmpty)?.let { needle ->
            candidates.filter { it.name.lowercase().contains(needle) || it.type.lowercase().contains(needle) }
        } ?: candidates
    }
    val selected = remember(candidates, selectedIds) { candidates.filter { it.id in selectedIds } }
    val canCreate = connection.allows(AccessCapability.MONITOR_CREATE) &&
        MonitorEditorRegistry.createVerified("group", compatibility)
    val canAssign = selectedIds.isEmpty() || connection.allows(
        AccessCapability.MONITOR_EDIT,
        AccessCapability.BULK_WRITE,
    )
    val canApply = canCreate && canAssign && name.isNotBlank() && !saving

    BackHandler(enabled = !saving) {
        when {
            result != null -> result = null
            preview -> preview = false
            else -> onClose()
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.monitor_group_wizard_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AccessProfileNotice(connection) }
            when (val outcome = result) {
                null -> if (preview) {
                    item {
                        Text(
                            stringResource(R.string.monitor_group_preview_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(name.trim(), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.monitor_group_preview_count, selected.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (selected.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.monitor_group_preview_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(selected, key = Monitor::id) { monitor ->
                            Text("• ${monitor.name}", modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !saving, onClick = { preview = false }) {
                                Text(stringResource(R.string.action_back))
                            }
                            Button(
                                enabled = canApply,
                                onClick = {
                                    saving = true
                                    vm.createMonitorGroup(name, selectedIds) {
                                        result = it
                                        saving = false
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (saving) R.string.action_saving else R.string.monitor_group_apply,
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.monitor_group_wizard_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.monitor_group_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                stringResource(R.string.monitor_group_existing_children),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row {
                                TextButton(onClick = { selectedIds = visible.mapTo(mutableSetOf(), Monitor::id) }) {
                                    Text(stringResource(R.string.push_kuma_all))
                                }
                                TextButton(onClick = { selectedIds = emptySet() }) {
                                    Text(stringResource(R.string.push_kuma_none))
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(R.string.action_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (visible.isEmpty()) {
                        item {
                            OperationalStatePanel(
                                kind = OperationalStateKind.EMPTY,
                                title = stringResource(R.string.monitor_group_no_children_title),
                                message = stringResource(R.string.monitor_group_no_children_message),
                            )
                        }
                    } else {
                        items(visible, key = Monitor::id) { monitor ->
                            val checked = monitor.id in selectedIds
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = { enabled ->
                                            selectedIds = if (enabled) selectedIds + monitor.id
                                            else selectedIds - monitor.id
                                        },
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Column(Modifier.weight(1f)) {
                                    Text(monitor.name)
                                    Text(
                                        monitor.type,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    item {
                        Button(
                            enabled = name.isNotBlank() && canCreate && canAssign,
                            onClick = { preview = true },
                        ) { Text(stringResource(R.string.monitor_group_preview)) }
                    }
                    if (!canCreate || !canAssign) {
                        item {
                            Text(
                                stringResource(R.string.monitor_group_access_required),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                else -> {
                    val groupId = outcome.groupId
                    item {
                        OperationalStatePanel(
                            kind = when {
                                groupId == null -> OperationalStateKind.ERROR
                                outcome.failedIds.isNotEmpty() -> OperationalStateKind.PARTIAL
                                else -> OperationalStateKind.EMPTY
                            },
                            title = stringResource(
                                when {
                                    groupId == null -> R.string.monitor_group_failed_title
                                    outcome.failedIds.isNotEmpty() -> R.string.monitor_group_partial_title
                                    else -> R.string.monitor_group_success_title
                                },
                            ),
                            message = when {
                                groupId == null -> listOfNotNull(
                                    outcome.message,
                                    stringResource(R.string.monitor_group_failed_message),
                                ).joinToString("\n")
                                outcome.failedIds.isNotEmpty() -> stringResource(
                                    R.string.monitor_group_partial_message,
                                    outcome.succeededIds.size,
                                    outcome.failedIds.size,
                                )
                                else -> stringResource(R.string.monitor_group_success_message, outcome.succeededIds.size)
                            },
                        )
                    }
                    if (outcome.failedIds.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.monitor_group_failed_assignments),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        items(outcome.failedIds.sorted(), key = { "failed-$it" }) { id ->
                            val label = candidates.firstOrNull { it.id == id }?.name
                                ?: stringResource(R.string.monitor_fallback_name, id)
                            Text("• $label", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (groupId != null && outcome.failedIds.isNotEmpty()) {
                        item {
                            Button(
                                enabled = !saving,
                                onClick = {
                                    saving = true
                                    vm.retryMonitorGroupAssignments(groupId, outcome.failedIds) { retry ->
                                        result = outcome.copy(
                                            succeededIds = outcome.succeededIds + retry.succeededIds,
                                            failedIds = retry.failedIds,
                                            message = retry.message,
                                        )
                                        saving = false
                                    }
                                },
                            ) { Text(stringResource(R.string.monitor_group_retry_failed)) }
                        }
                    }
                    if (groupId != null) {
                        item {
                            OutlinedButton(onClick = { vm.createMonitor(groupId) }) {
                                Text(stringResource(R.string.monitor_group_create_child))
                            }
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                result = null
                                if (groupId != null) {
                                    name = ""
                                    selectedIds = emptySet()
                                    preview = false
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (groupId == null) R.string.monitor_group_back_to_plan
                                    else R.string.monitor_group_start_another,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
