package dev.astoris.ursa.ui.monitors

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.ui.KioskData
import dev.astoris.ursa.ui.StatusPill
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.labelRes
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val showingCache by vm.showingCache.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()
    val connections by vm.connections.collectAsStateWithLifecycle()
    val activeUrl by vm.activeUrl.collectAsStateWithLifecycle()
    val activeConnection = connections.firstOrNull { it.url == activeUrl }
    val summary = remember(monitors) { KioskData.summarize(monitors) }
    val view = LocalView.current

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    BackHandler(onBack = vm::exitKioskMode)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            activeConnection?.displayName ?: stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.connection_status, stringResource(state.labelRes)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = vm::exitKioskMode) { Text(stringResource(R.string.kiosk_exit)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = StatusUi.color(summary.overallStatus).copy(alpha = 0.12f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPill(summary.overallStatus)
                        Text(
                            when {
                                summary.down > 0 -> pluralStringResource(
                                    R.plurals.kiosk_monitors_down,
                                    summary.down,
                                    summary.down,
                                )
                                summary.pending > 0 -> pluralStringResource(
                                    R.plurals.kiosk_monitors_pending,
                                    summary.pending,
                                    summary.pending,
                                )
                                summary.up + summary.maintenance == 0 -> stringResource(R.string.kiosk_no_active)
                                summary.maintenance > 0 -> stringResource(R.string.kiosk_maintenance_active)
                                else -> stringResource(R.string.kiosk_all_operational)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        if (showingCache && lastUpdated != null) {
                            Text(
                                stringResource(
                                    R.string.kiosk_cached_at,
                                    DateUtils.getRelativeTimeSpanString(lastUpdated!!),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { KioskMetrics(summary.up, summary.down, summary.pending, summary.maintenance, summary.paused) }
            item {
                Text(
                    stringResource(R.string.kiosk_monitors_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(summary.rows, key = Monitor::id) { monitor -> KioskMonitorRow(monitor) }
            if (monitors.size > summary.rows.size) {
                item {
                    Text(
                        pluralStringResource(
                            R.plurals.kiosk_showing_rows,
                            monitors.size,
                            summary.rows.size,
                            monitors.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun KioskMetrics(up: Int, down: Int, pending: Int, maintenance: Int, paused: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val values = listOf(
            R.string.kiosk_metric_up to up,
            R.string.kiosk_metric_down to down,
            R.string.kiosk_metric_pending to pending,
            R.string.kiosk_metric_maintenance to maintenance,
            R.string.kiosk_metric_paused to paused,
        )
        if (maxWidth >= 700.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                values.forEach { (label, value) -> KioskMetric(label, value, Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                values.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, value) -> KioskMetric(label, value, Modifier.weight(1f)) }
                        repeat(3 - row.size) { KioskMetric(null, 0, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KioskMetric(labelRes: Int?, value: Int, modifier: Modifier = Modifier) {
    if (labelRes == null) {
        Column(modifier) {}
        return
    }
    Card(modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun KioskMonitorRow(monitor: Monitor) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (monitor.active) {
                StatusPill(monitor.status)
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        stringResource(R.string.kiosk_metric_paused),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                monitor.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val metrics = buildString {
                monitor.ping?.let { append("${it}ms") }
                monitor.uptime24h?.let {
                    if (isNotEmpty()) append("  ")
                    append(String.format(Locale.US, "%.1f%%", it * 100.0))
                }
            }
            if (metrics.isNotEmpty()) {
                Text(
                    metrics,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
