package dev.astoris.ursa.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KumaCapabilitiesTest {

    @Test fun parsesOnlyCompleteStableVersions() {
        assertEquals(KumaVersion(2, 5, 5), KumaVersion.parse(" 2.5.5 "))
        assertNull(KumaVersion.parse("2.5"))
        assertNull(KumaVersion.parse("v2.5.5"))
        assertNull(KumaVersion.parse("2.5.5-beta.1"))
        assertNull(KumaVersion.parse("02.5.5"))
        assertNull(KumaVersion.parse("2147483648.0.0"))
    }

    @Test fun awaitingAndUnrecognizedVersionsKeepOnlySafeReads() {
        val awaiting = KumaCapabilities.evaluate(null)
        val prerelease = KumaCapabilities.evaluate("2.5.6-beta.1")

        assertEquals(KumaCompatibilityTier.AWAITING_VERSION, awaiting.tier)
        assertEquals(KumaCompatibilityTier.UNRECOGNIZED, prerelease.tier)
        assertEquals(setOf(KumaFeature.CORE_READS), awaiting.features)
        assertEquals(setOf(KumaFeature.CORE_READS), prerelease.features)
        assertFalse(prerelease.writesVerified)
    }

    @Test fun olderAndNewerStableVersionsAreReadOnlyUnverified() {
        val older = KumaCapabilities.evaluate("2.3.1")
        val newer = KumaCapabilities.evaluate("2.5.6")

        assertEquals(KumaCompatibilityTier.UNVERIFIED_OLDER, older.tier)
        assertEquals(KumaCompatibilityTier.UNVERIFIED_NEWER, newer.tier)
        assertEquals(setOf(KumaFeature.CORE_READS), older.features)
        assertEquals(setOf(KumaFeature.CORE_READS), newer.features)
    }

    @Test fun verifiedFloorHasBaselineWritesButNotNewerSchemas() {
        val result = KumaCapabilities.evaluate("2.4.0")

        assertEquals(KumaCompatibilityTier.VERIFIED, result.tier)
        assertTrue(result.writesVerified)
        assertTrue(result.supports(KumaFeature.MONITOR_STATE_WRITE))
        assertTrue(result.supports(KumaFeature.MONITOR_COMMON_WRITE))
        assertTrue(result.supports(KumaFeature.MONITOR_DELETE))
        assertTrue(result.supports(KumaFeature.MAINTENANCE_WRITE))
        assertTrue(result.supports(KumaFeature.MANAGED_PUSH_WRITE))
        assertFalse(result.supports(KumaFeature.NTP_PM2_SCHEMA))
        assertFalse(result.supports(KumaFeature.SFTP_SCHEMA))
        assertFalse(result.supportsMonitorSchema("sftp"))
        assertTrue(result.supportsMonitorSchema("http"))
    }

    @Test fun featureThresholdsMatchKumaReleases() {
        val v250 = KumaCapabilities.evaluate("2.5.0")
        val v253 = KumaCapabilities.evaluate("2.5.3")
        val v254 = KumaCapabilities.evaluate("2.5.4")
        val v255 = KumaCapabilities.evaluate("2.5.5")

        assertTrue(v250.supports(KumaFeature.NTP_PM2_SCHEMA))
        assertFalse(v250.supports(KumaFeature.SFTP_SCHEMA))
        assertFalse(v253.supports(KumaFeature.SFTP_SCHEMA))
        assertTrue(v254.supports(KumaFeature.SFTP_SCHEMA))
        assertTrue(v254.supportsMonitorSchema("sftp"))
        assertTrue(v255.supports(KumaFeature.SFTP_SCHEMA))
        assertTrue(v255.writesVerified)
        assertFalse(MonitorTypeCatalog.creatableFor(v253).any { it.key == "sftp" })
        assertTrue(MonitorTypeCatalog.creatableFor(v254).any { it.key == "sftp" })
    }

    @Test fun allStable24PatchesRemainInsideDocumentedRange() {
        val result = KumaCapabilities.evaluate("2.4.99")

        assertEquals(KumaCompatibilityTier.VERIFIED, result.tier)
        assertTrue(result.writesVerified)
    }
}
