package dev.astoris.ursa.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.lock.BiometricGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    BackHandler { vm.exitSettings() }

    val context = LocalContext.current
    val canLock = remember { BiometricGate.canAuthenticate(context) }
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = { vm.exitSettings() }) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Require unlock", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (canLock) {
                            "Ask for your biometric or device PIN/pattern before showing monitors."
                        } else {
                            "Set up a screen lock or biometric on your device to use this."
                        },
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
        }
    }
}
