package dev.astoris.ursa.ui

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import java.util.Locale

object AdaptiveLayout {
    const val EXPANDED_MIN_WIDTH_DP = 840f

    fun isExpanded(widthDp: Float): Boolean = widthDp >= EXPANDED_MIN_WIDTH_DP
}

data class KioskSummary(
    val up: Int,
    val down: Int,
    val pending: Int,
    val maintenance: Int,
    val paused: Int,
    val overallStatus: MonitorStatus,
    val rows: List<Monitor>,
)

object KioskData {
    const val MAX_ROWS = 12

    fun summarize(monitors: List<Monitor>): KioskSummary {
        val active = monitors.filter(Monitor::active)
        val up = active.count { it.status == MonitorStatus.UP }
        val down = active.count { it.status == MonitorStatus.DOWN }
        val pending = active.count { it.status == MonitorStatus.PENDING }
        val maintenance = active.count { it.status == MonitorStatus.MAINTENANCE }
        val paused = monitors.size - active.size
        val overall = when {
            down > 0 -> MonitorStatus.DOWN
            pending > 0 || active.isEmpty() -> MonitorStatus.PENDING
            maintenance > 0 -> MonitorStatus.MAINTENANCE
            else -> MonitorStatus.UP
        }
        return KioskSummary(
            up = up,
            down = down,
            pending = pending,
            maintenance = maintenance,
            paused = paused,
            overallStatus = overall,
            rows = monitors.sortedWith(
                compareBy<Monitor>({ rowPriority(it) }, { it.name.lowercase(Locale.ROOT) }),
            ).take(MAX_ROWS),
        )
    }

    private fun rowPriority(monitor: Monitor): Int = when {
        !monitor.active -> 4
        monitor.status == MonitorStatus.DOWN -> 0
        monitor.status == MonitorStatus.PENDING -> 1
        monitor.status == MonitorStatus.MAINTENANCE -> 2
        else -> 3
    }
}
