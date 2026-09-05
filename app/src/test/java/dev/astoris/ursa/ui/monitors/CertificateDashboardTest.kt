package dev.astoris.ursa.ui.monitors

import dev.astoris.ursa.core.storage.CertExpiryUtil
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateDashboardTest {

    private val now = 1_800_000_000_000L

    @Test
    fun groupsExpiredInvalidExpiringHealthyAndUnknownCertificates() {
        val monitors = (1..5).map { monitor(it, "Monitor $it") }
        val certs = mapOf(
            1 to cert(valid = false, days = -2),
            2 to cert(valid = false, days = 30),
            3 to cert(valid = true, days = 7),
            4 to cert(valid = true, days = 45),
            5 to cert(valid = true, days = null),
        )

        val entries = certificateEntries(monitors, certs, now)

        assertEquals(
            listOf(
                CertificateHealth.EXPIRED,
                CertificateHealth.INVALID,
                CertificateHealth.EXPIRING,
                CertificateHealth.HEALTHY,
                CertificateHealth.UNKNOWN,
            ),
            entries.map { it.health },
        )
    }

    @Test
    fun sortsByExpiryWithinGroupsAndCanSortByName() {
        val monitors = listOf(monitor(1, "Zulu"), monitor(2, "Alpha"), monitor(3, "Missing"))
        val certs = mapOf(
            1 to cert(valid = true, days = 30),
            2 to cert(valid = true, days = 60),
        )

        assertEquals(
            listOf("Zulu", "Alpha"),
            certificateEntries(monitors, certs, now, CertificateSort.EXPIRY).map { it.monitorName },
        )
        assertEquals(
            listOf("Alpha", "Zulu"),
            certificateEntries(monitors, certs, now, CertificateSort.NAME).map { it.monitorName },
        )
    }

    @Test
    fun expiryThresholdIsInclusiveAndUnreportedMonitorsAreExcluded() {
        val monitors = listOf(monitor(1, "Threshold"), monitor(2, "No event"))
        val entries = certificateEntries(
            monitors,
            mapOf(1 to cert(valid = true, days = CertExpiryUtil.DEFAULT_THRESHOLD_DAYS.toInt())),
            now,
        )

        assertEquals(1, entries.size)
        assertEquals(CertificateHealth.EXPIRING, entries.single().health)
    }

    private fun cert(valid: Boolean, days: Int?) = CertInfo(
        valid = valid,
        subject = null,
        issuer = "Test CA",
        daysRemaining = days,
    )

    private fun monitor(id: Int, name: String) = Monitor(
        id = id,
        name = name,
        url = "https://example.com/$id",
        type = "http",
        active = true,
        status = MonitorStatus.UP,
    )
}
