package dev.astoris.ursa.ui.monitors

import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.MonitorInventoryDocument
import dev.astoris.ursa.core.network.MonitorInventoryExport
import dev.astoris.ursa.core.network.MonitorInventoryExportFormat
import dev.astoris.ursa.ui.AppDeepLink
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.OperationalStateKind
import dev.astoris.ursa.ui.components.OperationalStatePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorInventoryExportScreen(
    vm: UrsaViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val activeConnection by vm.activeConnection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var pendingDocument by remember { mutableStateOf<MonitorInventoryDocument?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    fun writeDocument(uri: Uri?) {
        val document = pendingDocument
        pendingDocument = null
        if (uri == null || document == null) return
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(document.content)
                    } ?: error("No output stream")
                }.isSuccess
            }
            resultMessage = if (written) {
                if (document.truncated) {
                    resources.getString(
                        R.string.inventory_export_saved_truncated,
                        document.rowCount,
                        MonitorInventoryExport.MAX_ROWS,
                    )
                } else {
                    resources.getQuantityString(
                        R.plurals.inventory_export_saved,
                        document.rowCount,
                        document.rowCount,
                    )
                }
            } else {
                resources.getString(R.string.inventory_export_failed)
            }
        }
    }

    val csvDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MonitorInventoryExportFormat.CSV.mimeType),
        ::writeDocument,
    )
    val htmlDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MonitorInventoryExportFormat.HTML.mimeType),
        ::writeDocument,
    )
    BackHandler(onBack = onClose)

    fun startExport(format: MonitorInventoryExportFormat) {
        val connection = activeConnection ?: return
        val document = MonitorInventoryExport.create(
            format = format,
            monitors = monitors,
            serverName = connection.displayName,
            serverScope = AppDeepLink.serverScope(connection.url),
            capturedAtMillis = System.currentTimeMillis(),
        )
        pendingDocument = document
        resultMessage = null
        when (format) {
            MonitorInventoryExportFormat.CSV -> csvDocument.launch(document.fileName)
            MonitorInventoryExportFormat.HTML -> htmlDocument.launch(document.fileName)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_export_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
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
                    stringResource(R.string.inventory_export_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val connection = activeConnection
            if (connection == null) {
                item {
                    OperationalStatePanel(
                        kind = OperationalStateKind.OFFLINE,
                        title = stringResource(R.string.inventory_export_no_server_title),
                        message = stringResource(R.string.inventory_export_no_server_message),
                    )
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(connection.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                pluralStringResource(
                                    R.plurals.inventory_export_monitor_count,
                                    monitors.size,
                                    monitors.size,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.inventory_export_excludes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = { startExport(MonitorInventoryExportFormat.CSV) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.inventory_export_csv))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { startExport(MonitorInventoryExportFormat.HTML) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.inventory_export_html))
                    }
                }
                resultMessage?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
