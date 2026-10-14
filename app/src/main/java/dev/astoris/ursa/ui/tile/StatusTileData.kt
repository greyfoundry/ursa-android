package dev.astoris.ursa.ui.tile

import dev.astoris.ursa.data.model.FleetFreshness
import dev.astoris.ursa.data.model.FleetServerAvailability
import dev.astoris.ursa.data.model.FleetServerSnapshot
import dev.astoris.ursa.data.model.FleetSnapshot

internal enum class StatusTileKind {
    LOCKED,
    NO_DATA,
    URGENT,
    OFFLINE,
    STALE,
    MAINTENANCE,
    CLEAR,
}

internal data class StatusTileData(
    val kind: StatusTileKind,
    val urgentMonitors: Int = 0,
    val affectedServers: Int = 0,
    val maintenanceMonitors: Int = 0,
    val staleServers: Int = 0,
    val unavailableServers: Int = 0,
    val targetServerUrl: String? = null,
)

internal fun statusTileData(snapshot: FleetSnapshot?): StatusTileData {
    if (snapshot == null) return StatusTileData(StatusTileKind.NO_DATA)
    if (snapshot.isLocked) return StatusTileData(StatusTileKind.LOCKED)
    if (snapshot.totalServerCount == 0 || snapshot.counts == null) {
        return StatusTileData(StatusTileKind.NO_DATA)
    }

    val urgent = snapshot.counts.down + snapshot.counts.pending
    val maintenance = snapshot.counts.maintenance
    val affected = snapshot.servers.count { it.urgentCount > 0 }
    val stale = snapshot.servers.count { it.freshness == FleetFreshness.STALE }
    val unavailable = snapshot.servers.count { it.availability != FleetServerAvailability.AVAILABLE }
    val target = snapshot.servers.firstOrNull { it.urgentCount > 0 }
        ?: snapshot.servers.firstOrNull { (it.counts?.maintenance ?: 0) > 0 }
    val kind = when {
        urgent > 0 -> StatusTileKind.URGENT
        unavailable > 0 -> StatusTileKind.OFFLINE
        stale > 0 -> StatusTileKind.STALE
        maintenance > 0 -> StatusTileKind.MAINTENANCE
        else -> StatusTileKind.CLEAR
    }
    return StatusTileData(
        kind = kind,
        urgentMonitors = urgent,
        affectedServers = affected,
        maintenanceMonitors = maintenance,
        staleServers = stale,
        unavailableServers = unavailable,
        targetServerUrl = target?.serverUrl,
    )
}

private val FleetServerSnapshot.urgentCount: Int
    get() = (counts?.down ?: 0) + (counts?.pending ?: 0)
