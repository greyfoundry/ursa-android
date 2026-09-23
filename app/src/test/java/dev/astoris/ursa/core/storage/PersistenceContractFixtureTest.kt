package dev.astoris.ursa.core.storage

import dev.astoris.ursa.core.push.PushAlertMode
import dev.astoris.ursa.core.push.PushAlertPreferenceKey
import dev.astoris.ursa.core.push.PushAlertTiming
import dev.astoris.ursa.core.push.PushAlertTimingCodec
import dev.astoris.ursa.core.push.PushPendingAlertCodec
import dev.astoris.ursa.core.push.PushQuietHours
import dev.astoris.ursa.core.push.PushSeverity
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.ui.widget.WidgetConfig
import dev.astoris.ursa.ui.widget.WidgetSource
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceContractFixtureTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun released_connections_decode_with_legacy_defaults() {
        val connections = json.decodeFromString<List<ServerConnection>>(fixture("connections_v0.json"))

        assertEquals(4, connections.size)
        assertEquals(
            ServerConnection(url = "https://minimal.example.test", username = ""),
            connections[0],
        )
        assertEquals("fixture-session-token", connections[1].jwt)
        assertEquals("Home", connections[1].alias)
        assertEquals(2, connections[2].headers.size)
        assertEquals("fixture-client-secret", connections[2].headers[1].value)
        assertTrue(connections[3].insecure)
        assertNull(connections[3].jwt)
    }

    @Test fun released_backup_v1_decrypts_with_sessions_headers_and_preferences() {
        val password = "ursa-fixture-password".toCharArray()
        try {
            val result = ConnectionBackupCodec.decrypt(fixture("connection_backup_v1.ursa"), password)
            assertTrue(result is BackupDecodeResult.Success)
            val data = (result as BackupDecodeResult.Success).data
            val connection = data.connections.single()

            assertEquals("https://legacy.example.test", connection.url)
            assertEquals("fixture-session", connection.jwt)
            assertEquals("fixture-header", connection.headers.single().value)
            assertTrue(connection.insecure)
            assertTrue(data.preferences.dynamicColor)
            assertTrue(data.preferences.slowAlertsEnabled)
            assertEquals(2_500, data.preferences.slowAlertThresholdMs)
            assertEquals(3_500L, data.preferences.perMonitorThresholds["${connection.url}:7"])
            assertEquals(setOf(7, 9), data.preferences.favoritesByServer[connection.url])
        } finally {
            password.fill('\u0000')
        }
    }

    @Test fun released_snapshot_decodes_without_rewriting_optional_fields() {
        val snapshot = SnapshotCodec.decode(fixture("monitor_snapshot_v1.json"))!!

        assertEquals(1_720_000_000_000, snapshot.updatedAt)
        assertEquals(2, snapshot.monitors.size)
        assertEquals(10, snapshot.monitors[0].parentId)
        assertEquals(20, snapshot.monitors[0].weight)
        assertEquals(listOf("prod", "public"), snapshot.monitors[0].tags)
        assertNull(snapshot.monitors[1].url)
        assertFalse(snapshot.monitors[1].active)
    }

    @Test fun released_push_alert_and_preference_keys_remain_decodable() {
        val serverId = "0123456789abcdef0123456789abcdef"
        val alert = PushPendingAlertCodec.decode(fixture("push_pending_alert_v1.json"))!!
        val preferences = keyValues("push_alert_preferences_v1.properties")

        assertEquals(serverId, alert.serverId)
        assertEquals(7, alert.monitorId)
        assertEquals(PushSeverity.STANDARD, alert.severity)
        assertEquals(PushAlertTiming(5, 15, 3), alert.timing)
        assertEquals(1, alert.deliveredCount)

        val modeKey = PushAlertPreferenceKey.mode(serverId, 7)!!
        val severityKey = PushAlertPreferenceKey.severity(serverId, 7)!!
        val timingKey = PushAlertPreferenceKey.timing(serverId, 7)!!
        val snoozeKey = PushAlertPreferenceKey.snooze(serverId, 7)!!
        assertEquals(PushAlertMode.DOWN_AND_RECOVERY, PushAlertMode.valueOf(preferences.getValue(modeKey)))
        assertEquals(PushSeverity.STANDARD, PushSeverity.valueOf(preferences.getValue(severityKey)))
        assertEquals(PushAlertTiming(5, 15, 3), PushAlertTimingCodec.decode(preferences[timingKey]))
        assertEquals(1_720_003_600_000, preferences.getValue(snoozeKey).toLong())
    }

    @Test fun released_quiet_hours_lock_and_widget_formats_remain_readable() {
        val quiet = keyValues("push_quiet_hours_v1.properties")
        val schedule = PushQuietHours(
            enabled = quiet.getValue("enabled").toBooleanStrict(),
            startMinute = quiet.getValue("start_minute").toInt(),
            endMinute = quiet.getValue("end_minute").toInt(),
            daysMask = quiet.getValue("days_mask").toInt(),
        )
        val lock = keyValues("lock_preferences_v1.properties")
        val widget = json.decodeFromString<WidgetConfig>(fixture("widget_config_v1.json"))

        assertEquals(PushQuietHours(enabled = true), schedule)
        assertTrue(lock.getValue("enabled").toBooleanStrict())
        assertEquals(WidgetSource.PRIVATE_SERVER, widget.source)
        assertEquals("https://minimal.example.test", widget.sourceId)
        assertEquals(setOf(1, 2, 7), widget.selectedMonitorIds)
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.getResource("/fixtures/$name"),
    ) { "Missing persistence fixture: $name" }.readText()

    private fun keyValues(name: String): Map<String, String> = fixture(name)
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith('#') }
        .associate { line ->
            val delimiter = line.indexOf('=')
            require(delimiter > 0) { "Invalid fixture entry in $name: $line" }
            line.substring(0, delimiter) to line.substring(delimiter + 1)
        }
}
