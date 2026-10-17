package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.MonitorStatus
import java.io.StringWriter
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatSlaReportTest {
    private val hour = HeartbeatHistoryRange.HOUR_MILLIS

    @Test
    fun boundaryOutageProducesTimeWeightedSla() {
        val summary = HeartbeatSlaReport.calculate(
            history(
                "2026-10-14T00:00:00Z",
                "2026-10-14T04:00:00Z",
                beat("2026-10-14 00:00:00", MonitorStatus.UP),
                beat("2026-10-14 01:00:00", MonitorStatus.DOWN),
                beat("2026-10-14 02:00:00", MonitorStatus.UP),
                beat("2026-10-14 03:00:00", MonitorStatus.UP),
                beat("2026-10-14 04:00:00", MonitorStatus.DOWN),
            ),
        )

        assertEquals(3 * hour, summary.upMillis)
        assertEquals(hour, summary.downMillis)
        assertEquals(0L, summary.missingMillis)
        assertEquals(0.75, summary.uptimeFraction!!, 0.0001)
        assertEquals(1.0, summary.coverageFraction, 0.0001)
    }

    @Test
    fun largeInternalGapIsMissingAndNeverUptime() {
        val summary = HeartbeatSlaReport.calculate(
            history(
                "2026-10-14T00:00:00Z",
                "2026-10-14T04:00:00Z",
                beat("2026-10-14 00:00:00", MonitorStatus.UP),
                beat("2026-10-14 01:00:00", MonitorStatus.UP),
                beat("2026-10-14 04:00:00", MonitorStatus.DOWN),
            ),
        )

        assertEquals(2 * hour, summary.upMillis)
        assertEquals(2 * hour, summary.missingMillis)
        assertEquals(1.0, summary.uptimeFraction!!, 0.0001)
        assertEquals(0.5, summary.coverageFraction, 0.0001)
        assertEquals(
            listOf(
                SlaMissingPeriod(
                    Instant.parse("2026-10-14T02:00:00Z").toEpochMilli(),
                    Instant.parse("2026-10-14T04:00:00Z").toEpochMilli(),
                ),
            ),
            summary.missingPeriods,
        )
    }

    @Test
    fun maintenanceAndPendingAreObservedButExcludedFromSla() {
        val summary = HeartbeatSlaReport.calculate(
            history(
                "2026-10-14T00:00:00Z",
                "2026-10-14T05:00:00Z",
                beat("2026-10-14 00:00:00", MonitorStatus.UP),
                beat("2026-10-14 01:00:00", MonitorStatus.MAINTENANCE),
                beat("2026-10-14 02:00:00", MonitorStatus.PENDING),
                beat("2026-10-14 03:00:00", MonitorStatus.DOWN),
                beat("2026-10-14 04:00:00", MonitorStatus.UP),
                beat("2026-10-14 05:00:00", MonitorStatus.UP),
            ),
        )

        assertEquals(2 * hour, summary.upMillis)
        assertEquals(hour, summary.downMillis)
        assertEquals(hour, summary.maintenanceMillis)
        assertEquals(hour, summary.pendingMillis)
        assertEquals(2.0 / 3.0, summary.uptimeFraction!!, 0.0001)
        assertEquals(1.0, summary.coverageFraction, 0.0001)
    }

    @Test
    fun insufficientSamplesReportNoSlaAndFullMissingRange() {
        val summary = HeartbeatSlaReport.calculate(
            history(
                "2026-10-14T00:00:00Z",
                "2026-10-14T04:00:00Z",
                beat("2026-10-14 01:00:00", MonitorStatus.UP),
            ),
        )

        assertNull(summary.nominalIntervalMillis)
        assertNull(summary.uptimeFraction)
        assertEquals(4 * hour, summary.missingMillis)
        assertEquals(0.0, summary.coverageFraction, 0.0001)
    }

    @Test
    fun utcCalculationDoesNotChangeAcrossDeviceTimezoneBoundary() {
        val summary = HeartbeatSlaReport.calculate(
            history(
                "2026-10-25T00:00:00Z",
                "2026-10-25T03:00:00Z",
                beat("2026-10-25 00:00:00", MonitorStatus.UP),
                beat("2026-10-25 01:00:00", MonitorStatus.DOWN),
                beat("2026-10-25 02:00:00", MonitorStatus.UP),
                beat("2026-10-25 03:00:00", MonitorStatus.UP),
            ),
        )

        assertEquals(hour, summary.downMillis)
        assertEquals(2 * hour, summary.upMillis)
        assertEquals(2.0 / 3.0, summary.uptimeFraction!!, 0.0001)
    }

    @Test
    fun csvAndHtmlDiscloseCoverageMethodAndEscapeIdentity() {
        val history = history(
            "2026-10-14T00:00:00Z",
            "2026-10-14T02:00:00Z",
            beat("2026-10-14 00:00:00", MonitorStatus.UP),
            beat("2026-10-14 01:00:00", MonitorStatus.DOWN),
            beat("2026-10-14 02:00:00", MonitorStatus.UP),
        )
        val data = HeartbeatSlaReportData(
            serverName = "Ops <west>",
            serverScope = "0376a9af36e38c3e",
            monitorId = 7,
            monitorName = "=API & jobs",
            capturedAtMillis = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(),
            history = history,
        )
        val csv = StringWriter()
        val html = StringWriter()

        HeartbeatSlaReport.write(csv, HeartbeatHistoryExportFormat.CSV, data)
        HeartbeatSlaReport.write(html, HeartbeatHistoryExportFormat.HTML, data)

        assertTrue(csv.toString().contains("coverage_percent,sla_percent"))
        assertTrue(csv.toString().contains("\"'=API & jobs\""))
        assertTrue(csv.toString().contains(HeartbeatSlaReport.CALCULATION_METHOD))
        assertTrue(html.toString().contains("<html lang=\"en\">"))
        assertTrue(html.toString().contains("<caption>Coverage-aware duration totals</caption>"))
        assertTrue(html.toString().contains("Ops &lt;west&gt;"))
        assertTrue(html.toString().contains("=API &amp; jobs"))
        assertFalse(html.toString().contains("<script"))
        assertFalse(csv.toString().contains("unexported-message-marker"))
    }

    @Test
    fun addressLikeServerLabelIsNotExported() {
        val output = StringWriter()
        val history = history(
            "2026-10-14T00:00:00Z",
            "2026-10-14T01:00:00Z",
            beat("2026-10-14 00:00:00", MonitorStatus.UP),
            beat("2026-10-14 01:00:00", MonitorStatus.UP),
        )

        HeartbeatSlaReport.write(
            output,
            HeartbeatHistoryExportFormat.CSV,
            HeartbeatSlaReportData(
                serverName = "10.0.2.2:3001",
                serverScope = "0376a9af36e38c3e",
                monitorId = 7,
                monitorName = "API",
                capturedAtMillis = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(),
                history = history,
            ),
        )

        assertFalse(output.toString().contains("10.0.2.2"))
        assertTrue(output.toString().contains("Server 0376a9af36e3"))
    }

    @Test
    fun csvStreamsEveryMissingPeriodAsItsOwnRow() {
        val output = StringWriter()
        val history = history(
            "2026-10-14T00:00:00Z",
            "2026-10-14T08:00:00Z",
            beat("2026-10-14 00:00:00", MonitorStatus.UP),
            beat("2026-10-14 01:00:00", MonitorStatus.UP),
            beat("2026-10-14 04:00:00", MonitorStatus.UP),
            beat("2026-10-14 05:00:00", MonitorStatus.UP),
            beat("2026-10-14 08:00:00", MonitorStatus.UP),
        )

        HeartbeatSlaReport.write(
            output,
            HeartbeatHistoryExportFormat.CSV,
            HeartbeatSlaReportData(
                serverName = "Kuma",
                serverScope = "0376a9af36e38c3e",
                monitorId = 7,
                monitorName = "API",
                capturedAtMillis = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(),
                history = history,
            ),
        )

        val lines = output.toString().lineSequence().filter(String::isNotBlank).toList()
        assertEquals(3, lines.size)
        assertTrue(lines[1].contains("2026-10-14T02:00:00Z"))
        assertTrue(lines[1].contains("2026-10-14T04:00:00Z"))
        assertTrue(lines[2].contains("2026-10-14T06:00:00Z"))
        assertTrue(lines[2].contains("2026-10-14T08:00:00Z"))
    }

    private fun history(start: String, end: String, vararg beats: Heartbeat): HeartbeatHistoryResult {
        val startMillis = Instant.parse(start).toEpochMilli()
        val endMillis = Instant.parse(end).toEpochMilli()
        return HeartbeatHistoryResult(
            range = HeartbeatHistoryRange(startMillis, endMillis),
            beats = beats.toList(),
            observedStartMillis = beats.firstOrNull()?.time?.let(::kumaHeartbeatUtcMillisOrNull),
            observedEndMillis = beats.lastOrNull()?.time?.let(::kumaHeartbeatUtcMillisOrNull),
            truncated = false,
            malformedRows = 0,
        )
    }

    private fun beat(time: String, status: MonitorStatus) = Heartbeat(
        monitorId = 7,
        status = status,
        time = time,
        msg = "unexported-message-marker",
        ping = 42,
        important = status == MonitorStatus.DOWN,
    )
}
