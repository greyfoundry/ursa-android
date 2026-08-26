package dev.astoris.ursa.ui.monitors

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.components.UrsaPressableCard

internal data class PinnedLiveMonitor(
    val monitor: Monitor,
    val recentBeats: List<Heartbeat>,
) {
    val latestBeat: Heartbeat? get() = recentBeats.lastOrNull()
}

internal fun pinnedLiveMonitors(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    pinnedIds: Set<Int>,
    sampleCount: Int = PINNED_SAMPLE_COUNT,
): List<PinnedLiveMonitor> = monitors.asSequence()
    .filter { it.id in pinnedIds }
    .map { monitor ->
        PinnedLiveMonitor(monitor, history[monitor.id].orEmpty().takeLast(sampleCount.coerceAtLeast(1)))
    }
    .sortedWith(
        compareBy<PinnedLiveMonitor> { if (it.monitor.active) 0 else 1 }
            .thenBy { it.monitor.status.pinnedAttentionPriority }
            .thenBy { it.monitor.name.lowercase() },
    )
    .toList()

@Composable
internal fun PinnedLivePanel(
    monitors: List<Monitor>,
    history: Map<Int, List<Heartbeat>>,
    pinnedIds: Set<Int>,
    onClose: () -> Unit,
    onMonitorClick: (Int) -> Unit,
    onUnpin: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val pinned = remember(monitors, history, pinnedIds) {
        pinnedLiveMonitors(monitors, history, pinnedIds)
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.pinned_live_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.incident_center_close)) }
        }
        Text(
            stringResource(R.string.pinned_live_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        if (pinned.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.pinned_live_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.pinned_live_empty_desc),
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
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            pluralStringResource(R.plurals.pinned_live_count, pinned.size, pinned.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                items(pinned, key = { it.monitor.id }) { entry ->
                    PinnedLiveRow(
                        entry = entry,
                        onClick = { onMonitorClick(entry.monitor.id) },
                        onUnpin = { onUnpin(entry.monitor.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedLiveRow(
    entry: PinnedLiveMonitor,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
) {
    val monitor = entry.monitor
    val latest = entry.latestBeat
    UrsaPressableCard(onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(
                    modifier = Modifier.width(10.dp).height(10.dp),
                    shape = CircleShape,
                    color = if (monitor.active) StatusUi.color(monitor.status)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    content = {},
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        monitor.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            if (monitor.active) StatusUi.labelRes(monitor.status)
                            else R.string.filter_paused,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                latest?.ping?.takeIf { it >= 0 }?.let {
                    Text(
                        stringResource(R.string.response_time_milliseconds, it),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = onUnpin) { Text(stringResource(R.string.pinned_live_unpin)) }
            }
            CompactHeartbeatStrip(entry.recentBeats)
            Text(
                latest?.let { beat ->
                    kumaUtcMillisOrNull(beat.time)?.let { atMillis ->
                        DateUtils.getRelativeTimeSpanString(
                            atMillis,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ).toString()
                    } ?: stringResource(R.string.pinned_live_time_unavailable)
                } ?: stringResource(R.string.detail_no_history),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactHeartbeatStrip(beats: List<Heartbeat>) {
    if (beats.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        return
    }
    val up = beats.count { it.status == MonitorStatus.UP }
    val down = beats.count { it.status == MonitorStatus.DOWN }
    val other = beats.size - up - down
    val summary = stringResource(
        R.string.heartbeat_summary,
        pluralStringResource(R.plurals.heartbeat_up_count, up, up),
        pluralStringResource(R.plurals.heartbeat_down_count, down, down),
        pluralStringResource(R.plurals.heartbeat_other_count, other, other),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = summary },
    ) {
        beats.forEach { beat ->
            Box(
                Modifier.width(10.dp).height(16.dp).clip(RoundedCornerShape(3.dp))
                    .background(StatusUi.color(beat.status)),
            )
        }
    }
}

private val MonitorStatus.pinnedAttentionPriority: Int
    get() = when (this) {
        MonitorStatus.DOWN -> 0
        MonitorStatus.PENDING -> 1
        MonitorStatus.MAINTENANCE -> 2
        MonitorStatus.UP -> 3
    }

private const val PINNED_SAMPLE_COUNT = 16
