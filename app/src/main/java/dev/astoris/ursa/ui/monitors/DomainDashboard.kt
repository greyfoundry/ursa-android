package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusCircle
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.theme.KumaGreen
import java.net.IDN
import java.net.URI

internal enum class DomainSort { ATTENTION, NAME }

internal data class DomainGroup(
    val key: String,
    val displayHost: String,
    val monitors: List<Monitor>,
    val upCount: Int,
    val downCount: Int,
    val pendingCount: Int,
    val maintenanceCount: Int,
    val pausedCount: Int,
    val tlsMonitorCount: Int,
    val certificateEntries: List<CertificateEntry>,
) {
    val activeCount: Int get() = monitors.size - pausedCount
    val attentionScore: Int
        get() = downCount * 10_000 + pendingCount * 1_000 + maintenanceCount * 100 + certificateAttention
    private val certificateAttention: Int
        get() = certificateEntries.minOfOrNull {
            when (it.health) {
                CertificateHealth.EXPIRED -> 0
                CertificateHealth.INVALID -> 1
                CertificateHealth.EXPIRING -> 2
                CertificateHealth.UNKNOWN -> 3
                CertificateHealth.HEALTHY -> 4
            }
        }?.let { 4 - it } ?: 0
}

private data class MonitorHost(
    val monitor: Monitor,
    val key: String,
    val displayHost: String,
    val tls: Boolean,
)

private val httpFamilyTypes = setOf("http", "keyword", "json-query", "real-browser", "websocket-upgrade")
private val httpFamilySchemes = setOf("http", "https", "ws", "wss")

internal fun domainGroups(
    monitors: List<Monitor>,
    certs: Map<Int, CertInfo>,
    nowMillis: Long,
    sort: DomainSort = DomainSort.ATTENTION,
): List<DomainGroup> {
    val parsed = monitors.mapNotNull(::monitorHost)
    val certificateByMonitor = certificateEntries(monitors, certs, nowMillis).associateBy { it.monitorId }
    val groups = parsed.groupBy { it.key }.map { (key, members) ->
        val groupedMonitors = members.map { it.monitor }
        val active = groupedMonitors.filter { it.active }
        DomainGroup(
            key = key,
            displayHost = members.first().displayHost,
            monitors = groupedMonitors.sortedBy { it.name.lowercase() },
            upCount = active.count { it.status == MonitorStatus.UP },
            downCount = active.count { it.status == MonitorStatus.DOWN },
            pendingCount = active.count { it.status == MonitorStatus.PENDING },
            maintenanceCount = active.count { it.status == MonitorStatus.MAINTENANCE },
            pausedCount = groupedMonitors.count { !it.active },
            tlsMonitorCount = members.count { it.tls },
            certificateEntries = members.filter { it.tls }.mapNotNull { certificateByMonitor[it.monitor.id] },
        )
    }
    return when (sort) {
        DomainSort.ATTENTION -> groups.sortedWith(
            compareByDescending<DomainGroup> { it.attentionScore }.thenBy { it.displayHost.lowercase() },
        )
        DomainSort.NAME -> groups.sortedBy { it.displayHost.lowercase() }
    }
}

private fun monitorHost(monitor: Monitor): MonitorHost? {
    if (monitor.type.lowercase() !in httpFamilyTypes) return null
    val uri = runCatching { URI(monitor.url?.trim().orEmpty()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme !in httpFamilySchemes) return null
    val authority = uri.rawAuthority?.substringAfterLast('@') ?: return null
    val rawHost = uri.host ?: when {
        authority.startsWith('[') -> authority.substringAfter('[').substringBefore(']')
        authority.count { it == ':' } == 1 -> authority.substringBeforeLast(':')
        else -> authority
    }
    val unwrappedHost = rawHost.removePrefix("[").removeSuffix("]")
    val ipv6 = unwrappedHost.contains(':')
    val asciiHost = if (ipv6) {
        unwrappedHost.lowercase()
    } else {
        runCatching { IDN.toASCII(unwrappedHost).lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return null
    }
    val displayHost = if (ipv6) asciiHost else runCatching { IDN.toUnicode(asciiHost) }.getOrDefault(asciiHost)
    val port = uri.port.takeIf { it >= 0 } ?: authority.substringAfterLast(':', "").toIntOrNull()
    val defaultPort = (scheme == "http" || scheme == "ws") && port == 80 ||
        (scheme == "https" || scheme == "wss") && port == 443
    val portSuffix = port?.takeUnless { defaultPort }?.let { ":$it" }.orEmpty()
    val display = if (ipv6) "[$displayHost]$portSuffix" else "$displayHost$portSuffix"
    return MonitorHost(
        monitor = monitor,
        key = "$asciiHost$portSuffix",
        displayHost = display,
        tls = scheme == "https" || scheme == "wss",
    )
}

@Composable
internal fun DomainDashboard(
    monitors: List<Monitor>,
    certs: Map<Int, CertInfo>,
    onClose: () -> Unit,
    onMonitorClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    var sort by remember { mutableStateOf(DomainSort.ATTENTION) }
    val listState = rememberLazyListState()
    val nowMillis = remember { System.currentTimeMillis() }
    val groups = remember(monitors, certs, nowMillis, sort) { domainGroups(monitors, certs, nowMillis, sort) }
    LaunchedEffect(sort) { listState.scrollToItem(0) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.domain_dashboard_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.incident_center_close)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DomainSort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { sort = option },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = KumaGreen.copy(alpha = 0.16f),
                        selectedLabelColor = KumaGreen,
                    ),
                    label = {
                        Text(
                            stringResource(
                                if (option == DomainSort.ATTENTION) R.string.domain_sort_attention
                                else R.string.certificate_sort_name,
                            ),
                        )
                    },
                )
            }
        }
        if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.domain_dashboard_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.domain_dashboard_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.key }) { group ->
                    DomainGroupCard(group, onMonitorClick)
                }
            }
        }
    }
}

@Composable
private fun DomainGroupCard(group: DomainGroup, onMonitorClick: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (group.activeCount > 0) {
                        StatusCircle(domainStatus(group))
                    } else {
                        Box(
                            Modifier.size(10.dp).background(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                CircleShape,
                            ),
                        )
                    }
                    Text(group.displayHost, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        pluralStringResource(R.plurals.domain_monitor_count, group.monitors.size, group.monitors.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    domainStatusSummary(group),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    domainCertificateSummary(group),
                    style = MaterialTheme.typography.bodySmall,
                    color = domainCertificateColor(group),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            group.monitors.forEach { monitor ->
                Surface(onClick = { onMonitorClick(monitor.id) }, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(monitor.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (monitor.active) stringResource(StatusUi.labelRes(monitor.status))
                            else stringResource(R.string.filter_paused),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (monitor.active) StatusUi.color(monitor.status)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun domainStatus(group: DomainGroup): MonitorStatus = when {
    group.downCount > 0 -> MonitorStatus.DOWN
    group.pendingCount > 0 -> MonitorStatus.PENDING
    group.maintenanceCount > 0 -> MonitorStatus.MAINTENANCE
    group.upCount > 0 -> MonitorStatus.UP
    else -> MonitorStatus.PENDING
}

@Composable
private fun domainStatusSummary(group: DomainGroup): String = listOfNotNull(
    group.upCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.fleet_up_count, it, it) },
    group.downCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.fleet_down_count, it, it) },
    group.pendingCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.fleet_pending_count, it, it) },
    group.maintenanceCount.takeIf { it > 0 }?.let {
        pluralStringResource(R.plurals.domain_maintenance_count, it, it)
    },
    group.pausedCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.fleet_paused_count, it, it) },
).joinToString(" • ").ifEmpty { stringResource(R.string.domain_no_active_checks) }

@Composable
private fun domainCertificateSummary(group: DomainGroup): String {
    if (group.tlsMonitorCount == 0) return stringResource(R.string.domain_no_tls)
    if (group.certificateEntries.isEmpty()) return stringResource(R.string.domain_tls_pending)
    val worst = group.certificateEntries.minBy { certificatePriority(it.health) }
    val health = stringResource(
        when (worst.health) {
            CertificateHealth.EXPIRED -> R.string.domain_tls_expired
            CertificateHealth.INVALID -> R.string.domain_tls_invalid
            CertificateHealth.EXPIRING -> R.string.domain_tls_expiring
            CertificateHealth.HEALTHY -> R.string.domain_tls_healthy
            CertificateHealth.UNKNOWN -> R.string.domain_tls_unknown
        },
    )
    val coverage = pluralStringResource(
        R.plurals.domain_tls_coverage,
        group.tlsMonitorCount,
        group.certificateEntries.size,
        group.tlsMonitorCount,
    )
    return stringResource(R.string.domain_tls_summary, health, coverage)
}

@Composable
private fun domainCertificateColor(group: DomainGroup) = group.certificateEntries
    .minByOrNull { certificatePriority(it.health) }
    ?.let { certificateHealthColorForDomain(it.health) }
    ?: MaterialTheme.colorScheme.onSurfaceVariant

private fun certificatePriority(health: CertificateHealth): Int = when (health) {
    CertificateHealth.EXPIRED -> 0
    CertificateHealth.INVALID -> 1
    CertificateHealth.EXPIRING -> 2
    CertificateHealth.UNKNOWN -> 3
    CertificateHealth.HEALTHY -> 4
}

@Composable
private fun certificateHealthColorForDomain(health: CertificateHealth) = when (health) {
    CertificateHealth.EXPIRED, CertificateHealth.INVALID -> MaterialTheme.colorScheme.error
    CertificateHealth.EXPIRING -> dev.astoris.ursa.ui.theme.KumaOrange
    CertificateHealth.HEALTHY -> KumaGreen
    CertificateHealth.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
