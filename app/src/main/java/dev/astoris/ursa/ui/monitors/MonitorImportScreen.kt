package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.components.accessibilityHeading
import dev.astoris.ursa.core.network.MonitorImport
import dev.astoris.ursa.core.network.MonitorImportRow
import dev.astoris.ursa.core.network.MonitorImportStatus
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.allows
import dev.astoris.ursa.ui.components.OperationalStateKind
import dev.astoris.ursa.ui.components.OperationalStatePanel
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorImportScreen(
    vm: UrsaViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val compatibility by vm.kumaCompatibility.collectAsStateWithLifecycle()
    val activeConnection by vm.activeConnection.collectAsStateWithLifecycle()
    val importState by vm.monitorImport.collectAsStateWithLifecycle()
    val canImport = activeConnection.allows(AccessCapability.MONITOR_CREATE, AccessCapability.BULK_WRITE)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var readFailed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            readFailed = false
            val document = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readTextLimited(MonitorImport.MAX_DOCUMENT_CHARS)
                    } ?: error("No input stream")
                }.getOrNull()
            }
            val preview = document?.takeIf { it.length <= MonitorImport.MAX_DOCUMENT_CHARS }?.let {
                MonitorImport.preview(it, monitors, compatibility)
            }
            vm.setMonitorImportPreview(preview)
            readFailed = document == null || document.length > MonitorImport.MAX_DOCUMENT_CHARS
            busy = false
        }
    }
    val close = {
        vm.clearMonitorImport()
        onClose()
    }
    BackHandler(onBack = close)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.monitor_import_title), Modifier.accessibilityHeading()) },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.monitor_import_description, MonitorImport.MAX_ROWS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    stringResource(R.string.monitor_import_columns),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(
                    onClick = {
                        picker.launch(
                            arrayOf(
                                "text/*",
                                "application/csv",
                                "application/vnd.ms-excel",
                                "application/octet-stream",
                            ),
                        )
                    },
                    enabled = canImport && !busy,
                ) { Text(stringResource(R.string.monitor_import_choose)) }
            }
            if (!canImport) {
                item {
                    OperationalStatePanel(
                        kind = OperationalStateKind.ERROR,
                        title = stringResource(R.string.access_denied_title),
                        message = stringResource(R.string.monitor_import_access_denied),
                    )
                }
            } else if (readFailed || importState.preview?.documentError != null) {
                item {
                    OperationalStatePanel(
                        kind = OperationalStateKind.ERROR,
                        title = stringResource(R.string.monitor_import_invalid_title),
                        message = stringResource(R.string.monitor_import_invalid_message),
                    )
                }
            }
            importState.preview?.takeIf { it.documentError == null }?.let { dryRun ->
                item {
                    Text(
                        stringResource(
                            R.string.monitor_import_summary,
                            dryRun.rows.size,
                            dryRun.readyRows.size,
                            dryRun.rows.count { it.status == MonitorImportStatus.REVIEW_REQUIRED },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(dryRun.rows, key = MonitorImportRow::lineNumber) { row ->
                    MonitorImportRowCard(
                        row = row,
                        imported = row.lineNumber in importState.result?.succeededLines.orEmpty(),
                        failed = row.lineNumber in importState.result?.failedLines.orEmpty(),
                        onReview = { row.draft?.let(vm::reviewImportedMonitor) },
                    )
                }
                val pendingRows = dryRun.readyRows.takeIf { importState.result == null }.orEmpty()
                if (pendingRows.isNotEmpty()) {
                    item {
                        Button(
                            onClick = {
                                busy = true
                                vm.importMonitors(pendingRows) {
                                    busy = false
                                }
                            },
                            enabled = canImport && !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                pluralStringResource(
                                    R.plurals.monitor_import_create,
                                    pendingRows.size,
                                    pendingRows.size,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorImportRowCard(
    row: MonitorImportRow,
    imported: Boolean,
    failed: Boolean,
    onReview: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                row.draft?.name?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.monitor_import_line, row.lineNumber),
                style = MaterialTheme.typography.titleMedium,
            )
            row.draft?.let {
                Text(
                    stringResource(
                        R.string.monitor_import_row_details,
                        it.type,
                        it.endpoint.ifBlank { stringResource(R.string.monitor_import_no_endpoint) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(
                    when {
                        imported -> R.string.monitor_import_status_imported
                        failed -> R.string.monitor_import_status_failed
                        row.status == MonitorImportStatus.READY -> R.string.monitor_import_status_ready
                        row.status == MonitorImportStatus.REVIEW_REQUIRED -> R.string.monitor_import_status_review
                        row.status == MonitorImportStatus.DUPLICATE -> R.string.monitor_import_status_duplicate
                        row.status == MonitorImportStatus.UNSUPPORTED -> R.string.monitor_import_status_unsupported
                        else -> R.string.monitor_import_status_invalid
                    },
                ),
            )
            if (row.status == MonitorImportStatus.REVIEW_REQUIRED && !imported) {
                Button(onClick = onReview) { Text(stringResource(R.string.monitor_import_review)) }
            }
        }
    }
}

private fun InputStream.readTextLimited(maxChars: Int): String = bufferedReader().use { reader ->
    val buffer = CharArray(8_192)
    val result = StringBuilder()
    while (result.length <= maxChars) {
        val read = reader.read(buffer)
        if (read < 0) break
        result.append(buffer, 0, read)
    }
    result.toString()
}
