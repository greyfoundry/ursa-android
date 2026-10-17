package dev.astoris.ursa.ui.monitors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.repository.BulkMonitorEdit
import dev.astoris.ursa.data.repository.BulkMonitorEditPreview
import dev.astoris.ursa.data.repository.BulkMonitorEditRow

@Composable
internal fun BulkMonitorEditDialog(
    selectedCount: Int,
    preview: BulkMonitorEditPreview?,
    inFlight: Boolean,
    onPreview: (BulkMonitorEdit) -> Unit,
    onApply: (BulkMonitorEditPreview) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var intervalEnabled by remember { mutableStateOf(false) }
    var retryIntervalEnabled by remember { mutableStateOf(false) }
    var retriesEnabled by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf("") }
    var retryInterval by remember { mutableStateOf("") }
    var retries by remember { mutableStateOf("") }
    val edit = BulkMonitorEdit(
        intervalSeconds = interval.takeIf { intervalEnabled }?.toIntOrNull(),
        retryIntervalSeconds = retryInterval.takeIf { retryIntervalEnabled }?.toIntOrNull(),
        maxRetries = retries.takeIf { retriesEnabled }?.toIntOrNull(),
    )

    AlertDialog(
        onDismissRequest = { if (!inFlight) onDismiss() },
        title = {
            Text(
                if (preview == null) {
                    pluralStringResource(R.plurals.bulk_edit_title, selectedCount, selectedCount)
                } else {
                    stringResource(R.string.bulk_edit_preview_title)
                },
            )
        },
        text = {
            if (preview == null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.bulk_edit_scope_help))
                    BulkNumberField(
                        checked = intervalEnabled,
                        onCheckedChange = { intervalEnabled = it },
                        value = interval,
                        onValueChange = { interval = it },
                        label = stringResource(R.string.monitor_interval_label),
                    )
                    BulkNumberField(
                        checked = retryIntervalEnabled,
                        onCheckedChange = { retryIntervalEnabled = it },
                        value = retryInterval,
                        onValueChange = { retryInterval = it },
                        label = stringResource(R.string.monitor_retry_interval_label),
                    )
                    BulkNumberField(
                        checked = retriesEnabled,
                        onCheckedChange = { retriesEnabled = it },
                        value = retries,
                        onValueChange = { retries = it },
                        label = stringResource(R.string.monitor_retries_label),
                    )
                    if ((intervalEnabled || retryIntervalEnabled || retriesEnabled) && !edit.isValid) {
                        Text(stringResource(R.string.bulk_edit_invalid))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(stringResource(R.string.bulk_edit_preview_help))
                    }
                    items(preview.rows, key = BulkMonitorEditRow::monitorId) { row ->
                        BulkMonitorEditPreviewRow(row)
                        HorizontalDivider()
                    }
                    if (preview.unchangedIds.isNotEmpty()) {
                        item {
                            Text(
                                pluralStringResource(
                                    R.plurals.bulk_edit_unchanged,
                                    preview.unchangedIds.size,
                                    preview.unchangedIds.size,
                                ),
                            )
                        }
                    }
                    if (preview.failedIds.isNotEmpty()) {
                        item {
                            Text(
                                pluralStringResource(
                                    R.plurals.bulk_edit_preview_failed,
                                    preview.failedIds.size,
                                    preview.failedIds.size,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (preview == null) onPreview(edit) else onApply(preview) },
                enabled = !inFlight && if (preview == null) edit.isValid else preview.rows.isNotEmpty(),
            ) {
                Text(stringResource(if (preview == null) R.string.action_preview else R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (preview == null) onDismiss() else onBack() },
                enabled = !inFlight,
            ) {
                Text(stringResource(if (preview == null) R.string.action_cancel else R.string.action_back))
            }
        },
    )
}

@Composable
private fun BulkNumberField(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit).take(5)) },
            label = { Text(label) },
            enabled = checked,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BulkMonitorEditPreviewRow(row: BulkMonitorEditRow) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(row.monitorName)
        if (row.before.intervalSeconds != row.after.intervalSeconds) {
            Text(
                stringResource(
                    R.string.bulk_edit_change_seconds,
                    stringResource(R.string.bulk_edit_check_interval),
                    row.before.intervalSeconds,
                    row.after.intervalSeconds,
                ),
            )
        }
        if (row.before.retryIntervalSeconds != row.after.retryIntervalSeconds) {
            Text(
                stringResource(
                    R.string.bulk_edit_change_seconds,
                    stringResource(R.string.bulk_edit_retry_interval),
                    row.before.retryIntervalSeconds,
                    row.after.retryIntervalSeconds,
                ),
            )
        }
        if (row.before.maxRetries != row.after.maxRetries) {
            Text(
                stringResource(
                    R.string.bulk_edit_change_number,
                    stringResource(R.string.monitor_retries_label),
                    row.before.maxRetries,
                    row.after.maxRetries,
                ),
            )
        }
    }
}
