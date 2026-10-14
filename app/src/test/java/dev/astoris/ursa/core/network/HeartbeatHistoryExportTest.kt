package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.MonitorStatus
import java.io.StringWriter
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatHistoryExportTest {
    private val start = Instant.parse("2026-10-14T09:00:00Z").toEpochMilli()
    private val end = Instant.parse("2026-10-14T10:00:00Z").toEpochMilli()

    @Test
    fun csvContainsBoundedOperationalFieldsAndExcludesMessages() {
        val output = StringWriter()
        val summary = HeartbeatHistoryExport.write(
            output,
            HeartbeatHistoryExportFormat.CSV,
            data(monitorName = "=WEBSERVICE(secret)"),
        )
        val csv = output.toString()

        assertEquals(2, summary.rowCount)
        assertTrue(summary.truncated)
        assertTrue(csv.contains("requested_start_utc"))
        assertTrue(csv.contains("\"2026-10-14T09:15:00Z\",\"1\",\"up\",\"42\""))
        assertTrue(csv.contains("\"'=WEBSERVICE(secret)\""))
        assertFalse(csv.contains("unexported-message-marker"))
        assertFalse(csv.contains("private-address-marker"))
        assertFalse(csv.contains("custom-header-marker"))
    }

    @Test
    fun htmlEscapesIdentityAndProvidesAccessibleTableMetadata() {
        val output = StringWriter()
        HeartbeatHistoryExport.write(
            output,
            HeartbeatHistoryExportFormat.HTML,
            data(serverName = "Ops <west>", monitorName = "API & jobs"),
        )
        val html = output.toString()

        assertTrue(html.contains("<html lang=\"en\">"))
        assertTrue(html.contains("<caption>Observed heartbeat rows in timestamp order</caption>"))
        assertTrue(html.contains("<th scope=\"col\">timestamp_utc</th>"))
        assertTrue(html.contains("Ops &lt;west&gt;"))
        assertTrue(html.contains("API &amp; jobs"))
        assertTrue(html.contains("Requested start UTC"))
        assertTrue(html.contains("Malformed source rows excluded"))
        assertFalse(html.contains("<script"))
        assertFalse(html.contains("unexported-message-marker"))
    }

    @Test
    fun emptyHistoryStillExportsRequestedRangeAndNoRows() {
        val output = StringWriter()
        val empty = data().copy(
            history = HeartbeatHistoryResult(
                range = HeartbeatHistoryRange(start, end),
                beats = emptyList(),
                observedStartMillis = null,
                observedEndMillis = null,
                truncated = false,
                malformedRows = 0,
            ),
        )

        val summary = HeartbeatHistoryExport.write(
            output,
            HeartbeatHistoryExportFormat.HTML,
            empty,
        )

        assertEquals(0, summary.rowCount)
        assertFalse(summary.truncated)
        assertTrue(output.toString().contains("2026-10-14T09:00:00Z"))
        assertFalse(output.toString().contains("<tr><td>"))
    }

    @Test
    fun fileNameUsesOnlyMonitorIdAndUtcCaptureTime() {
        assertEquals(
            "ursa-heartbeat-history-7-20261014-120000Z.csv",
            HeartbeatHistoryExport.fileName(
                HeartbeatHistoryExportFormat.CSV,
                monitorId = 7,
                capturedAtMillis = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(),
            ),
        )
    }

    private fun data(
        serverName: String = "Kuma",
        monitorName: String = "API",
    ) = HeartbeatHistoryExportData(
        serverName = serverName,
        serverScope = "0376a9af36e38c3e",
        monitorId = 7,
        monitorName = monitorName,
        capturedAtMillis = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(),
        history = HeartbeatHistoryResult(
            range = HeartbeatHistoryRange(start, end),
            beats = listOf(
                beat("2026-10-14 09:15:00", MonitorStatus.UP, 42),
                beat("2026-10-14 09:45:00", MonitorStatus.DOWN, null),
            ),
            observedStartMillis = Instant.parse("2026-10-14T09:15:00Z").toEpochMilli(),
            observedEndMillis = Instant.parse("2026-10-14T09:45:00Z").toEpochMilli(),
            truncated = true,
            malformedRows = 3,
        ),
    )

    private fun beat(time: String, status: MonitorStatus, ping: Int?) = Heartbeat(
        monitorId = 7,
        status = status,
        time = time,
        msg = "unexported-message-marker private-address-marker custom-header-marker",
        ping = ping,
        important = status == MonitorStatus.DOWN,
    )
}
