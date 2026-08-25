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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusPill
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.theme.KumaGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorDetailScreen(vm: UrsaViewModel, monitor: Monitor, modifier: Modifier = Modifier) {
    val beats by vm.beats.collectAsStateWithLifecycle()
    val certs by vm.certs.collectAsStateWithLifecycle()
    val cert = certs[monitor.id]
    val beatRange by vm.beatRange.collectAsStateWithLifecycle()
    val slowAlertEnabled by vm.slowAlertEnabled.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var overrideText by remember(monitor.id) { mutableStateOf("") }
    var actionInFlight by remember(monitor.id) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pausedMessage = stringResource(R.string.monitor_paused)
    val pauseFailedMessage = stringResource(R.string.monitor_pause_failed)
    val resumedMessage = stringResource(R.string.monitor_resumed)
    val resumeFailedMessage = stringResource(R.string.monitor_resume_failed)
    LaunchedEffect(monitor.id) { overrideText = vm.monitorThresholdMs(monitor.id)?.toString() ?: "" }

    BackHandler { vm.back() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(monitor.name) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
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

            FilterChip(
                selected = monitor.id in favorites,
                onClick = { vm.toggleFavorite(monitor.id) },
                label = {
                    Text(
                        stringResource(
                            if (monitor.id in favorites) R.string.action_remove_favorite
                            else R.string.action_add_favorite,
                        ),
                    )
                },
            )

            monitor.url?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(stringResource(R.string.detail_type, monitor.type), style = MaterialTheme.typography.bodySmall)

            Text(stringResource(R.string.detail_response_time), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeartbeatRange.entries.forEach { range ->
                    FilterChip(
                        selected = beatRange == range,
                        onClick = { vm.setBeatRange(range) },
                        label = { Text(range.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                            selectedLabelColor = KumaGreen,
                        ),
                    )
                }
            }
            ResponseTimeChart(beats)
            Text(stringResource(R.string.detail_recent_heartbeats), style = MaterialTheme.typography.titleSmall)
            HeartbeatBar(beats)
            IncidentTimeline(beats)

            if (cert != null) {
                Text(stringResource(R.string.detail_tls_certificate), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(if (cert.valid) R.string.cert_valid else R.string.cert_invalid),
                    color = if (cert.valid) StatusUi.color(dev.astoris.ursa.data.model.MonitorStatus.UP) else MaterialTheme.colorScheme.error,
                )
                cert.issuer?.let { Text(stringResource(R.string.detail_issuer, it), style = MaterialTheme.typography.bodySmall) }
                cert.subject?.let { Text(stringResource(R.string.detail_subject, it), style = MaterialTheme.typography.bodySmall) }
            }

            if (monitor.active) {
                Button(
                    enabled = !actionInFlight,
                    onClick = {
                        actionInFlight = true
                        vm.pause(monitor.id) { succeeded ->
                            actionInFlight = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (succeeded) pausedMessage else pauseFailedMessage,
                                )
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (actionInFlight) R.string.action_pausing else R.string.action_pause))
                }
            } else {
                Button(
                    enabled = !actionInFlight,
                    onClick = {
                        actionInFlight = true
                        vm.resume(monitor.id) { succeeded ->
                            actionInFlight = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (succeeded) resumedMessage else resumeFailedMessage,
                                )
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (actionInFlight) R.string.action_resuming else R.string.action_resume))
                }
            }

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
    val recent = beats.takeLast(40)
    val upCount = recent.count { it.status == MonitorStatus.UP }
    val downCount = recent.count { it.status == MonitorStatus.DOWN }
    val otherCount = recent.size - upCount - downCount
    val summary = stringResource(
        R.string.heartbeat_summary,
        pluralStringResource(R.plurals.heartbeat_up_count, upCount, upCount),
        pluralStringResource(R.plurals.heartbeat_down_count, downCount, downCount),
        pluralStringResource(R.plurals.heartbeat_other_count, otherCount, otherCount),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics { contentDescription = summary },
    ) {
        recent.forEach { beat ->
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

private data class IncidentEvent(val status: MonitorStatus, val time: String, val message: String?)

@Composable
private fun IncidentTimeline(beats: List<Heartbeat>) {
    val events = beats.zipWithNext().mapNotNull { (previous, current) ->
        when {
            previous.status != MonitorStatus.DOWN && current.status == MonitorStatus.DOWN ->
                IncidentEvent(current.status, current.time, current.msg)
            previous.status == MonitorStatus.DOWN && current.status == MonitorStatus.UP ->
                IncidentEvent(current.status, current.time, current.msg)
            previous.status != current.status -> IncidentEvent(current.status, current.time, current.msg)
            else -> null
        }
    }.takeLast(8).asReversed()

    if (events.isEmpty()) return
    Text(stringResource(R.string.detail_recent_incidents), style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        events.forEach { event ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(event.status)
                Column {
                    val label = when (event.status) {
                        MonitorStatus.DOWN -> R.string.incident_down
                        MonitorStatus.UP -> R.string.incident_recovered
                        else -> R.string.incident_status_changed
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        event.message?.takeIf { it.isNotBlank() } ?: event.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
