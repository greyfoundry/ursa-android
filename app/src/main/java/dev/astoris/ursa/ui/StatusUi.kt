package dev.astoris.ursa.ui

import androidx.compose.ui.graphics.Color
import dev.astoris.ursa.data.model.MonitorStatus

object StatusUi {
    fun color(status: MonitorStatus): Color = when (status) {
        MonitorStatus.UP -> Color(0xFF2E7D32)
        MonitorStatus.DOWN -> Color(0xFFC62828)
        MonitorStatus.PENDING -> Color(0xFFF9A825)
        MonitorStatus.MAINTENANCE -> Color(0xFF1565C0)
    }

    fun label(status: MonitorStatus): String = when (status) {
        MonitorStatus.UP -> "Up"
        MonitorStatus.DOWN -> "Down"
        MonitorStatus.PENDING -> "Pending"
        MonitorStatus.MAINTENANCE -> "Maintenance"
    }
}
