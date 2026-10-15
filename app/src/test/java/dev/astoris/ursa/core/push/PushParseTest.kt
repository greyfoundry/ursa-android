package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bodies below mirror what Uptime Kuma's Webhook notification POSTs
 * (`{heartbeat, monitor, msg}` - see notification-providers/webhook.js). The push
 * payload is untrusted input, so the parser must be tolerant and never throw.
 */
class PushParseTest {

    @Test fun down_webhook_preserves_payload_for_localized_presentation() {
        val n = PushParse.parse(
            """{"heartbeat":{"monitorID":7,"status":0,"important":true,"msg":"connect ECONNREFUSED"},
               "monitor":{"id":7,"name":"API"},
               "msg":"[API] [🔴 Down] connect ECONNREFUSED"}"""
        )!!
        assertEquals(7, n.monitorId)
        assertEquals("API", n.monitorName)
        assertEquals("", n.title)
        assertEquals("[API] [🔴 Down] connect ECONNREFUSED", n.body)
        assertTrue(n.important)
    }

    @Test fun up_webhook_maps_status_1() {
        val n = PushParse.parse(
            """{"heartbeat":{"monitorID":3,"status":1},"monitor":{"id":3,"name":"Site"},"msg":"[Site] [✅ Up]"}"""
        )!!
        assertEquals(1, n.status)
        assertFalse(n.important)
    }

    @Test fun important_as_int_1_is_true() {
        val n = PushParse.parse(
            """{"heartbeat":{"monitorID":1,"status":1,"important":1},"monitor":{"name":"X"},"msg":"m"}"""
        )!!
        assertTrue(n.important)
    }

    @Test fun missing_msg_is_left_for_localized_presentation() {
        val n = PushParse.parse("""{"heartbeat":{"monitorID":9,"status":3},"monitor":{"name":"DB"}}""")!!
        assertEquals("DB", n.monitorName)
        assertEquals("", n.body)
    }

    @Test fun missing_name_is_left_for_localized_presentation() {
        val n = PushParse.parse("""{"heartbeat":{"status":0},"msg":"boom"}""")!!
        assertEquals("", n.monitorName)
        assertEquals("boom", n.body)
    }

    @Test fun non_json_returns_null() {
        assertNull(PushParse.parse("not json at all"))
        assertNull(PushParse.parse(""))
    }

    @Test fun unknown_keys_are_ignored() {
        val n = PushParse.parse(
            """{"heartbeat":{"monitorID":2,"status":1,"extra":"x"},"monitor":{"name":"Y","foo":1},"msg":"ok","bar":true}"""
        )!!
        assertEquals("Y", n.monitorName)
    }

    @Test fun exposes_status_for_downtime_tracking() {
        assertEquals(0, PushParse.parse("""{"heartbeat":{"monitorID":1,"status":0}}""")!!.status)
        assertEquals(1, PushParse.parse("""{"heartbeat":{"monitorID":1,"status":1}}""")!!.status)
    }

    @Test fun managed_webhook_exposes_only_valid_server_scope() {
        val id = "0123456789abcdef0123456789abcdef"
        val scoped = PushParse.parse(
            """{"heartbeat":{"monitorID":7,"status":0},"ursaServerId":"$id"}""",
        )!!
        val invalid = PushParse.parse(
            """{"heartbeat":{"monitorID":7,"status":0},"ursaServerId":"not-valid"}""",
        )!!

        assertEquals(id, scoped.serverId)
        assertNull(invalid.serverId)
    }

    @Test fun maps_every_status_to_localized_presentation_resources() {
        assertEquals(dev.astoris.ursa.R.string.push_status_up, pushStatusTextRes(1))
        assertEquals(dev.astoris.ursa.R.string.push_status_down, pushStatusTextRes(0))
        assertEquals(dev.astoris.ursa.R.string.push_status_pending, pushStatusTextRes(2))
        assertEquals(dev.astoris.ursa.R.string.push_status_maintenance, pushStatusTextRes(3))
        assertEquals(dev.astoris.ursa.R.string.push_status_updated, pushStatusTextRes(null))
    }

    @Test fun splits_downtime_into_localizable_units() {
        assertEquals(emptyList<Pair<Int, Int>>(), pushDurationUnits(30_000))
        assertEquals(listOf(1 to dev.astoris.ursa.R.plurals.push_duration_minutes), pushDurationUnits(90_000))
        assertEquals(
            listOf(
                1 to dev.astoris.ursa.R.plurals.push_duration_days,
                1 to dev.astoris.ursa.R.plurals.push_duration_hours,
            ),
            pushDurationUnits(25 * 60 * 60_000L),
        )
    }
}
