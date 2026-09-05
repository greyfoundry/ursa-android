package dev.astoris.ursa.core.storage

import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.data.model.ServerConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionBackupCodecTest {

    private val connection = ServerConnection(
        url = "https://kuma.example.com",
        username = "operator",
        jwt = "temporary-session",
        insecure = false,
        alias = "Home",
        headers = listOf(RequestHeader("CF-Access-Client-Id", "client-value")),
    )

    @Test fun round_trip_excludes_sessions_by_default() {
        val password = "correct horse".toCharArray()
        try {
            val preferences = PortablePreferences(
                dynamicColor = true,
                slowAlertsEnabled = true,
                perMonitorThresholds = mapOf("${connection.url}:7" to 2_500L),
                favoritesByServer = mapOf(connection.url to setOf(7, 9)),
            )
            val document = ConnectionBackupCodec.encrypt(
                ConnectionBackupData(listOf(connection), preferences),
                password,
            )
            val result = ConnectionBackupCodec.decrypt(document, password)
            assertTrue(result is BackupDecodeResult.Success)
            val decoded = (result as BackupDecodeResult.Success).data
            val restored = decoded.connections.single()
            assertEquals(connection.copy(jwt = null), restored)
            assertNull(restored.jwt)
            assertEquals(preferences, decoded.preferences)
        } finally {
            password.fill('\u0000')
        }
    }

    @Test fun optional_session_round_trip_and_wrong_password_rejection() {
        val password = "another password".toCharArray()
        val wrong = "wrong password".toCharArray()
        try {
            val document = ConnectionBackupCodec.encrypt(
                ConnectionBackupData(listOf(connection)),
                password,
                includeSessions = true,
            )
            val restored = ConnectionBackupCodec.decrypt(document, password) as BackupDecodeResult.Success
            assertEquals(connection, restored.data.connections.single())
            val rejected = ConnectionBackupCodec.decrypt(document, wrong)
            assertEquals(
                BackupError.WRONG_PASSWORD_OR_DAMAGED,
                (rejected as BackupDecodeResult.Error).reason,
            )
        } finally {
            password.fill('\u0000')
            wrong.fill('\u0000')
        }
    }
}
