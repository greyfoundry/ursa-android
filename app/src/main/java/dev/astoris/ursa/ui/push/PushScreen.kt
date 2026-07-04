package dev.astoris.ursa.ui.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.ui.UrsaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    BackHandler { vm.exitPush() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val distributors by vm.distributors.collectAsStateWithLifecycle()
    val distributor by vm.pushDistributor.collectAsStateWithLifecycle()
    val endpoint by vm.pushEndpoint.collectAsStateWithLifecycle()

    // Notification permission (API 33+). Below 33 it is granted at install time.
    fun notifGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(notifGranted()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Push notifications") },
                navigationIcon = { TextButton(onClick = { vm.exitPush() }) { Text("Back") } },
                actions = { TextButton(onClick = { vm.refreshDistributors() }) { Text("Refresh") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Get a notification the moment a monitor goes down — over UnifiedPush, " +
                    "with no relay server and no Google services.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!granted) {
                Section("1. Allow notifications") {
                    Text(
                        "Android needs permission to show monitor alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow notifications")
                    }
                }
            }

            Section("Distributor") {
                if (distributors.isEmpty()) {
                    Text(
                        "No UnifiedPush distributor found. Install one (ntfy is recommended: " +
                            "free, open-source, self-hostable), then tap Refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    distributors.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(d, style = MaterialTheme.typography.bodyMedium)
                            if (d == distributor) {
                                Text("Selected", color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = { vm.registerPush(d) }) { Text("Use") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            val ep = endpoint
            if (ep != null) {
                Section("Your endpoint") {
                    Text(
                        "Paste this URL into a Webhook notification in Uptime Kuma " +
                            "(Settings -> Notifications -> Webhook), then attach it to your monitors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            ep,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Text(
                        "ntfy users: append ?up=1 to the URL so ntfy forwards Kuma's raw body.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { clipboard.setText(AnnotatedString(ep)) }) { Text("Copy") }
                        OutlinedButton(onClick = { vm.unregisterPush() }) { Text("Disconnect") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
