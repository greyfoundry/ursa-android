package dev.astoris.ursa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import kotlin.math.roundToInt

internal fun normalizedUptimeOrNull(value: Double): Float? =
    value.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.toFloat()

internal fun uptimeDisplayPercentage(value: Double): Int? =
    normalizedUptimeOrNull(value)?.times(100)?.roundToInt()

/** Compact, static 24-hour uptime indicator for a scrolling monitor row. */
@Composable
fun UptimeRing(uptime24h: Double, color: Color, modifier: Modifier = Modifier) {
    val fraction = normalizedUptimeOrNull(uptime24h) ?: return
    val percentage = uptimeDisplayPercentage(uptime24h) ?: return
    val description = stringResource(R.string.uptime_24h_summary, percentage)
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier.clearAndSetSemantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(Modifier.size(24.dp)) {
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            drawArc(trackColor, -90f, 360f, false, style = stroke)
            if (fraction > 0f) drawArc(color, -90f, fraction * 360f, false, style = stroke)
        }
        Text(stringResource(R.string.uptime_percentage, percentage), style = MaterialTheme.typography.labelMedium)
    }
}
