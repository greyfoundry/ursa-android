package dev.astoris.ursa.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseAlertUtilTest {

    private val now = 1_720_000_000_000L

    @Test fun alerts_when_up_and_over_threshold() {
        assertTrue(ResponseAlertUtil.shouldAlert(1, 1500, 1000, null, now))
    }

    @Test fun no_alert_at_or_under_threshold() {
        assertFalse(ResponseAlertUtil.shouldAlert(1, 1000, 1000, null, now))
        assertFalse(ResponseAlertUtil.shouldAlert(1, 200, 1000, null, now))
    }

    @Test fun no_alert_when_not_up() {
        assertFalse(ResponseAlertUtil.shouldAlert(0, 5000, 1000, null, now)) // down
        assertFalse(ResponseAlertUtil.shouldAlert(2, 5000, 1000, null, now)) // pending
    }

    @Test fun disabled_when_threshold_not_positive() {
        assertFalse(ResponseAlertUtil.shouldAlert(1, 5000, 0, null, now))
        assertFalse(ResponseAlertUtil.shouldAlert(1, 5000, -1, null, now))
    }

    @Test fun null_ping_never_alerts() {
        assertFalse(ResponseAlertUtil.shouldAlert(1, null, 1000, null, now))
    }

    @Test fun respects_cooldown() {
        val cooldown = ResponseAlertUtil.COOLDOWN_MS
        // alerted 10 minutes ago -> still cooling down
        assertFalse(ResponseAlertUtil.shouldAlert(1, 5000, 1000, now - 600_000, now, cooldown))
        // alerted just over the cooldown ago -> allowed again
        assertTrue(ResponseAlertUtil.shouldAlert(1, 5000, 1000, now - cooldown - 1, now, cooldown))
    }

    @Test fun per_monitor_override_wins_when_positive() {
        assertEquals(500, ResponseAlertUtil.effectiveThreshold(500, 1000))
        assertEquals(1000, ResponseAlertUtil.effectiveThreshold(null, 1000))
        assertEquals(1000, ResponseAlertUtil.effectiveThreshold(0, 1000))
    }

    @Test fun map_roundtrips_with_url_keys() {
        val map = mapOf(
            "https://kuma.example.com:3001:7" to 1_720_000_000_000L,
            "http://10.0.2.2:3001:12" to 42L,
        )
        assertEquals(map, ResponseAlertUtil.decodeMap(ResponseAlertUtil.encodeMap(map)))
        assertEquals(emptyMap<String, Long>(), ResponseAlertUtil.decodeMap(""))
    }
}
