package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.ui.StatusPill
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorDetailScreen(vm: UrsaViewModel, monitor: Monitor, modifier: Modifier = Modifier) {
    val beats by vm.beats.collectAsStateWithLifecycle()
    val certs by vm.certs.collectAsStateWithLifecycle()
    val cert = certs[monitor.id]
    val slowAlertEnabled by vm.slowAlertEnabled.collectAsStateWithLifecycle()
    var overrideText by remember(monitor.id) { mutableStateOf("") }
    LaunchedEffect(monitor.id) { overrideText = vm.monitorThresholdMs(monitor.id)?.toString() ?: "" }

    BackHandler { vm.back() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(monitor.name) },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Text("‹", style = MaterialTheme.typography.headlineSmall) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill(monitor.status)
                monitor.ping?.let { Text("${it}ms", style = MaterialTheme.typography.bodyMedium) }
                monitor.uptime24h?.let { Text("${(it * 100).toInt()}% 24h", style = MaterialTheme.typography.bodyMedium) }
            }

            monitor.url?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(stringResource(R.string.detail_type, monitor.type), style = MaterialTheme.typography.bodySmall)

            Text(stringResource(R.string.detail_recent_heartbeats), style = MaterialTheme.typography.titleSmall)
            HeartbeatBar(beats)

            if (cert != null) {
                Text(stringResource(R.string.detail_tls_certificate), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(if (cert.valid) R.string.cert_valid else R.string.cert_invalid),
                    color = if (cert.valid) StatusUi.color(dev.astoris.ursa.data.model.MonitorStatus.UP) else MaterialTheme.colorScheme.error,
                )
                cert.issuer?.let { Text(stringResource(R.string.detail_issuer, it), style = MaterialTheme.typography.bodySmall) }
                cert.subject?.let { Text(stringResource(R.string.detail_subject, it), style = MaterialTheme.typography.bodySmall) }
            }

            if (monitor.active) Button(onClick = { vm.pause(monitor.id) }) { Text(stringResource(R.string.action_pause)) }
            else Button(onClick = { vm.resume(monitor.id) }) { Text(stringResource(R.string.action_resume)) }

            if (slowAlertEnabled) {
                OutlinedTextField(
                    value = overrideText,
                    onValueChange = { input ->
                        overrideText = input.filter { it.isDigit() }.take(6)
                        vm.setMonitorThresholdMs(monitor.id, overrideText.toIntOrNull()?.takeIf { it > 0 })
                    },
                    label = { Text(stringResource(R.string.detail_slow_override)) },
                    supportingText = { Text(stringResource(R.string.detail_slow_override_desc)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HeartbeatBar(beats: List<Heartbeat>) {
    if (beats.isEmpty()) {
        Text(stringResource(R.string.detail_no_history), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        beats.takeLast(40).forEach { beat ->
            Box(
                Modifier
                    .width(6.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(StatusUi.color(beat.status)),
            )
        }
    }
}
