package dev.astoris.ursa.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.MonitorStatus

object StatusUi {
    fun color(status: MonitorStatus): Color = when (status) {
        MonitorStatus.UP -> Color(0xFF2E7D32)
        MonitorStatus.DOWN -> Color(0xFFC62828)
        MonitorStatus.PENDING -> Color(0xFFF9A825)
        MonitorStatus.MAINTENANCE -> Color(0xFF1565C0)
    }

    @StringRes
    fun labelRes(status: MonitorStatus): Int = when (status) {
        MonitorStatus.UP -> R.string.status_up
        MonitorStatus.DOWN -> R.string.status_down
        MonitorStatus.PENDING -> R.string.status_pending
        MonitorStatus.MAINTENANCE -> R.string.status_maintenance
    }
}
