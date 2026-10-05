package dev.astoris.ursa.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorDraftCodecTest {

    @Test
    fun catalogCoversEveryKuma255MonitorTypeWithoutDuplicates() {
        val keys = MonitorTypeCatalog.all.map(MonitorTypeOption::key)

        assertEquals(34, keys.size)
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.containsAll(listOf("http", "globalping", "rabbitmq", "sftp", "oracledb", "gamedig")))
        assertTrue(MonitorTypeCatalog.creatable.any { it.key == "sftp" })
        assertTrue(MonitorTypeCatalog.creatable.any { it.key == "mqtt" })
    }

    @Test
    fun newMqttPayloadMatchesKumaKeywordContract() {
        val draft = MonitorDraft.create("mqtt").copy(
            name = "Broker",
            endpoint = "wss://broker.example.net",
            port = 443,
            mqttUsername = "observer",
            mqttPassword = "secret",
            mqttTopic = "service/health",
            mqttWebsocketPath = "/mqtt",
            mqttCheckType = MqttCheckType.KEYWORD,
            mqttSuccessMessage = "ready",
        )

        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)

        assertEquals("mqtt", payload["type"]!!.jsonPrimitive.content)
        assertEquals("wss://broker.example.net", payload["hostname"]!!.jsonPrimitive.content)
        assertEquals(443, payload["port"]!!.jsonPrimitive.content.toInt())
        assertEquals("observer", payload["mqttUsername"]!!.jsonPrimitive.content)
        assertEquals("secret", payload["mqttPassword"]!!.jsonPrimitive.content)
        assertEquals("service/health", payload["mqttTopic"]!!.jsonPrimitive.content)
        assertEquals("/mqtt", payload["mqttWebsocketPath"]!!.jsonPrimitive.content)
        assertEquals("keyword", payload["mqttCheckType"]!!.jsonPrimitive.content)
        assertEquals("ready", payload["mqttSuccessMessage"]!!.jsonPrimitive.content)
        assertEquals("$", payload["jsonPath"]!!.jsonPrimitive.content)
        assertEquals("", payload["expectedValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun mqttEditRetainsMaskedPasswordConditionsAndFutureFields() {
        val raw = Json.parseToJsonElement(
            """{
                "id":44,"type":"mqtt","name":"Broker","hostname":"mqtts://broker.internal","port":8883,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"mqttUsername":"observer","mqttPassword":"saved-secret",
                "mqttTopic":"service/health","mqttWebsocketPath":"","mqttCheckType":"keyword",
                "mqttSuccessMessage":"ready","jsonPath":"payload.state","expectedValue":"ok",
                "conditions":[{"variable":"msg","operator":"contains","value":"ready"}],
                "futureMqtt":{"mode":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.mqttHasSavedPassword)
        assertEquals("", loaded.mqttPassword)
        val updated = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(name = "Primary broker", mqttTopic = "service/ready"),
        )!!

        assertEquals("saved-secret", updated["mqttPassword"]!!.jsonPrimitive.content)
        assertEquals("service/ready", updated["mqttTopic"]!!.jsonPrimitive.content)
        assertEquals(raw["conditions"], updated["conditions"])
        assertEquals(raw["futureMqtt"], updated["futureMqtt"])
    }

    @Test
    fun mqttPasswordCanBeReplacedOrExplicitlyRemoved() {
        val raw = Json.parseToJsonElement(
            """{
                "id":44,"type":"mqtt","name":"Broker","hostname":"broker.internal","port":1883,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"mqttUsername":"observer","mqttPassword":"saved-secret",
                "mqttTopic":"service/health","mqttCheckType":"keyword"
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        val replaced = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(mqttPassword = "replacement"),
        )!!
        val cleared = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(mqttClearSavedPassword = true),
        )!!

        assertEquals("replacement", replaced["mqttPassword"]!!.jsonPrimitive.content)
        assertEquals("", cleared["mqttPassword"]!!.jsonPrimitive.content)
    }

    @Test
    fun mqttNativeTextFieldsRoundTripWithoutNormalization() {
        val draft = MonitorDraft.create("mqtt").copy(
            name = "Exact broker fields",
            endpoint = "broker.example.net",
            port = 1883,
            mqttUsername = " observer ",
            mqttTopic = " service/health ",
            mqttSuccessMessage = " ready ",
        )

        val payload = MonitorDraftCodec.newPayload(draft)

        assertEquals(" observer ", payload["mqttUsername"]!!.jsonPrimitive.content)
        assertEquals(" service/health ", payload["mqttTopic"]!!.jsonPrimitive.content)
        assertEquals(" ready ", payload["mqttSuccessMessage"]!!.jsonPrimitive.content)
    }

    @Test
    fun mqttJsonQueryAndValidationFollowKumaContract() {
        val valid = MonitorDraft.create("mqtt").copy(
            name = "JSON broker",
            endpoint = "mqtt://broker.example.net",
            port = 1883,
            mqttTopic = "service/health",
            mqttCheckType = MqttCheckType.JSON_QUERY,
            mqttJsonQueryExpression = "payload.state",
            mqttJsonQueryExpectedValue = "ready",
        )

        assertNull(MonitorDraftCodec.validate(valid))
        val payload = MonitorDraftCodec.newPayload(valid)
        assertEquals("json-query", payload["mqttCheckType"]!!.jsonPrimitive.content)
        assertEquals("payload.state", payload["jsonPath"]!!.jsonPrimitive.content)
        assertEquals("ready", payload["expectedValue"]!!.jsonPrimitive.content)
        assertEquals(
            MonitorDraftError.MQTT_TOPIC_REQUIRED,
            MonitorDraftCodec.validate(valid.copy(mqttTopic = "")),
        )
        assertEquals(
            MonitorDraftError.MQTT_WEBSOCKET_PATH_INVALID,
            MonitorDraftCodec.validate(valid.copy(mqttWebsocketPath = "mqtt/path")),
        )
        assertEquals(
            MonitorDraftError.MQTT_ENDPOINT_INVALID,
            MonitorDraftCodec.validate(valid.copy(endpoint = "https://broker.example.net")),
        )
        assertEquals(
            MonitorDraftError.MQTT_JSON_QUERY_EXPRESSION_REQUIRED,
            MonitorDraftCodec.validate(valid.copy(mqttJsonQueryExpression = "")),
        )
        assertEquals(
            MonitorDraftError.MQTT_JSON_QUERY_EXPECTED_VALUE_REQUIRED,
            MonitorDraftCodec.validate(valid.copy(mqttJsonQueryExpectedValue = "")),
        )
    }

    @Test
    fun unknownFutureMqttCheckModeRemainsOpaque() {
        val raw = Json.parseToJsonElement(
            """{
                "id":44,"type":"mqtt","name":"Broker","hostname":"broker.internal","port":1883,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"mqttPassword":"saved-secret","mqttTopic":"service/health",
                "mqttCheckType":"future-script","mqttSuccessMessage":"ready","jsonPath":"x","expectedValue":"y"
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertFalse(loaded.mqttFieldsEditable)
        val updated = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(name = "Renamed"))!!
        assertEquals("Renamed", updated["name"]!!.jsonPrimitive.content)
        assertEquals("future-script", updated["mqttCheckType"]!!.jsonPrimitive.content)
        assertEquals("saved-secret", updated["mqttPassword"]!!.jsonPrimitive.content)
        assertEquals("ready", updated["mqttSuccessMessage"]!!.jsonPrimitive.content)
    }

    @Test
    fun newSmtpPayloadRequiresAndMapsAnExplicitSecurityMode() {
        val draft = MonitorDraft.create("smtp").copy(
            name = "Mail relay",
            endpoint = "smtp.example.net",
            port = 587,
            smtpSecurityMode = SmtpSecurityMode.STARTTLS,
        )

        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)

        assertEquals("smtp", payload["type"]!!.jsonPrimitive.content)
        assertEquals("smtp.example.net", payload["hostname"]!!.jsonPrimitive.content)
        assertEquals(587, payload["port"]!!.jsonPrimitive.content.toInt())
        assertEquals("starttls", payload["smtpSecurity"]!!.jsonPrimitive.content)
        assertEquals(
            MonitorDraftError.SMTP_SECURITY_REQUIRED,
            MonitorDraftCodec.validate(draft.copy(smtpSecurityMode = null)),
        )
        assertEquals(
            MonitorDraftError.SMTP_HOST_INVALID,
            MonitorDraftCodec.validate(draft.copy(endpoint = "smtp://smtp.example.net")),
        )
        assertEquals(
            MonitorDraftError.SMTP_HOST_INVALID,
            MonitorDraftCodec.validate(draft.copy(endpoint = "999.1.1.1")),
        )
        assertNull(MonitorDraftCodec.validate(draft.copy(endpoint = "2001:db8::25")))
    }

    @Test
    fun smtpEditChangesOnlyTheDeclaredMode() {
        val raw = Json.parseToJsonElement(
            """{
                "id":45,"type":"smtp","name":"Mail","hostname":"mail.internal","port":465,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"smtpSecurity":"secure","expiryNotification":true,
                "futureTls":{"minimum":"TLSv1.3"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertEquals(SmtpSecurityMode.SMTPS, loaded.smtpSecurityMode)
        val updated = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(smtpSecurityMode = SmtpSecurityMode.STARTTLS, port = 587),
        )!!
        assertEquals("starttls", updated["smtpSecurity"]!!.jsonPrimitive.content)
        assertEquals(587, updated["port"]!!.jsonPrimitive.content.toInt())
        assertEquals(raw["expiryNotification"], updated["expiryNotification"])
        assertEquals(raw["futureTls"], updated["futureTls"])
    }

    @Test
    fun nullAndUnknownSmtpSecurityModesRemainOpaque() {
        listOf("null", "\"future-tls\"").forEach { rawMode ->
            val raw = Json.parseToJsonElement(
                """{
                    "id":45,"type":"smtp","name":"Mail","hostname":"mail.internal","port":25,
                    "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                    "notificationIDList":{},"smtpSecurity":$rawMode,"futureTls":true
                }""",
            ).jsonObject
            val loaded = MonitorDraftCodec.from(raw)!!

            assertFalse(loaded.smtpSecurityEditable)
            assertEquals(
                MonitorDraftError.SMTP_HOST_INVALID,
                MonitorDraftCodec.validate(loaded.copy(endpoint = "smtp://mail.internal")),
            )
            val updated = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(name = "Renamed"))!!
            assertEquals(raw["smtpSecurity"], updated["smtpSecurity"])
            assertEquals(raw["futureTls"], updated["futureTls"])
        }
    }

    @Test
    fun newNtpPayloadMatchesKumaDefaultsAndWireFields() {
        val draft = MonitorDraft.create("ntp").copy(
            name = "Public time",
            endpoint = "time.google.com",
        )

        assertEquals(123, draft.port)
        assertEquals(300, draft.intervalSeconds)
        assertEquals(5, draft.ntpStratumThreshold)
        assertEquals(1_000, draft.ntpTimeOffsetThreshold)
        assertEquals(500, draft.ntpRootDispersionThreshold)
        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)

        assertEquals("ntp", payload["type"]!!.jsonPrimitive.content)
        assertEquals("time.google.com", payload["hostname"]!!.jsonPrimitive.content)
        assertEquals(123, payload["port"]!!.jsonPrimitive.content.toInt())
        assertEquals(300, payload["interval"]!!.jsonPrimitive.content.toInt())
        assertEquals(48, payload["timeout"]!!.jsonPrimitive.content.toInt())
        assertEquals(5, payload["ntpStratumThreshold"]!!.jsonPrimitive.content.toInt())
        assertEquals(1_000, payload["ntpTimeOffsetThreshold"]!!.jsonPrimitive.content.toInt())
        assertEquals(500, payload["ntpRootDispersionThreshold"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun ntpValidationMatchesKumaThresholdBounds() {
        val draft = MonitorDraft.create("ntp").copy(
            name = "Time",
            endpoint = "time.internal",
        )

        assertEquals(
            MonitorDraftError.NTP_HOST_INVALID,
            MonitorDraftCodec.validate(draft.copy(endpoint = "ntp://time.internal")),
        )
        assertEquals(
            MonitorDraftError.NTP_STRATUM_THRESHOLD_INVALID,
            MonitorDraftCodec.validate(draft.copy(ntpStratumThreshold = 16)),
        )
        assertEquals(
            MonitorDraftError.NTP_TIME_OFFSET_THRESHOLD_INVALID,
            MonitorDraftCodec.validate(draft.copy(ntpTimeOffsetThreshold = 0)),
        )
        assertEquals(
            MonitorDraftError.NTP_ROOT_DISPERSION_THRESHOLD_INVALID,
            MonitorDraftCodec.validate(draft.copy(ntpRootDispersionThreshold = 0)),
        )
        assertNull(
            MonitorDraftCodec.validate(
                draft.copy(
                    endpoint = "2001:4860:4806:8::",
                    ntpStratumThreshold = null,
                    ntpTimeOffsetThreshold = null,
                    ntpRootDispersionThreshold = null,
                ),
            ),
        )
    }

    @Test
    fun ntpEditChangesThresholdsWithoutNormalizingNullOrFutureFields() {
        val raw = Json.parseToJsonElement(
            """{
                "id":46,"type":"ntp","name":"Time","hostname":"time.internal","port":123,
                "interval":300,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"ntpStratumThreshold":5,"ntpTimeOffsetThreshold":null,
                "ntpRootDispersionThreshold":500,"futureNtp":{"version":4}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertEquals(5, loaded.ntpStratumThreshold)
        assertNull(loaded.ntpTimeOffsetThreshold)
        val updated = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(ntpStratumThreshold = 4, ntpRootDispersionThreshold = 250),
        )!!
        assertEquals(4, updated["ntpStratumThreshold"]!!.jsonPrimitive.content.toInt())
        assertEquals(JsonNull, updated["ntpTimeOffsetThreshold"])
        assertEquals(250, updated["ntpRootDispersionThreshold"]!!.jsonPrimitive.content.toInt())
        assertEquals(raw["futureNtp"], updated["futureNtp"])
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

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)!!

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
                "resendInterval":0,"maxretries":0,"active":false,"notificationIDList":{},
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

        assertEquals("monitor-user", draft.sftpUsername)
        assertEquals("/incoming/health.txt", draft.sftpPath)
        assertEquals(SftpAuthMethod.PRIVATE_KEY, draft.sftpAuthMethod)
        assertEquals("", draft.sftpPassword)
        assertEquals("", draft.sftpPrivateKey)
        assertEquals("", draft.sftpPassphrase)
        assertTrue(draft.sftpHasSavedPassword)
        assertTrue(draft.sftpHasSavedPrivateKey)
        assertTrue(draft.sftpHasSavedPassphrase)
        assertFalse(draft.active)

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)!!

        assertEquals("Primary SFTP", updated["name"]!!.jsonPrimitive.content)
        assertEquals("files.example.net", updated["hostname"]!!.jsonPrimitive.content)
        assertEquals(2222, updated["port"]!!.jsonPrimitive.content.toInt())
        assertEquals("privateKey", updated["sshAuthMethod"]!!.jsonPrimitive.content)
        assertEquals("monitor-user", updated["sshUsername"]!!.jsonPrimitive.content)
        assertEquals("fallback-secret", updated["sshPassword"]!!.jsonPrimitive.content)
        assertEquals("private-key-data", updated["sshPrivateKey"]!!.jsonPrimitive.content)
        assertEquals("key-secret", updated["sshPassphrase"]!!.jsonPrimitive.content)
        assertEquals("/incoming/health.txt", updated["sftpPath"]!!.jsonPrimitive.content)
        assertFalse(updated["active"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun newSftpPayloadSupportsPasswordAuthentication() {
        val draft = MonitorDraft.create("sftp").copy(
            name = "Files",
            endpoint = "files.example.net",
            sftpUsername = "monitor",
            sftpPassword = "password with spaces",
            sftpPath = "/health/ready.txt",
        )

        assertEquals(null, MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)

        assertEquals("sftp", payload["type"]!!.jsonPrimitive.content)
        assertEquals("files.example.net", payload["hostname"]!!.jsonPrimitive.content)
        assertEquals(22, payload["port"]!!.jsonPrimitive.content.toInt())
        assertEquals(10, payload["timeout"]!!.jsonPrimitive.content.toInt())
        assertEquals("password", payload["sshAuthMethod"]!!.jsonPrimitive.content)
        assertEquals("monitor", payload["sshUsername"]!!.jsonPrimitive.content)
        assertEquals("password with spaces", payload["sshPassword"]!!.jsonPrimitive.content)
        assertEquals("", payload["sshPrivateKey"]!!.jsonPrimitive.content)
        assertEquals("", payload["sshPassphrase"]!!.jsonPrimitive.content)
        assertEquals("/health/ready.txt", payload["sftpPath"]!!.jsonPrimitive.content)
    }

    @Test
    fun newSftpPayloadSupportsPrivateKeyAuthentication() {
        val draft = MonitorDraft.create("sftp").copy(
            name = "Files",
            endpoint = "files.example.net",
            sftpAuthMethod = SftpAuthMethod.PRIVATE_KEY,
            sftpUsername = "monitor",
            sftpPrivateKey = "-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----",
            sftpPassphrase = "key secret",
        )

        assertEquals(null, MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)

        assertEquals("privateKey", payload["sshAuthMethod"]!!.jsonPrimitive.content)
        assertEquals("", payload["sshPassword"]!!.jsonPrimitive.content)
        assertTrue(payload["sshPrivateKey"]!!.jsonPrimitive.content.startsWith("-----BEGIN"))
        assertEquals("key secret", payload["sshPassphrase"]!!.jsonPrimitive.content)
    }

    @Test
    fun changingSftpAuthenticationReplacesOnlySubmittedSecrets() {
        val raw = Json.parseToJsonElement(
            """{
                "id":12,"type":"sftp","name":"SFTP","hostname":"files.internal","port":22,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"sshAuthMethod":"privateKey","sshUsername":"monitor",
                "sshPassword":"old-password","sshPrivateKey":"old-key","sshPassphrase":"old-passphrase"
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(
            sftpAuthMethod = SftpAuthMethod.PASSWORD,
            sftpPassword = "new-password",
        )

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)!!

        assertEquals("password", updated["sshAuthMethod"]!!.jsonPrimitive.content)
        assertEquals("new-password", updated["sshPassword"]!!.jsonPrimitive.content)
        assertEquals("", updated["sshPrivateKey"]!!.jsonPrimitive.content)
        assertEquals("", updated["sshPassphrase"]!!.jsonPrimitive.content)
    }

    @Test
    fun replacingSftpPrivateKeyDoesNotReuseOldPassphrase() {
        val raw = Json.parseToJsonElement(
            """{
                "id":12,"type":"sftp","name":"SFTP","hostname":"files.internal","port":22,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"sshAuthMethod":"privateKey","sshUsername":"monitor",
                "sshPrivateKey":"old-key","sshPassphrase":"old-passphrase"
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(sftpPrivateKey = "new-key")

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)!!

        assertEquals("new-key", updated["sshPrivateKey"]!!.jsonPrimitive.content)
        assertEquals("", updated["sshPassphrase"]!!.jsonPrimitive.content)
        assertFalse(draft.sftpClearSavedPassphrase)
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

        val pushDraft = MonitorDraft.create("push").copy(name = "Heartbeat")
        val push = MonitorDraftCodec.newPayload(pushDraft)
        assertTrue(pushDraft.pushToken.matches(Regex("^[A-Za-z0-9]{32}$")))
        assertEquals(pushDraft.pushToken, push["pushToken"]!!.jsonPrimitive.content)
        assertEquals(
            "https://kuma.example/base/api/push/${pushDraft.pushToken}?status=up&msg=OK&ping=",
            MonitorDraftCodec.pushUrl("https://kuma.example/base/", pushDraft.pushToken),
        )
        val existingPush = MonitorDraftCodec.from(
            Json.parseToJsonElement(
                """{"id":9,"type":"push","name":"Agent","pushToken":"${pushDraft.pushToken}"}""",
            ).jsonObject,
        )!!
        assertEquals(pushDraft.pushToken, existingPush.pushToken)
        assertNull(MonitorDraftCodec.validate(existingPush))
        assertNull(MonitorDraftCodec.pushUrl("https://user:secret@kuma.example", pushDraft.pushToken))
        assertNull(MonitorDraftCodec.pushUrl("https://kuma.example", "invalid"))
    }

    @Test
    fun keywordCreateAndGuardedEditUseExactKumaFields() {
        val created = MonitorDraftCodec.newPayload(
            MonitorDraft.create("keyword").copy(
                name = "Maintenance marker",
                endpoint = "https://example.com/health",
                keyword = "maintenance",
                invertKeyword = true,
            ),
        )
        assertEquals("keyword", created["type"]!!.jsonPrimitive.content)
        assertEquals("maintenance", created["keyword"]!!.jsonPrimitive.content)
        assertTrue(created["invertKeyword"]!!.jsonPrimitive.content.toBoolean())

        val raw = Json.parseToJsonElement(
            """{
                "id":19,"type":"keyword","name":"Marker","url":"https://example.com",
                "keyword":"before","invertKeyword":false,"interval":60,"retryInterval":60,
                "resendInterval":0,"maxretries":0,"active":true,"notificationIDList":{},
                "headers":"{\"Authorization\":\"secret\"}","future":{"mode":"kept"}
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(keyword = "after", invertKeyword = true)

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)!!

        assertEquals("after", updated["keyword"]!!.jsonPrimitive.content)
        assertTrue(updated["invertKeyword"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(raw["headers"], updated["headers"])
        assertEquals(raw["future"], updated["future"])
    }

    @Test
    fun jsonQueryCreateAndGuardedEditUseExactKumaFields() {
        val created = MonitorDraftCodec.newPayload(
            MonitorDraft.create("json-query").copy(
                name = "API state",
                endpoint = "https://example.com/health.json",
                jsonQueryExpression = "status",
                jsonQueryOperator = "==",
                jsonQueryExpectedValue = "ready",
            ),
        )
        assertEquals("status", created["jsonPath"]!!.jsonPrimitive.content)
        assertEquals("==", created["jsonPathOperator"]!!.jsonPrimitive.content)
        assertEquals("ready", created["expectedValue"]!!.jsonPrimitive.content)

        val raw = Json.parseToJsonElement(
            """{
                "id":20,"type":"json-query","name":"API","url":"https://example.com",
                "jsonPath":"before","jsonPathOperator":"==","expectedValue":"old",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{},"headers":"{\"X-Key\":\"secret\"}",
                "retryOnlyOnStatusCodeFailure":true,"future":{"mode":"kept"}
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(
            jsonQueryExpression = "after",
            jsonQueryOperator = "contains",
            jsonQueryExpectedValue = "ready",
        )

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)!!

        assertEquals("after", updated["jsonPath"]!!.jsonPrimitive.content)
        assertEquals("contains", updated["jsonPathOperator"]!!.jsonPrimitive.content)
        assertEquals("ready", updated["expectedValue"]!!.jsonPrimitive.content)
        assertEquals(raw["headers"], updated["headers"])
        assertEquals(raw["retryOnlyOnStatusCodeFailure"], updated["retryOnlyOnStatusCodeFailure"])
        assertEquals(raw["future"], updated["future"])
    }

    @Test
    fun websocketTransportCreateAndGuardedEditUseExactKumaFields() {
        val created = MonitorDraftCodec.newPayload(
            MonitorDraft.create("websocket-upgrade").copy(
                name = "Realtime API",
                endpoint = "wss://example.com/socket",
                websocketSubprotocols = "graphql-ws, graphql-transport-ws, graphql-ws",
                websocketAcceptedCodes = "1000, 1001, 3000, 1000",
                websocketIgnoreAcceptHeader = true,
                websocketHeaders = listOf(
                    MonitorHeaderDraft(name = "X-Tenant", value = "primary"),
                    MonitorHeaderDraft(name = "X-Trace", value = "enabled"),
                ),
            ),
        )
        assertEquals("wss://example.com/socket", created["url"]!!.jsonPrimitive.content)
        assertEquals("graphql-ws, graphql-transport-ws", created["wsSubprotocol"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("1000", "1001", "3000"),
            created["accepted_statuscodes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(created["wsIgnoreSecWebsocketAcceptHeader"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            mapOf("X-Tenant" to "primary", "X-Trace" to "enabled"),
            Json.parseToJsonElement(created["headers"]!!.jsonPrimitive.content).jsonObject
                .mapValues { it.value.jsonPrimitive.content },
        )

        val raw = Json.parseToJsonElement(
            """{
                "id":21,"type":"websocket-upgrade","name":"Socket","url":"ws://example.com/socket",
                "wsSubprotocol":"chat","accepted_statuscodes":["1000"],
                "wsIgnoreSecWebsocketAcceptHeader":false,"interval":60,"retryInterval":60,
                "resendInterval":0,"maxretries":0,"active":true,"notificationIDList":{},
                "headers":"{\"X-API-Key\":\"opaque-value\",\"X-Tenant\":\"primary\"}","authMethod":"bearer",
                "bearer_token":"secret","tlsKey":"private-key","future":{"mode":"kept"}
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(
            endpoint = "wss://example.com/socket",
            websocketSubprotocols = "chat, telemetry",
            websocketAcceptedCodes = "1000, 4001",
            websocketIgnoreAcceptHeader = true,
            websocketHeaders = listOf(
                MonitorDraftCodec.from(raw)!!.websocketHeaders.first(),
                MonitorHeaderDraft(name = "X-Trace", value = "replacement"),
            ),
        )

        assertEquals(listOf("X-API-Key", "X-Tenant"), MonitorDraftCodec.from(raw)!!.websocketHeaders.map { it.name })
        assertTrue(MonitorDraftCodec.from(raw)!!.websocketHeaders.all { it.value.isEmpty() && it.hasSavedValue })

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)!!

        assertEquals("wss://example.com/socket", updated["url"]!!.jsonPrimitive.content)
        assertEquals("chat, telemetry", updated["wsSubprotocol"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("1000", "4001"),
            updated["accepted_statuscodes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(updated["wsIgnoreSecWebsocketAcceptHeader"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            mapOf("X-API-Key" to "opaque-value", "X-Trace" to "replacement"),
            Json.parseToJsonElement(updated["headers"]!!.jsonPrimitive.content).jsonObject
                .mapValues { it.value.jsonPrimitive.content },
        )
        assertEquals(raw["authMethod"], updated["authMethod"])
        assertEquals(raw["bearer_token"], updated["bearer_token"])
        assertEquals(raw["tlsKey"], updated["tlsKey"])
        assertEquals(raw["future"], updated["future"])
    }

    @Test
    fun websocketValidationRequiresItsProtocolAndSupportedCloseCodes() {
        val base = MonitorDraft.create("websocket-upgrade").copy(
            name = "Socket",
            endpoint = "wss://example.com/socket",
        )

        assertNull(MonitorDraftCodec.validate(base))
        assertEquals(
            MonitorDraftError.INVALID_URL,
            MonitorDraftCodec.validate(base.copy(endpoint = "https://example.com/socket")),
        )
        assertEquals(
            MonitorDraftError.WEBSOCKET_ACCEPTED_CODES_REQUIRED,
            MonitorDraftCodec.validate(base.copy(websocketAcceptedCodes = "")),
        )
        listOf("999", "5000", "1000, nope", "1000,").forEach { invalid ->
            assertEquals(
                MonitorDraftError.WEBSOCKET_ACCEPTED_CODE_INVALID,
                MonitorDraftCodec.validate(base.copy(websocketAcceptedCodes = invalid)),
            )
        }
        assertNull(
            MonitorDraftCodec.validate(
                MonitorDraft.create().copy(name = "Site", endpoint = "https://example.com"),
            ),
        )

        assertEquals(
            MonitorDraftError.WEBSOCKET_HEADER_VALUE_REQUIRED,
            MonitorDraftCodec.validate(
                base.copy(websocketHeaders = listOf(MonitorHeaderDraft(name = "X-New"))),
            ),
        )
        assertEquals(
            MonitorDraftError.WEBSOCKET_HEADER_INVALID,
            MonitorDraftCodec.validate(
                base.copy(websocketHeaders = listOf(MonitorHeaderDraft(name = "Bad Header", value = "value"))),
            ),
        )
        assertEquals(
            MonitorDraftError.WEBSOCKET_HEADER_DUPLICATE,
            MonitorDraftCodec.validate(
                base.copy(
                    websocketHeaders = listOf(
                        MonitorHeaderDraft(name = "X-Test", value = "one"),
                        MonitorHeaderDraft(name = "x-test", value = "two"),
                    ),
                ),
            ),
        )
        assertEquals(
            MonitorDraftError.WEBSOCKET_BASIC_PASSWORD_REQUIRED,
            MonitorDraftCodec.validate(base.copy(websocketAuthMethod = WebSocketAuthMethod.BASIC)),
        )
        assertEquals(
            MonitorDraftError.WEBSOCKET_BEARER_TOKEN_REQUIRED,
            MonitorDraftCodec.validate(base.copy(websocketAuthMethod = WebSocketAuthMethod.BEARER)),
        )
    }

    @Test
    fun websocketUnsupportedHeaderShapesStayOpaque() {
        val raw = Json.parseToJsonElement(
            """{
                "id":22,"type":"websocket-upgrade","name":"Socket","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"headers":"{\"X-List\":[\"one\",\"two\"]}",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!

        assertFalse(draft.websocketHeadersEditable)
        assertTrue(draft.websocketHeaders.isEmpty())
        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft.copy(name = "Renamed"))!!
        assertEquals(raw["headers"], updated["headers"])

        val withoutHeaders = Json.parseToJsonElement(
            """{
                "id":23,"type":"websocket-upgrade","name":"No headers","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"interval":60,"retryInterval":60,
                "resendInterval":0,"maxretries":0,"active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val withoutHeadersDraft = MonitorDraftCodec.from(withoutHeaders)!!.copy(name = "Still no headers")
        assertFalse("headers" in MonitorDraftCodec.safeExistingPayload(withoutHeaders, withoutHeadersDraft)!!)
    }

    @Test
    fun websocketBasicAndBearerSecretsUseMaskedReplacementSemantics() {
        val basicCreated = MonitorDraftCodec.newPayload(
            MonitorDraft.create("websocket-upgrade").copy(
                name = "Basic socket",
                endpoint = "wss://example.com/socket",
                websocketAuthMethod = WebSocketAuthMethod.BASIC,
                websocketBasicUsername = "monitor-user",
                websocketBasicPassword = "new-password",
            ),
        )
        assertEquals("basic", basicCreated["authMethod"]!!.jsonPrimitive.content)
        assertEquals("monitor-user", basicCreated["basic_auth_user"]!!.jsonPrimitive.content)
        assertEquals("new-password", basicCreated["basic_auth_pass"]!!.jsonPrimitive.content)

        val rawBasic = Json.parseToJsonElement(
            """{
                "id":24,"type":"websocket-upgrade","name":"Basic","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"authMethod":"basic",
                "basic_auth_user":"saved-user","basic_auth_pass":"saved-password",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{},"future":{"kept":true}
            }""",
        ).jsonObject
        val basicDraft = MonitorDraftCodec.from(rawBasic)!!
        assertEquals(WebSocketAuthMethod.BASIC, basicDraft.websocketAuthMethod)
        assertEquals("saved-user", basicDraft.websocketBasicUsername)
        assertTrue(basicDraft.websocketBasicPassword.isEmpty())
        assertTrue(basicDraft.websocketHasSavedBasicPassword)
        val retainedBasic = MonitorDraftCodec.safeExistingPayload(rawBasic, basicDraft.copy(name = "Renamed"))!!
        assertEquals("saved-password", retainedBasic["basic_auth_pass"]!!.jsonPrimitive.content)
        assertEquals(rawBasic["future"], retainedBasic["future"])
        val switchedToBearer = MonitorDraftCodec.safeExistingPayload(
            rawBasic,
            basicDraft.copy(
                websocketAuthMethod = WebSocketAuthMethod.BEARER,
                websocketBearerToken = "replacement-token",
            ),
        )!!
        assertEquals("bearer", switchedToBearer["authMethod"]!!.jsonPrimitive.content)
        assertEquals("", switchedToBearer["basic_auth_user"]!!.jsonPrimitive.content)
        assertEquals("", switchedToBearer["basic_auth_pass"]!!.jsonPrimitive.content)
        assertEquals("replacement-token", switchedToBearer["bearer_token"]!!.jsonPrimitive.content)

        val bearerCreated = MonitorDraftCodec.newPayload(
            MonitorDraft.create("websocket-upgrade").copy(
                name = "Bearer socket",
                endpoint = "wss://example.com/socket",
                websocketAuthMethod = WebSocketAuthMethod.BEARER,
                websocketBearerToken = "new-token",
            ),
        )
        assertEquals("bearer", bearerCreated["authMethod"]!!.jsonPrimitive.content)
        assertEquals("new-token", bearerCreated["bearer_token"]!!.jsonPrimitive.content)

        val rawBearer = Json.parseToJsonElement(
            """{
                "id":25,"type":"websocket-upgrade","name":"Bearer","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"authMethod":"bearer","bearer_token":"saved-token",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val bearerDraft = MonitorDraftCodec.from(rawBearer)!!
        assertEquals(WebSocketAuthMethod.BEARER, bearerDraft.websocketAuthMethod)
        assertTrue(bearerDraft.websocketBearerToken.isEmpty())
        assertTrue(bearerDraft.websocketHasSavedBearerToken)
        val replacedBearer = MonitorDraftCodec.safeExistingPayload(
            rawBearer,
            bearerDraft.copy(websocketBearerToken = "replacement-token"),
        )!!
        assertEquals("replacement-token", replacedBearer["bearer_token"]!!.jsonPrimitive.content)
        val clearedBearer = MonitorDraftCodec.safeExistingPayload(
            rawBearer,
            bearerDraft.copy(websocketAuthMethod = WebSocketAuthMethod.NONE),
        )!!
        assertTrue(clearedBearer["authMethod"] is JsonNull)
        assertEquals("", clearedBearer["bearer_token"]!!.jsonPrimitive.content)
    }

    @Test
    fun websocketOAuthAndMtlsSecretsUseMaskedReplacementSemantics() {
        val createdOAuth = MonitorDraftCodec.newPayload(
            MonitorDraft.create("websocket-upgrade").copy(
                name = "OAuth socket",
                endpoint = "wss://example.com/socket",
                websocketAuthMethod = WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS,
                websocketOAuthAuthMethod = WebSocketOAuthAuthMethod.FORM_BODY,
                websocketOAuthTokenUrl = "https://id.example.com/token",
                websocketOAuthClientId = "client",
                websocketOAuthClientSecret = "new-secret",
                websocketOAuthScopes = "status read",
                websocketOAuthAudience = "monitoring",
            ),
        )
        assertEquals("oauth2-cc", createdOAuth["authMethod"]!!.jsonPrimitive.content)
        assertEquals("client_secret_post", createdOAuth["oauth_auth_method"]!!.jsonPrimitive.content)
        assertEquals("https://id.example.com/token", createdOAuth["oauth_token_url"]!!.jsonPrimitive.content)
        assertEquals("client", createdOAuth["oauth_client_id"]!!.jsonPrimitive.content)
        assertEquals("new-secret", createdOAuth["oauth_client_secret"]!!.jsonPrimitive.content)
        assertEquals("status read", createdOAuth["oauth_scopes"]!!.jsonPrimitive.content)
        assertEquals("monitoring", createdOAuth["oauth_audience"]!!.jsonPrimitive.content)

        val rawOAuth = Json.parseToJsonElement(
            """{
                "id":26,"type":"websocket-upgrade","name":"OAuth","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"authMethod":"oauth2-cc",
                "oauth_auth_method":"client_secret_post","oauth_token_url":"https://id.example.com/token",
                "oauth_client_id":"client","oauth_client_secret":"saved-secret",
                "oauth_scopes":"status read","oauth_audience":"monitoring",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{},"future":{"kept":true}
            }""",
        ).jsonObject
        val oauthDraft = MonitorDraftCodec.from(rawOAuth)!!

        assertTrue(oauthDraft.websocketAuthEditable)
        assertEquals(WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS, oauthDraft.websocketAuthMethod)
        assertEquals(WebSocketOAuthAuthMethod.FORM_BODY, oauthDraft.websocketOAuthAuthMethod)
        assertTrue(oauthDraft.websocketOAuthClientSecret.isEmpty())
        assertTrue(oauthDraft.websocketHasSavedOAuthClientSecret)
        assertNull(MonitorDraftCodec.validate(oauthDraft))
        val retainedOAuth = MonitorDraftCodec.safeExistingPayload(rawOAuth, oauthDraft.copy(name = "Renamed"))!!
        assertEquals("saved-secret", retainedOAuth["oauth_client_secret"]!!.jsonPrimitive.content)
        assertEquals(rawOAuth["future"], retainedOAuth["future"])
        val replacedOAuth = MonitorDraftCodec.safeExistingPayload(
            rawOAuth,
            oauthDraft.copy(websocketOAuthClientSecret = "replacement-secret"),
        )!!
        assertEquals("replacement-secret", replacedOAuth["oauth_client_secret"]!!.jsonPrimitive.content)

        val rawMtls = Json.parseToJsonElement(
            """{
                "id":27,"type":"websocket-upgrade","name":"mTLS","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"authMethod":"mtls",
                "tlsCert":"saved-certificate","tlsKey":"saved-private-key","tlsCa":"saved-ca",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val mtlsDraft = MonitorDraftCodec.from(rawMtls)!!
        assertEquals(WebSocketAuthMethod.MTLS, mtlsDraft.websocketAuthMethod)
        assertTrue(mtlsDraft.websocketTlsCertificate.isEmpty())
        assertTrue(mtlsDraft.websocketTlsPrivateKey.isEmpty())
        assertTrue(mtlsDraft.websocketTlsCaCertificate.isEmpty())
        assertTrue(mtlsDraft.websocketHasSavedTlsCertificate)
        assertTrue(mtlsDraft.websocketHasSavedTlsPrivateKey)
        assertTrue(mtlsDraft.websocketHasSavedTlsCaCertificate)
        assertNull(MonitorDraftCodec.validate(mtlsDraft))
        val retainedMtls = MonitorDraftCodec.safeExistingPayload(rawMtls, mtlsDraft)!!
        assertEquals("saved-certificate", retainedMtls["tlsCert"]!!.jsonPrimitive.content)
        assertEquals("saved-private-key", retainedMtls["tlsKey"]!!.jsonPrimitive.content)
        assertEquals("saved-ca", retainedMtls["tlsCa"]!!.jsonPrimitive.content)
        val clearedCa = MonitorDraftCodec.safeExistingPayload(
            rawMtls,
            mtlsDraft.copy(websocketClearSavedTlsCaCertificate = true),
        )!!
        assertEquals("", clearedCa["tlsCa"]!!.jsonPrimitive.content)

        val createdMtls = MonitorDraftCodec.newPayload(
            MonitorDraft.create("websocket-upgrade").copy(
                name = "mTLS socket",
                endpoint = "wss://example.com/socket",
                websocketAuthMethod = WebSocketAuthMethod.MTLS,
                websocketTlsCertificate = "certificate",
                websocketTlsPrivateKey = "private-key",
                websocketTlsCaCertificate = "ca-certificate",
            ),
        )
        assertEquals("mtls", createdMtls["authMethod"]!!.jsonPrimitive.content)
        assertEquals("certificate", createdMtls["tlsCert"]!!.jsonPrimitive.content)
        assertEquals("private-key", createdMtls["tlsKey"]!!.jsonPrimitive.content)
        assertEquals("ca-certificate", createdMtls["tlsCa"]!!.jsonPrimitive.content)
    }

    @Test
    fun websocketUnknownAuthenticationRemainsOpaque() {
        val raw = Json.parseToJsonElement(
            """{
                "id":28,"type":"websocket-upgrade","name":"Future auth","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"authMethod":"future-auth","future_secret":"opaque",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!

        assertFalse(draft.websocketAuthEditable)
        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft.copy(name = "Renamed"))!!
        assertEquals(raw["authMethod"], updated["authMethod"])
        assertEquals(raw["future_secret"], updated["future_secret"])

        val futureOAuth = Json.parseToJsonElement(
            """{
                "id":29,"type":"websocket-upgrade","name":"Future OAuth","url":"wss://example.com",
                "accepted_statuscodes":["1000"],"authMethod":"oauth2-cc",
                "oauth_auth_method":"future-client-auth","oauth_client_secret":"opaque",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val futureOAuthDraft = MonitorDraftCodec.from(futureOAuth)!!
        assertFalse(futureOAuthDraft.websocketAuthEditable)
        val retainedFutureOAuth = MonitorDraftCodec.safeExistingPayload(futureOAuth, futureOAuthDraft)!!
        assertEquals(futureOAuth["oauth_auth_method"], retainedFutureOAuth["oauth_auth_method"])
        assertEquals(futureOAuth["oauth_client_secret"], retainedFutureOAuth["oauth_client_secret"])
    }

    @Test
    fun websocketOAuthAndMtlsValidationRequiresNewSecrets() {
        val base = MonitorDraft.create("websocket-upgrade").copy(
            name = "Socket",
            endpoint = "wss://example.com/socket",
        )
        assertEquals(
            MonitorDraftError.WEBSOCKET_OAUTH_TOKEN_URL_REQUIRED,
            MonitorDraftCodec.validate(base.copy(websocketAuthMethod = WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS)),
        )
        val oauth = base.copy(
            websocketAuthMethod = WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS,
            websocketOAuthTokenUrl = "https://id.example.com/token",
            websocketOAuthClientId = "client",
        )
        assertEquals(
            MonitorDraftError.WEBSOCKET_OAUTH_TOKEN_URL_INVALID,
            MonitorDraftCodec.validate(oauth.copy(websocketOAuthTokenUrl = "wss://id.example.com/token")),
        )
        assertEquals(MonitorDraftError.WEBSOCKET_OAUTH_CLIENT_SECRET_REQUIRED, MonitorDraftCodec.validate(oauth))
        assertNull(MonitorDraftCodec.validate(oauth.copy(websocketOAuthClientSecret = "new-secret")))
        assertEquals(
            MonitorDraftError.WEBSOCKET_MTLS_CERTIFICATE_REQUIRED,
            MonitorDraftCodec.validate(base.copy(websocketAuthMethod = WebSocketAuthMethod.MTLS)),
        )
        val mtls = base.copy(
            websocketAuthMethod = WebSocketAuthMethod.MTLS,
            websocketTlsCertificate = "certificate",
        )
        assertEquals(MonitorDraftError.WEBSOCKET_MTLS_PRIVATE_KEY_REQUIRED, MonitorDraftCodec.validate(mtls))
        assertNull(MonitorDraftCodec.validate(mtls.copy(websocketTlsPrivateKey = "private-key")))
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
        assertEquals(
            MonitorDraftError.KEYWORD_REQUIRED,
            MonitorDraftCodec.validate(
                MonitorDraft.create("keyword").copy(name = "Marker", endpoint = "https://example.com"),
            ),
        )
        assertEquals(
            MonitorDraftError.JSON_QUERY_EXPECTED_VALUE_REQUIRED,
            MonitorDraftCodec.validate(
                MonitorDraft.create("json-query").copy(name = "API", endpoint = "https://example.com"),
            ),
        )
        assertEquals(
            MonitorDraftError.JSON_QUERY_OPERATOR_INVALID,
            MonitorDraftCodec.validate(
                MonitorDraft.create("json-query").copy(
                    name = "API",
                    endpoint = "https://example.com",
                    jsonQueryOperator = "matches",
                    jsonQueryExpectedValue = "ready",
                ),
            ),
        )
        val sftp = MonitorDraft.create("sftp").copy(name = "SFTP", endpoint = "host")
        assertEquals(MonitorDraftError.SFTP_USERNAME_REQUIRED, MonitorDraftCodec.validate(sftp))
        assertEquals(
            MonitorDraftError.SFTP_PASSWORD_REQUIRED,
            MonitorDraftCodec.validate(sftp.copy(sftpUsername = "monitor")),
        )
        assertEquals(
            MonitorDraftError.SFTP_PRIVATE_KEY_REQUIRED,
            MonitorDraftCodec.validate(
                sftp.copy(sftpUsername = "monitor", sftpAuthMethod = SftpAuthMethod.PRIVATE_KEY),
            ),
        )
    }
}
