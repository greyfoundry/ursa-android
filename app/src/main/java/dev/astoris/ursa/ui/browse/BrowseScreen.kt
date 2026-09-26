package dev.astoris.ursa.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.SectionHeading
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.monitors.CertificateDashboard
import dev.astoris.ursa.ui.monitors.DomainDashboard
import dev.astoris.ursa.ui.monitors.FleetAggregateDashboard
import dev.astoris.ursa.ui.monitors.GlobalEventLog
import dev.astoris.ursa.ui.monitors.MaintenanceScreen
import dev.astoris.ursa.ui.monitors.PinnedLivePanel
import dev.astoris.ursa.ui.push.PushScreen
import dev.astoris.ursa.ui.settings.SettingsScreen

private enum class BrowseTool {
    MAINTENANCE,
    NOTIFICATIONS,
    CERTIFICATES,
    DOMAINS,
    EVENTS,
    FLEET,
    LIVE,
    SETTINGS,
}

@Composable
fun BrowseScreen(
    vm: UrsaViewModel,
    modifier: Modifier = Modifier,
) {
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val history by vm.beatHistory.collectAsStateWithLifecycle()
    val certs by vm.certs.collectAsStateWithLifecycle()
    val localEvents by vm.localEvents.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationsFromSettings by rememberSaveable { mutableStateOf(false) }
    val selected = BrowseTool.entries.firstOrNull { it.name == selectedName }
    val close = {
        selectedName = if (selected == BrowseTool.NOTIFICATIONS && notificationsFromSettings) {
            BrowseTool.SETTINGS.name
        } else {
            null
        }
        notificationsFromSettings = false
    }
    val openMonitor: (Int) -> Unit = { id ->
        selectedName = null
        notificationsFromSettings = false
        vm.openMonitor(id)
    }

    when (selected) {
        null -> BrowseIndex(
            onTool = {
                notificationsFromSettings = false
                selectedName = it.name
            },
            onStatusPages = vm::enterStatusPage,
            onMonitors = { vm.openMonitors() },
            onServers = vm::enterConnectionManager,
            modifier = modifier,
        )
        BrowseTool.NOTIFICATIONS -> {
            BackHandler(onBack = close)
            PushScreen(vm, modifier)
        }
        BrowseTool.SETTINGS -> {
            BackHandler(onBack = close)
            SettingsScreen(
                vm = vm,
                modifier = modifier,
                onNotificationsClick = {
                    notificationsFromSettings = true
                    selectedName = BrowseTool.NOTIFICATIONS.name
                },
            )
        }
        else -> Box(
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        ) {
            when (selected) {
                BrowseTool.MAINTENANCE -> MaintenanceScreen(vm, close, Modifier.fillMaxSize())
                BrowseTool.CERTIFICATES -> CertificateDashboard(
                    monitors, certs, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.DOMAINS -> DomainDashboard(
                    monitors, certs, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.EVENTS -> GlobalEventLog(
                    monitors, history, localEvents, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.FLEET -> FleetAggregateDashboard(
                    monitors, vm::fleetChartData, close, openMonitor, Modifier.fillMaxSize(),
                )
                BrowseTool.LIVE -> PinnedLivePanel(
                    monitors = monitors,
                    history = history,
                    pinnedIds = favorites,
                    onClose = close,
                    onMonitorClick = openMonitor,
                    onUnpin = vm::toggleFavorite,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun BrowseIndex(
    onTool: (BrowseTool) -> Unit,
    onStatusPages: () -> Unit,
    onMonitors: () -> Unit,
    onServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.browse_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.browse_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionHeading(stringResource(R.string.browse_operations)) }
            item {
                BrowseItem(
                    stringResource(R.string.status_maintenance),
                    stringResource(R.string.browse_maintenance_desc),
                ) { onTool(BrowseTool.MAINTENANCE) }
            }
            item {
                BrowseItem(
                    stringResource(R.string.statuspage_title),
                    stringResource(R.string.settings_status_pages_desc),
                    onStatusPages,
                )
            }
            item {
                BrowseItem(
                    stringResource(R.string.nav_notifications),
                    stringResource(R.string.settings_notifications_desc),
                ) { onTool(BrowseTool.NOTIFICATIONS) }
            }
            item { SectionHeading(stringResource(R.string.browse_insights)) }
            item {
                BrowseItem(
                    stringResource(R.string.certificate_dashboard_title),
                    stringResource(R.string.browse_certificates_desc),
                ) { onTool(BrowseTool.CERTIFICATES) }
            }
            item {
                BrowseItem(
                    stringResource(R.string.domain_dashboard_title),
                    stringResource(R.string.browse_domains_desc),
                ) { onTool(BrowseTool.DOMAINS) }
            }
            item {
                BrowseItem(
                    stringResource(R.string.event_log_title),
                    stringResource(R.string.browse_events_desc),
                ) { onTool(BrowseTool.EVENTS) }
            }
            item {
                BrowseItem(
                    stringResource(R.string.fleet_aggregate_title),
                    stringResource(R.string.browse_fleet_desc),
                ) { onTool(BrowseTool.FLEET) }
            }
            item {
                BrowseItem(
                    stringResource(R.string.pinned_live_title),
                    stringResource(R.string.browse_live_desc),
                ) { onTool(BrowseTool.LIVE) }
            }
            item { SectionHeading(stringResource(R.string.browse_manage)) }
            item {
                BrowseItem(
                    stringResource(R.string.browse_monitors_title),
                    stringResource(R.string.browse_monitors_desc),
                    onMonitors,
                )
            }
            item {
                BrowseItem(
                    stringResource(R.string.servers_title),
                    stringResource(R.string.browse_servers_desc),
                    onServers,
                )
            }
            item {
                BrowseItem(
                    stringResource(R.string.browse_settings_title),
                    stringResource(R.string.browse_settings_desc),
                ) { onTool(BrowseTool.SETTINGS) }
            }
        }
    }
}

@Composable
private fun BrowseItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    UrsaPressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
