package dev.astoris.ursa.ui.monitors

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.core.storage.CertExpiryUtil
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.theme.KumaGreen
import dev.astoris.ursa.ui.theme.KumaOrange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class CertificateHealth { EXPIRED, INVALID, EXPIRING, HEALTHY, UNKNOWN }

internal enum class CertificateSort { EXPIRY, NAME }

internal data class CertificateEntry(
    val monitorId: Int,
    val monitorName: String,
    val health: CertificateHealth,
    val validToMillis: Long?,
    val daysUntilExpiry: Long?,
    val issuer: String?,
)

internal fun certificateEntries(
    monitors: List<Monitor>,
    certs: Map<Int, CertInfo>,
    nowMillis: Long,
    sort: CertificateSort = CertificateSort.EXPIRY,
): List<CertificateEntry> {
    val entries = monitors.mapNotNull { monitor ->
        val cert = certs[monitor.id] ?: return@mapNotNull null
        val validToMillis = CertExpiryUtil.resolveValidToMillis(cert.validTo, cert.daysRemaining, nowMillis)
        val days = validToMillis?.let { CertExpiryUtil.daysUntil(it, nowMillis) }
        val health = when {
            days != null && days < 0 -> CertificateHealth.EXPIRED
            !cert.valid -> CertificateHealth.INVALID
            days != null && days <= CertExpiryUtil.DEFAULT_THRESHOLD_DAYS -> CertificateHealth.EXPIRING
            days != null -> CertificateHealth.HEALTHY
            else -> CertificateHealth.UNKNOWN
        }
        CertificateEntry(
            monitorId = monitor.id,
            monitorName = monitor.name,
            health = health,
            validToMillis = validToMillis,
            daysUntilExpiry = days,
            issuer = cert.issuer,
        )
    }
    val healthThenName = compareBy<CertificateEntry> { it.health.ordinal }
        .thenBy { it.monitorName.lowercase() }
    val healthThenExpiry = compareBy<CertificateEntry> { it.health.ordinal }
        .thenBy(nullsLast<Long>()) { it.validToMillis }
        .thenBy { it.monitorName.lowercase() }
    return entries.sortedWith(if (sort == CertificateSort.EXPIRY) healthThenExpiry else healthThenName)
}

@Composable
internal fun CertificateDashboard(
    monitors: List<Monitor>,
    certs: Map<Int, CertInfo>,
    onClose: () -> Unit,
    onCertificateClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    var sort by remember { mutableStateOf(CertificateSort.EXPIRY) }
    val listState = rememberLazyListState()
    val nowMillis = remember { System.currentTimeMillis() }
    val entries = remember(monitors, certs, nowMillis, sort) {
        certificateEntries(monitors, certs, nowMillis, sort)
    }
    LaunchedEffect(sort) { listState.scrollToItem(0) }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.certificate_dashboard_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.incident_center_close)) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CertificateSort.entries.forEach { option ->
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
                                if (option == CertificateSort.EXPIRY) R.string.certificate_sort_expiry
                                else R.string.certificate_sort_name,
                            ),
                        )
                    },
                )
            }
        }
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.certificate_dashboard_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.certificate_dashboard_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CertificateHealth.entries.forEach { health ->
                    val group = entries.filter { it.health == health }
                    if (group.isNotEmpty()) {
                        item(key = "header-$health") {
                            Text(
                                pluralStringResource(
                                    certificateGroupPlural(health),
                                    group.size,
                                    group.size,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = certificateHealthColor(health),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(group, key = { it.monitorId }) { entry ->
                            CertificateRow(entry, onClick = { onCertificateClick(entry.monitorId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificateRow(entry: CertificateEntry, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val expiryDate = entry.validToMillis?.let {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(it))
    }
    UrsaPressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = certificateHealthColor(entry.health),
                content = {},
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.monitorName, style = MaterialTheme.typography.titleSmall)
                entry.issuer?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        stringResource(R.string.certificate_issuer, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                expiryDate?.let {
                    Text(
                        stringResource(R.string.certificate_expires_on, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                certificateExpiryLabel(entry),
                style = MaterialTheme.typography.labelLarge,
                color = certificateHealthColor(entry.health),
            )
        }
    }
}

@Composable
private fun certificateExpiryLabel(entry: CertificateEntry): String = when {
    entry.health == CertificateHealth.INVALID -> stringResource(R.string.cert_invalid)
    entry.daysUntilExpiry == null -> stringResource(R.string.reliability_unavailable)
    entry.daysUntilExpiry < 0 -> (-entry.daysUntilExpiry)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
        .let { days -> pluralStringResource(R.plurals.certificate_expired_days, days, days) }
    entry.daysUntilExpiry == 0L -> stringResource(R.string.certificate_expires_today)
    else -> pluralStringResource(
        R.plurals.certificate_days_left,
        entry.daysUntilExpiry.toInt(),
        entry.daysUntilExpiry.toInt(),
    )
}

@Composable
private fun certificateHealthColor(health: CertificateHealth) = when (health) {
    CertificateHealth.EXPIRED, CertificateHealth.INVALID -> MaterialTheme.colorScheme.error
    CertificateHealth.EXPIRING -> KumaOrange
    CertificateHealth.HEALTHY -> KumaGreen
    CertificateHealth.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun certificateGroupPlural(health: CertificateHealth) = when (health) {
    CertificateHealth.EXPIRED -> R.plurals.certificate_group_expired
    CertificateHealth.INVALID -> R.plurals.certificate_group_invalid
    CertificateHealth.EXPIRING -> R.plurals.certificate_group_expiring
    CertificateHealth.HEALTHY -> R.plurals.certificate_group_healthy
    CertificateHealth.UNKNOWN -> R.plurals.certificate_group_unknown
}
