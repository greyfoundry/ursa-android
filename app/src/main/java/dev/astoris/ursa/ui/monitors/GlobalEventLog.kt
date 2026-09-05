package dev.astoris.ursa.ui.monitors

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.core.storage.LocalEvent
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.theme.KumaBlue
import dev.astoris.ursa.ui.theme.KumaGreen
import dev.astoris.ursa.ui.theme.KumaOrange
import dev.astoris.ursa.ui.theme.KumaRed

internal enum class EventLogKind {
    DOWN,
    UP,
    RECOVERED,
    PENDING,
    MAINTENANCE_STARTED,
    MAINTENANCE_ENDED,
    PAUSED,
    RESUMED,
    SLOW_RESPONSE,
    CERTIFICATE_EXPIRY,
    PUSH_ALERT,
}

internal enum class EventLogFilter { ALL, STATE, MAINTENANCE, ACTIONS, ALERTS }

internal enum class EventLogSource { KUMA, DEVICE }

internal data class EventLogEntry(
    val id: String,
    val monitorId: Int?,
    val monitorName: String,
    val kind: EventLogKind,
    val atMillis: Long,
    val detail: String?,
    val source: EventLogSource,
)

/** Builds only observed transitions; the first available heartbeat is not presented as an event. */
internal fun heartbeatEventLog(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
): List<EventLogEntry> = monitors.flatMap { monitor ->
    val ordered = history[monitor.id].orEmpty()
        .mapNotNull { beat -> kumaUtcMillisOrNull(beat.time)?.let { it to beat } }
        .sortedBy { it.first }
    ordered.zipWithNext().flatMap { (previous, current) ->
        val before = previous.second
        val after = current.second
        if (before.status == after.status) return@flatMap emptyList()
        val events = mutableListOf<EventLogEntry>()
        if (before.status == MonitorStatus.MAINTENANCE && after.status != MonitorStatus.MAINTENANCE) {
            events += heartbeatEvent(monitor, after, current.first, EventLogKind.MAINTENANCE_ENDED)
        }
        if (after.status == MonitorStatus.MAINTENANCE) {
            events += heartbeatEvent(monitor, after, current.first, EventLogKind.MAINTENANCE_STARTED)
        } else {
            val kind = when {
                after.status == MonitorStatus.DOWN -> EventLogKind.DOWN
                before.status == MonitorStatus.DOWN && after.status == MonitorStatus.UP -> EventLogKind.RECOVERED
                after.status == MonitorStatus.UP -> EventLogKind.UP
                after.status == MonitorStatus.PENDING -> EventLogKind.PENDING
                else -> null
            }
            if (kind != null) events += heartbeatEvent(monitor, after, current.first, kind)
        }
        events
    }
}

private fun heartbeatEvent(
    monitor: Monitor,
    beat: Heartbeat,
    atMillis: Long,
    kind: EventLogKind,
) = EventLogEntry(
    id = "kuma:${monitor.id}:${beat.time}:$kind",
    monitorId = monitor.id,
    monitorName = monitor.name,
    kind = kind,
    atMillis = atMillis,
    detail = beat.msg?.trim()?.take(240)?.ifBlank { null },
    source = EventLogSource.KUMA,
)

internal fun combinedEventLog(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    localEvents: List<LocalEvent>,
): List<EventLogEntry> = (
    heartbeatEventLog(monitors, history) + localEvents.map { event ->
        EventLogEntry(
            id = "local:${event.id}",
            monitorId = event.monitorId,
            monitorName = event.monitorName,
            kind = when (event.kind) {
                LocalEventKind.PAUSED -> EventLogKind.PAUSED
                LocalEventKind.RESUMED -> EventLogKind.RESUMED
                LocalEventKind.SLOW_RESPONSE -> EventLogKind.SLOW_RESPONSE
                LocalEventKind.CERTIFICATE_EXPIRY -> EventLogKind.CERTIFICATE_EXPIRY
                LocalEventKind.PUSH_ALERT -> EventLogKind.PUSH_ALERT
            },
            atMillis = event.atMillis,
            detail = event.detail,
            source = EventLogSource.DEVICE,
        )
    }
).distinctBy { it.id }
    .sortedWith(compareByDescending<EventLogEntry> { it.atMillis }.thenBy { it.id })

internal fun EventLogEntry.matches(filter: EventLogFilter): Boolean = when (filter) {
    EventLogFilter.ALL -> true
    EventLogFilter.STATE -> kind in setOf(
        EventLogKind.DOWN,
        EventLogKind.UP,
        EventLogKind.RECOVERED,
        EventLogKind.PENDING,
    )
    EventLogFilter.MAINTENANCE -> kind in setOf(EventLogKind.MAINTENANCE_STARTED, EventLogKind.MAINTENANCE_ENDED)
    EventLogFilter.ACTIONS -> kind in setOf(EventLogKind.PAUSED, EventLogKind.RESUMED)
    EventLogFilter.ALERTS -> kind in setOf(
        EventLogKind.SLOW_RESPONSE,
        EventLogKind.CERTIFICATE_EXPIRY,
        EventLogKind.PUSH_ALERT,
    )
}

@Composable
internal fun GlobalEventLog(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    localEvents: List<LocalEvent>,
    onClose: () -> Unit,
    onMonitorClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    var filter by remember { mutableStateOf(EventLogFilter.ALL) }
    val entries = remember(monitors, history, localEvents) {
        combinedEventLog(monitors, history, localEvents)
    }
    val shown = entries.filter { it.matches(filter) }
    val monitorIds = remember(monitors) { monitors.mapTo(mutableSetOf()) { it.id } }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.event_log_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.incident_center_close)) }
        }
        Text(
            stringResource(R.string.event_log_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(EventLogFilter.entries) { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                        selectedLabelColor = KumaGreen,
                    ),
                    label = { Text(stringResource(option.labelRes)) },
                )
            }
        }
        if (shown.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.event_log_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.event_log_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(shown, key = { it.id }) { entry ->
                    val selectable = entry.monitorId in monitorIds
                    if (selectable) {
                        UrsaPressableCard(
                            onClick = { entry.monitorId?.let(onMonitorClick) },
                        ) { EventLogRowContent(entry) }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) { EventLogRowContent(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EventLogRowContent(entry: EventLogEntry) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.padding(top = 5.dp).size(10.dp),
            shape = CircleShape,
            color = eventColor(entry.kind),
            content = {},
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.monitorName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        entry.atMillis,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(entry.kind.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = eventColor(entry.kind),
            )
            entry.detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(
                    if (entry.source == EventLogSource.KUMA) R.string.event_source_kuma
                    else R.string.event_source_device,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val EventLogFilter.labelRes: Int
    get() = when (this) {
        EventLogFilter.ALL -> R.string.filter_all
        EventLogFilter.STATE -> R.string.event_filter_state
        EventLogFilter.MAINTENANCE -> R.string.event_filter_maintenance
        EventLogFilter.ACTIONS -> R.string.event_filter_actions
        EventLogFilter.ALERTS -> R.string.event_filter_alerts
    }

private val EventLogKind.labelRes: Int
    get() = when (this) {
        EventLogKind.DOWN -> R.string.event_down
        EventLogKind.UP -> R.string.event_up
        EventLogKind.RECOVERED -> R.string.event_recovered
        EventLogKind.PENDING -> R.string.event_pending
        EventLogKind.MAINTENANCE_STARTED -> R.string.event_maintenance_started
        EventLogKind.MAINTENANCE_ENDED -> R.string.event_maintenance_ended
        EventLogKind.PAUSED -> R.string.event_paused
        EventLogKind.RESUMED -> R.string.event_resumed
        EventLogKind.SLOW_RESPONSE -> R.string.event_slow_response
        EventLogKind.CERTIFICATE_EXPIRY -> R.string.event_certificate_expiry
        EventLogKind.PUSH_ALERT -> R.string.event_push_alert
    }

@Composable
private fun eventColor(kind: EventLogKind): Color = when (kind) {
    EventLogKind.DOWN -> KumaRed
    EventLogKind.UP, EventLogKind.RECOVERED, EventLogKind.RESUMED -> KumaGreen
    EventLogKind.PENDING, EventLogKind.SLOW_RESPONSE, EventLogKind.CERTIFICATE_EXPIRY -> KumaOrange
    EventLogKind.MAINTENANCE_STARTED, EventLogKind.MAINTENANCE_ENDED -> KumaBlue
    EventLogKind.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
    EventLogKind.PUSH_ALERT -> MaterialTheme.colorScheme.primary
}
