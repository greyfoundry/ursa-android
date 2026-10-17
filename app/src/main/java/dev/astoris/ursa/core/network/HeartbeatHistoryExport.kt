package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HeartbeatHistoryExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    HTML("html", "text/html"),
}

data class HeartbeatHistoryExportData(
    val serverName: String,
    val serverScope: String,
    val monitorId: Int,
    val monitorName: String,
    val capturedAtMillis: Long,
    val history: HeartbeatHistoryResult,
)

data class HeartbeatHistoryExportSummary(
    val rowCount: Int,
    val truncated: Boolean,
)

object HeartbeatHistoryExport {
    private const val MAX_FIELD_CHARS = 512
    private val unsafeControl = Regex("[\\p{Cc}\\p{Cf}]")
    private val repeatedWhitespace = Regex("\\s+")
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun fileName(format: HeartbeatHistoryExportFormat, monitorId: Int, capturedAtMillis: Long): String =
        "ursa-heartbeat-history-$monitorId-${fileTimestamp.format(Instant.ofEpochMilli(capturedAtMillis))}.${format.extension}"

    fun write(
        writer: Writer,
        format: HeartbeatHistoryExportFormat,
        data: HeartbeatHistoryExportData,
    ): HeartbeatHistoryExportSummary {
        when (format) {
            HeartbeatHistoryExportFormat.CSV -> writeCsv(writer, data)
            HeartbeatHistoryExportFormat.HTML -> writeHtml(writer, data)
        }
        return HeartbeatHistoryExportSummary(
            rowCount = data.history.beats.size,
            truncated = data.history.truncated,
        )
    }

    private fun writeCsv(writer: Writer, data: HeartbeatHistoryExportData) {
        writer.appendLine(COLUMNS.joinToString(","))
        data.history.beats.forEach { beat ->
            writer.appendLine(values(data, beat).joinToString(",", transform = ::csvCell))
        }
    }

    private fun writeHtml(writer: Writer, data: HeartbeatHistoryExportData) {
        val history = data.history
        writer.appendLine("<!doctype html>")
        writer.appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\">")
        writer.appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        writer.appendLine("<title>URSA heartbeat history</title>")
        writer.appendLine("<style>body{font-family:system-ui,sans-serif;max-width:96rem;margin:auto;padding:1rem}table{border-collapse:collapse;width:100%}caption{text-align:left;margin:.5rem 0}th,td{border:1px solid #888;padding:.4rem;text-align:left;vertical-align:top}thead{background:#eee}@media(prefers-color-scheme:dark){body{background:#111;color:#eee}thead{background:#292929}th,td{border-color:#666}}</style>")
        writer.appendLine("</head><body><main><h1>Heartbeat history</h1>")
        writer.appendLine("<dl>")
        metadata(data).forEach { (label, value) ->
            writer.appendLine("<dt>${htmlText(label)}</dt><dd>${htmlText(value)}</dd>")
        }
        writer.appendLine("</dl>")
        if (history.truncated) {
            writer.appendLine("<p><strong>Warning:</strong> The retained result reached URSA's ${HeartbeatHistoryRange.MAX_RESULT_ROWS}-row safety limit.</p>")
        }
        writer.appendLine("<table><caption>Observed heartbeat rows in timestamp order</caption><thead><tr>")
        COLUMNS.forEach { writer.append("<th scope=\"col\">${htmlText(it)}</th>") }
        writer.appendLine("</tr></thead><tbody>")
        history.beats.forEach { beat ->
            writer.append("<tr>")
            values(data, beat).forEach { writer.append("<td>${htmlText(it)}</td>") }
            writer.appendLine("</tr>")
        }
        writer.appendLine("</tbody></table></main></body></html>")
    }

    private fun metadata(data: HeartbeatHistoryExportData): List<Pair<String, String>> {
        val history = data.history
        val serverLabel = credentialFreeServerLabel(data.serverName, data.serverScope)
        return listOf(
            "Server" to serverLabel,
            "Server scope" to data.serverScope,
            "Monitor ID" to data.monitorId.toString(),
            "Monitor" to data.monitorName,
            "Captured UTC" to instant(data.capturedAtMillis),
            "Requested start UTC" to instant(history.range.startMillis),
            "Requested end UTC" to instant(history.range.endMillis),
            "Observed start UTC" to history.observedStartMillis?.let(::instant).orEmpty(),
            "Observed end UTC" to history.observedEndMillis?.let(::instant).orEmpty(),
            "Timezone" to "UTC",
            "Rows" to history.beats.size.toString(),
            "Truncated" to history.truncated.toString(),
            "Malformed source rows excluded" to history.malformedRows.toString(),
        )
    }

    private fun values(data: HeartbeatHistoryExportData, beat: Heartbeat): List<String> {
        val history = data.history
        val serverLabel = credentialFreeServerLabel(data.serverName, data.serverScope)
        return listOf(
            serverLabel,
            data.serverScope,
            data.monitorId.toString(),
            data.monitorName,
            instant(data.capturedAtMillis),
            instant(history.range.startMillis),
            instant(history.range.endMillis),
            history.observedStartMillis?.let(::instant).orEmpty(),
            history.observedEndMillis?.let(::instant).orEmpty(),
            "UTC",
            history.truncated.toString(),
            history.malformedRows.toString(),
            kumaHeartbeatUtcMillisOrNull(beat.time)?.let(::instant).orEmpty(),
            beat.status.code.toString(),
            beat.status.name.lowercase(Locale.ROOT),
            beat.ping?.toString().orEmpty(),
            beat.important.toString(),
        ).map(::safeText)
    }

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
        "server_name",
        "server_scope",
        "monitor_id",
        "monitor_name",
        "captured_at_utc",
        "requested_start_utc",
        "requested_end_utc",
        "observed_start_utc",
        "observed_end_utc",
        "timezone",
        "history_truncated",
        "malformed_source_rows_excluded",
        "timestamp_utc",
        "status_code",
        "status",
        "ping_ms",
        "important",
    )
}
