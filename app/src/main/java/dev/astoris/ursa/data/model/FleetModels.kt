package dev.astoris.ursa.data.model

enum class FleetServerAvailability {
    AVAILABLE,
    NO_CACHE,
    UNAVAILABLE,
}

enum class FleetSnapshotSource {
    CACHE,
    REFRESHED,
    LIVE,
}

enum class FleetFreshness {
    LIVE,
    RECENT,
    STALE,
}

enum class FleetServerError {
    CACHE_STORE_UNAVAILABLE,
    CACHE_DECRYPTION_FAILED,
    INVALID_CACHE,
}

enum class FleetRefreshError {
    MISSING_SESSION,
    CLEARTEXT_BLOCKED,
    DEVICE_OFFLINE,
    SERVER_UNREACHABLE,
    AUTHENTICATION_FAILED,
    CERTIFICATE,
    INCOMPATIBLE_RESPONSE,
    TIMED_OUT,
    CACHE_WRITE_FAILED,
    UNKNOWN,
}

data class FleetMonitorCounts(
    val total: Int,
    val active: Int,
    val up: Int,
    val down: Int,
    val pending: Int,
    val maintenance: Int,
    val paused: Int,
) {
    init {
        require(listOf(total, active, up, down, pending, maintenance, paused).all { it >= 0 })
        require(active == up + down + pending + maintenance)
        require(total == active + paused)
    }

    operator fun plus(other: FleetMonitorCounts) = FleetMonitorCounts(
        total = total + other.total,
        active = active + other.active,
        up = up + other.up,
        down = down + other.down,
        pending = pending + other.pending,
        maintenance = maintenance + other.maintenance,
        paused = paused + other.paused,
    )

    companion object {
        val EMPTY = FleetMonitorCounts(0, 0, 0, 0, 0, 0, 0)

        fun from(monitors: List<Monitor>): FleetMonitorCounts {
            var up = 0
            var down = 0
            var pending = 0
            var maintenance = 0
            var paused = 0
            monitors.forEach { monitor ->
                if (!monitor.active) {
                    paused += 1
                } else {
                    when (monitor.status) {
                        MonitorStatus.UP -> up += 1
                        MonitorStatus.DOWN -> down += 1
                        MonitorStatus.PENDING -> pending += 1
                        MonitorStatus.MAINTENANCE -> maintenance += 1
                    }
                }
            }
            val active = up + down + pending + maintenance
            return FleetMonitorCounts(
                total = monitors.size,
                active = active,
                up = up,
                down = down,
                pending = pending,
                maintenance = maintenance,
                paused = paused,
            )
        }
    }
}

data class FleetServerSnapshot(
    val serverUrl: String,
    val displayName: String,
    val isActiveServer: Boolean,
    val availability: FleetServerAvailability,
    val source: FleetSnapshotSource? = null,
    val freshness: FleetFreshness? = null,
    val capturedAtMillis: Long? = null,
    val ageMillis: Long? = null,
    val counts: FleetMonitorCounts? = null,
    val error: FleetServerError? = null,
    val refreshAttemptedAtMillis: Long? = null,
    val refreshError: FleetRefreshError? = null,
) {
    init {
        val available = availability == FleetServerAvailability.AVAILABLE
        require(available == (source != null))
        require(available == (freshness != null))
        require(available == (capturedAtMillis != null))
        require(available == (ageMillis != null))
        require(available == (counts != null))
        require((availability == FleetServerAvailability.UNAVAILABLE) == (error != null))
        require(refreshError == null || refreshAttemptedAtMillis != null)
    }
}

data class FleetSnapshot(
    val servers: List<FleetServerSnapshot>,
    val counts: FleetMonitorCounts?,
    val loadedAtMillis: Long,
    val isLocked: Boolean = false,
    val hiddenServerCount: Int = 0,
) {
    init {
        require(hiddenServerCount >= 0)
        require(!isLocked || servers.isEmpty())
        require(!isLocked || counts == null)
    }

    val totalServerCount: Int get() = servers.size + hiddenServerCount
    val availableServerCount: Int
        get() = servers.count { it.availability == FleetServerAvailability.AVAILABLE }
    val unavailableServerCount: Int get() = servers.size - availableServerCount
    val affectedServerCount: Int get() = servers.count { (it.counts?.down ?: 0) > 0 }
    val hasPartialData: Boolean get() = availableServerCount > 0 && unavailableServerCount > 0
}
