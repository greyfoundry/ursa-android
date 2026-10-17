package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.MonitorStatus
import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SlaMissingPeriod(
    val startMillis: Long,
    val endMillis: Long,
)

data class HeartbeatSlaSummary(
    val requestedDurationMillis: Long,
    val nominalIntervalMillis: Long?,
    val upMillis: Long,
    val downMillis: Long,
    val maintenanceMillis: Long,
    val pendingMillis: Long,
    val missingMillis: Long,
    val missingPeriods: List<SlaMissingPeriod>,
) {
    val eligibleMillis: Long get() = upMillis + downMillis
    val observedMillis: Long get() = requestedDurationMillis - missingMillis
    val uptimeFraction: Double? get() = eligibleMillis.takeIf { it > 0L }
        ?.let { upMillis.toDouble() / it.toDouble() }
    val coverageFraction: Double get() = if (requestedDurationMillis > 0L) {
        observedMillis.toDouble() / requestedDurationMillis.toDouble()
    } else {
        0.0
    }
}

data class HeartbeatSlaReportData(
    val serverName: String,
    val serverScope: String,
    val monitorId: Int,
    val monitorName: String,
    val capturedAtMillis: Long,
    val history: HeartbeatHistoryResult,
)

object HeartbeatSlaReport {
    const val CALCULATION_METHOD =
        "Time-weighted observed intervals; maintenance and pending excluded; missing periods never count as uptime."

    private const val MAX_FIELD_CHARS = 512
    private const val TIMELY_CHECK_MULTIPLIER = 2L
    private val unsafeControl = Regex("[\\p{Cc}\\p{Cf}]")
    private val repeatedWhitespace = Regex("\\s+")
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun calculate(history: HeartbeatHistoryResult): HeartbeatSlaSummary {
        val range = history.range
        val samples = history.beats.mapNotNull { beat ->
            kumaHeartbeatUtcMillisOrNull(beat.time)
                ?.takeIf { it in range.startMillis..range.endMillis }
                ?.let { timestamp -> timestamp to beat }
        }
            .distinctBy { it.first }
            .sortedBy { it.first }
        val deltas = samples.zipWithNext { first, second -> second.first - first.first }
            .filter { it > 0L }
            .sorted()
        val nominal = deltas.takeIf { it.isNotEmpty() }?.let { it[(it.size - 1) / 2] }
        if (nominal == null) {
            return HeartbeatSlaSummary(
                requestedDurationMillis = range.endMillis - range.startMillis,
                nominalIntervalMillis = null,
                upMillis = 0L,
                downMillis = 0L,
                maintenanceMillis = 0L,
                pendingMillis = 0L,
                missingMillis = range.endMillis - range.startMillis,
                missingPeriods = listOf(SlaMissingPeriod(range.startMillis, range.endMillis)),
            )
        }

        var cursor = range.startMillis
        var up = 0L
        var down = 0L
        var maintenance = 0L
        var pending = 0L
        val missing = mutableListOf<SlaMissingPeriod>()
        samples.forEachIndexed { index, (timestamp, beat) ->
            if (timestamp >= range.endMillis) return@forEachIndexed
            if (cursor < timestamp) missing += SlaMissingPeriod(cursor, timestamp)
            val nextTimestamp = samples.getOrNull(index + 1)?.first
            val timelyNext = nextTimestamp?.takeIf { it - timestamp <= nominal * TIMELY_CHECK_MULTIPLIER }
            val segmentEnd = minOf(
                range.endMillis,
                timelyNext ?: (timestamp + nominal),
            )
            val segmentStart = maxOf(timestamp, cursor, range.startMillis)
            val duration = (segmentEnd - segmentStart).coerceAtLeast(0L)
            when (beat.status) {
                MonitorStatus.UP -> up += duration
                MonitorStatus.DOWN -> down += duration
                MonitorStatus.MAINTENANCE -> maintenance += duration
                MonitorStatus.PENDING -> pending += duration
            }
            cursor = maxOf(cursor, segmentEnd)
        }
        if (cursor < range.endMillis) missing += SlaMissingPeriod(cursor, range.endMillis)
        val mergedMissing = missing.filter { it.endMillis > it.startMillis }
            .fold(mutableListOf<SlaMissingPeriod>()) { result, item ->
            val previous = result.lastOrNull()
            if (previous != null && item.startMillis <= previous.endMillis) {
                result[result.lastIndex] = previous.copy(endMillis = maxOf(previous.endMillis, item.endMillis))
            } else {
                result += item
            }
                result
            }
        val requested = range.endMillis - range.startMillis
        val missingMillis = (requested - up - down - maintenance - pending).coerceAtLeast(0L)
        return HeartbeatSlaSummary(
            requestedDurationMillis = requested,
            nominalIntervalMillis = nominal,
            upMillis = up,
            downMillis = down,
            maintenanceMillis = maintenance,
            pendingMillis = pending,
            missingMillis = missingMillis,
            missingPeriods = mergedMissing,
        )
    }

    fun fileName(format: HeartbeatHistoryExportFormat, monitorId: Int, capturedAtMillis: Long): String =
        "ursa-sla-report-$monitorId-${fileTimestamp.format(Instant.ofEpochMilli(capturedAtMillis))}.${format.extension}"

    fun write(
        writer: Writer,
        format: HeartbeatHistoryExportFormat,
        data: HeartbeatSlaReportData,
    ): HeartbeatSlaSummary {
        val summary = calculate(data.history)
        when (format) {
            HeartbeatHistoryExportFormat.CSV -> writeCsv(writer, data, summary)
            HeartbeatHistoryExportFormat.HTML -> writeHtml(writer, data, summary)
        }
        return summary
    }

    private fun writeCsv(writer: Writer, data: HeartbeatSlaReportData, summary: HeartbeatSlaSummary) {
        writer.appendLine(COLUMNS.joinToString(","))
        val periods = summary.missingPeriods.map<SlaMissingPeriod, SlaMissingPeriod?> { it }
            .ifEmpty { listOf(null) }
        periods.forEach { period ->
            writer.appendLine(values(data, summary, period).joinToString(",", transform = ::csvCell))
        }
    }

    private fun writeHtml(writer: Writer, data: HeartbeatSlaReportData, summary: HeartbeatSlaSummary) {
        writer.appendLine("<!doctype html>")
        writer.appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\">")
        writer.appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        writer.appendLine("<title>URSA SLA report</title>")
        writer.appendLine("<style>body{font-family:system-ui,sans-serif;max-width:72rem;margin:auto;padding:1rem}table{border-collapse:collapse;width:100%;margin:1rem 0}caption{text-align:left;margin:.5rem 0}th,td{border:1px solid #888;padding:.4rem;text-align:left;vertical-align:top}thead{background:#eee}@media(prefers-color-scheme:dark){body{background:#111;color:#eee}thead{background:#292929}th,td{border-color:#666}}</style>")
        writer.appendLine("</head><body><main><h1>SLA report</h1><dl>")
        metadata(data, summary).forEach { (label, value) ->
            writer.appendLine("<dt>${htmlText(label)}</dt><dd>${htmlText(value)}</dd>")
        }
        writer.appendLine("</dl>")
        writer.appendLine("<table><caption>Coverage-aware duration totals</caption><thead><tr><th scope=\"col\">Category</th><th scope=\"col\">Duration seconds</th><th scope=\"col\">Included in SLA</th></tr></thead><tbody>")
        listOf(
            Triple("Up", summary.upMillis, "Yes"),
            Triple("Down", summary.downMillis, "Yes"),
            Triple("Maintenance", summary.maintenanceMillis, "No"),
            Triple("Pending", summary.pendingMillis, "No"),
            Triple("Missing", summary.missingMillis, "No"),
        ).forEach { (label, millis, included) ->
            writer.appendLine("<tr><td>$label</td><td>${millis / 1_000L}</td><td>$included</td></tr>")
        }
        writer.appendLine("</tbody></table>")
        writer.appendLine("<table><caption>Missing periods excluded from SLA</caption><thead><tr><th scope=\"col\">Start UTC</th><th scope=\"col\">End UTC</th></tr></thead><tbody>")
        summary.missingPeriods.forEach { period ->
            writer.appendLine("<tr><td>${instant(period.startMillis)}</td><td>${instant(period.endMillis)}</td></tr>")
        }
        writer.appendLine("</tbody></table></main></body></html>")
    }

    private fun metadata(
        data: HeartbeatSlaReportData,
        summary: HeartbeatSlaSummary,
    ): List<Pair<String, String>> = listOf(
        "Server" to credentialFreeServerLabel(data.serverName, data.serverScope),
        "Server scope" to data.serverScope,
        "Monitor ID" to data.monitorId.toString(),
        "Monitor" to data.monitorName,
        "Captured UTC" to instant(data.capturedAtMillis),
        "Requested start UTC" to instant(data.history.range.startMillis),
        "Requested end UTC" to instant(data.history.range.endMillis),
        "Observed start UTC" to data.history.observedStartMillis?.let(::instant).orEmpty(),
        "Observed end UTC" to data.history.observedEndMillis?.let(::instant).orEmpty(),
        "Timezone" to "UTC",
        "Source rows" to data.history.beats.size.toString(),
        "Source truncated" to data.history.truncated.toString(),
        "Malformed source rows excluded" to data.history.malformedRows.toString(),
        "Estimated nominal interval seconds" to summary.nominalIntervalMillis?.div(1_000L)?.toString().orEmpty(),
        "Coverage percent" to percent(summary.coverageFraction),
        "SLA percent" to summary.uptimeFraction?.let(::percent).orEmpty(),
        "Calculation method" to CALCULATION_METHOD,
    )

    private fun values(
        data: HeartbeatSlaReportData,
        summary: HeartbeatSlaSummary,
        missingPeriod: SlaMissingPeriod?,
    ): List<String> = listOf(
        credentialFreeServerLabel(data.serverName, data.serverScope),
        data.serverScope,
        data.monitorId.toString(),
        data.monitorName,
        instant(data.capturedAtMillis),
        instant(data.history.range.startMillis),
        instant(data.history.range.endMillis),
        data.history.observedStartMillis?.let(::instant).orEmpty(),
        data.history.observedEndMillis?.let(::instant).orEmpty(),
        "UTC",
        data.history.beats.size.toString(),
        data.history.truncated.toString(),
        data.history.malformedRows.toString(),
        summary.nominalIntervalMillis?.div(1_000L)?.toString().orEmpty(),
        (summary.upMillis / 1_000L).toString(),
        (summary.downMillis / 1_000L).toString(),
        (summary.maintenanceMillis / 1_000L).toString(),
        (summary.pendingMillis / 1_000L).toString(),
        (summary.missingMillis / 1_000L).toString(),
        percent(summary.coverageFraction),
        summary.uptimeFraction?.let(::percent).orEmpty(),
        CALCULATION_METHOD,
        missingPeriod?.startMillis?.let(::instant).orEmpty(),
        missingPeriod?.endMillis?.let(::instant).orEmpty(),
    ).map(::safeText)

    private fun percent(fraction: Double): String = String.format(Locale.ROOT, "%.3f", fraction * 100.0)
        .trimEnd('0')
        .trimEnd('.')

    private fun instant(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    private fun safeText(value: String): String = value
        .replace(unsafeControl, " ")
        .replace(repeatedWhitespace, " ")
        .trim()
        .take(MAX_FIELD_CHARS)

    private fun csvCell(value: String): String {
        val formulaSafe = if (value.firstOrNull() in CSV_FORMULA_PREFIXES) "'$value" else value
        return "\"${formulaSafe.replace("\"", "\"\"")}\""
    }

    private fun htmlText(value: String): String = safeText(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private val CSV_FORMULA_PREFIXES = setOf('=', '+', '-', '@')
    private val COLUMNS = listOf(
        "server_name", "server_scope", "monitor_id", "monitor_name", "captured_at_utc",
        "requested_start_utc", "requested_end_utc", "observed_start_utc", "observed_end_utc",
        "timezone", "source_rows", "source_truncated", "malformed_source_rows_excluded",
        "estimated_nominal_interval_seconds", "up_seconds", "down_seconds", "maintenance_seconds",
        "pending_seconds", "missing_seconds", "coverage_percent", "sla_percent",
        "calculation_method", "missing_period_start_utc", "missing_period_end_utc",
    )
}
