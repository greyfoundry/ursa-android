package dev.astoris.ursa.ui.monitors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus

@Composable
internal fun AdvancedFilterDialog(
    filter: MonitorViewFilter,
    monitors: List<Monitor>,
    savedViews: List<SavedMonitorView>,
    onApply: (MonitorViewFilter) -> Unit,
    onSave: (SavedMonitorView) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    var name by remember { mutableStateOf("") }
    val groups = remember(monitors) { monitors.filter { it.type == "group" }.sortedBy { it.name.lowercase() } }
    val types = remember(monitors) { monitors.map(Monitor::type).distinct().sorted() }
    val tags = remember(monitors) { monitors.flatMap(Monitor::tags).distinct().sorted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_advanced)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterSection(stringResource(R.string.filter_activity)) {
                    ActivityFilter.entries.forEach { value ->
                        FilterChip(
                            selected = draft.activity == value,
                            onClick = { draft = draft.copy(activity = value) },
                            label = { Text(stringResource(value.labelRes())) },
                        )
                    }
                }
                FilterSection(stringResource(R.string.filter_status)) {
                    MonitorStatus.entries.forEach { status ->
                        FilterChip(
                            selected = status in draft.statuses,
                            onClick = { draft = draft.copy(statuses = draft.statuses.toggle(status)) },
                            label = { Text(status.name.lowercase().replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
                if (tags.isNotEmpty()) FilterSection(stringResource(R.string.filter_tags)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag in draft.tags,
                            onClick = { draft = draft.copy(tags = draft.tags.toggle(tag)) },
                            label = { Text(tag) },
                        )
                    }
                }
                if (groups.isNotEmpty()) FilterSection(stringResource(R.string.filter_groups)) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = group.id in draft.groups,
                            onClick = { draft = draft.copy(groups = draft.groups.toggle(group.id)) },
                            label = { Text(group.name) },
                        )
                    }
                }
                FilterSection(stringResource(R.string.filter_types)) {
                    types.forEach { type ->
                        FilterChip(
                            selected = type in draft.types,
                            onClick = { draft = draft.copy(types = draft.types.toggle(type)) },
                            label = { Text(type) },
                        )
                    }
                }
                FilterSection(stringResource(R.string.filter_certificate)) {
                    CertificateFilter.entries.forEach { value ->
                        FilterChip(
                            selected = draft.certificate == value,
                            onClick = { draft = draft.copy(certificate = value) },
                            label = { Text(stringResource(value.labelRes())) },
                        )
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.saved_views_title))
                savedViews.forEach { view ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { draft = view.filter }) { Text(view.name) }
                        TextButton(onClick = { onDelete(view.name) }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.saved_view_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onSave(SavedMonitorView(name.trim(), draft)); name = "" },
                    enabled = MonitorViewCodec.isValidName(name),
                ) { Text(stringResource(R.string.saved_view_save)) }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = {
            TextButton(onClick = { draft = MonitorViewFilter(); onApply(draft) }) {
                Text(stringResource(R.string.action_clear))
            }
        },
    )
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = { content() },
        )
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private fun ActivityFilter.labelRes() = when (this) {
    ActivityFilter.ACTIVE -> R.string.filter_active
    ActivityFilter.PAUSED -> R.string.filter_paused
    ActivityFilter.ALL -> R.string.filter_all
}

private fun CertificateFilter.labelRes() = when (this) {
    CertificateFilter.ANY -> R.string.filter_any_certificate
    CertificateFilter.HAS_CERTIFICATE -> R.string.filter_has_certificate
    CertificateFilter.NO_CERTIFICATE -> R.string.filter_no_certificate
}
