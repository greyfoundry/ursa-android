package dev.astoris.ursa.ui.push

import android.Manifest
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import dev.astoris.ursa.R
import dev.astoris.ursa.core.push.PushAlertMode
import dev.astoris.ursa.core.push.PushAlertTiming
import dev.astoris.ursa.core.push.PushDownDelivery
import dev.astoris.ursa.core.push.PushEffectivePolicy
import dev.astoris.ursa.core.push.PushEffectivePolicyResolver
import dev.astoris.ursa.core.push.PushPolicyLayer
import dev.astoris.ursa.core.push.PushPolicyScope
import dev.astoris.ursa.core.push.PushSeverity
import dev.astoris.ursa.core.push.PushQuietHours
import dev.astoris.ursa.core.push.PushEventPolicy
import dev.astoris.ursa.core.work.CertExpiryWorker
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.ui.AccessProfileNotice
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.KumaPushSetupError
import dev.astoris.ursa.ui.KumaPushSetupUiState
import dev.astoris.ursa.ui.allows
import dev.astoris.ursa.ui.lock.BiometricGate
import dev.astoris.ursa.core.push.PushLocalTestResult
import dev.astoris.ursa.core.push.PushDiagnostics
import dev.astoris.ursa.core.push.PushRegistrationError
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PushScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
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
    val snoozes by vm.pushSnoozes.collectAsStateWithLifecycle()
    val dependencyGraph by vm.pushDependencyGraph.collectAsStateWithLifecycle()
    val dependencyStatuses by vm.pushDependencyStatuses.collectAsStateWithLifecycle()
    val quietHours by vm.pushQuietHours.collectAsStateWithLifecycle()
    val eventPreferences by vm.pushEventPreferences.collectAsStateWithLifecycle()
    val overallStatusEnabled by vm.overallStatusEnabled.collectAsStateWithLifecycle()
    val activeConnection by vm.activeConnection.collectAsStateWithLifecycle()
    val destructiveStepUpEnabled by vm.destructiveStepUpEnabled.collectAsStateWithLifecycle()
    val canSetupKuma = activeConnection.allows(AccessCapability.PUSH_SETUP)
    var selectedMonitorIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var defaultForNew by remember { mutableStateOf(true) }
    var confirmRemove by remember { mutableStateOf(false) }
    var modeMonitorId by remember { mutableStateOf<Int?>(null) }
    var severityMonitorId by remember { mutableStateOf<Int?>(null) }
    var timingMonitorId by remember { mutableStateOf<Int?>(null) }
    var dependencyMonitorId by remember { mutableStateOf<Int?>(null) }
    var policyMonitorId by remember { mutableStateOf<Int?>(null) }

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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = notifGranted()
                vm.refreshPushDependencyStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
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

            Section(stringResource(R.string.push_quiet_hours_title)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = quietHours.enabled,
                            role = Role.Checkbox,
                            onValueChange = { vm.setPushQuietHours(quietHours.copy(enabled = it)) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = quietHours.enabled, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.push_quiet_hours_enable))
                        Text(
                            stringResource(R.string.push_quiet_hours_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (quietHours.enabled) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showTimePicker(context, quietHours.startMinute) { minute ->
                                    vm.setPushQuietHours(quietHours.copy(startMinute = minute))
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    R.string.push_quiet_hours_from,
                                    formatMinute(context, quietHours.startMinute),
                                ),
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showTimePicker(context, quietHours.endMinute) { minute ->
                                    vm.setPushQuietHours(quietHours.copy(endMinute = minute))
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    R.string.push_quiet_hours_until,
                                    formatMinute(context, quietHours.endMinute),
                                ),
                            )
                        }
                    }
                    QuietDayRow(
                        days = DayOfWeek.entries.take(5),
                        schedule = quietHours,
                        onChange = vm::setPushQuietHours,
                    )
                    QuietDayRow(
                        days = DayOfWeek.entries.drop(5),
                        schedule = quietHours,
                        onChange = vm::setPushQuietHours,
                    )
                }
                Text(
                    stringResource(R.string.push_quiet_hours_dnd_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        context.startActivity(Intent(ACTION_ZEN_MODE_SETTINGS))
                    },
                ) {
                    Text(stringResource(R.string.push_quiet_hours_dnd_button))
                }
            }

            Section(stringResource(R.string.push_event_types_title)) {
                EventPreferenceRow(
                    title = stringResource(R.string.push_event_recovery),
                    description = stringResource(R.string.push_event_recovery_desc),
                    checked = eventPreferences.recoveryEnabled,
                    onChecked = {
                        vm.setPushEventPreferences(eventPreferences.copy(recoveryEnabled = it))
                    },
                )
                EventPreferenceRow(
                    title = stringResource(R.string.push_event_maintenance),
                    description = stringResource(R.string.push_event_maintenance_desc),
                    checked = eventPreferences.maintenanceEnabled,
                    onChecked = {
                        vm.setPushEventPreferences(eventPreferences.copy(maintenanceEnabled = it))
                    },
                )
                EventPreferenceRow(
                    title = stringResource(R.string.push_event_certificate),
                    description = stringResource(R.string.push_event_certificate_desc),
                    checked = eventPreferences.certificateEnabled,
                    onChecked = {
                        vm.setPushEventPreferences(eventPreferences.copy(certificateEnabled = it))
                    },
                )
                EventPreferenceRow(
                    title = stringResource(R.string.push_event_update),
                    description = stringResource(R.string.push_event_update_desc),
                    checked = eventPreferences.updateEnabled,
                    onChecked = {
                        vm.setPushEventPreferences(eventPreferences.copy(updateEnabled = it))
                    },
                )
            }

            Section(stringResource(R.string.overall_status_title)) {
                EventPreferenceRow(
                    title = stringResource(R.string.overall_status_enable),
                    description = stringResource(R.string.overall_status_desc),
                    checked = overallStatusEnabled,
                    onChecked = vm::setOverallStatusEnabled,
                )
                Text(
                    stringResource(R.string.overall_status_cost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(stringResource(R.string.push_channels_title)) {
                Text(
                    stringResource(R.string.push_channels_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PushSeverity.entries.forEach { severity ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            openChannelSettings(
                                context,
                                dev.astoris.ursa.core.push.PushSeverityPolicy.route(severity).channelId,
                            )
                        },
                    ) {
                        Text(stringResource(R.string.push_channels_configure, stringResource(severity.labelRes)))
                    }
                }
                listOf(
                    R.string.push_event_recovery to PushEventPolicy.RECOVERY_ROUTE.channelId,
                    R.string.push_event_maintenance to PushEventPolicy.MAINTENANCE_ROUTE.channelId,
                    R.string.push_event_certificate to CertExpiryWorker.CHANNEL_ID,
                    R.string.push_event_update to PushEventPolicy.UPDATE_ROUTE.channelId,
                ).forEach { (labelRes, channelId) ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openChannelSettings(context, channelId) },
                    ) {
                        Text(stringResource(R.string.push_channels_configure, stringResource(labelRes)))
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
                                    val clip = ClipData.newPlainText("UnifiedPush endpoint", ep).apply {
                                        description.extras = PersistableBundle().apply {
                                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                                        }
                                    }
                                    clipboard.setClipEntry(ClipEntry(clip))
                                }
                            },
                        ) { Text(stringResource(R.string.push_copy)) }
                        OutlinedButton(onClick = { vm.unregisterPush() }) { Text(stringResource(R.string.push_disconnect)) }
                    }
                }

                Section(stringResource(R.string.push_kuma_section)) {
                    AccessProfileNotice(activeConnection)
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
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
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
                                    )
                                    DiagnosticRow(
                                        stringResource(R.string.push_kuma_connection_label),
                                        activeConnection?.displayName
                                            ?: stringResource(R.string.push_kuma_connection_unavailable),
                                    )
                                    DiagnosticRow(
                                        stringResource(R.string.push_kuma_provider_label),
                                        stringResource(R.string.push_kuma_provider_value),
                                    )
                                    DiagnosticRow(
                                        stringResource(R.string.push_kuma_delivery_label),
                                        distributor?.let {
                                            stringResource(R.string.push_kuma_delivery_value, it)
                                        } ?: stringResource(R.string.push_kuma_delivery_endpoint),
                                    )
                                    DiagnosticRow(
                                        stringResource(R.string.push_kuma_test_label),
                                        diagnostics.deliveryTestSummary(),
                                    )
                                }
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
                                        enabled = canSetupKuma,
                                        role = Role.Checkbox,
                                        onValueChange = { defaultForNew = it },
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = defaultForNew,
                                    enabled = canSetupKuma,
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
                                    TextButton(
                                        onClick = { selectedMonitorIds = monitors.mapTo(mutableSetOf()) { it.id } },
                                        enabled = canSetupKuma,
                                    ) {
                                        Text(stringResource(R.string.push_kuma_all))
                                    }
                                    TextButton(onClick = { selectedMonitorIds = emptySet() }, enabled = canSetupKuma) {
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
                                            enabled = canSetupKuma,
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
                                        enabled = canSetupKuma,
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
                                    enabled = canSetupKuma,
                                ) {
                                    Text(
                                        stringResource(
                                            if (setup.notificationId == null) R.string.push_kuma_create
                                            else R.string.push_kuma_update,
                                        ),
                                    )
                                }
                                if (setup.notificationId != null) {
                                    OutlinedButton(
                                        onClick = { confirmRemove = true },
                                        enabled = canSetupKuma,
                                    ) {
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
                                monitors.forEachIndexed { index, monitor ->
                                    val mode = alertModes[monitor.id] ?: PushAlertMode.ALL_TRANSITIONS
                                    val severity = severities[monitor.id] ?: PushSeverity.CRITICAL
                                    val timing = alertTimings[monitor.id] ?: PushAlertTiming()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            monitor.name,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            TextButton(onClick = { modeMonitorId = monitor.id }) {
                                                Text(stringResource(mode.labelRes))
                                            }
                                            TextButton(onClick = { severityMonitorId = monitor.id }) {
                                                Text(stringResource(severity.labelRes))
                                            }
                                            TextButton(onClick = { timingMonitorId = monitor.id }) {
                                                Text(timing.summary())
                                            }
                                            TextButton(
                                                onClick = { dependencyMonitorId = monitor.id },
                                                enabled = monitor.id in setup.selectedMonitorIds,
                                            ) {
                                                val count = dependencyGraph.parentsOf(monitor.id).size
                                                Text(
                                                    if (count == 0) stringResource(R.string.push_dependency_none)
                                                    else pluralStringResource(R.plurals.push_dependency_count, count, count),
                                                )
                                            }
                                            TextButton(onClick = { policyMonitorId = monitor.id }) {
                                                Text(stringResource(R.string.push_policy_explain))
                                            }
                                        }
                                    }
                                    if (index < monitors.lastIndex) HorizontalDivider()
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
                                    !kumaTestSending &&
                                    canSetupKuma,
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
                    enabled = canSetupKuma,
                    onClick = {
                        BiometricGate.confirmDestructiveAction(
                            activity = activity,
                            enabled = destructiveStepUpEnabled,
                            onSuccess = {
                                confirmRemove = false
                                vm.deleteKumaPushSetup()
                            },
                        )
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
    val dependencyMonitor = dependencyMonitorId?.let { id -> monitors.firstOrNull { it.id == id } }
    if (dependencyMonitor != null) {
        val ready = kumaSetup as? KumaPushSetupUiState.Ready
        PushDependencyDialog(
            monitorId = dependencyMonitor.id,
            monitorName = dependencyMonitor.name,
            initialParentIds = dependencyGraph.parentsOf(dependencyMonitor.id),
            candidates = monitors.filter {
                it.id != dependencyMonitor.id && it.id in ready?.selectedMonitorIds.orEmpty()
            },
            statuses = dependencyStatuses,
            onSave = { parentIds ->
                if (vm.setPushDependencies(dependencyMonitor.id, parentIds)) {
                    dependencyMonitorId = null
                    true
                } else {
                    false
                }
            },
            onDismiss = { dependencyMonitorId = null },
        )
    }
    val policyMonitor = policyMonitorId?.let { id -> monitors.firstOrNull { it.id == id } }
    if (policyMonitor != null) {
        val mode = alertModes[policyMonitor.id] ?: PushAlertMode.ALL_TRANSITIONS
        val severity = severities[policyMonitor.id] ?: PushSeverity.CRITICAL
        val timing = alertTimings[policyMonitor.id] ?: PushAlertTiming()
        val ready = kumaSetup as? KumaPushSetupUiState.Ready
        val policy = PushEffectivePolicyResolver.resolve(
            layers = listOf(
                PushPolicyLayer(
                    scope = PushPolicyScope.MONITOR,
                    stableKey = policyMonitor.id.toString(),
                    mode = mode.takeUnless { it == PushAlertMode.ALL_TRANSITIONS },
                    severity = severity.takeUnless { it == PushSeverity.CRITICAL },
                    timing = timing.takeUnless { it == PushAlertTiming() },
                ),
            ),
            quietHours = quietHours,
            eventPreferences = eventPreferences,
            setupCurrent = ready?.notificationId != null && ready.configurationCurrent,
            providerAssigned = policyMonitor.id in ready?.selectedMonitorIds.orEmpty(),
            notificationsAllowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled(),
            snoozedUntilMillis = snoozes[policyMonitor.id],
            dependencyGraph = dependencyGraph,
            monitorId = policyMonitor.id,
            dependencyStatuses = dependencyStatuses,
        )
        PushEffectivePolicyDialog(
            monitorName = policyMonitor.name,
            policy = policy,
            monitorNames = monitors.associate { it.id to it.name },
            onDismiss = { policyMonitorId = null },
        )
    }
}

@Composable
private fun PushEffectivePolicyDialog(
    monitorName: String,
    policy: PushEffectivePolicy,
    monitorNames: Map<Int, String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.push_policy_title, monitorName)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    policy.downSummary(monitorNames),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (policy.downDelivery == PushDownDelivery.IMMEDIATE) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurface,
                )
                DiagnosticRow(
                    stringResource(R.string.push_policy_mode),
                    stringResource(
                        R.string.push_policy_sourced_value,
                        stringResource(policy.mode.value.labelRes),
                        stringResource(policy.mode.source.labelRes),
                    ),
                )
                val severityText = if (policy.effectiveSeverity == policy.severity.value) {
                    stringResource(policy.severity.value.labelRes)
                } else {
                    stringResource(
                        R.string.push_policy_effective_severity,
                        stringResource(policy.severity.value.labelRes),
                        stringResource(policy.effectiveSeverity.labelRes),
                    )
                }
                DiagnosticRow(
                    stringResource(R.string.push_policy_severity),
                    stringResource(
                        R.string.push_policy_sourced_value,
                        severityText,
                        stringResource(policy.severity.source.labelRes),
                    ),
                )
                DiagnosticRow(
                    stringResource(R.string.push_policy_timing),
                    stringResource(
                        R.string.push_policy_sourced_value,
                        policy.timing.value.summary(),
                        stringResource(policy.timing.source.labelRes),
                    ),
                )
                DiagnosticRow(
                    stringResource(R.string.push_policy_quiet_hours),
                    stringResource(
                        if (policy.quietHoursActive) R.string.push_policy_quiet_active
                        else R.string.push_policy_quiet_inactive,
                    ),
                )
                DiagnosticRow(
                    stringResource(R.string.push_policy_snooze),
                    policy.snoozedUntilMillis?.let {
                        stringResource(R.string.push_policy_snoozed_until, it.diagnosticTimeOrNever())
                    } ?: stringResource(R.string.push_policy_not_active),
                )
                DiagnosticRow(
                    stringResource(R.string.push_policy_acknowledgement),
                    stringResource(R.string.push_policy_acknowledgement_value),
                )
                DiagnosticRow(
                    stringResource(R.string.push_event_recovery),
                    stringResource(
                        if (policy.recoveryWillNotify) R.string.push_policy_will_notify
                        else R.string.push_policy_will_not_notify,
                    ),
                )
                DiagnosticRow(
                    stringResource(R.string.push_event_maintenance),
                    stringResource(
                        if (policy.maintenanceWillNotify) R.string.push_policy_will_notify
                        else R.string.push_policy_will_not_notify,
                    ),
                )
                DiagnosticRow(
                    stringResource(R.string.push_policy_dependencies),
                    policy.dependencySummary(monitorNames),
                )
                DiagnosticRow(
                    stringResource(R.string.push_policy_grouping),
                    stringResource(R.string.push_policy_grouping_default),
                )
                Text(
                    stringResource(R.string.push_policy_precedence_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun PushDependencyDialog(
    monitorId: Int,
    monitorName: String,
    initialParentIds: Set<Int>,
    candidates: List<Monitor>,
    statuses: Map<Int, Int?>,
    onSave: (Set<Int>) -> Boolean,
    onDismiss: () -> Unit,
) {
    var selected by remember(monitorId, initialParentIds) { mutableStateOf(initialParentIds) }
    var cycleRejected by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.push_dependency_title, monitorName)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.push_dependency_desc))
                if (candidates.isEmpty()) {
                    Text(
                        stringResource(R.string.push_dependency_no_candidates),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                candidates.forEach { candidate ->
                    val checked = candidate.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { enabled ->
                                    selected = if (enabled) selected + candidate.id else selected - candidate.id
                                    cycleRejected = false
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text(candidate.name)
                            Text(
                                stringResource(
                                    R.string.push_dependency_last_status,
                                    stringResource(statuses[candidate.id].dependencyStatusLabelRes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (cycleRejected) {
                    Text(
                        stringResource(R.string.push_dependency_cycle_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { cycleRejected = !onSave(selected) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun QuietDayRow(
    days: List<DayOfWeek>,
    schedule: PushQuietHours,
    onChange: (PushQuietHours) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { day ->
            val selected = schedule.includes(day)
            TextButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val bit = PushQuietHours.dayMask(day)
                    val nextMask = if (selected) schedule.daysMask and bit.inv()
                    else schedule.daysMask or bit
                    onChange(schedule.copy(daysMask = nextMask))
                },
            ) {
                Text(
                    day.getDisplayName(TextStyle.SHORT, locale),
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventPreferenceRow(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun showTimePicker(context: Context, initialMinute: Int, onPicked: (Int) -> Unit) {
    val safeMinute = initialMinute.coerceIn(0, PushQuietHours.MINUTES_PER_DAY - 1)
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        safeMinute / 60,
        safeMinute % 60,
        android.text.format.DateFormat.is24HourFormat(context),
    ).show()
}

private fun formatMinute(context: Context, minute: Int): String {
    val safeMinute = minute.coerceIn(0, PushQuietHours.MINUTES_PER_DAY - 1)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, safeMinute / 60)
        set(Calendar.MINUTE, safeMinute % 60)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}

private fun openChannelSettings(context: Context, channelId: String) {
    val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    try {
        context.startActivity(channelIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
}

private const val ACTION_ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"

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

private val PushPolicyScope.labelRes: Int
    get() = when (this) {
        PushPolicyScope.DEFAULT -> R.string.push_policy_source_default
        PushPolicyScope.SERVER -> R.string.push_policy_source_server
        PushPolicyScope.GROUP -> R.string.push_policy_source_group
        PushPolicyScope.TAG -> R.string.push_policy_source_tag
        PushPolicyScope.MONITOR -> R.string.push_policy_source_monitor
    }

@Composable
private fun PushEffectivePolicy.downSummary(monitorNames: Map<Int, String>): String = when (downDelivery) {
    PushDownDelivery.SETUP_REQUIRED -> stringResource(R.string.push_policy_down_setup)
    PushDownDelivery.NOT_ASSIGNED -> stringResource(R.string.push_policy_down_unassigned)
    PushDownDelivery.DEVICE_BLOCKED -> stringResource(R.string.push_policy_down_device_blocked)
    PushDownDelivery.MODE_BLOCKED -> stringResource(R.string.push_policy_down_mode_blocked)
    PushDownDelivery.DEPENDENCY_SUPPRESSED -> stringResource(
        R.string.push_policy_down_dependency_suppressed,
        dependencySuppression?.parentId?.let(monitorNames::get)
            ?: stringResource(R.string.push_dependency_unknown_parent),
    )
    PushDownDelivery.DELAYED -> stringResource(
        R.string.push_policy_down_delayed,
        scheduledAtMillis.diagnosticTimeOrNever(),
    )
    PushDownDelivery.SNOOZED -> stringResource(
        R.string.push_policy_down_snoozed,
        scheduledAtMillis.diagnosticTimeOrNever(),
    )
    PushDownDelivery.IMMEDIATE -> stringResource(R.string.push_policy_down_immediate)
}

@Composable
private fun PushEffectivePolicy.dependencySummary(monitorNames: Map<Int, String>): String = when {
    dependencySuppression != null -> stringResource(
        R.string.push_policy_dependency_suppressed,
        monitorNames[dependencySuppression.parentId] ?: stringResource(R.string.push_dependency_unknown_parent),
    )
    dependencyParentIds.isNotEmpty() -> pluralStringResource(
        R.plurals.push_policy_dependency_clear,
        dependencyParentIds.size,
        dependencyParentIds.size,
    )
    else -> stringResource(R.string.push_policy_dependencies_default)
}

private val Int?.dependencyStatusLabelRes: Int
    get() = when (this) {
        0 -> R.string.status_down
        1 -> R.string.status_up
        2 -> R.string.status_pending
        3 -> R.string.status_maintenance
        else -> R.string.push_dependency_status_unknown
    }

private val KumaPushSetupError.messageRes: Int
    get() = when (this) {
        KumaPushSetupError.INVALID_ENDPOINT -> R.string.push_kuma_invalid_endpoint
        KumaPushSetupError.SERVER_UNAVAILABLE -> R.string.push_kuma_server_unavailable
        KumaPushSetupError.SAVE_FAILED -> R.string.push_kuma_save_failed
        KumaPushSetupError.SCOPE_SAVE_FAILED -> R.string.push_kuma_scope_save_failed
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

@Composable
private fun PushDiagnostics.deliveryTestSummary(): String = when {
    deliveryTestRequestedAtMs == null -> stringResource(R.string.push_diagnostics_never)
    deliveryTestRejectedAtMs.isAtOrAfter(deliveryTestRequestedAtMs) ->
        stringResource(R.string.push_test_kuma_rejected)
    deliveryTestReceivedAtMs.isAtOrAfter(deliveryTestRequestedAtMs) ->
        stringResource(R.string.push_test_kuma_received, deliveryTestReceivedAtMs.diagnosticTimeOrNever())
    else -> stringResource(R.string.push_test_kuma_waiting)
}

private fun Long?.isAtOrAfter(reference: Long?): Boolean =
    this != null && reference != null && this >= reference

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
