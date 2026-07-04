package dev.astoris.ursa.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.data.model.Heartbeat

/**
 * A tiny response-time sparkline from a monitor's recent heartbeat pings, drawn in
 * the status color. Renders nothing when there are too few data points.
 */
@Composable
fun Sparkline(beats: List<Heartbeat>, color: Color, modifier: Modifier = Modifier) {
    val pings = beats.mapNotNull { it.ping }.takeLast(30)
    Canvas(modifier) {
        if (pings.size < 2) return@Canvas
        val max = pings.max().toFloat()
        val min = pings.min().toFloat()
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (pings.size - 1)
        val path = Path()
        pings.forEachIndexed { i, p ->
            val x = i * stepX
            val y = if (max == min) size.height / 2f
            else size.height - ((p - min) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
