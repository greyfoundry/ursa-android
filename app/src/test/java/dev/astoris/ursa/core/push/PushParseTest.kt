package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bodies below mirror what Uptime Kuma's Webhook notification POSTs
 * (`{heartbeat, monitor, msg}` — see notification-providers/webhook.js). The push
 * payload is untrusted input, so the parser must be tolerant and never throw.
 */
class PushParseTest {

    @Test fun down_webhook_builds_title_and_body() {
        val n = PushParse.parse(
            """{"heartbeat":{"monitorID":7,"status":0,"important":true,"msg":"connect ECONNREFUSED"},
               "monitor":{"id":7,"name":"API"},
               "msg":"[API] [🔴 Down] connect ECONNREFUSED"}"""
        )!!
        assertEquals(7, n.monitorId)
        assertEquals("API is Down", n.title)
        assertEquals("[API] [🔴 Down] connect ECONNREFUSED", n.body)
        assertTrue(n.important)
    }

    @Test fun up_webhook_maps_status_1() {
        val n = PushParse.parse(
            """{"heartbeat":{"monitorID":3,"status":1},"monitor":{"id":3,"name":"Site"},"msg":"[Site] [✅ Up]"}"""
        )!!
        assertEquals("Site is Up", n.title)
        assertFalse(n.important)
    }

    @Test fun important_as_int_1_is_true() {
        val n = PushParse.parse(
            """{"heartbeat":{"monitorID":1,"status":1,"important":1},"monitor":{"name":"X"},"msg":"m"}"""
        )!!
        assertTrue(n.important)
    }

    @Test fun missing_msg_falls_back_to_status_phrase() {
        val n = PushParse.parse("""{"heartbeat":{"monitorID":9,"status":3},"monitor":{"name":"DB"}}""")!!
        assertEquals("DB in Maintenance", n.title)
        assertEquals("in Maintenance", n.body)
    }

    @Test fun missing_name_defaults_to_monitor() {
        val n = PushParse.parse("""{"heartbeat":{"status":0},"msg":"boom"}""")!!
        assertEquals("Monitor is Down", n.title)
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
        assertEquals("Y is Up", n.title)
    }
}
