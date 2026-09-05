package dev.astoris.ursa.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConnectionTest {

    @Test fun displayName_prefers_trimmed_alias() {
        val connection = ServerConnection("https://kuma.example.com", "user", alias = "  Home  ")
        assertEquals("Home", connection.displayName)
    }

    @Test fun displayName_falls_back_to_host_and_port() {
        val connection = ServerConnection("https://kuma.example.com:3001/path", "user")
        assertEquals("kuma.example.com:3001", connection.displayName)
    }

    @Test fun old_serialized_connection_without_alias_still_decodes() {
        val decoded = Json.decodeFromString<ServerConnection>(
            """{"url":"https://kuma.example.com","username":"user","jwt":"token","insecure":false}""",
        )
        assertNull(decoded.alias)
        assertEquals(emptyList<RequestHeader>(), decoded.headers)
        assertEquals("kuma.example.com", decoded.displayName)
    }

    @Test fun request_header_normalizes_valid_input() {
        val header = RequestHeader("  X-Forwarded-User ", " operator@example.com ").normalizedOrNull()
        assertEquals(RequestHeader("X-Forwarded-User", "operator@example.com"), header)
    }

    @Test fun request_header_rejects_invalid_name_and_line_breaks() {
        assertNull(RequestHeader("Bad Header", "value").normalizedOrNull())
        assertNull(RequestHeader("X-Test", "good\r\ninjected: value").normalizedOrNull())
    }
}
