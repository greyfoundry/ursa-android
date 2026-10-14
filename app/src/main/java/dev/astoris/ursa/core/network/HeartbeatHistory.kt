package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import kotlin.math.ceil

enum class HeartbeatHistoryRangeError {
    EMPTY,
    FUTURE,
    SPAN_TOO_LARGE,
    LOOKBACK_TOO_LARGE,
}

data class HeartbeatHistoryRange(
    val startMillis: Long,
    val endMillis: Long,
) {
    fun validationError(nowMillis: Long): HeartbeatHistoryRangeError? = when {
        startMillis >= endMillis -> HeartbeatHistoryRangeError.EMPTY
        endMillis > nowMillis + FUTURE_TOLERANCE_MILLIS -> HeartbeatHistoryRangeError.FUTURE
        startMillis < nowMillis - MAX_LOOKBACK_MILLIS -> HeartbeatHistoryRangeError.LOOKBACK_TOO_LARGE
        endMillis - startMillis > MAX_RANGE_MILLIS -> HeartbeatHistoryRangeError.SPAN_TOO_LARGE
        else -> null
    }

    fun requiredLookbackHours(nowMillis: Long): Int = ceil(
        (nowMillis - startMillis).coerceAtLeast(1L).toDouble() / HOUR_MILLIS,
    ).toInt().coerceIn(1, MAX_LOOKBACK_HOURS)

    companion object {
        const val MAX_LOOKBACK_HOURS = 24 * 30
        const val MAX_RESULT_ROWS = 10_000
        const val HOUR_MILLIS = 60L * 60L * 1_000L
        const val MAX_RANGE_MILLIS = MAX_LOOKBACK_HOURS * HOUR_MILLIS
        const val MAX_LOOKBACK_MILLIS = MAX_LOOKBACK_HOURS * HOUR_MILLIS
        private const val FUTURE_TOLERANCE_MILLIS = 60_000L

        fun recent(hours: Int, nowMillis: Long): HeartbeatHistoryRange {
            val boundedHours = hours.coerceIn(1, MAX_LOOKBACK_HOURS)
            return HeartbeatHistoryRange(
                startMillis = nowMillis - boundedHours * HOUR_MILLIS,
                endMillis = nowMillis,
            )
        }
    }
}

data class HeartbeatHistoryResult(
    val range: HeartbeatHistoryRange,
    val beats: List<Heartbeat>,
    val observedStartMillis: Long?,
    val observedEndMillis: Long?,
    val truncated: Boolean,
    val malformedRows: Int,
)

private val kumaHeartbeatUtcFormat: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd HH:mm:ss")
    .optionalStart()
    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
    .optionalEnd()
    .toFormatter()

fun kumaHeartbeatUtcMillisOrNull(value: String): Long? = runCatching {
    LocalDateTime.parse(value, kumaHeartbeatUtcFormat)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
}.getOrNull()

fun filterHeartbeatHistory(
    rows: List<Heartbeat>,
    range: HeartbeatHistoryRange,
    maxRows: Int = HeartbeatHistoryRange.MAX_RESULT_ROWS,
): HeartbeatHistoryResult {
    require(maxRows > 0)
    var malformedRows = 0
    val matching = rows.mapNotNull { beat ->
        val timestamp = kumaHeartbeatUtcMillisOrNull(beat.time)
        if (timestamp == null) {
            malformedRows += 1
            null
        } else {
            (timestamp to beat).takeIf { timestamp in range.startMillis..range.endMillis }
        }
    }
        .distinctBy { (timestamp, beat) ->
            listOf(timestamp, beat.monitorId, beat.status.code, beat.ping, beat.msg, beat.important)
        }
        .sortedBy { it.first }
    val truncated = matching.size > maxRows
    val kept = matching.takeLast(maxRows)
    return HeartbeatHistoryResult(
        range = range,
        beats = kept.map { it.second },
        observedStartMillis = kept.firstOrNull()?.first,
        observedEndMillis = kept.lastOrNull()?.first,
        truncated = truncated,
        malformedRows = malformedRows,
    )
}
