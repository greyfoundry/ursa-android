package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Monitor
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MonitorInventoryExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    HTML("html", "text/html"),
}

data class MonitorInventoryDocument(
    val content: String,
    val fileName: String,
    val mimeType: String,
    val rowCount: Int,
    val truncated: Boolean,
)

object MonitorInventoryExport {
    const val MAX_ROWS = 10_000
    private const val MAX_FIELD_CHARS = 512
    private val unsafeControl = Regex("[\\p{Cc}\\p{Cf}]")
    private val repeatedWhitespace = Regex("\\s+")
    private val fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun create(
        format: MonitorInventoryExportFormat,
        monitors: List<Monitor>,
        serverName: String,
        serverScope: String,
        capturedAtMillis: Long,
    ): MonitorInventoryDocument {
        val rows = monitors.sortedBy(Monitor::id).take(MAX_ROWS)
        val truncated = monitors.size > rows.size
        val capturedAt = Instant.ofEpochMilli(capturedAtMillis).toString()
        val safeServerName = credentialFreeServerLabel(serverName, serverScope)
        val namesById = monitors.associate { it.id to it.name }
        val content = when (format) {
            MonitorInventoryExportFormat.CSV -> csv(
                rows,
                safeServerName,
                serverScope,
                capturedAt,
                monitors.size,
                truncated,
                namesById,
            )
            MonitorInventoryExportFormat.HTML -> html(
                rows,
                safeServerName,
                serverScope,
                capturedAt,
                monitors.size,
                truncated,
                namesById,
            )
        }
        return MonitorInventoryDocument(
            content = content,
            fileName = "ursa-monitor-inventory-${fileTimestamp.format(Instant.ofEpochMilli(capturedAtMillis))}.${format.extension}",
            mimeType = format.mimeType,
            rowCount = rows.size,
            truncated = truncated,
        )
    }

    private fun csv(
        monitors: List<Monitor>,
        serverName: String,
        serverScope: String,
        capturedAt: String,
        sourceCount: Int,
        truncated: Boolean,
        namesById: Map<Int, String>,
    ): String = buildString {
        appendLine(COLUMNS.joinToString(","))
        monitors.forEach { monitor ->
            appendLine(
                values(
                    monitor,
                    serverName,
                    serverScope,
                    capturedAt,
                    sourceCount,
                    truncated,
                    namesById,
                )
                    .joinToString(",", transform = ::csvCell),
            )
        }
    }

    private fun html(
        monitors: List<Monitor>,
        serverName: String,
        serverScope: String,
        capturedAt: String,
        sourceCount: Int,
        truncated: Boolean,
        namesById: Map<Int, String>,
    ): String = buildString {
        appendLine("<!doctype html>")
        appendLine("<html lang=\"en\"><head><meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("<title>URSA monitor inventory</title>")
        appendLine("<style>body{font-family:system-ui,sans-serif;max-width:96rem;margin:auto;padding:1rem}table{border-collapse:collapse;width:100%}caption{text-align:left;margin:.5rem 0}th,td{border:1px solid #888;padding:.4rem;text-align:left;vertical-align:top}thead{background:#eee}@media(prefers-color-scheme:dark){body{background:#111;color:#eee}thead{background:#292929}th,td{border-color:#666}}</style>")
        appendLine("</head><body><main><h1>Monitor inventory</h1>")
        appendLine("<p>Server: ${htmlText(serverName)}<br>Server scope: ${htmlText(serverScope)}<br>Captured: ${htmlText(capturedAt)}<br>Source monitors: $sourceCount</p>")
        if (truncated) appendLine("<p><strong>Warning:</strong> Export limited to the first $MAX_ROWS monitors by ID.</p>")
        appendLine("<table><caption>Credential-free monitor metadata</caption><thead><tr>")
        COLUMNS.forEach { append("<th scope=\"col\">${htmlText(it)}</th>") }
        appendLine("</tr></thead><tbody>")
        monitors.forEach { monitor ->
            append("<tr>")
            values(
                monitor,
                serverName,
                serverScope,
                capturedAt,
                sourceCount,
                truncated,
                namesById,
            ).forEach {
                append("<td>${htmlText(it)}</td>")
            }
            appendLine("</tr>")
        }
        appendLine("</tbody></table></main></body></html>")
    }

    private fun values(
        monitor: Monitor,
        serverName: String,
        serverScope: String,
        capturedAt: String,
        sourceCount: Int,
        truncated: Boolean,
        namesById: Map<Int, String>,
    ): List<String> = listOf(
        serverName,
        serverScope,
        capturedAt,
        sourceCount.toString(),
        truncated.toString(),
        monitor.id.toString(),
        monitor.name,
        monitor.type,
        monitor.active.toString(),
        monitor.status.name.lowercase(Locale.ROOT),
        monitor.parentId?.toString().orEmpty(),
        monitor.parentId?.let(namesById::get).orEmpty(),
        monitor.weight.toString(),
        monitor.tags.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(";"),
        monitor.ping?.toString().orEmpty(),
        monitor.avgPing?.toString().orEmpty(),
        monitor.uptime24h?.takeIf { it.isFinite() }?.let { uptime ->
            String.format(Locale.ROOT, "%.3f", uptime * 100.0).trimEnd('0').trimEnd('.')
        }.orEmpty(),
    ).map(::safeText)

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
        "captured_at_utc",
        "source_monitor_count",
        "export_truncated",
        "monitor_id",
        "name",
        "type",
        "active",
        "status",
        "parent_id",
        "parent_name",
        "weight",
        "tags",
        "current_ping_ms",
        "average_ping_ms",
        "uptime_24h_percent",
    )
}
