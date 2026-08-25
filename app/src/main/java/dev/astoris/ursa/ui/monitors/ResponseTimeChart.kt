package dev.astoris.ursa.ui.monitors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.ui.theme.KumaGreen
import kotlin.math.roundToInt

internal data class ResponseTimeSample(val time: String, val milliseconds: Int)

internal data class ResponseTimeStats(
    val minimum: Int,
    val average: Int,
    val maximum: Int,
)

internal fun responseTimeSamples(beats: List<Heartbeat>): List<ResponseTimeSample> =
    beats.mapNotNull { beat ->
        beat.ping?.takeIf { it >= 0 }?.let { ResponseTimeSample(beat.time, it) }
    }

internal fun responseTimeStats(samples: List<ResponseTimeSample>): ResponseTimeStats? {
    if (samples.isEmpty()) return null
    return ResponseTimeStats(
        minimum = samples.minOf { it.milliseconds },
        average = samples.map { it.milliseconds }.average().roundToInt(),
        maximum = samples.maxOf { it.milliseconds },
    )
}

internal fun responseTimeSampleIndex(positionX: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    return ((positionX / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

@Composable
internal fun ResponseTimeChart(beats: List<Heartbeat>, modifier: Modifier = Modifier) {
    val samples = remember(beats) { responseTimeSamples(beats) }
    val stats = remember(samples) { responseTimeStats(samples) }
    if (stats == null) {
        Text(
            stringResource(R.string.detail_no_response_time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    var selectedIndex by remember(samples) { mutableIntStateOf(samples.lastIndex) }
    val selected = samples[selectedIndex]
    val summary = stringResource(
        R.string.response_time_summary,
        stats.minimum,
        stats.average,
        stats.maximum,
    )
    val sampleDescription = stringResource(
        R.string.response_time_sample,
        selected.milliseconds,
        selected.time.substringBeforeLast('.', selected.time),
    )
    val previousLabel = stringResource(R.string.response_time_previous)
    val nextLabel = stringResource(R.string.response_time_next)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fillColor = KumaGreen.copy(alpha = 0.12f)
    val pointCenterColor = MaterialTheme.colorScheme.surface

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(samples.size) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                selectedIndex = responseTimeSampleIndex(down.position.x, size.width.toFloat(), samples.size)
                                do {
                                    val change = awaitPointerEvent().changes.firstOrNull() ?: break
                                    if (change.pressed) {
                                        selectedIndex = responseTimeSampleIndex(
                                            change.position.x,
                                            size.width.toFloat(),
                                            samples.size,
                                        )
                                        change.consume()
                                    }
                                } while (change.pressed)
                            }
                        }
                        .semantics {
                            contentDescription = summary
                            stateDescription = sampleDescription
                            customActions = listOf(
                                CustomAccessibilityAction(previousLabel) {
                                    if (selectedIndex > 0) {
                                        selectedIndex -= 1
                                        true
                                    } else {
                                        false
                                    }
                                },
                                CustomAccessibilityAction(nextLabel) {
                                    if (selectedIndex < samples.lastIndex) {
                                        selectedIndex += 1
                                        true
                                    } else {
                                        false
                                    }
                                },
                            )
                        },
                ) {
                    repeat(3) { line ->
                        val y = size.height * line / 2f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    val minimum = stats.minimum.toFloat()
                    val range = (stats.maximum - stats.minimum).toFloat().coerceAtLeast(1f)
                    val stepX = if (samples.size == 1) 0f else size.width / (samples.size - 1)
                    val chartTop = 6.dp.toPx()
                    val chartBottom = size.height - chartTop
                    val chartHeight = chartBottom - chartTop
                    fun xFor(index: Int): Float = if (samples.size == 1) size.width / 2f else index * stepX
                    fun yFor(value: Int): Float = if (stats.maximum == stats.minimum) {
                        size.height / 2f
                    } else {
                        chartBottom - ((value - minimum) / range) * chartHeight
                    }
                    val linePath = Path()
                    samples.forEachIndexed { index, sample ->
                        val x = xFor(index)
                        val y = yFor(sample.milliseconds)
                        if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                    }
                    if (samples.size > 1) {
                        val areaPath = Path().apply {
                            addPath(linePath)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(areaPath, fillColor)
                        drawPath(linePath, KumaGreen, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    }
                    val selectedX = xFor(selectedIndex)
                    val selectedY = yFor(selected.milliseconds)
                    drawLine(gridColor, Offset(selectedX, 0f), Offset(selectedX, size.height), 1.dp.toPx())
                    drawCircle(KumaGreen, 5.dp.toPx(), Offset(selectedX, selectedY))
                    drawCircle(pointCenterColor, 2.dp.toPx(), Offset(selectedX, selectedY))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResponseTimeStat(stringResource(R.string.response_time_minimum), stats.minimum, Modifier.weight(1f))
            ResponseTimeStat(stringResource(R.string.response_time_average), stats.average, Modifier.weight(1f))
            ResponseTimeStat(stringResource(R.string.response_time_maximum), stats.maximum, Modifier.weight(1f))
        }
        Text(
            sampleDescription,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResponseTimeStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.response_time_milliseconds, value), style = MaterialTheme.typography.titleSmall)
    }
}
