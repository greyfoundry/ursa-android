package dev.astoris.ursa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.MonitorStatus

/**
 * A leading status indicator for a monitor: a tinted circle in the status color with
 * an up / down / neutral arrow. Reads clearly in both light and dark themes.
 */
@Composable
fun StatusCircle(status: MonitorStatus, modifier: Modifier = Modifier) {
    val color = StatusUi.color(status)
    val iconRes = when (status) {
        MonitorStatus.UP -> R.drawable.ic_status_up
        MonitorStatus.DOWN -> R.drawable.ic_status_down
        else -> R.drawable.ic_status_pending
    }
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
    }
}
