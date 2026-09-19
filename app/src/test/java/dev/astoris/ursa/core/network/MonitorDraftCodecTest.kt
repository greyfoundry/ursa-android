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
    fun catalogCoversEveryKuma255MonitorTypeWithoutDuplicates() {
        val keys = MonitorTypeCatalog.all.map(MonitorTypeOption::key)

        assertEquals(34, keys.size)
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.containsAll(listOf("http", "globalping", "rabbitmq", "sftp", "oracledb", "gamedig")))
    }

    @Test
    fun editingCommonFieldsPreservesUnknownAndSensitiveProperties() {
        val raw = Json.parseToJsonElement(
            """{
                "id":7,"type":"http","name":"Old","description":"before","url":"https://old.example",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "headers":"{\"X-Secret\":\"value\"}","basic_auth_pass":"hidden","futureField":{"x":1},
                "notificationIDList":{"4":true,"9":false},"parent":3,
                "tags":[{"tag_id":4,"monitor_id":7,"name":"Prod","color":"#e74c3c","value":"eu"}]
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(
            name = "New",
            description = "after",
            endpoint = "https://new.example/health",
            intervalSeconds = 30,
            retryIntervalSeconds = 15,
            maxRetries = 2,
            notificationIds = setOf(9),
            parentId = 5,
        )

        val updated = MonitorDraftCodec.applyToExisting(raw, draft)

        assertEquals("New", updated["name"]!!.jsonPrimitive.content)
        assertEquals("https://new.example/health", updated["url"]!!.jsonPrimitive.content)
        assertEquals("hidden", updated["basic_auth_pass"]!!.jsonPrimitive.content)
        assertEquals("value", Json.parseToJsonElement(updated["headers"]!!.jsonPrimitive.content).jsonObject["X-Secret"]!!.jsonPrimitive.content)
        assertEquals(1, updated["futureField"]!!.jsonObject["x"]!!.jsonPrimitive.content.toInt())
        assertEquals(setOf("9"), updated["notificationIDList"]!!.jsonObject.keys)
        assertEquals(true, updated["notificationIDList"]!!.jsonObject["9"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(5, updated["parent"]!!.jsonPrimitive.content.toInt())
        assertEquals("eu", draft.tagAssignments.single().value)
    }

    @Test
    fun editingSftpCommonFieldsPreservesCredentialsAndTypeSpecificConfiguration() {
        val raw = Json.parseToJsonElement(
            """{
                "id":12,"type":"sftp","name":"Old SFTP","description":"before",
                "hostname":"files.internal","port":22,"interval":60,"retryInterval":60,
                "resendInterval":0,"maxretries":0,"active":true,"notificationIDList":{},
                "sshAuthMethod":"privateKey","sshUsername":"monitor-user",
                "sshPassword":"fallback-secret","sshPrivateKey":"private-key-data",
                "sshPassphrase":"key-secret","sftpPath":"/incoming/health.txt"
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(
            name = "Primary SFTP",
            endpoint = "files.example.net",
            port = 2222,
        )

        val updated = MonitorDraftCodec.applyToExisting(raw, draft)

        assertEquals("Primary SFTP", updated["name"]!!.jsonPrimitive.content)
        assertEquals("files.example.net", updated["hostname"]!!.jsonPrimitive.content)
        assertEquals(2222, updated["port"]!!.jsonPrimitive.content.toInt())
        assertEquals("privateKey", updated["sshAuthMethod"]!!.jsonPrimitive.content)
        assertEquals("monitor-user", updated["sshUsername"]!!.jsonPrimitive.content)
        assertEquals("fallback-secret", updated["sshPassword"]!!.jsonPrimitive.content)
        assertEquals("private-key-data", updated["sshPrivateKey"]!!.jsonPrimitive.content)
        assertEquals("key-secret", updated["sshPassphrase"]!!.jsonPrimitive.content)
        assertEquals("/incoming/health.txt", updated["sftpPath"]!!.jsonPrimitive.content)
    }

    @Test
    fun newHttpAndPushPayloadsUseVerifiedKumaDefaults() {
        val http = MonitorDraftCodec.newPayload(
            MonitorDraft.create().copy(name = "Site", endpoint = "https://example.com/health"),
        )
        assertEquals("GET", http["method"]!!.jsonPrimitive.content)
        assertEquals("200-299", http["accepted_statuscodes"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals(60, http["interval"]!!.jsonPrimitive.content.toInt())

        val grouped = MonitorDraftCodec.newPayload(
            MonitorDraft.create("manual").copy(name = "Worker", parentId = 8),
        )
        assertEquals(8, grouped["parent"]!!.jsonPrimitive.content.toInt())

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
