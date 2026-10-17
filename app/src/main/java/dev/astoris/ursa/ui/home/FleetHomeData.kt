package dev.astoris.ursa.ui.home

import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerSnapshot
import dev.astoris.ursa.data.model.FleetSnapshot

internal data class FleetHomeSummary(
    val totalServers: Int,
    val availableServers: Int,
    val unavailableServers: Int,
    val staleServers: Int,
    val affectedServers: Int,
    val urgentMonitors: Int,
    val maintenanceMonitors: Int,
    val totalMonitors: Int?,
    val lastRefreshAtMillis: Long?,
    val newestSnapshotAtMillis: Long?,
)

internal fun fleetHomeSummary(snapshot: FleetSnapshot): FleetHomeSummary {
    val visible = snapshot.servers
    return FleetHomeSummary(
        totalServers = snapshot.totalServerCount,
        availableServers = snapshot.availableServerCount,
        unavailableServers = snapshot.unavailableServerCount,
        staleServers = visible.count { it.freshness == FleetFreshness.STALE },
        affectedServers = visible.count(FleetServerSnapshot::needsAttention),
        urgentMonitors = visible.sumOf(FleetServerSnapshot::urgentMonitorCount),
        maintenanceMonitors = visible.sumOf { it.counts?.maintenance ?: 0 },
        totalMonitors = snapshot.counts?.total,
        lastRefreshAtMillis = visible.mapNotNull(FleetServerSnapshot::refreshAttemptedAtMillis).maxOrNull(),
        newestSnapshotAtMillis = visible.mapNotNull(FleetServerSnapshot::capturedAtMillis).maxOrNull(),
    )
}

internal fun FleetServerSnapshot.urgentMonitorCount(): Int =
    (counts?.down ?: 0) + (counts?.pending ?: 0)

internal fun FleetServerSnapshot.needsAttention(): Boolean =
    availability != FleetServerAvailability.AVAILABLE ||
        refreshError != null ||
        urgentMonitorCount() > 0 ||
        (counts?.maintenance ?: 0) > 0
