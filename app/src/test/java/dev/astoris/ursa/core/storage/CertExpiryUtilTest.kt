package dev.astoris.ursa.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertExpiryUtilTest {

    private val now = 1_720_000_000_000L // fixed "now"

    @Test fun prefers_iso_validTo() {
        val ms = CertExpiryUtil.resolveValidToMillis("2024-07-03T12:26:40Z", 999, now)!!
        assertEquals(Instant_parse("2024-07-03T12:26:40Z"), ms)
    }

    @Test fun falls_back_to_days_remaining() {
        val ms = CertExpiryUtil.resolveValidToMillis(null, 10, now)
        assertEquals(now + 10 * CertExpiryUtil.DAY_MS, ms)
    }

    @Test fun invalid_iso_falls_back_to_days() {
        val ms = CertExpiryUtil.resolveValidToMillis("not-a-date", 5, now)
        assertEquals(now + 5 * CertExpiryUtil.DAY_MS, ms)
    }

    @Test fun no_data_is_null() {
        assertNull(CertExpiryUtil.resolveValidToMillis(null, null, now))
        assertNull(CertExpiryUtil.resolveValidToMillis("", null, now))
    }

    @Test fun days_until_counts_down_and_negative_when_expired() {
        assertEquals(10L, CertExpiryUtil.daysUntil(now + 10 * CertExpiryUtil.DAY_MS, now))
        assertTrue(CertExpiryUtil.daysUntil(now - CertExpiryUtil.DAY_MS, now) < 0)
    }

    @Test fun codec_round_trips_and_tolerates_garbage() {
        val list = listOf(
            CertExpiry("https://k.example.com", 1, "API", now + CertExpiryUtil.DAY_MS),
            CertExpiry("https://k.example.com", 2, "Web", now),
        )
        assertEquals(list, CertExpiryUtil.decode(CertExpiryUtil.encode(list)))
        assertTrue(CertExpiryUtil.decode("garbage").isEmpty())
        assertTrue(CertExpiryUtil.decode(null).isEmpty())
    }

    private fun Instant_parse(s: String): Long = java.time.Instant.parse(s).toEpochMilli()
}
