package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainDashboardTest {

    private val now = 1_800_000_000_000L

    @Test
    fun groupsHttpFamilyMonitorsByHostAndPortWithoutPathOrCredentials() {
        val monitors = listOf(
            monitor(1, "API", "https://user:secret@Example.com/api", "http", MonitorStatus.UP),
            monitor(2, "Web", "https://example.com:443/home", "keyword", MonitorStatus.DOWN),
            monitor(3, "Admin", "https://example.com:8443", "real-browser", MonitorStatus.UP),
            monitor(4, "Socket", "wss://example.com/live", "websocket-upgrade", MonitorStatus.UP),
            monitor(5, "DNS", "example.com", "dns", MonitorStatus.UP),
            monitor(6, "IPv6", "https://[2001:db8::1]:8443/health", "http", MonitorStatus.UP),
        )

        val groups = domainGroups(monitors, emptyMap(), now, DomainSort.NAME)
        val example = groups.single { it.displayHost == "example.com" }

        assertEquals(listOf("[2001:db8::1]:8443", "example.com", "example.com:8443"), groups.map { it.displayHost })
        assertEquals(listOf("API", "Socket", "Web"), example.monitors.map { it.name })
        assertEquals(1, example.downCount)
        assertTrue(groups.none { it.displayHost.contains("secret") || it.displayHost.contains("api") })
    }

    @Test
    fun summarizesTlsCoverageAndSortsAttentionFirst() {
        val healthy = monitor(1, "Healthy", "https://healthy.example.com", "http", MonitorStatus.UP)
        val down = monitor(2, "Down", "https://down.example.com", "http", MonitorStatus.DOWN)
        val certs = mapOf(
            1 to CertInfo(true, null, null, daysRemaining = 60),
            2 to CertInfo(false, null, null, daysRemaining = -2),
        )

        val groups = domainGroups(listOf(healthy, down), certs, now)

        assertEquals("down.example.com", groups.first().displayHost)
        assertEquals(CertificateHealth.EXPIRED, groups.first().certificateEntries.single().health)
        assertEquals(1, groups.first().tlsMonitorCount)
    }

    @Test
    fun excludesMalformedHostlessAndNonHttpMonitors() {
        val monitors = listOf(
            monitor(1, "Relative", "/health", "http", MonitorStatus.UP),
            monitor(2, "Push", "https://example.com", "push", MonitorStatus.UP),
            monitor(3, "Broken", "not a url", "http", MonitorStatus.UP),
        )

        assertTrue(domainGroups(monitors, emptyMap(), now).isEmpty())
    }

    private fun monitor(
        id: Int,
        name: String,
        url: String,
        type: String,
        status: MonitorStatus,
    ) = Monitor(id, name, url, type, active = true, status = status)
}
