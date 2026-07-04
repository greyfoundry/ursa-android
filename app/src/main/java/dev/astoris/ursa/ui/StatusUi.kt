package dev.astoris.ursa.ui

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.theme.KumaBlue
import dev.astoris.ursa.ui.theme.KumaGreen
import dev.astoris.ursa.ui.theme.KumaOrange
import dev.astoris.ursa.ui.theme.KumaRed

object StatusUi {
    // Match Uptime Kuma's status colors exactly (vars.scss).
    fun color(status: MonitorStatus): Color = when (status) {
        MonitorStatus.UP -> KumaGreen
        MonitorStatus.DOWN -> KumaRed
        MonitorStatus.PENDING -> KumaOrange
        MonitorStatus.MAINTENANCE -> KumaBlue
    }

    @StringRes
    fun labelRes(status: MonitorStatus): Int = when (status) {
        MonitorStatus.UP -> R.string.status_up
        MonitorStatus.DOWN -> R.string.status_down
        MonitorStatus.PENDING -> R.string.status_pending
        MonitorStatus.MAINTENANCE -> R.string.status_maintenance
    }
}
