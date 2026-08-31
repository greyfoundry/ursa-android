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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.push.PushAlertMode
import dev.astoris.ursa.core.push.PushAlertTiming
import dev.astoris.ursa.core.push.PushSeverity
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.KumaPushSetupError
import dev.astoris.ursa.ui.KumaPushSetupUiState
import dev.astoris.ursa.core.push.PushLocalTestResult
import dev.astoris.ursa.core.push.PushRegistrationError
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val distributors by vm.distributors.collectAsStateWithLifecycle()
    val distributor by vm.pushDistributor.collectAsStateWithLifecycle()
    val endpoint by vm.pushEndpoint.collectAsStateWithLifecycle()
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val kumaSetup by vm.kumaPushSetup.collectAsStateWithLifecycle()
    val diagnostics by vm.pushDiagnostics.collectAsStateWithLifecycle()
    val kumaTestSending by vm.kumaPushTestSending.collectAsStateWithLifecycle()
    val alertModes by vm.pushAlertModes.collectAsStateWithLifecycle()
    val severities by vm.pushSeverities.collectAsStateWithLifecycle()
    val alertTimings by vm.pushAlertTimings.collectAsStateWithLifecycle()
    var selectedMonitorIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var defaultForNew by remember { mutableStateOf(true) }
    var confirmRemove by remember { mutableStateOf(false) }
    var modeMonitorId by remember { mutableStateOf<Int?>(null) }
    var severityMonitorId by remember { mutableStateOf<Int?>(null) }
    var timingMonitorId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(endpoint) {
        if (endpoint != null) vm.refreshKumaPushSetup()
    }
    LaunchedEffect(kumaSetup) {
        (kumaSetup as? KumaPushSetupUiState.Ready)?.let { ready ->
            selectedMonitorIds = ready.selectedMonitorIds
            defaultForNew = ready.isDefault
        }
    }

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

            Section(stringResource(R.string.push_diagnostics_section)) {
                Text(
                    stringResource(R.string.push_diagnostics_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = vm::testLocalPushNotification) {
                    Text(stringResource(R.string.push_test_local_button))
                }
                diagnostics.lastLocalTestResult?.let { result ->
                    Text(
                        stringResource(result.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result == PushLocalTestResult.POSTED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DiagnosticRow(
                            stringResource(R.string.push_diagnostics_last_registration),
                            diagnostics.lastRegistrationAtMs.diagnosticTimeOrNever(),
                        )
                        DiagnosticRow(
                            stringResource(R.string.push_diagnostics_last_message),
                            diagnostics.lastMessageAtMs.diagnosticTimeOrNever(),
                        )
                        val lastError = diagnostics.lastError
                        val lastErrorAt = diagnostics.lastErrorAtMs
                        val errorText = if (lastError != null && lastErrorAt != null) {
                            stringResource(
                                R.string.push_diagnostics_error_value,
                                stringResource(lastError.messageRes),
                                lastErrorAt.diagnosticTimeOrNever(),
                            )
                        } else {
                            stringResource(R.string.push_diagnostics_never)
                        }
                        DiagnosticRow(
                            stringResource(R.string.push_diagnostics_last_error),
                            errorText,
                        )
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

                Section(stringResource(R.string.push_kuma_section)) {
                    Text(
                        stringResource(R.string.push_kuma_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val setup = kumaSetup) {
                        KumaPushSetupUiState.Idle -> Button(onClick = vm::refreshKumaPushSetup) {
                            Text(stringResource(R.string.push_kuma_check))
                        }
                        KumaPushSetupUiState.Loading -> Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(stringResource(R.string.push_kuma_loading))
                        }
                        is KumaPushSetupUiState.Error -> {
                            Text(
                                stringResource(setup.reason.messageRes),
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = vm::refreshKumaPushSetup) {
                                Text(stringResource(R.string.push_kuma_retry))
                            }
                        }
                        is KumaPushSetupUiState.Ready -> {
                            Card(Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(
                                        when {
                                            setup.notificationId == null -> R.string.push_kuma_not_configured
                                            !setup.configurationCurrent -> R.string.push_kuma_update_needed
                                            else -> R.string.push_kuma_configured
                                        },
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (setup.notificationId != null && setup.configurationCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                            if (setup.recentlySaved) {
                                Text(
                                    stringResource(R.string.push_kuma_saved),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = defaultForNew,
                                        role = Role.Checkbox,
                                        onValueChange = { defaultForNew = it },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = defaultForNew,
                                    onCheckedChange = null,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.push_kuma_default))
                                    Text(
                                        stringResource(R.string.push_kuma_default_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    stringResource(R.string.push_kuma_existing),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row {
                                    TextButton(onClick = { selectedMonitorIds = monitors.mapTo(mutableSetOf()) { it.id } }) {
                                        Text(stringResource(R.string.push_kuma_all))
                                    }
                                    TextButton(onClick = { selectedMonitorIds = emptySet() }) {
                                        Text(stringResource(R.string.push_kuma_none))
                                    }
                                }
                            }
                            monitors.forEach { monitor ->
                                val selected = monitor.id in selectedMonitorIds
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = selected,
                                            role = Role.Checkbox,
                                            onValueChange = { checked ->
                                                selectedMonitorIds = if (checked) {
                                                    selectedMonitorIds + monitor.id
                                                } else {
                                                    selectedMonitorIds - monitor.id
                                                }
                                            },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = null,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(monitor.name)
                                        Text(
                                            stringResource(
                                                if (monitor.active) R.string.filter_active else R.string.filter_paused,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (setup.unavailableMonitorIds.isNotEmpty()) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.push_kuma_partial,
                                        setup.unavailableMonitorIds.size,
                                        setup.unavailableMonitorIds.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { vm.saveKumaPushSetup(selectedMonitorIds, defaultForNew) },
                                ) {
                                    Text(
                                        stringResource(
                                            if (setup.notificationId == null) R.string.push_kuma_create
                                            else R.string.push_kuma_update,
                                        ),
                                    )
                                }
                                if (setup.notificationId != null) {
                                    OutlinedButton(onClick = { confirmRemove = true }) {
                                        Text(stringResource(R.string.push_kuma_remove))
                                    }
                                }
                            }
                            HorizontalDivider()
                            Text(
                                stringResource(R.string.push_alert_modes_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.push_alert_modes_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!setup.configurationCurrent) {
                                Text(
                                    stringResource(R.string.push_alert_modes_update_needed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                monitors.forEach { monitor ->
                                    val mode = alertModes[monitor.id] ?: PushAlertMode.ALL_TRANSITIONS
                                    val severity = severities[monitor.id] ?: PushSeverity.CRITICAL
                                    val timing = alertTimings[monitor.id] ?: PushAlertTiming()
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            monitor.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            TextButton(onClick = { modeMonitorId = monitor.id }) {
                                                Text(stringResource(mode.labelRes))
                                            }
                                            TextButton(onClick = { severityMonitorId = monitor.id }) {
                                                Text(stringResource(severity.labelRes))
                                            }
                                            TextButton(onClick = { timingMonitorId = monitor.id }) {
                                                Text(timing.summary())
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                            Text(
                                stringResource(R.string.push_test_kuma_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.push_test_kuma_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = vm::testKumaPushDelivery,
                                enabled = setup.notificationId != null &&
                                    setup.configurationCurrent &&
                                    !kumaTestSending,
                            ) {
                                Text(stringResource(R.string.push_test_kuma_button))
                            }
                            when {
                                kumaTestSending -> Text(stringResource(R.string.push_test_kuma_sending))
                                diagnostics.deliveryTestRequestedAtMs == null -> Unit
                                diagnostics.deliveryTestRejectedAtMs.isAtOrAfter(
                                    diagnostics.deliveryTestRequestedAtMs,
                                ) -> Text(
                                    stringResource(R.string.push_test_kuma_rejected),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                diagnostics.deliveryTestReceivedAtMs.isAtOrAfter(
                                    diagnostics.deliveryTestRequestedAtMs,
                                ) -> Text(
                                    stringResource(
                                        R.string.push_test_kuma_received,
                                        diagnostics.deliveryTestReceivedAtMs.diagnosticTimeOrNever(),
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                else -> Text(
                                    stringResource(R.string.push_test_kuma_waiting),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.push_kuma_remove_title)) },
            text = { Text(stringResource(R.string.push_kuma_remove_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        vm.deleteKumaPushSetup()
                    },
                ) { Text(stringResource(R.string.push_kuma_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    val modeMonitor = modeMonitorId?.let { id -> monitors.firstOrNull { it.id == id } }
    if (modeMonitor != null) {
        val selectedMode = alertModes[modeMonitor.id] ?: PushAlertMode.ALL_TRANSITIONS
        AlertDialog(
            onDismissRequest = { modeMonitorId = null },
            title = {
                Text(stringResource(R.string.push_alert_mode_dialog_title, modeMonitor.name))
            },
            text = {
                Column {
                    PushAlertMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == selectedMode,
                                    role = Role.RadioButton,
                                    onClick = {
                                        vm.setPushAlertMode(modeMonitor.id, mode)
                                        modeMonitorId = null
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == selectedMode, onClick = null)
                            Column(
                                Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f),
                            ) {
                                Text(stringResource(mode.labelRes))
                                Text(
                                    stringResource(mode.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { modeMonitorId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    val severityMonitor = severityMonitorId?.let { id -> monitors.firstOrNull { it.id == id } }
    if (severityMonitor != null) {
        val selectedSeverity = severities[severityMonitor.id] ?: PushSeverity.CRITICAL
        AlertDialog(
            onDismissRequest = { severityMonitorId = null },
            title = {
                Text(stringResource(R.string.push_severity_dialog_title, severityMonitor.name))
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.push_severity_dialog_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PushSeverity.entries.forEach { severity ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = severity == selectedSeverity,
                                    role = Role.RadioButton,
                                    onClick = {
                                        vm.setPushSeverity(severityMonitor.id, severity)
                                        severityMonitorId = null
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = severity == selectedSeverity, onClick = null)
                            Column(
                                Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f),
                            ) {
                                Text(stringResource(severity.labelRes))
                                Text(
                                    stringResource(severity.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { severityMonitorId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    val timingMonitor = timingMonitorId?.let { id -> monitors.firstOrNull { it.id == id } }
    if (timingMonitor != null) {
        val selectedTiming = alertTimings[timingMonitor.id] ?: PushAlertTiming()
        AlertDialog(
            onDismissRequest = { timingMonitorId = null },
            title = { Text(stringResource(R.string.push_timing_dialog_title, timingMonitor.name)) },
            text = {
                Column {
                    TimingChoices(
                        title = stringResource(R.string.push_timing_delay),
                        choices = PushAlertTiming.FIRST_DELAY_CHOICES,
                        selected = selectedTiming.firstDelayMinutes,
                        label = { minutes -> minutes.minuteChoice(R.string.push_timing_immediate) },
                        onSelect = { vm.setPushAlertTiming(timingMonitor.id, selectedTiming.copy(firstDelayMinutes = it)) },
                    )
                    TimingChoices(
                        title = stringResource(R.string.push_timing_repeat),
                        choices = PushAlertTiming.REPEAT_CHOICES,
                        selected = selectedTiming.repeatMinutes,
                        label = { minutes -> minutes.minuteChoice(R.string.push_timing_off) },
                        onSelect = {
                            vm.setPushAlertTiming(
                                timingMonitor.id,
                                selectedTiming.copy(
                                    repeatMinutes = it,
                                    maxRepeats = if (it > 0 && selectedTiming.maxRepeats == 0) 1
                                    else selectedTiming.maxRepeats,
                                ),
                            )
                        },
                    )
                    if (selectedTiming.repeatMinutes > 0) {
                        TimingChoices(
                            title = stringResource(R.string.push_timing_repeat_count),
                            choices = PushAlertTiming.REPEAT_COUNT_CHOICES.filter { it > 0 },
                            selected = selectedTiming.maxRepeats.coerceAtLeast(1),
                            label = { it.toString() },
                            onSelect = { vm.setPushAlertTiming(timingMonitor.id, selectedTiming.copy(maxRepeats = it)) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { timingMonitorId = null }) {
                    Text(stringResource(R.string.action_done))
                }
            },
        )
    }
}

@Composable
private fun TimingChoices(
    title: String,
    choices: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.forEach { value ->
            TextButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f)) {
                Text(
                    label(value),
                    color = if (value == selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Int.minuteChoice(zeroLabel: Int): String =
    if (this == 0) stringResource(zeroLabel) else stringResource(R.string.push_timing_minutes, this)

@Composable
private fun PushAlertTiming.summary(): String = when {
    firstDelayMinutes == 0 && repeatMinutes == 0 -> stringResource(R.string.push_timing_default)
    repeatMinutes == 0 -> stringResource(R.string.push_timing_delay_summary, firstDelayMinutes)
    else -> pluralStringResource(
        R.plurals.push_timing_repeat_summary,
        maxRepeats,
        repeatMinutes,
        maxRepeats,
    )
}

private val PushAlertMode.labelRes: Int
    get() = when (this) {
        PushAlertMode.MUTED -> R.string.push_alert_mode_muted
        PushAlertMode.DOWN_ONLY -> R.string.push_alert_mode_down_only
        PushAlertMode.DOWN_AND_RECOVERY -> R.string.push_alert_mode_down_recovery
        PushAlertMode.ALL_TRANSITIONS -> R.string.push_alert_mode_all
    }

private val PushAlertMode.descriptionRes: Int
    get() = when (this) {
        PushAlertMode.MUTED -> R.string.push_alert_mode_muted_desc
        PushAlertMode.DOWN_ONLY -> R.string.push_alert_mode_down_only_desc
        PushAlertMode.DOWN_AND_RECOVERY -> R.string.push_alert_mode_down_recovery_desc
        PushAlertMode.ALL_TRANSITIONS -> R.string.push_alert_mode_all_desc
    }

private val PushSeverity.labelRes: Int
    get() = when (this) {
        PushSeverity.CRITICAL -> R.string.push_severity_critical
        PushSeverity.STANDARD -> R.string.push_severity_standard
        PushSeverity.SILENT -> R.string.push_severity_silent
    }

private val PushSeverity.descriptionRes: Int
    get() = when (this) {
        PushSeverity.CRITICAL -> R.string.push_severity_critical_desc
        PushSeverity.STANDARD -> R.string.push_severity_standard_desc
        PushSeverity.SILENT -> R.string.push_severity_silent_desc
    }

private val KumaPushSetupError.messageRes: Int
    get() = when (this) {
        KumaPushSetupError.INVALID_ENDPOINT -> R.string.push_kuma_invalid_endpoint
        KumaPushSetupError.SERVER_UNAVAILABLE -> R.string.push_kuma_server_unavailable
        KumaPushSetupError.SAVE_FAILED -> R.string.push_kuma_save_failed
        KumaPushSetupError.DELETE_FAILED -> R.string.push_kuma_delete_failed
    }

private val PushLocalTestResult.messageRes: Int
    get() = when (this) {
        PushLocalTestResult.POSTED -> R.string.push_test_local_posted
        PushLocalTestResult.PERMISSION_REQUIRED -> R.string.push_test_local_permission
        PushLocalTestResult.APP_NOTIFICATIONS_DISABLED -> R.string.push_test_local_app_disabled
        PushLocalTestResult.CHANNEL_DISABLED -> R.string.push_test_local_channel_disabled
    }

private val PushRegistrationError.messageRes: Int
    get() = when (this) {
        PushRegistrationError.INTERNAL_ERROR -> R.string.push_diagnostics_error_internal
        PushRegistrationError.NETWORK -> R.string.push_diagnostics_error_network
        PushRegistrationError.ACTION_REQUIRED -> R.string.push_diagnostics_error_action
        PushRegistrationError.VAPID_REQUIRED -> R.string.push_diagnostics_error_vapid
    }

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Long?.diagnosticTimeOrNever(): String = this?.let { timestamp ->
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
} ?: stringResource(R.string.push_diagnostics_never)

private fun Long?.isAtOrAfter(reference: Long?): Boolean =
    this != null && reference != null && this >= reference

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
