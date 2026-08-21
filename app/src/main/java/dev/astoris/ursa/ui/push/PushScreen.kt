package dev.astoris.ursa.ui.push

import android.Manifest
import android.content.ClipData
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.UrsaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val distributors by vm.distributors.collectAsStateWithLifecycle()
    val distributor by vm.pushDistributor.collectAsStateWithLifecycle()
    val endpoint by vm.pushEndpoint.collectAsStateWithLifecycle()

    // Notification permission (API 33+). Below 33 it is granted at install time.
    fun notifGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(notifGranted()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_notifications)) },
                actions = { TextButton(onClick = { vm.refreshDistributors() }) { Text(stringResource(R.string.push_refresh)) } },
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
                stringResource(R.string.push_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!granted) {
                Section(stringResource(R.string.push_section_allow)) {
                    Text(
                        stringResource(R.string.push_allow_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.push_allow_button))
                    }
                }
            }

            Section(stringResource(R.string.push_section_distributor)) {
                if (distributors.isEmpty()) {
                    Text(
                        stringResource(R.string.push_no_distributor),
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
                                Text(stringResource(R.string.push_selected), color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = { vm.registerPush(d) }) { Text(stringResource(R.string.push_use)) }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            val ep = endpoint
            if (ep != null) {
                Section(stringResource(R.string.push_section_endpoint)) {
                    Text(
                        stringResource(R.string.push_endpoint_desc),
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
                        stringResource(R.string.push_ntfy_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("UnifiedPush endpoint", ep)))
                                }
                            },
                        ) { Text(stringResource(R.string.push_copy)) }
                        OutlinedButton(onClick = { vm.unregisterPush() }) { Text(stringResource(R.string.push_disconnect)) }
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
