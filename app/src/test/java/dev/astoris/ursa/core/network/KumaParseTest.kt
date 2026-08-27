package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.MonitorStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payloads below are real captures from live Uptime Kuma instances through 2.5.0
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

    @Test fun importantHeartbeatPage_is_camelcase_bean_json() {
        val rows = KumaParse.heartbeatRows(
            Json.parseToJsonElement(
                """[{"monitorID":3,"status":0,"time":"2026-08-25 19:26:16.694","msg":"TLS failed","ping":null,"important":true}]""",
            ).jsonArray,
        )

        assertEquals(1, rows.size)
        assertEquals(3, rows.single().monitorId)
        assertEquals(MonitorStatus.DOWN, rows.single().status)
        assertEquals("2026-08-25 19:26:16.694", rows.single().time)
        assertTrue(rows.single().important)
    }

    @Test fun managedPushNotifications_ignoreUnmarkedAndOtherProviders() {
        val rows = KumaParse.managedPushNotifications(
            Json.parseToJsonElement(
                """[
                    {"id":4,"name":"Personal webhook","isDefault":false,"config":"{\"type\":\"webhook\",\"webhookURL\":\"https://other.example\"}"},
                    {"id":9,"name":"URSA UnifiedPush","isDefault":true,"config":"{\"type\":\"webhook\",\"webhookURL\":\"https://push.example/topic?up=1\",\"ursaManaged\":true}"},
                    {"id":10,"name":"Marked mail","isDefault":false,"config":"{\"type\":\"smtp\",\"ursaManaged\":true}"}
                ]""",
            ).jsonArray,
        )

        assertEquals(1, rows.size)
        assertEquals(9, rows.single().id)
        assertEquals("https://push.example/topic?up=1", rows.single().webhookUrl)
        assertTrue(rows.single().isDefault)
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

    @Test fun chartRows_parse_253_aggregate_shape_and_skip_invalid_rows() {
        val rows = KumaParse.chartRows(
            Json.parseToJsonElement(
                """[{"up":57,"down":3,"avgPing":13.67,"minPing":8,"maxPing":42,"timestamp":1787612400},
                    {"up":0,"down":4,"avgPing":null,"timestamp":1787616000},
                    {"up":-1,"down":0,"avgPing":2,"timestamp":1787619600},
                    {"up":1,"down":0,"avgPing":2}]""",
            ).jsonArray,
        )

        assertEquals(2, rows.size)
        assertEquals(57L, rows.first().up)
        assertEquals(3L, rows.first().down)
        assertEquals(13.67, rows.first().avgPing!!, 0.001)
        assertEquals(1787612400L, rows.first().timestamp)
        assertNull(rows.last().avgPing)
    }

    @Test fun cert_extracts_validity_and_cns() {
        val c = KumaParse.cert(
            obj("""{"valid":true,"certInfo":{"subject":{"CN":"example.com"},"issuer":{"CN":"Cloudflare TLS Issuing ECC CA 3"}}}""")
        )
        assertTrue(c.valid)
        assertEquals("example.com", c.subject)
        assertEquals("Cloudflare TLS Issuing ECC CA 3", c.issuer)
    }

    @Test fun cert_extracts_expiry_fields() {
        val c = KumaParse.cert(
            obj("""{"valid":true,"certInfo":{"subject":{"CN":"api.example.com"},"issuer":{"CN":"R3"},"validTo":"2026-09-01T12:00:00.000Z","daysRemaining":12}}""")
        )
        assertEquals("2026-09-01T12:00:00.000Z", c.validTo)
        assertEquals(12, c.daysRemaining)
    }

    @Test fun cert_without_expiry_is_null() {
        val c = KumaParse.cert(obj("""{"valid":true,"certInfo":{"subject":{"CN":"x"},"issuer":{"CN":"y"}}}"""))
        assertNull(c.validTo)
        assertNull(c.daysRemaining)
    }

    @Test fun status_code_mapping() {
        assertEquals(MonitorStatus.DOWN, MonitorStatus.from(0))
        assertEquals(MonitorStatus.UP, MonitorStatus.from(1))
        assertEquals(MonitorStatus.PENDING, MonitorStatus.from(2))
        assertEquals(MonitorStatus.MAINTENANCE, MonitorStatus.from(3))
        assertEquals(MonitorStatus.PENDING, MonitorStatus.from(99)) // unknown -> pending
    }

    @Test fun positional_monitor_id_accepts_number_and_numeric_string() {
        assertEquals(7, KumaParse.positionalInt(7))
        assertEquals(7, KumaParse.positionalInt(7L))
        assertEquals(7, KumaParse.positionalInt("7"))
        assertNull(KumaParse.positionalInt("not-an-id"))
    }

    @Test fun positional_decimal_accepts_number_and_numeric_string() {
        assertEquals(13.67, KumaParse.positionalDouble(13.67)!!, 0.001)
        assertEquals(13.67, KumaParse.positionalDouble("13.67")!!, 0.001)
        assertNull(KumaParse.positionalDouble("not-a-number"))
    }
}
