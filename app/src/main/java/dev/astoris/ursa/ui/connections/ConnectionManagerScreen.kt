package dev.astoris.ursa.ui.connections

import android.content.res.Resources
import java.io.InputStream
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.storage.BackupError
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.ui.ConnectionBackupResult
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionManagerScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val connectionState by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var editing by remember { mutableStateOf<ServerConnection?>(null) }
    var removing by remember { mutableStateOf<ServerConnection?>(null) }
    var backupDialog by remember { mutableStateOf<BackupDialogMode?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    val createBackupFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingExport
        backupMessage = if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                    ?: error("No output stream")
            }.fold(
                onSuccess = { resources.getString(R.string.backup_exported) },
                onFailure = { resources.getString(R.string.backup_write_failed) },
            )
        } else null
        pendingExport = null
    }
    val openBackupFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val document = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readTextLimited(MAX_BACKUP_DOCUMENT_CHARS)
                }
                    ?: error("No input stream")
            }.getOrNull()
            if (document == null || document.length > MAX_BACKUP_DOCUMENT_CHARS) {
                backupMessage = resources.getString(R.string.backup_read_failed)
            } else {
                pendingImport = document
                backupDialog = BackupDialogMode.IMPORT
            }
        }
    }

    BackHandler { vm.exitConnectionManager() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                navigationIcon = {
                    IconButton(onClick = { vm.exitConnectionManager() }) {
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
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.servers_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(connections, key = { it.url }) { connection ->
                ConnectionCard(
                    connection = connection,
                    active = connection.url == activeUrl,
                    connectionState = connectionState,
                    onSelect = {
                        vm.switchTo(connection)
                        vm.exitConnectionManager()
                    },
                    onRename = { editing = connection },
                    onReauthenticate = { vm.reauthenticate(connection) },
                    onRemove = { removing = connection },
                )
            }
            item {
                Button(
                    onClick = { vm.startAddingConnection() },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.servers_add))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { backupDialog = BackupDialogMode.EXPORT },
                        enabled = !backupBusy && connections.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(
                        onClick = { openBackupFile.launch(arrayOf("application/json", "text/plain")) },
                        enabled = !backupBusy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.backup_import)) }
                }
            }
            backupMessage?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { connection ->
        RenameConnectionDialog(
            connection = connection,
            onDismiss = { editing = null },
            onSave = { alias ->
                vm.renameConnection(connection.url, alias)
                editing = null
            },
        )
    }

    removing?.let { connection ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.servers_remove_title)) },
            text = { Text(stringResource(R.string.servers_remove_message, connection.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.removeConnection(connection.url)
                        removing = null
                    },
                ) {
                    Text(stringResource(R.string.servers_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    backupDialog?.let { mode ->
        BackupPasswordDialog(
            mode = mode,
            busy = backupBusy,
            onDismiss = {
                backupDialog = null
                if (mode == BackupDialogMode.IMPORT) pendingImport = null
            },
            onConfirm = { password, includeSessions ->
                backupBusy = true
                backupMessage = null
                if (mode == BackupDialogMode.EXPORT) {
                    vm.createConnectionBackup(password, includeSessions) { result ->
                        backupBusy = false
                        backupDialog = null
                        when (result) {
                            is ConnectionBackupResult.Document -> {
                                pendingExport = result.content
                                createBackupFile.launch("ursa-connections.ursa.json")
                            }
                            is ConnectionBackupResult.Error -> {
                                backupMessage = backupErrorMessage(resources, result.reason)
                            }
                            is ConnectionBackupResult.Imported -> Unit
                        }
                    }
                } else {
                    val document = pendingImport.orEmpty()
                    vm.importConnectionBackup(document, password) { result ->
                        backupBusy = false
                        backupDialog = null
                        pendingImport = null
                        backupMessage = when (result) {
                            is ConnectionBackupResult.Imported -> resources.getQuantityString(
                                R.plurals.backup_imported_count,
                                result.count,
                                result.count,
                            )
                            is ConnectionBackupResult.Error -> backupErrorMessage(resources, result.reason)
                            is ConnectionBackupResult.Document -> null
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ConnectionCard(
    connection: ServerConnection,
    active: Boolean,
    connectionState: ConnectionState,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onReauthenticate: () -> Unit,
    onRemove: () -> Unit,
) {
    UrsaPressableCard(onClick = onSelect) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(connection.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        connection.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (connection.headers.isNotEmpty()) {
                        Text(
                            pluralStringResource(
                                R.plurals.access_header_count,
                                connection.headers.size,
                                connection.headers.size,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ConnectionBadge(active = active, state = connectionState)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRename) { Text(stringResource(R.string.servers_rename)) }
                TextButton(onClick = onReauthenticate) {
                    Text(stringResource(R.string.servers_reauthenticate))
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.servers_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ConnectionBadge(active: Boolean, state: ConnectionState) {
    val status = if (active) state else null
    val foreground = when (status) {
        ConnectionState.Authenticated -> MaterialTheme.colorScheme.primary
        ConnectionState.AuthenticationFailed, ConnectionState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = foreground.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = if (status == null) {
                stringResource(R.string.servers_saved)
            } else {
                stringResource(
                    R.string.servers_active_status,
                    stringResource(R.string.servers_active),
                    stringResource(status.labelRes),
                )
            },
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun RenameConnectionDialog(
    connection: ServerConnection,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var alias by remember(connection.url) { mutableStateOf(connection.alias.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.servers_rename_title)) },
        text = {
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it.take(80) },
                label = { Text(stringResource(R.string.login_server_name)) },
                supportingText = { Text(connection.url) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(alias.trim().takeIf { it.isNotEmpty() }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun BackupPasswordDialog(
    mode: BackupDialogMode,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, includeSessions: Boolean) -> Unit,
) {
    var password by remember(mode) { mutableStateOf("") }
    var confirmation by remember(mode) { mutableStateOf("") }
    var includeSessions by remember(mode) { mutableStateOf(false) }
    val valid = password.length >= 8 && (mode == BackupDialogMode.IMPORT || password == confirmation)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (mode == BackupDialogMode.EXPORT) R.string.backup_export_title
                    else R.string.backup_import_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (mode == BackupDialogMode.EXPORT) R.string.backup_export_desc
                        else R.string.backup_import_desc,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(256) },
                    label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (mode == BackupDialogMode.EXPORT) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.take(256) },
                        label = { Text(stringResource(R.string.backup_confirm_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = includeSessions,
                            role = Role.Checkbox,
                            onValueChange = { includeSessions = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includeSessions, onCheckedChange = null)
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(R.string.backup_include_sessions))
                            Text(
                                stringResource(R.string.backup_include_sessions_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (password.isNotEmpty() && password.length < 8) {
                    Text(
                        stringResource(R.string.backup_password_too_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (mode == BackupDialogMode.EXPORT && confirmation.isNotEmpty() && password != confirmation) {
                    Text(
                        stringResource(R.string.backup_password_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = { onConfirm(password, includeSessions) }) {
                Text(
                    stringResource(
                        if (busy) R.string.backup_working
                        else if (mode == BackupDialogMode.EXPORT) R.string.backup_export
                        else R.string.backup_import,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun backupErrorMessage(resources: Resources, error: BackupError): String = resources.getString(
    when (error) {
        BackupError.INVALID_DOCUMENT -> R.string.backup_invalid_document
        BackupError.WRONG_PASSWORD_OR_DAMAGED -> R.string.backup_wrong_password
        BackupError.INVALID_CONTENT -> R.string.backup_invalid_content
    },
)

private enum class BackupDialogMode { EXPORT, IMPORT }

private const val MAX_BACKUP_DOCUMENT_CHARS = 1_000_000

private fun InputStream.readTextLimited(maxChars: Int): String = bufferedReader().use { reader ->
    val output = StringBuilder()
    val buffer = CharArray(8_192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        if (output.length + count > maxChars) error("Backup is too large")
        output.append(buffer, 0, count)
    }
    output.toString()
}
