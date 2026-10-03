package dev.astoris.ursa.ui.incidents

import android.text.format.DateUtils
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.core.storage.LocalEvent
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.theme.KumaGreen
import dev.astoris.ursa.ui.theme.KumaOrange
import dev.astoris.ursa.ui.theme.KumaRed

internal enum class LocalAlertGroupState { ACTIVE, DELAYED, SNOOZED, ACKNOWLEDGED, SUPPRESSED, RECOVERED }

internal data class LocalAlertGroup(
    val id: String,
    val monitorId: Int?,
    val monitorName: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val state: LocalAlertGroupState,
    val events: List<LocalEvent>,
)

internal fun localAlertGroups(events: List<LocalEvent>): List<LocalAlertGroup> = events.asSequence()
    .filter { it.alertId != null && it.kind in alertEventKinds }
    .groupBy { requireNotNull(it.alertId) }
    .map { (alertId, groupEvents) ->
        val ordered = groupEvents.sortedWith(
            compareBy<LocalEvent> { it.atMillis }.thenBy { alertEventOrder(it.kind) }.thenBy { it.id },
        )
        LocalAlertGroup(
            id = alertId,
            monitorId = ordered.mapNotNull(LocalEvent::monitorId).lastOrNull(),
            monitorName = ordered.last().monitorName,
            startedAtMillis = ordered.first().atMillis,
            updatedAtMillis = ordered.last().atMillis,
            state = alertGroupState(ordered),
            events = ordered,
        )
    }
    .sortedWith(compareByDescending<LocalAlertGroup> { it.updatedAtMillis }.thenBy { it.id })

private fun alertGroupState(events: List<LocalEvent>): LocalAlertGroupState {
    if (events.any { it.kind == LocalEventKind.PUSH_RECOVERED }) return LocalAlertGroupState.RECOVERED
    if (events.any { it.kind == LocalEventKind.PUSH_ACKNOWLEDGED }) return LocalAlertGroupState.ACKNOWLEDGED
    val latestState = events.lastOrNull { it.kind != LocalEventKind.PUSH_CORRELATED }?.kind
    return when (latestState) {
        LocalEventKind.PUSH_SUPPRESSED -> LocalAlertGroupState.SUPPRESSED
        LocalEventKind.PUSH_SNOOZED -> LocalAlertGroupState.SNOOZED
        LocalEventKind.PUSH_DELAYED -> LocalAlertGroupState.DELAYED
        else -> LocalAlertGroupState.ACTIVE
    }
}

private fun alertEventOrder(kind: LocalEventKind): Int = when (kind) {
    LocalEventKind.PUSH_RECEIVED -> 0
    LocalEventKind.PUSH_DELAYED -> 1
    LocalEventKind.PUSH_ALERT -> 2
    LocalEventKind.PUSH_REPEATED -> 3
    LocalEventKind.PUSH_CORRELATED -> 4
    LocalEventKind.PUSH_SNOOZED -> 5
    LocalEventKind.PUSH_ACKNOWLEDGED -> 6
    LocalEventKind.PUSH_SUPPRESSED -> 7
    LocalEventKind.PUSH_RECOVERED -> 8
    else -> 9
}

private val alertEventKinds = setOf(
    LocalEventKind.PUSH_RECEIVED,
    LocalEventKind.PUSH_DELAYED,
    LocalEventKind.PUSH_ALERT,
    LocalEventKind.PUSH_REPEATED,
    LocalEventKind.PUSH_ACKNOWLEDGED,
    LocalEventKind.PUSH_SNOOZED,
    LocalEventKind.PUSH_SUPPRESSED,
    LocalEventKind.PUSH_CORRELATED,
    LocalEventKind.PUSH_RECOVERED,
)

@Composable
internal fun LocalAlertGroups(
    events: List<LocalEvent>,
    validMonitorIds: Set<Int>,
    onMonitorClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(events) { localAlertGroups(events) }
    if (groups.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.incident_alert_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.incident_alert_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(groups, key = LocalAlertGroup::id) { group ->
            val content: @Composable () -> Unit = { LocalAlertGroupContent(group) }
            group.monitorId?.takeIf { it in validMonitorIds }?.let { monitorId ->
                UrsaPressableCard(
                    onClick = { onMonitorClick(monitorId) },
                    modifier = Modifier.fillMaxWidth(),
                ) { content() }
            } ?: Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) { content() }
        }
    }
}

@Composable
private fun LocalAlertGroupContent(group: LocalAlertGroup) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.size(9.dp),
                shape = CircleShape,
                color = alertGroupColor(group.state),
                content = {},
            )
            Text(
                group.monitorName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateUtils.getRelativeTimeSpanString(
                    group.updatedAtMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(group.state.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = alertGroupColor(group.state),
        )
        group.events.takeLast(3).forEach { event ->
            val label = stringResource(event.kind.labelRes)
            Text(
                buildString {
                    append(label)
                    event.detail?.takeIf(String::isNotBlank)?.let { append(" · "); append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            pluralStringResource(R.plurals.incident_alert_event_count, group.events.size, group.events.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LocalAlertGroupState.labelRes: Int
    get() = when (this) {
        LocalAlertGroupState.ACTIVE -> R.string.incident_alert_active
        LocalAlertGroupState.DELAYED -> R.string.incident_alert_delayed
        LocalAlertGroupState.SNOOZED -> R.string.incident_alert_snoozed
        LocalAlertGroupState.ACKNOWLEDGED -> R.string.incident_alert_acknowledged
        LocalAlertGroupState.SUPPRESSED -> R.string.incident_alert_suppressed
        LocalAlertGroupState.RECOVERED -> R.string.incident_alert_recovered
    }

@Composable
private fun alertGroupColor(state: LocalAlertGroupState): Color = when (state) {
        LocalAlertGroupState.ACTIVE -> KumaRed
        LocalAlertGroupState.DELAYED, LocalAlertGroupState.SNOOZED, LocalAlertGroupState.SUPPRESSED -> KumaOrange
        LocalAlertGroupState.ACKNOWLEDGED, LocalAlertGroupState.RECOVERED -> KumaGreen
}

private val LocalEventKind.labelRes: Int
    get() = when (this) {
        LocalEventKind.PUSH_RECEIVED -> R.string.event_push_received
        LocalEventKind.PUSH_DELAYED -> R.string.event_push_delayed
        LocalEventKind.PUSH_ALERT -> R.string.event_push_alert
        LocalEventKind.PUSH_REPEATED -> R.string.event_push_repeated
        LocalEventKind.PUSH_ACKNOWLEDGED -> R.string.event_push_acknowledged
        LocalEventKind.PUSH_SNOOZED -> R.string.event_push_snoozed
        LocalEventKind.PUSH_SUPPRESSED -> R.string.event_push_suppressed
        LocalEventKind.PUSH_CORRELATED -> R.string.event_push_correlated
        LocalEventKind.PUSH_RECOVERED -> R.string.event_push_recovered
        else -> R.string.event_push_alert
    }
