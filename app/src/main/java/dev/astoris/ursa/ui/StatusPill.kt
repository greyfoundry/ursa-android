package dev.astoris.ursa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.theme.White

/**
 * A status badge matching Uptime Kuma's `badge rounded-pill bg-{color}`: a fully
 * rounded, status-colored pill with white text and a 64dp minimum width.
 */
@Composable
fun StatusPill(status: MonitorStatus, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 64.dp)
            .clip(CircleShape)
            .background(StatusUi.color(status))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(StatusUi.labelRes(status)),
            color = White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
