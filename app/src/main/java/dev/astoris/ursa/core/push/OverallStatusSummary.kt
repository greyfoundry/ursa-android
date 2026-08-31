package dev.astoris.ursa.core.push

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus

enum class OverallFleetState { NO_DATA, HEALTHY, ATTENTION, PENDING, MAINTENANCE }

data class OverallStatusSummary(
    val total: Int,
    val up: Int,
    val down: Int,
    val pending: Int,
    val maintenance: Int,
    val state: OverallFleetState,
) {
    companion object {
        fun from(monitors: List<Monitor>): OverallStatusSummary {
            val active = monitors.filter(Monitor::active)
            val up = active.count { it.status == MonitorStatus.UP }
            val down = active.count { it.status == MonitorStatus.DOWN }
            val pending = active.count { it.status == MonitorStatus.PENDING }
            val maintenance = active.count { it.status == MonitorStatus.MAINTENANCE }
            val state = when {
                active.isEmpty() -> OverallFleetState.NO_DATA
                down > 0 -> OverallFleetState.ATTENTION
                pending > 0 -> OverallFleetState.PENDING
                maintenance > 0 -> OverallFleetState.MAINTENANCE
                else -> OverallFleetState.HEALTHY
            }
            return OverallStatusSummary(active.size, up, down, pending, maintenance, state)
        }
    }
}
