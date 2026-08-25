package dev.astoris.ursa.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.UrsaViewModel
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
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val activeConnection = connections.firstOrNull { it.url == activeUrl }
    val canDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
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
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }
}
