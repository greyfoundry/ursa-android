package dev.astoris.ursa.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearStatusParserTest {
    @Test
    fun statusPageAddressPreservesReverseProxyBasePath() {
        val address = StatusPageAddress.parse("https://status.example.com/kuma/status/home")

        assertEquals("https://status.example.com/kuma", address?.baseUrl)
        assertEquals("home", address?.slug)
        assertEquals(
            "https://status.example.com/kuma/api/status-page/heartbeat/home",
            address?.heartbeatUrl,
        )
        assertNull(StatusPageAddress.parse("javascript://status/home"))
        assertNull(StatusPageAddress.parse("https://user:pass@example.com/status/home"))
        assertNull(StatusPageAddress.parse("https://example.com/status/a%2Fb"))
    }

    @Test
    fun publicPayloadProducesLatencyUptimeGroupsAndTags() {
        val snapshot = WearStatusParser.parse(CONFIG_JSON, HEARTBEAT_JSON)

        assertEquals("Home lab", snapshot.title)
        assertEquals(2, snapshot.monitors.size)
        assertEquals(1, snapshot.up)
        assertEquals(1, snapshot.down)
        with(snapshot.monitors.first { it.id == 7 }) {
            assertEquals("Core", group)
            assertEquals(WearMonitorStatus.UP, status)
            assertEquals(42, pingMs)
            assertEquals(0.999, uptime24h!!, 0.0001)
            assertEquals(listOf("production: eu", "public"), tags)
        }
    }

    @Test
    fun duplicatePublicMonitorCountsOnceAndAttentionSortsFirst() {
        val snapshot = WearStatusParser.parse(DUPLICATE_CONFIG_JSON, HEARTBEAT_JSON)

        assertEquals(2, snapshot.monitors.size)
        assertEquals(listOf(8, 7), snapshot.attentionFirst().map(WearMonitor::id))
    }

    @Test
    fun actionConfigRequiresSafeServerAndNonBlankToken() {
        assertTrue(WearActionConfig("https://kuma.example.com", "jwt").isReady)
        assertTrue(WearActionConfig("http://10.0.2.2:3001", "jwt").isReady)
        assertFalse(WearActionConfig("javascript://kuma", "jwt").isReady)
        assertFalse(WearActionConfig("https://user:pass@kuma.example.com", "jwt").isReady)
        assertFalse(WearActionConfig("https://kuma.example.com", "  ").isReady)
        assertFalse(
            WearActionConfig(
                "https://kuma.example.com",
                "jwt",
                listOf(WearActionHeader("X-Test", "bad\nvalue")),
            ).isReady,
        )
        assertFalse(
            WearActionConfig(
                "https://kuma.example.com",
                "jwt",
                listOf(
                    WearActionHeader("X-Test", "one"),
                    WearActionHeader("x-test", "two"),
                ),
            ).isReady,
        )
        assertFalse(
            WearActionConfig(
                "https://kuma.example.com",
                "jwt",
                List(9) { WearActionHeader("X-Test-$it", "value") },
            ).isReady,
        )
    }

    @Test
    fun displayCopyKeepsStatusAndMetricsConsistentAcrossSurfaces() {
        val snapshot = WearStatusParser.parse(CONFIG_JSON, HEARTBEAT_JSON)
        val api = snapshot.monitors.first { it.id == 7 }

        assertEquals("1 down · 1 up", WearDisplay.fleetSummary(snapshot))
        assertEquals("1 down", WearDisplay.complicationText(snapshot))
        assertEquals("42 ms · 99.9%", WearDisplay.metrics(api))
        assertEquals("No monitors", WearDisplay.complicationText(WearSnapshot("Empty", emptyList())))
    }

    private companion object {
        val CONFIG_JSON = """
            {
              "config": { "title": "Home lab" },
              "publicGroupList": [
                {
                  "name": "Core",
                  "monitorList": [
                    {
                      "id": 7,
                      "name": "API",
                      "tags": [
                        { "name": "production", "value": "eu" },
                        { "name": "public", "value": "" }
                      ]
                    },
                    { "id": 8, "name": "Database" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val DUPLICATE_CONFIG_JSON = """
            {
              "config": { "title": "Home lab" },
              "publicGroupList": [
                { "name": "Core", "monitorList": [{ "id": 7, "name": "API" }] },
                {
                  "name": "Public",
                  "monitorList": [
                    { "id": 7, "name": "API" },
                    { "id": 8, "name": "Database" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val HEARTBEAT_JSON = """
            {
              "heartbeatList": {
                "7": [{ "status": 1, "ping": 41 }, { "status": 1, "ping": 42 }],
                "8": [{ "status": 0, "ping": 105 }]
              },
              "uptimeList": { "7_24": 0.999, "8_24": 0.95 }
            }
        """.trimIndent()
    }
}
