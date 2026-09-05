package dev.astoris.ursa.ui.monitors

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.core.storage.IncidentNote
import dev.astoris.ursa.core.storage.IncidentNoteCodec
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.theme.KumaGreen
import kotlinx.coroutines.delay
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal data class FleetIncident(
    val monitorId: Int,
    val monitorName: String,
    val startedAt: String?,
    val resolvedAt: String?,
    val message: String?,
) {
    val active: Boolean get() = resolvedAt == null
}

internal enum class FleetIncidentFilter { ALL, ACTIVE, RESOLVED }

internal data class MonitorFlakiness(
    val monitorId: Int,
    val monitorName: String,
    val incidents: Int,
)

internal data class FleetReliabilitySummary(
    val observedDowntimeMillis: Long,
    val meanTimeToRecoveryMillis: Long?,
    val flakiestMonitor: MonitorFlakiness?,
    val incompleteMonitorCount: Int,
    val activeMonitorCount: Int,
)

private val kumaHeartbeatTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]")

internal fun kumaUtcMillisOrNull(value: String?): Long? = runCatching {
    value?.let { LocalDateTime.parse(it, kumaHeartbeatTime).toInstant(ZoneOffset.UTC).toEpochMilli() }
}.getOrNull()

internal fun incidentDurationMillis(incident: FleetIncident, nowMillis: Long): Long? {
    val start = kumaUtcMillisOrNull(incident.startedAt) ?: return null
    val end = kumaUtcMillisOrNull(incident.resolvedAt) ?: nowMillis
    return (end - start).coerceAtLeast(0L)
}

internal fun compactDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

internal fun incidentOverlapsWindow(incident: FleetIncident, cutoffMillis: Long, nowMillis: Long): Boolean {
    val start = kumaUtcMillisOrNull(incident.startedAt)
    val end = kumaUtcMillisOrNull(incident.resolvedAt) ?: nowMillis
    return when {
        incident.active && start == null -> true
        start == null -> false
        else -> start <= nowMillis && end >= cutoffMillis
    }
}

internal fun fleetReliabilitySummary(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    incidents: List<FleetIncident>,
    windowHours: Int,
    nowMillis: Long,
): FleetReliabilitySummary {
    val cutoff = nowMillis - windowHours * 3_600_000L
    val activeMonitors = monitors.filter { it.active }
    val activeMonitorIds = activeMonitors.mapTo(mutableSetOf()) { it.id }
    val incompleteMonitorCount = activeMonitors.count { monitor ->
        history[monitor.id].orEmpty().mapNotNull { kumaUtcMillisOrNull(it.time) }.minOrNull()
            ?.let { it > cutoff } != false
    }
    var observedDowntime = 0L
    val recoveryDurations = mutableListOf<Long>()
    val outageCounts = mutableMapOf<Int, Int>()

    incidents.forEach { incident ->
        if (incident.monitorId !in activeMonitorIds) return@forEach
        val start = kumaUtcMillisOrNull(incident.startedAt) ?: return@forEach
        val end = (kumaUtcMillisOrNull(incident.resolvedAt) ?: nowMillis).coerceAtMost(nowMillis)
        if (end < cutoff || start > nowMillis) return@forEach
        observedDowntime += (end - start.coerceAtLeast(cutoff)).coerceAtLeast(0L)
        if (start >= cutoff) {
            outageCounts[incident.monitorId] = outageCounts.getOrDefault(incident.monitorId, 0) + 1
            if (!incident.active) recoveryDurations += (end - start).coerceAtLeast(0L)
        }
    }

    val names = monitors.associate { it.id to it.name }
    val flakiest = outageCounts.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { names[it.key].orEmpty() })
        .firstOrNull()
        ?.let { MonitorFlakiness(it.key, names[it.key].orEmpty(), it.value) }
    return FleetReliabilitySummary(
        observedDowntimeMillis = observedDowntime,
        meanTimeToRecoveryMillis = recoveryDurations.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        flakiestMonitor = flakiest,
        incompleteMonitorCount = incompleteMonitorCount,
        activeMonitorCount = activeMonitors.size,
    )
}

internal fun fleetIncidents(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
): List<FleetIncident> = monitors.flatMap { monitor ->
    val incidents = mutableListOf<FleetIncident>()
    var downStart: Heartbeat? = null
    history[monitor.id].orEmpty().forEach { beat ->
        if (beat.status == MonitorStatus.DOWN && downStart == null) {
            downStart = beat
        } else if (beat.status != MonitorStatus.DOWN && downStart != null) {
            val start = downStart
            incidents += FleetIncident(
                monitorId = monitor.id,
                monitorName = monitor.name,
                startedAt = start.time,
                resolvedAt = beat.time,
                message = start.msg,
            )
            downStart = null
        }
    }
    if (downStart != null || (monitor.status == MonitorStatus.DOWN && incidents.none { it.active })) {
        incidents += FleetIncident(
            monitorId = monitor.id,
            monitorName = monitor.name,
            startedAt = downStart?.time,
            resolvedAt = null,
            message = downStart?.msg,
        )
    }
    incidents
}.sortedWith(
    compareByDescending<FleetIncident> { it.active }
        .thenByDescending { it.startedAt.orEmpty() },
)

internal fun mergedIncidentHistory(
    liveHistory: Map<Int, List<Heartbeat>>,
    importantBeats: List<Heartbeat>,
): Map<Int, List<Heartbeat>> = (liveHistory.keys + importantBeats.map(Heartbeat::monitorId))
    .associateWith { monitorId ->
        (importantBeats.filter { it.monitorId == monitorId } + liveHistory[monitorId].orEmpty())
            .distinctBy { Triple(it.monitorId, it.time, it.status) }
            .sortedBy(Heartbeat::time)
    }

internal data class IncidentShareCopy(
    val heading: String,
    val active: String,
    val resolved: String,
    val started: String,
    val startUnknown: String,
    val resolvedAt: String,
    val duration: String,
    val message: String,
    val note: String,
    val redacted: String,
)

internal fun incidentShareText(
    incident: FleetIncident,
    note: String?,
    duration: String?,
    serverUrl: String?,
    copy: IncidentShareCopy,
): String {
    fun safe(value: String): String = redactSharedIncidentValue(value, serverUrl, copy.redacted)
    return buildString {
        appendLine(copy.heading)
        append(safe(incident.monitorName))
        append(" - ")
        appendLine(if (incident.active) copy.active else copy.resolved)
        append(copy.started)
        append(' ')
        append(incident.startedAt?.let(::safe) ?: copy.startUnknown)
        incident.resolvedAt?.let {
            appendLine()
            append(copy.resolvedAt)
            append(' ')
            append(safe(it))
        }
        duration?.let {
            appendLine()
            append(copy.duration)
            append(' ')
            append(safe(it))
        }
        incident.message?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            append(copy.message)
            append(' ')
            append(safe(it))
        }
        note?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            append(copy.note)
            append(' ')
            append(safe(it))
        }
    }
}

private fun redactSharedIncidentValue(value: String, serverUrl: String?, replacement: String): String {
    var redacted = value.trim()
    serverUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let { url ->
        val serverParts = buildSet {
            add(url)
            runCatching { URI(url) }.getOrNull()?.let { uri ->
                uri.rawAuthority?.takeIf(String::isNotBlank)?.let(::add)
                uri.host?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.sortedByDescending(String::length)
        serverParts.forEach { redacted = redacted.replace(it, replacement, ignoreCase = true) }
    }
    redacted = SHARED_URL_PATTERN.replace(redacted, replacement)
    return SHARED_SECRET_PATTERN.replace(redacted) { match ->
        "${match.groupValues[1]}=$replacement"
    }
}

@Composable
internal fun FleetIncidentCenter(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    notes: List<IncidentNote>,
    serverUrl: String?,
    loadImportantHeartbeats: suspend () -> List<Heartbeat>?,
    onClose: () -> Unit,
    onIncidentClick: (Int) -> Unit,
    onSaveNote: (Int, String?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var importantHistory by remember { mutableStateOf<ImportantHistoryState>(ImportantHistoryState.Loading) }
    LaunchedEffect(Unit) {
        importantHistory = loadImportantHeartbeats()?.let(ImportantHistoryState::Loaded)
            ?: ImportantHistoryState.Failed
    }
    val incidentHistory = remember(history, importantHistory) {
        when (val state = importantHistory) {
            is ImportantHistoryState.Loaded -> mergedIncidentHistory(history, state.beats)
            ImportantHistoryState.Loading, ImportantHistoryState.Failed -> history
        }
    }
    val notesEnabled = importantHistory is ImportantHistoryState.Loaded
    val incidents = remember(monitors, incidentHistory) { fleetIncidents(monitors, incidentHistory) }
    val noteByIncident = remember(notes) { notes.associateBy { it.monitorId to it.startedAt } }
    var editingIncident by remember { mutableStateOf<FleetIncident?>(null) }
    var filter by remember { mutableStateOf(FleetIncidentFilter.ALL) }
    var window by remember { mutableStateOf(HeartbeatRange.DAY) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }
    val cutoffMillis = nowMillis - window.hours * 3_600_000L
    val windowedIncidents = incidents.filter { incidentOverlapsWindow(it, cutoffMillis, nowMillis) }
    val reliability = fleetReliabilitySummary(monitors, incidentHistory, incidents, window.hours, nowMillis)
    val shown = windowedIncidents.filter { incident ->
        when (filter) {
            FleetIncidentFilter.ALL -> true
            FleetIncidentFilter.ACTIVE -> incident.active
            FleetIncidentFilter.RESOLVED -> !incident.active
        }
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.incident_center_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.incident_center_close)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FleetIncidentFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                        selectedLabelColor = KumaGreen,
                    ),
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    FleetIncidentFilter.ALL -> R.string.filter_all
                                    FleetIncidentFilter.ACTIVE -> R.string.incident_filter_active
                                    FleetIncidentFilter.RESOLVED -> R.string.incident_filter_resolved
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (importantHistory == ImportantHistoryState.Failed) {
            Text(
                stringResource(R.string.incident_notes_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeartbeatRange.entries.forEach { option ->
                FilterChip(
                    selected = window == option,
                    onClick = { window = option },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                        selectedLabelColor = KumaGreen,
                    ),
                    label = { Text(option.label) },
                )
            }
        }
        if (shown.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { ReliabilitySummaryCard(window, reliability) }
                item {
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.incident_center_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ReliabilitySummaryCard(window, reliability) }
                items(shown, key = { "${it.monitorId}-${it.startedAt}-${it.resolvedAt}" }) { incident ->
                    val note = incident.startedAt?.let { noteByIncident[incident.monitorId to it]?.text }
                    UrsaPressableCard(onClick = { onIncidentClick(incident.monitorId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape).background(
                                        StatusUi.color(if (incident.active) MonitorStatus.DOWN else MonitorStatus.UP),
                                    ),
                                )
                                Text(incident.monitorName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    stringResource(
                                        if (incident.active) R.string.statuspage_incident_active
                                        else R.string.statuspage_incident_resolved,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                incident.startedAt?.let { stringResource(R.string.incident_started_at, it) }
                                    ?: stringResource(R.string.incident_start_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            incident.resolvedAt?.let {
                                Text(
                                    stringResource(R.string.incident_resolved_at, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            incidentDurationMillis(incident, nowMillis)?.let { duration ->
                                Text(
                                    stringResource(R.string.incident_duration, compactDuration(duration)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (incident.active) StatusUi.color(MonitorStatus.DOWN)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            incident.message?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            note?.let {
                                Text(
                                    stringResource(R.string.incident_note_preview, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 3,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = { editingIncident = incident },
                                    enabled = notesEnabled && incident.startedAt != null,
                                ) {
                                    Text(
                                        stringResource(
                                            if (note == null) R.string.incident_note_add
                                            else R.string.incident_note_edit,
                                        ),
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        shareIncident(
                                            context = context,
                                            incident = incident,
                                            note = note,
                                            duration = incidentDurationMillis(incident, nowMillis)?.let(::compactDuration),
                                            serverUrl = serverUrl,
                                        )
                                    },
                                ) { Text(stringResource(R.string.incident_share)) }
                            }
                        }
                    }
                }
            }
        }
    }
    editingIncident?.let { incident ->
        val existing = incident.startedAt?.let { noteByIncident[incident.monitorId to it]?.text }
        IncidentNoteDialog(
            incident = incident,
            existing = existing,
            onDismiss = { editingIncident = null },
            onSave = { text ->
                onSaveNote(incident.monitorId, incident.startedAt, text)
                editingIncident = null
            },
        )
    }
}

@Composable
private fun IncidentNoteDialog(
    incident: FleetIncident,
    existing: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(incident, existing) { mutableStateOf(existing.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.incident_note_title, incident.monitorName)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(IncidentNoteCodec.MAX_TEXT_LENGTH) },
                label = { Text(stringResource(R.string.incident_note_label)) },
                minLines = 3,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        stringResource(
                            R.string.incident_note_support,
                            text.length,
                            IncidentNoteCodec.MAX_TEXT_LENGTH,
                        ),
                    )
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { onSave("") }) {
                        Text(stringResource(R.string.incident_note_delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private fun shareIncident(
    context: Context,
    incident: FleetIncident,
    note: String?,
    duration: String?,
    serverUrl: String?,
) {
    val copy = IncidentShareCopy(
        heading = context.getString(R.string.incident_share_heading),
        active = context.getString(R.string.statuspage_incident_active),
        resolved = context.getString(R.string.statuspage_incident_resolved),
        started = context.getString(R.string.incident_share_started),
        startUnknown = context.getString(R.string.incident_start_unknown),
        resolvedAt = context.getString(R.string.incident_share_resolved),
        duration = context.getString(R.string.incident_share_duration),
        message = context.getString(R.string.incident_share_message),
        note = context.getString(R.string.incident_share_note),
        redacted = context.getString(R.string.incident_share_redacted),
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.incident_share_subject))
        putExtra(Intent.EXTRA_TEXT, incidentShareText(incident, note, duration, serverUrl, copy))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.incident_share_chooser)))
}

private sealed interface ImportantHistoryState {
    data object Loading : ImportantHistoryState
    data object Failed : ImportantHistoryState
    data class Loaded(val beats: List<Heartbeat>) : ImportantHistoryState
}

@Composable
private fun ReliabilitySummaryCard(
    window: HeartbeatRange,
    summary: FleetReliabilitySummary,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.reliability_title, window.label),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReliabilityMetric(
                    value = compactDuration(summary.observedDowntimeMillis),
                    label = stringResource(R.string.reliability_downtime),
                    modifier = Modifier.weight(1f),
                )
                ReliabilityMetric(
                    value = summary.meanTimeToRecoveryMillis?.let(::compactDuration)
                        ?: stringResource(R.string.reliability_unavailable),
                    label = stringResource(R.string.reliability_mttr),
                    modifier = Modifier.weight(1f),
                )
                ReliabilityMetric(
                    value = summary.flakiestMonitor?.monitorName
                        ?: stringResource(R.string.reliability_unavailable),
                    label = summary.flakiestMonitor?.let {
                        pluralStringResource(R.plurals.reliability_incidents, it.incidents, it.incidents)
                    } ?: stringResource(R.string.reliability_no_outages),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (summary.activeMonitorCount == 0) {
                    stringResource(R.string.reliability_no_active_monitors)
                } else if (summary.incompleteMonitorCount == 0) {
                    pluralStringResource(
                        R.plurals.reliability_complete,
                        summary.activeMonitorCount,
                        summary.activeMonitorCount,
                    )
                } else {
                    pluralStringResource(
                        R.plurals.reliability_incomplete,
                        summary.activeMonitorCount,
                        summary.incompleteMonitorCount,
                        summary.activeMonitorCount,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReliabilityMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val SHARED_URL_PATTERN = Regex("(?i)\\b(?:https?|wss?)://\\S+")
private val SHARED_SECRET_PATTERN = Regex(
    "(?im)\\b(password|passcode|token|authorization|api[-_ ]?key|secret)\\s*[:=]\\s*[^\\r\\n,;]+",
)
