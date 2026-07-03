package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.MonitorStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payloads below are real captures from a live Uptime Kuma 2.4.0 instance
 * (see docs/references/uptime-kuma-api.mdx). This guards the adapter against
 * the known wire quirks: id-keyed monitorList, camelCase heartbeat, null url,
 * tag name extraction, and status-code mapping.
 */
class KumaParseTest {

    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test fun monitor_parses_core_fields() {
        val m = KumaParse.monitor(
            obj("""{"id":2,"name":"down-test","url":"http://127.0.0.1:9","type":"http","active":true,"tags":[]}""")
        )!!
        assertEquals(2, m.id)
        assertEquals("down-test", m.name)
        assertEquals("http://127.0.0.1:9", m.url)
        assertEquals("http", m.type)
        assertTrue(m.active)
    }

    @Test fun monitor_null_url_becomes_null_and_inactive() {
        val m = KumaParse.monitor(obj("""{"id":5,"name":"x","url":null,"type":"push","active":false}"""))!!
        assertNull(m.url)
        assertFalse(m.active)
    }

    @Test fun monitor_missing_id_is_null() {
        assertNull(KumaParse.monitor(obj("""{"name":"no id"}""")))
    }

    @Test fun tags_extracted_by_name() {
        val tags = KumaParse.tags(obj("""{"tags":[{"name":"prod"},{"name":"db"}]}"""))
        assertEquals(listOf("prod", "db"), tags)
    }

    @Test fun monitorList_is_keyed_by_id() {
        val map = KumaParse.monitorList(
            obj("""{"1":{"id":1,"name":"a","type":"http","active":true},"2":{"id":2,"name":"b","type":"http","active":true}}""")
        )
        assertEquals(setOf(1, 2), map.keys)
        assertEquals("a", map.getValue(1).name)
    }

    @Test fun heartbeat_up_camelcase() {
        val hb = KumaParse.heartbeat(
            obj("""{"monitorID":1,"status":1,"time":"2026-07-03 19:05:12.296","msg":"200 - OK","ping":167,"important":true,"retries":0}""")
        )!!
        assertEquals(1, hb.monitorId)
        assertEquals(MonitorStatus.UP, hb.status)
        assertEquals(167, hb.ping)
        assertEquals("200 - OK", hb.msg)
        assertTrue(hb.important)
    }

    @Test fun heartbeat_down() {
        val hb = KumaParse.heartbeat(
            obj("""{"monitorID":2,"status":0,"time":"t","msg":"connect ECONNREFUSED 127.0.0.1:9","important":true,"retries":1}""")
        )!!
        assertEquals(MonitorStatus.DOWN, hb.status)
    }

    @Test fun beatRow_snakecase_with_int_important() {
        // getMonitorBeats rows are snake_case; important is 1/0, not a boolean
        val hb = KumaParse.beatRow(
            obj("""{"id":1,"important":1,"monitor_id":1,"status":1,"msg":"200 - OK","time":"2026-07-03 19:05:12.296","ping":167}""")
        )!!
        assertEquals(1, hb.monitorId)
        assertEquals(MonitorStatus.UP, hb.status)
        assertEquals(167, hb.ping)
        assertTrue(hb.important)
    }

    @Test fun cert_extracts_validity_and_cns() {
        val c = KumaParse.cert(
            obj("""{"valid":true,"certInfo":{"subject":{"CN":"example.com"},"issuer":{"CN":"Cloudflare TLS Issuing ECC CA 3"}}}""")
        )
        assertTrue(c.valid)
        assertEquals("example.com", c.subject)
        assertEquals("Cloudflare TLS Issuing ECC CA 3", c.issuer)
    }

    @Test fun status_code_mapping() {
        assertEquals(MonitorStatus.DOWN, MonitorStatus.from(0))
        assertEquals(MonitorStatus.UP, MonitorStatus.from(1))
        assertEquals(MonitorStatus.PENDING, MonitorStatus.from(2))
        assertEquals(MonitorStatus.MAINTENANCE, MonitorStatus.from(3))
        assertEquals(MonitorStatus.PENDING, MonitorStatus.from(99)) // unknown -> pending
    }
}
