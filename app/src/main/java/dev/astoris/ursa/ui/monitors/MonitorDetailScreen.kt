package dev.astoris.ursa.ui.monitors

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.ServerWebLink
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusPill
import dev.astoris.ursa.ui.AccessProfileNotice
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.allows
import dev.astoris.ursa.ui.components.FreshnessIndicator
import dev.astoris.ursa.ui.components.FreshnessState
import dev.astoris.ursa.ui.components.TelemetrySummary
import dev.astoris.ursa.ui.components.resolveFreshness
import dev.astoris.ursa.ui.lock.BiometricGate
import dev.astoris.ursa.ui.theme.KumaGreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorDetailScreen(
    vm: UrsaViewModel,
    monitor: Monitor,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    handleSystemBack: Boolean = true,
) {
    val beats by vm.beats.collectAsStateWithLifecycle()
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val certs by vm.certs.collectAsStateWithLifecycle()
    val cert = certs[monitor.id]
    val beatRange by vm.beatRange.collectAsStateWithLifecycle()
    val slowAlertEnabled by vm.slowAlertEnabled.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val activeConnection by vm.activeConnection.collectAsStateWithLifecycle()
    val connectionState by vm.state.collectAsStateWithLifecycle()
    val showingCache by vm.showingCache.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val destructiveStepUpEnabled by vm.destructiveStepUpEnabled.collectAsStateWithLifecycle()
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(DateUtils.MINUTE_IN_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    val canChangeState = activeConnection.allows(AccessCapability.MONITOR_STATE)
    val canEdit = activeConnection.allows(AccessCapability.MONITOR_EDIT)
    val canDelete = activeConnection.allows(AccessCapability.MONITOR_DELETE)
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val browserUrl = remember(activeUrl, monitor.id) {
        activeUrl?.let { ServerWebLink.monitor(it, monitor.id) }
    }
    val browserChooserTitle = stringResource(R.string.open_in_kuma_chooser)
    var overrideText by remember(monitor.id) { mutableStateOf("") }
    var actionInFlight by remember(monitor.id) { mutableStateOf(false) }
    var moreOpen by remember(monitor.id) { mutableStateOf(false) }
    var confirmBrowserOpen by remember(monitor.id) { mutableStateOf(false) }
    var confirmDelete by remember(monitor.id) { mutableStateOf(false) }
    var deleteChildren by remember(monitor.id) { mutableStateOf(false) }
    var section by rememberSaveable(monitor.id) { mutableStateOf(MonitorWorkspaceSection.OVERVIEW) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pausedMessage = stringResource(R.string.monitor_paused)
    val pauseFailedMessage = stringResource(R.string.monitor_pause_failed)
    val resumedMessage = stringResource(R.string.monitor_resumed)
    val resumeFailedMessage = stringResource(R.string.monitor_resume_failed)
    val deleteFailedMessage = stringResource(R.string.monitor_delete_failed)
    val parentName = remember(monitor.parentId, monitors) {
        monitor.parentId?.let { parentId -> monitors.firstOrNull { it.id == parentId }?.name }
    }
    val lastTransition = remember(beats) { monitorTransitionEvents(beats).lastOrNull() }
    val freshness = monitorWorkspaceFreshness(connectionState, showingCache, lastUpdated, nowMillis)
    val freshnessLabel = when {
        connectionState == ConnectionState.Authenticated && !showingCache -> {
            stringResource(R.string.home_data_live)
        }
        lastUpdated != null -> stringResource(
            R.string.home_data_cached,
            DateUtils.getRelativeTimeSpanString(
                lastUpdated!!,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
        )
        else -> stringResource(R.string.connection_offline)
    }
    LaunchedEffect(monitor.id) { overrideText = vm.monitorThresholdMs(monitor.id)?.toString() ?: "" }

    BackHandler(enabled = handleSystemBack) { vm.back() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(monitor.name) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { vm.back() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vertical),
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            DropdownMenuItem(
                                enabled = canEdit,
                                text = { Text(stringResource(R.string.action_edit_monitor)) },
                                onClick = {
                                    moreOpen = false
                                    vm.editMonitor(monitor.id)
                                },
                            )
                            DropdownMenuItem(
                                enabled = browserUrl != null,
                                text = { Text(stringResource(R.string.action_open_in_kuma)) },
                                onClick = {
                                    moreOpen = false
                                    confirmBrowserOpen = true
                                },
                            )
                            DropdownMenuItem(
                                enabled = canDelete,
                                text = { Text(stringResource(R.string.action_delete_monitor)) },
                                onClick = {
                                    moreOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AccessProfileNotice(
                activeConnection,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PrimaryScrollableTabRow(selectedTabIndex = section.ordinal) {
                MonitorWorkspaceSection.entries.forEach { target ->
                    Tab(
                        selected = section == target,
                        onClick = { section = target },
                        text = { Text(stringResource(target.labelRes)) },
                    )
                }
            }
            when (section) {
                MonitorWorkspaceSection.OVERVIEW -> MonitorOverviewSection(
                    monitor = monitor,
                    serverName = activeConnection?.displayName ?: stringResource(R.string.app_name),
                    parentName = parentName,
                    lastTransition = lastTransition,
                    freshnessLabel = freshnessLabel,
                    freshness = freshness,
                    favorite = monitor.id in favorites,
                    canChangeState = canChangeState,
                    actionInFlight = actionInFlight,
                    onFavorite = { vm.toggleFavorite(monitor.id) },
                    onToggleActive = {
                        actionInFlight = true
                        val result: (Boolean) -> Unit = { succeeded ->
                            actionInFlight = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (monitor.active) {
                                        if (succeeded) pausedMessage else pauseFailedMessage
                                    } else {
                                        if (succeeded) resumedMessage else resumeFailedMessage
                                    },
                                )
                            }
                        }
                        if (monitor.active) vm.pause(monitor.id, result) else vm.resume(monitor.id, result)
                    },
                    modifier = Modifier.weight(1f),
                )
                MonitorWorkspaceSection.HISTORY -> MonitorHistorySection(
                    beats = beats,
                    selectedRange = beatRange,
                    cert = cert,
                    onRangeSelected = vm::setBeatRange,
                    modifier = Modifier.weight(1f),
                )
                MonitorWorkspaceSection.CONFIGURATION -> MonitorConfigurationSection(
                    monitor = monitor,
                    serverName = activeConnection?.displayName ?: stringResource(R.string.app_name),
                    parentName = parentName,
                    canEdit = canEdit,
                    canOpenInKuma = browserUrl != null,
                    slowAlertEnabled = slowAlertEnabled,
                    overrideText = overrideText,
                    onOverrideChange = { input ->
                        overrideText = input.filter(Char::isDigit).take(6)
                        vm.setMonitorThresholdMs(monitor.id, overrideText.toIntOrNull()?.takeIf { it > 0 })
                    },
                    onEdit = { vm.editMonitor(monitor.id) },
                    onOpenInKuma = { confirmBrowserOpen = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (confirmBrowserOpen && browserUrl != null) {
        AlertDialog(
            onDismissRequest = { confirmBrowserOpen = false },
            title = { Text(stringResource(R.string.open_in_kuma_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.open_in_kuma_message))
                    Text(browserUrl, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmBrowserOpen = false
                        val view = Intent(Intent.ACTION_VIEW, browserUrl.toUri())
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                        context.startActivity(
                            Intent.createChooser(view, browserChooserTitle),
                        )
                    },
                ) { Text(stringResource(R.string.action_open_browser)) }
            },
            dismissButton = {
                Button(onClick = { confirmBrowserOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!actionInFlight) confirmDelete = false },
            title = { Text(stringResource(R.string.monitor_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.monitor_delete_message, monitor.name))
                    if (monitor.type == "group") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = deleteChildren,
                                onCheckedChange = { deleteChildren = it },
                            )
                            Text(stringResource(R.string.monitor_delete_children))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = canDelete && !actionInFlight,
                    onClick = {
                        BiometricGate.confirmDestructiveAction(
                            activity = activity,
                            enabled = destructiveStepUpEnabled,
                            onSuccess = {
                                actionInFlight = true
                                vm.deleteMonitor(monitor.id, deleteChildren) { result ->
                                    actionInFlight = false
                                    if (!result.ok) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(result.message ?: deleteFailedMessage)
                                        }
                                    }
                                }
                            },
                        )
                    },
                ) { Text(stringResource(R.string.action_delete_monitor)) }
            },
            dismissButton = {
                Button(
                    enabled = !actionInFlight,
                    onClick = { confirmDelete = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private enum class MonitorWorkspaceSection {
    OVERVIEW,
    HISTORY,
    CONFIGURATION,
}

private val MonitorWorkspaceSection.labelRes: Int
    get() = when (this) {
        MonitorWorkspaceSection.OVERVIEW -> R.string.monitor_workspace_overview
        MonitorWorkspaceSection.HISTORY -> R.string.monitor_workspace_history
        MonitorWorkspaceSection.CONFIGURATION -> R.string.monitor_workspace_configuration
    }

internal data class MonitorTransitionEvent(
    val status: MonitorStatus,
    val time: String,
    val message: String?,
)

internal fun monitorTransitionEvents(beats: List<Heartbeat>): List<MonitorTransitionEvent> =
    beats.zipWithNext().mapNotNull { (previous, current) ->
        current.takeIf { previous.status != current.status }?.let {
            MonitorTransitionEvent(it.status, it.time, it.msg)
        }
    }

internal fun monitorWorkspaceFreshness(
    connectionState: ConnectionState,
    showingCache: Boolean,
    lastUpdated: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): FreshnessState {
    if (connectionState == ConnectionState.Authenticated && !showingCache) return FreshnessState.LIVE
    val updated = lastUpdated ?: return FreshnessState.OFFLINE
    val online = connectionState == ConnectionState.Connecting ||
        connectionState == ConnectionState.Connected ||
        connectionState == ConnectionState.Authenticated
    return resolveFreshness(
        ageMillis = nowMillis - updated,
        staleAfterMillis = 5L * DateUtils.MINUTE_IN_MILLIS,
        isOnline = online,
    )
}

@Composable
private fun MonitorOverviewSection(
    monitor: Monitor,
    serverName: String,
    parentName: String?,
    lastTransition: MonitorTransitionEvent?,
    freshnessLabel: String,
    freshness: FreshnessState,
    favorite: Boolean,
    canChangeState: Boolean,
    actionInFlight: Boolean,
    onFavorite: () -> Unit,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatusPill(monitor.status)
            FreshnessIndicator(label = freshnessLabel, state = freshness)
        }
        (monitor.ping ?: monitor.avgPing)?.let {
            TelemetrySummary(
                label = stringResource(R.string.detail_response_time),
                value = stringResource(R.string.response_time_milliseconds, it),
                supportingText = monitor.avgPing?.takeIf { monitor.ping != null }?.let { average ->
                    stringResource(R.string.monitor_workspace_average, average)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        monitor.uptime24h?.let {
            TelemetrySummary(
                label = stringResource(R.string.monitor_workspace_uptime),
                value = stringResource(R.string.uptime_percentage, (it * 100).coerceIn(0.0, 100.0).toInt()),
                supportingText = stringResource(R.string.monitor_workspace_last_24_hours),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FilterChip(
            selected = favorite,
            onClick = onFavorite,
            label = {
                Text(
                    stringResource(
                        if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                    ),
                )
            },
        )
        WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_server), serverName)
        WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_type), monitor.type)
        monitor.url?.let { WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_address), it) }
        parentName?.let { WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_parent), it) }
        if (monitor.tags.isNotEmpty()) {
            WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_tags), monitor.tags.joinToString())
        }
        HorizontalDivider()
        Text(stringResource(R.string.monitor_workspace_last_transition), style = MaterialTheme.typography.titleSmall)
        if (lastTransition == null) {
            Text(
                stringResource(R.string.monitor_workspace_no_transition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(lastTransition.status)
                Column {
                    Text(lastTransition.time, style = MaterialTheme.typography.bodyMedium)
                    lastTransition.message?.takeIf(String::isNotBlank)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onToggleActive,
            enabled = canChangeState && !actionInFlight,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    when {
                        monitor.active && actionInFlight -> R.string.action_pausing
                        monitor.active -> R.string.action_pause
                        actionInFlight -> R.string.action_resuming
                        else -> R.string.action_resume
                    },
                ),
            )
        }
    }
}

@Composable
private fun MonitorHistorySection(
    beats: List<Heartbeat>,
    selectedRange: HeartbeatRange,
    cert: CertInfo?,
    onRangeSelected: (HeartbeatRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.detail_response_time), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeartbeatRange.entries.forEach { range ->
                FilterChip(
                    selected = selectedRange == range,
                    onClick = { onRangeSelected(range) },
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
        cert?.let {
            HorizontalDivider()
            Text(stringResource(R.string.detail_tls_certificate), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (it.valid) R.string.cert_valid else R.string.cert_invalid),
                color = if (it.valid) StatusUi.color(MonitorStatus.UP) else MaterialTheme.colorScheme.error,
            )
            it.validTo?.let { value ->
                WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_expires), value)
            }
            it.issuer?.let { value ->
                WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_issuer), value)
            }
            it.subject?.let { value ->
                WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_subject), value)
            }
        }
    }
}

@Composable
private fun MonitorConfigurationSection(
    monitor: Monitor,
    serverName: String,
    parentName: String?,
    canEdit: Boolean,
    canOpenInKuma: Boolean,
    slowAlertEnabled: Boolean,
    overrideText: String,
    onOverrideChange: (String) -> Unit,
    onEdit: () -> Unit,
    onOpenInKuma: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.monitor_workspace_configuration_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_server), serverName)
        WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_type), monitor.type)
        monitor.url?.let { WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_address), it) }
        parentName?.let { WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_parent), it) }
        if (monitor.tags.isNotEmpty()) {
            WorkspaceMetadataRow(stringResource(R.string.monitor_workspace_tags), monitor.tags.joinToString())
        }
        Button(onClick = onEdit, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_edit_monitor))
        }
        OutlinedButton(
            onClick = onOpenInKuma,
            enabled = canOpenInKuma,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_open_in_kuma))
        }
        if (slowAlertEnabled) {
            HorizontalDivider()
            OutlinedTextField(
                value = overrideText,
                onValueChange = onOverrideChange,
                label = { Text(stringResource(R.string.detail_slow_override)) },
                supportingText = { Text(stringResource(R.string.detail_slow_override_desc)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WorkspaceMetadataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun IncidentTimeline(beats: List<Heartbeat>) {
    val events = monitorTransitionEvents(beats).takeLast(8).asReversed()

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
