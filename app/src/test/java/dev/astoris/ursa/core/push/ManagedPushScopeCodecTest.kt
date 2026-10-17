package dev.astoris.ursa.core.push

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.ServerConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ManagedPushScopeCodecTest {

    @Test fun registryRoundTripsOnlyPolicyMetadata() {
        val scope = ManagedPushScopeCodec.create(
            serverId = SERVER_ID,
            serverUrl = "https://kuma.example.test/base/",
            monitors = listOf(
                monitor(1, "Production", "group"),
                monitor(
                    id = 2,
                    name = "API",
                    type = "http",
                    url = "https://secret.example.test/private",
                    parentId = 1,
                    tags = listOf("critical", "backend", "critical"),
                ),
            ),
            updatedAtMillis = 123L,
        )!!

        val encoded = ManagedPushScopeCodec.encode(scope)
        val decoded = ManagedPushScopeCodec.decode(encoded)!!

        assertEquals("https://kuma.example.test/base", decoded.serverUrl)
        assertEquals(123L, decoded.updatedAtMillis)
        assertEquals(listOf("critical", "backend"), decoded.monitor(2)?.tags)
        assertEquals(1, decoded.monitor(2)?.parentId)
        assertFalse(encoded.contains("secret.example.test"))
    }

    @Test fun invalidScopeIdentityIsRejected() {
        assertNull(ManagedPushScopeCodec.create("bad", "https://kuma.example.test", emptyList()))
        assertNull(ManagedPushScopeCodec.create(SERVER_ID, "https://user:pass@example.test", emptyList()))
        assertNull(
            ManagedPushScopeCodec.decode(
                """{"version":2,"serverId":"$SERVER_ID","serverUrl":"https://kuma.example.test","monitors":[],"updatedAtMillis":1}""",
            ),
        )
    }

    @Test fun notificationActionTargetsOnlyItsBoundServer() {
        val first = ServerConnection("https://first.example.test", "first")
        val second = ServerConnection("https://second.example.test", "second")

        assertEquals(second, MonitorActionReceiver.targetConnection(listOf(first, second), second.url))
        assertNull(MonitorActionReceiver.targetConnection(listOf(first, second), "https://missing.example.test"))
    }

    @Test fun managedPolicyRequiresBoundProviderAndMonitor() {
        val scope = ManagedPushScopeCodec.create(
            SERVER_ID,
            "https://kuma.example.test",
            listOf(monitor(1, "API", "http")),
        )!!

        assertNull(ManagedPushScopePolicy.issue(null, null, 1))
        assertEquals(ManagedPushScopeIssue.UNKNOWN_PROVIDER, ManagedPushScopePolicy.issue(null, SERVER_ID, 1))
        assertEquals(ManagedPushScopeIssue.UNKNOWN_MONITOR, ManagedPushScopePolicy.issue(scope, SERVER_ID, 2))
        assertNull(ManagedPushScopePolicy.issue(scope, SERVER_ID, 1))
    }

    @Test fun serverBindingStatusSeparatesConfiguredNotConfiguredAndUnknown() {
        val scope = ManagedPushScopeCodec.create(
            SERVER_ID,
            "https://kuma.example.test/",
            emptyList(),
            updatedAtMillis = 123L,
        )!!

        assertEquals(
            ManagedPushServerBinding.Configured(123L),
            resolveManagedPushServerBinding("https://kuma.example.test", listOf(scope), true),
        )
        assertEquals(
            ManagedPushServerBinding.NotConfigured,
            resolveManagedPushServerBinding("https://other.example.test", listOf(scope), false),
        )
        assertEquals(
            ManagedPushServerBinding.Unknown,
            resolveManagedPushServerBinding("https://other.example.test", listOf(scope), true),
        )
    }

    private fun monitor(
        id: Int,
        name: String,
        type: String,
        url: String? = null,
        parentId: Int? = null,
        tags: List<String> = emptyList(),
    ) = Monitor(
        id = id,
        name = name,
        url = url,
        type = type,
        active = true,
        tags = tags,
        parentId = parentId,
    )

    private companion object {
        const val SERVER_ID = "0123456789abcdef0123456789abcdef"
    }
}
