package dev.astoris.ursa.ui.monitors

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorListScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val showingCache by vm.showingCache.collectAsStateWithLifecycle()
    val lastUpdated by vm.lastUpdated.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Monitors") },
                actions = {
                    TextButton(onClick = { vm.enterPush() }) { Text("Push") }
                    TextButton(onClick = { vm.enterSettings() }) { Text("Settings") }
                    TextButton(onClick = { vm.logout() }) { Text("Sign out") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state != ConnectionState.Authenticated) {
                Text(
                    text = "Connection: $state",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (showingCache) {
                val whenText = lastUpdated?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    )
                }
                Text(
                    text = whenText?.let { "Showing last-known data, updated $it" }
                        ?: "Showing last-known data",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (monitors.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No monitors yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(monitors, key = { it.id }) { monitor ->
                        MonitorRow(
                            monitor = monitor,
                            onClick = { vm.select(monitor.id) },
                            onPause = { vm.pause(monitor.id) },
                            onResume = { vm.resume(monitor.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorRow(monitor: Monitor, onClick: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(StatusUi.color(monitor.status)),
        )
        Column(Modifier.weight(1f)) {
            Text(monitor.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(StatusUi.label(monitor.status))
                monitor.ping?.let { append(" · ${it}ms") }
                monitor.uptime24h?.let { append(" · ${(it * 100).toInt()}% 24h") }
            }
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (monitor.active) TextButton(onClick = onPause) { Text("Pause") }
        else TextButton(onClick = onResume) { Text("Resume") }
    }
}
