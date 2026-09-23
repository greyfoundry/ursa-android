package dev.astoris.ursa.core.wear

import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.data.model.CleartextPolicy
import dev.astoris.ursa.data.model.ServerConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSessionTransferTest {
    @Test
    fun createsBoundedPairingPayloadWithAccessHeaders() {
        val transfer = WearSessionTransfer.from(
            ServerConnection(
                url = "https://kuma.example.com/base",
                username = "admin",
                jwt = "private-token",
                alias = "Home\nserver",
                headers = listOf(RequestHeader("CF-Access-Client-Id", "client-id")),
            ),
        )

        val root = Json.parseToJsonElement(requireNotNull(transfer).encode().decodeToString()).jsonObject
        val header = root.getValue("headers").jsonArray.single().jsonObject

        assertEquals("https://kuma.example.com/base", root.getValue("serverUrl").jsonPrimitive.content)
        assertEquals("private-token", root.getValue("sessionToken").jsonPrimitive.content)
        assertEquals("Home server", root.getValue("serverName").jsonPrimitive.content)
        assertEquals("CF-Access-Client-Id", header.getValue("name").jsonPrimitive.content)
        assertEquals("client-id", header.getValue("value").jsonPrimitive.content)
        assertEquals("2", root.getValue("protocolVersion").jsonPrimitive.content)
        assertEquals("1", root.getValue("policyVersion").jsonPrimitive.content)
        assertTrue(
            root.getValue("allowedCapabilities").jsonArray
                .any { it.jsonPrimitive.content == AccessCapability.MONITOR_STATE.name },
        )
        assertTrue(transfer.encode().size <= WearSessionTransfer.MAX_MESSAGE_BYTES)
    }

    @Test
    fun rejectsMissingSessionUnsafeTlsAndInvalidHeaders() {
        val base = ServerConnection("https://kuma.example.com", "admin", jwt = "token")

        assertNull(WearSessionTransfer.from(base.copy(jwt = null)))
        assertNull(WearSessionTransfer.from(base.copy(insecure = true)))
        assertNull(
            WearSessionTransfer.from(
                base.copy(url = "http://kuma.example.com", cleartextPolicy = CleartextPolicy.DENY),
            ),
        )
        assertNull(
            WearSessionTransfer.from(
                base.copy(headers = listOf(RequestHeader("X-Test", "bad\nvalue"))),
            ),
        )
        assertNull(
            WearSessionTransfer.from(
                base.copy(
                    headers = listOf(
                        RequestHeader("X-Test", "one"),
                        RequestHeader("x-test", "two"),
                    ),
                ),
            ),
        )
    }

    @Test fun restricted_profiles_never_route_to_legacy_watches() {
        val restricted = requireNotNull(
            WearSessionTransfer.from(
                ServerConnection(
                    url = "https://kuma.example.com",
                    username = "admin",
                    jwt = "token",
                    accessProfile = AccessProfile.VIEW_ONLY,
                ),
            ),
        )

        val targets = WearProtocolRouting.plan(
            version1Nodes = setOf("old", "new"),
            version2Nodes = setOf("new"),
            allowLegacy = restricted.canUseLegacyProtocol,
        )

        assertEquals(setOf("new"), targets.version2)
        assertTrue(targets.legacy.isEmpty())
        assertEquals(setOf("old"), targets.updateRequired)
    }

    @Test fun manage_profiles_keep_legacy_watch_compatibility() {
        val targets = WearProtocolRouting.plan(
            version1Nodes = setOf("old", "new"),
            version2Nodes = setOf("new"),
            allowLegacy = true,
        )

        assertEquals(setOf("new"), targets.version2)
        assertEquals(setOf("old"), targets.legacy)
        assertTrue(targets.updateRequired.isEmpty())
    }
}
