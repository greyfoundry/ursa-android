package dev.astoris.ursa.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorDraftCodecTest {

    @Test
    fun catalogCoversEveryKuma253MonitorTypeWithoutDuplicates() {
        val keys = MonitorTypeCatalog.all.map(MonitorTypeOption::key)

        assertEquals(33, keys.size)
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.containsAll(listOf("http", "globalping", "rabbitmq", "oracledb", "gamedig")))
    }

    @Test
    fun editingCommonFieldsPreservesUnknownAndSensitiveProperties() {
        val raw = Json.parseToJsonElement(
            """{
                "id":7,"type":"http","name":"Old","description":"before","url":"https://old.example",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "headers":"{\"X-Secret\":\"value\"}","basic_auth_pass":"hidden","futureField":{"x":1}
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(
            name = "New",
            description = "after",
            endpoint = "https://new.example/health",
            intervalSeconds = 30,
            retryIntervalSeconds = 15,
            maxRetries = 2,
        )

        val updated = MonitorDraftCodec.applyToExisting(raw, draft)

        assertEquals("New", updated["name"]!!.jsonPrimitive.content)
        assertEquals("https://new.example/health", updated["url"]!!.jsonPrimitive.content)
        assertEquals("hidden", updated["basic_auth_pass"]!!.jsonPrimitive.content)
        assertEquals("value", Json.parseToJsonElement(updated["headers"]!!.jsonPrimitive.content).jsonObject["X-Secret"]!!.jsonPrimitive.content)
        assertEquals(1, updated["futureField"]!!.jsonObject["x"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun newHttpAndPushPayloadsUseVerifiedKumaDefaults() {
        val http = MonitorDraftCodec.newPayload(
            MonitorDraft.create().copy(name = "Site", endpoint = "https://example.com/health"),
        )
        assertEquals("GET", http["method"]!!.jsonPrimitive.content)
        assertEquals("200-299", http["accepted_statuscodes"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(60, http["interval"]!!.jsonPrimitive.content.toInt())

        val push = MonitorDraftCodec.newPayload(
            MonitorDraft.create("push").copy(name = "Heartbeat"),
        )
        assertTrue(push["pushToken"]!!.jsonPrimitive.content.matches(Regex("^[a-f0-9]{32}$")))
    }

    @Test
    fun validationRequiresOnlyFieldsRelevantToTheSelectedType() {
        assertEquals(MonitorDraftError.NAME_REQUIRED, MonitorDraftCodec.validate(MonitorDraft.create()))
        assertEquals(
            MonitorDraftError.ENDPOINT_REQUIRED,
            MonitorDraftCodec.validate(MonitorDraft.create().copy(name = "Site")),
        )
        assertEquals(
            null,
            MonitorDraftCodec.validate(MonitorDraft.create("group").copy(name = "Production")),
        )
        assertEquals(
            MonitorDraftError.PORT_REQUIRED,
            MonitorDraftCodec.validate(MonitorDraft.create("port").copy(name = "SSH", endpoint = "host")),
        )
    }
}
