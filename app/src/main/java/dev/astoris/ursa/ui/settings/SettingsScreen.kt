package dev.astoris.ursa.ui.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.WearPairingError
import dev.astoris.ursa.ui.WearPairingUiState
import dev.astoris.ursa.ui.UpdateCheckUiState
import dev.astoris.ursa.core.network.ConnectionFailureReason
import dev.astoris.ursa.ui.lock.BiometricGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val canLock = remember { BiometricGate.canAuthenticate(context) }
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val slowAlertEnabled by vm.slowAlertEnabled.collectAsStateWithLifecycle()
    val slowThresholdMs by vm.slowThresholdMs.collectAsStateWithLifecycle()
    val dynamicColorEnabled by vm.dynamicColorEnabled.collectAsStateWithLifecycle()
    val compactDisplayEnabled by vm.compactDisplayEnabled.collectAsStateWithLifecycle()
    val connectionFailure by vm.connectionFailure.collectAsStateWithLifecycle()
    val updateCheck by vm.updateCheck.collectAsStateWithLifecycle()
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val wearPairing by vm.wearPairing.collectAsStateWithLifecycle()
    val activeConnection = connections.firstOrNull { it.url == activeUrl }
    val canDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var showKioskWarning by remember { mutableStateOf(false) }
    var showWearConfirmation by remember { mutableStateOf(false) }
    var showConnectionHelp by remember { mutableStateOf(false) }
    var showUpdates by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            OutlinedButton(onClick = { vm.enterConnectionManager() }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.servers_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        activeConnection?.displayName ?: stringResource(R.string.settings_servers_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { vm.enterStatusPage() }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.statuspage_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.settings_status_pages_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { showKioskWarning = true }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_kiosk_title), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.settings_kiosk_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (vm.wearBridgeAvailable) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        vm.clearWearPairingResult()
                        showWearConfirmation = true
                    },
                    enabled = wearPairing != WearPairingUiState.Sending &&
                        activeConnection?.jwt?.isNotBlank() == true && activeConnection.insecure.not(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_wear_pair), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(
                                when {
                                    activeConnection == null || activeConnection.jwt.isNullOrBlank() ->
                                        R.string.settings_wear_no_session
                                    activeConnection.insecure -> R.string.settings_wear_self_signed
                                    else -> R.string.settings_wear_desc
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        WearPairingResult(wearPairing)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(stringResource(R.string.settings_require_unlock), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(
                            if (canLock) R.string.settings_require_unlock_desc
                            else R.string.settings_require_unlock_unavailable,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = lockEnabled && canLock,
                    enabled = canLock,
                    onCheckedChange = { vm.setLockEnabled(it) },
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(stringResource(R.string.settings_slow_alert), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_slow_alert_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = slowAlertEnabled,
                    onCheckedChange = { vm.setSlowAlertEnabled(it) },
                )
            }
            if (slowAlertEnabled) {
                Spacer(Modifier.height(8.dp))
                var thresholdText by remember(slowThresholdMs) { mutableStateOf(slowThresholdMs.toString()) }
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { input ->
                        thresholdText = input.filter { it.isDigit() }.take(6)
                        thresholdText.toIntOrNull()?.takeIf { it > 0 }?.let { vm.setSlowThresholdMs(it) }
                    },
                    label = { Text(stringResource(R.string.settings_slow_threshold)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(
                            if (canDynamicColor) R.string.settings_dynamic_color_desc
                            else R.string.settings_dynamic_color_unavailable,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = dynamicColorEnabled && canDynamicColor,
                    enabled = canDynamicColor,
                    onCheckedChange = { vm.setDynamicColorEnabled(it) },
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(stringResource(R.string.settings_compact), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_compact_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = compactDisplayEnabled,
                    onCheckedChange = vm::setCompactDisplayEnabled,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            OutlinedButton(onClick = { showConnectionHelp = true }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_help), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.settings_help_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.clearUpdateCheck(); showUpdates = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.settings_updates_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }

    if (showKioskWarning) {
        AlertDialog(
            onDismissRequest = { showKioskWarning = false },
            title = { Text(stringResource(R.string.kiosk_warning_title)) },
            text = { Text(stringResource(R.string.kiosk_warning_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showKioskWarning = false
                        vm.enterKioskMode()
                    },
                ) { Text(stringResource(R.string.kiosk_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showKioskWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showWearConfirmation) {
        AlertDialog(
            onDismissRequest = { showWearConfirmation = false },
            title = { Text(stringResource(R.string.settings_wear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_wear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWearConfirmation = false
                        vm.sendActiveSessionToWear()
                    },
                ) { Text(stringResource(R.string.settings_wear_send)) }
            },
            dismissButton = {
                TextButton(onClick = { showWearConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showConnectionHelp) {
        AlertDialog(
            onDismissRequest = { showConnectionHelp = false },
            title = { Text(stringResource(R.string.connection_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.connection_help_current, stringResource(connectionFailure.helpRes())))
                    Text(stringResource(R.string.connection_help_direct))
                    Text(stringResource(R.string.connection_help_access))
                    Text(stringResource(R.string.connection_help_auth))
                    Text(stringResource(R.string.connection_help_tls))
                    Text(stringResource(R.string.connection_help_push))
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectionHelp = false }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }

    if (showUpdates) {
        AlertDialog(
            onDismissRequest = { showUpdates = false },
            title = { Text(stringResource(R.string.update_dialog_title)) },
            text = {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.update_privacy))
                    Text(stringResource(R.string.update_current_notes))
                    when (val state = updateCheck) {
                        UpdateCheckUiState.Idle -> Unit
                        UpdateCheckUiState.Loading -> CircularProgressIndicator()
                        UpdateCheckUiState.Current -> Text(stringResource(R.string.update_current))
                        UpdateCheckUiState.Error -> Text(stringResource(R.string.update_check_failed))
                        is UpdateCheckUiState.Available -> {
                            Text(stringResource(R.string.update_available_title, state.release.version.toString()))
                            if (state.release.notes.isNotBlank()) Text(state.release.notes)
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, state.release.webUrl.toUri())
                                            .addCategory(Intent.CATEGORY_BROWSABLE),
                                    )
                                },
                            ) { Text(stringResource(R.string.update_open_release)) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = vm::checkForUpdates,
                    enabled = updateCheck != UpdateCheckUiState.Loading,
                ) {
                    Text(
                        stringResource(
                            if (updateCheck == UpdateCheckUiState.Loading) R.string.update_checking
                            else R.string.update_check,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdates = false }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }
}

private fun ConnectionFailureReason?.helpRes() = when (this) {
    ConnectionFailureReason.DEVICE_OFFLINE -> R.string.connection_reason_device
    ConnectionFailureReason.SERVER_UNREACHABLE -> R.string.connection_reason_server
    ConnectionFailureReason.AUTHENTICATION -> R.string.connection_reason_auth
    ConnectionFailureReason.CERTIFICATE -> R.string.connection_reason_certificate
    ConnectionFailureReason.INCOMPATIBLE_RESPONSE -> R.string.connection_reason_incompatible
    ConnectionFailureReason.UNKNOWN, null -> R.string.connection_reason_unknown
}

@Composable
private fun WearPairingResult(state: WearPairingUiState) {
    val message = when (state) {
        WearPairingUiState.Idle -> return
        WearPairingUiState.Sending -> stringResource(R.string.settings_wear_sending)
        is WearPairingUiState.Success -> pluralStringResource(
            R.plurals.settings_wear_sent,
            state.watchCount,
            state.watchCount,
        )
        is WearPairingUiState.Error -> stringResource(
            when (state.reason) {
                WearPairingError.NO_ACTIVE_SESSION -> R.string.settings_wear_no_session
                WearPairingError.SELF_SIGNED_UNSUPPORTED -> R.string.settings_wear_self_signed
                WearPairingError.NO_REACHABLE_WATCH -> R.string.settings_wear_no_watch
                WearPairingError.TRANSFER_FAILED -> R.string.settings_wear_failed
            },
        )
    }
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
