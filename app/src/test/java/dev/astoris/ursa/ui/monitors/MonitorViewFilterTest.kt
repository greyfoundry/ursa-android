package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorViewFilterTest {
    private val rows = listOf(
        monitor(1, "Web", "http", MonitorStatus.UP, tags = listOf("prod")),
        monitor(2, "DB", "port", MonitorStatus.DOWN, parentId = 9),
        monitor(3, "Paused", "http", MonitorStatus.UP, active = false),
        monitor(9, "Core", "group", MonitorStatus.UP),
    )

    @Test
    fun combinesStatusTagGroupTypeCertificateAndPausedCriteria() {
        val spec = MonitorViewFilter(
            statuses = setOf(MonitorStatus.UP),
            tags = setOf("prod"),
            types = setOf("http"),
            certificate = CertificateFilter.HAS_CERTIFICATE,
            activity = ActivityFilter.ACTIVE,
        )
        assertEquals(listOf(1), rows.filter { spec.matches(it, rows, setOf(1)) }.map(Monitor::id))

        val groupSpec = MonitorViewFilter(groups = setOf(9), activity = ActivityFilter.ALL)
        assertEquals(listOf(2), rows.filter { groupSpec.matches(it, rows, emptySet()) }.map(Monitor::id))
    }

    @Test
    fun emptySpecAndCodecAreStableAndBounded() {
        assertTrue(MonitorViewFilter().matches(rows.first(), rows, emptySet()))
        val encoded = MonitorViewCodec.encode(SavedMonitorView("Needs eyes", MonitorViewFilter(statuses = setOf(MonitorStatus.DOWN))))
        assertEquals("Needs eyes", MonitorViewCodec.decode(encoded)?.name)
        assertFalse(MonitorViewCodec.isValidName(""))
        assertFalse(MonitorViewCodec.isValidName("x".repeat(41)))
    }

    private fun monitor(
        id: Int,
        name: String,
        type: String,
        status: MonitorStatus,
        active: Boolean = true,
        tags: List<String> = emptyList(),
        parentId: Int? = null,
    ) = Monitor(id, name, null, type, active, tags = tags, parentId = parentId, status = status)
}
