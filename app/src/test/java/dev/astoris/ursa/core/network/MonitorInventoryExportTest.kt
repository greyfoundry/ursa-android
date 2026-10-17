package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorInventoryExportTest {
    private val capturedAt = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()

    @Test
    fun csvIsCredentialFreeDeterministicAndFormulaSafe() {
        val monitors = listOf(
            monitor(2, "Child, \"quoted\"").copy(parentId = 1, tags = listOf("beta", "Alpha")),
            monitor(1, "=HYPERLINK(\"https://example.invalid/token\")").copy(
                url = "https://user:password@example.invalid/?token=secret",
            ),
        )

        val document = MonitorInventoryExport.create(
            MonitorInventoryExportFormat.CSV,
            monitors,
            serverName = "+Production",
            serverScope = "0123456789abcdef",
            capturedAtMillis = capturedAt,
        )

        val lines = document.content.lineSequence().filter(String::isNotBlank).toList()
        assertEquals(3, lines.size)
        assertTrue(lines.first().contains("source_monitor_count,export_truncated"))
        assertTrue(lines[1].contains("\"'=HYPERLINK("))
        assertTrue(lines[1].contains("\"'+Production\""))
        assertTrue(lines[2].contains("\"Alpha;beta\""))
        assertTrue(lines[2].contains("\"'=HYPERLINK("))
        assertFalse(document.content.contains("user:password"))
        assertFalse(document.content.contains("token=secret"))
        assertEquals(2, document.rowCount)
        assertFalse(document.truncated)
    }

    @Test
    fun htmlEscapesUntrustedMetadataAndUsesAccessibleHeaders() {
        val document = MonitorInventoryExport.create(
            MonitorInventoryExportFormat.HTML,
            listOf(monitor(1, "<script>alert('x')</script>")),
            serverName = "A&B",
            serverScope = "0123456789abcdef",
            capturedAtMillis = capturedAt,
        )

        assertTrue(document.content.contains("<html lang=\"en\">"))
        assertTrue(document.content.contains("<th scope=\"col\">name</th>"))
        assertTrue(document.content.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"))
        assertTrue(document.content.contains("A&amp;B"))
        assertFalse(document.content.contains("<script>alert"))
    }

    @Test
    fun exportAppliesTheDocumentRowBoundary() {
        val monitors = (MonitorInventoryExport.MAX_ROWS downTo 0).map { monitor(it + 1, "Monitor $it") }
        val document = MonitorInventoryExport.create(
            MonitorInventoryExportFormat.CSV,
            monitors,
            serverName = "Server",
            serverScope = "0123456789abcdef",
            capturedAtMillis = capturedAt,
        )

        assertEquals(MonitorInventoryExport.MAX_ROWS, document.rowCount)
        assertTrue(document.truncated)
        assertTrue(document.content.lineSequence().drop(1).first().contains("\"true\""))
        assertEquals(
            MonitorInventoryExport.MAX_ROWS + 1,
            document.content.lineSequence().count(String::isNotBlank),
        )
    }

    private fun monitor(id: Int, name: String) = Monitor(
        id = id,
        name = name,
        url = "https://example.invalid/$id",
        type = "http",
        active = true,
        status = MonitorStatus.UP,
        ping = 42,
        avgPing = 45,
        uptime24h = 0.999,
    )
}
