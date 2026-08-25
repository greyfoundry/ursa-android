package dev.astoris.ursa.ui.connections

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionManagerScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val connectionState by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ServerConnection?>(null) }
    var removing by remember { mutableStateOf<ServerConnection?>(null) }

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
