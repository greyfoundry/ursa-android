package dev.astoris.ursa.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorEditorRegistryTest {

    @Test
    fun registryIsCompleteUniqueAndConservative() {
        val definitions = MonitorEditorRegistry.all

        assertEquals(34, definitions.size)
        assertEquals(definitions.size, definitions.map(MonitorEditorDefinition::type).distinct().size)
        definitions.forEach { definition ->
            assertTrue(definition.type.isNotBlank())
            assertTrue(definition.label.isNotBlank())
            assertTrue(definition.verifiedMin <= definition.verifiedMax)
            assertTrue(MonitorEditorField.NAME in definition.editableFields)
            assertTrue(definition.browserFallback)
        }
        assertEquals(
            setOf(
                "http",
                "keyword",
                "json-query",
                "port",
                "ping",
                "dns",
                "group",
                "push",
                "manual",
                "mqtt",
                "ntp",
                "smtp",
                "sftp",
                "websocket-upgrade",
                "globalping",
                "grpc-keyword",
                "snmp",
                "rabbitmq",
                "kafka-producer",
                "docker",
                "real-browser",
                "oracledb",
                "postgres",
                "mysql",
                "sqlserver",
                "mongodb",
                "redis",
            ),
            definitions.filter(MonitorEditorDefinition::createSupported)
                .map(MonitorEditorDefinition::type)
                .toSet(),
        )
    }

    @Test
    fun writeAvailabilityRequiresGlobalAndTypeSpecificVerifiedRanges() {
        val v240 = KumaCapabilities.evaluate("2.4.0")
        val v253 = KumaCapabilities.evaluate("2.5.3")
        val v255 = KumaCapabilities.evaluate("2.5.5")
        val newer = KumaCapabilities.evaluate("2.5.6")
        val prerelease = KumaCapabilities.evaluate("2.5.5-beta.1")

        assertTrue(MonitorEditorRegistry.writeVerified("http", v240))
        assertTrue(MonitorEditorRegistry.writeVerified("keyword", v240))
        assertTrue(MonitorEditorRegistry.writeVerified("json-query", v240))
        assertFalse(MonitorEditorRegistry.writeVerified("ntp", v240))
        assertFalse(MonitorEditorRegistry.writeVerified("sftp", v253))
        assertTrue(MonitorEditorRegistry.writeVerified("ntp", v253))
        assertTrue(MonitorEditorRegistry.writeVerified("sftp", v255))
        assertFalse(MonitorEditorRegistry.writeVerified("http", newer))
        assertFalse(MonitorEditorRegistry.writeVerified("http", prerelease))
        assertFalse(MonitorEditorRegistry.writeVerified("future-type", v255))
        assertTrue(MonitorEditorRegistry.creatableFor(newer).isEmpty())
    }

    @Test
    fun keywordDeclaresNativeFieldsWithoutOverstatingFidelity() {
        val keyword = requireNotNull(MonitorEditorRegistry.find("keyword"))

        assertTrue(keyword.createSupported)
        assertEquals(MonitorEditorCodec.KEYWORD, keyword.codec)
        assertEquals(MonitorEditorValidation.KEYWORD, keyword.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, keyword.fidelity)
        assertTrue(MonitorEditorField.KEYWORD in keyword.editableFields)
        assertTrue(MonitorEditorField.INVERT_KEYWORD in keyword.editableFields)
        assertTrue(keyword.sensitiveFields.isEmpty())
    }

    @Test
    fun jsonQueryDeclaresItsNativeComparisonFields() {
        val jsonQuery = requireNotNull(MonitorEditorRegistry.find("json-query"))

        assertTrue(jsonQuery.createSupported)
        assertEquals(MonitorEditorCodec.JSON_QUERY, jsonQuery.codec)
        assertEquals(MonitorEditorValidation.JSON_QUERY, jsonQuery.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, jsonQuery.fidelity)
        assertTrue(MonitorEditorField.JSON_QUERY_EXPRESSION in jsonQuery.editableFields)
        assertTrue(MonitorEditorField.JSON_QUERY_OPERATOR in jsonQuery.editableFields)
        assertTrue(MonitorEditorField.JSON_QUERY_EXPECTED_VALUE in jsonQuery.editableFields)
    }

    @Test
    fun websocketDeclaresItsNativeTransportFieldsWithoutOverstatingFidelity() {
        val websocket = requireNotNull(MonitorEditorRegistry.find("websocket-upgrade"))

        assertTrue(websocket.createSupported)
        assertEquals(MonitorEditorCodec.WEBSOCKET, websocket.codec)
        assertEquals(MonitorEditorValidation.WEBSOCKET, websocket.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, websocket.fidelity)
        assertTrue(MonitorEditorField.WEBSOCKET_SUBPROTOCOLS in websocket.editableFields)
        assertTrue(MonitorEditorField.WEBSOCKET_ACCEPTED_CODES in websocket.editableFields)
        assertTrue(MonitorEditorField.WEBSOCKET_IGNORE_ACCEPT_HEADER in websocket.editableFields)
        assertTrue(MonitorEditorField.WEBSOCKET_HEADERS in websocket.editableFields)
        assertEquals(
            setOf(
                MonitorEditorField.WEBSOCKET_BASIC_PASSWORD,
                MonitorEditorField.WEBSOCKET_BEARER_TOKEN,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_SECRET,
                MonitorEditorField.WEBSOCKET_MTLS_CERTIFICATE,
                MonitorEditorField.WEBSOCKET_MTLS_PRIVATE_KEY,
                MonitorEditorField.WEBSOCKET_MTLS_CA_CERTIFICATE,
            ),
            websocket.sensitiveFields,
        )
        assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, websocket.transferEligibility)
    }

    @Test
    fun mqttDeclaresItsConditionalFieldsAndSecretContract() {
        val mqtt = requireNotNull(MonitorEditorRegistry.find("mqtt"))

        assertTrue(mqtt.createSupported)
        assertEquals(MonitorEndpointKind.HOST_PORT, mqtt.endpointKind)
        assertEquals(MonitorEditorCodec.MQTT, mqtt.codec)
        assertEquals(MonitorEditorValidation.MQTT, mqtt.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, mqtt.fidelity)
        assertEquals(setOf(MonitorEditorField.MQTT_PASSWORD), mqtt.sensitiveFields)
        assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, mqtt.transferEligibility)
        assertTrue(
            mqtt.conditions.any {
                it.field == MonitorEditorField.MQTT_SUCCESS_MESSAGE &&
                    MqttCheckType.KEYWORD.wireValue in it.acceptedValues
            },
        )
        assertTrue(
            mqtt.conditions.any {
                it.field == MonitorEditorField.MQTT_JSON_QUERY_EXPRESSION &&
                    MqttCheckType.JSON_QUERY.wireValue in it.acceptedValues
            },
        )
    }

    @Test
    fun smtpDeclaresItsSingleCredentialFreeProtocolField() {
        val smtp = requireNotNull(MonitorEditorRegistry.find("smtp"))

        assertTrue(smtp.createSupported)
        assertEquals(MonitorEndpointKind.HOST_PORT, smtp.endpointKind)
        assertEquals(MonitorEditorCodec.SMTP, smtp.codec)
        assertEquals(MonitorEditorValidation.SMTP, smtp.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, smtp.fidelity)
        assertTrue(MonitorEditorField.SMTP_SECURITY in smtp.editableFields)
        assertTrue(smtp.sensitiveFields.isEmpty())
        assertEquals(MonitorTransferEligibility.CREDENTIAL_FREE, smtp.transferEligibility)
        assertNull(smtp.defaults.port)
    }

    @Test
    fun ntpDeclaresItsVersionedDefaultsAndThresholdFields() {
        val ntp = requireNotNull(MonitorEditorRegistry.find("ntp"))

        assertTrue(ntp.createSupported)
        assertEquals(MonitorEndpointKind.HOST_PORT, ntp.endpointKind)
        assertEquals(MonitorEditorCodec.NTP, ntp.codec)
        assertEquals(MonitorEditorValidation.NTP, ntp.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, ntp.fidelity)
        assertEquals(123, ntp.defaults.port)
        assertEquals(300, ntp.defaults.intervalSeconds)
        assertEquals(48, ntp.defaults.timeoutSeconds)
        assertTrue(MonitorEditorField.NTP_STRATUM_THRESHOLD in ntp.editableFields)
        assertTrue(MonitorEditorField.NTP_TIME_OFFSET_THRESHOLD in ntp.editableFields)
        assertTrue(MonitorEditorField.NTP_ROOT_DISPERSION_THRESHOLD in ntp.editableFields)
        assertTrue(ntp.sensitiveFields.isEmpty())
        assertEquals(MonitorTransferEligibility.CREDENTIAL_FREE, ntp.transferEligibility)
    }

    @Test
    fun globalpingDeclaresFullSubtypeAndSecretContract() {
        val globalping = requireNotNull(MonitorEditorRegistry.find("globalping"))

        assertTrue(globalping.createSupported)
        assertEquals(MonitorEndpointKind.NONE, globalping.endpointKind)
        assertEquals(MonitorEditorCodec.GLOBALPING, globalping.codec)
        assertEquals(MonitorEditorValidation.GLOBALPING, globalping.validation)
        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, globalping.fidelity)
        assertEquals(80, globalping.defaults.port)
        assertEquals(48, globalping.defaults.timeoutSeconds)
        assertTrue(MonitorEditorField.GLOBALPING_SUBTYPE in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_LOCATION in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_IP_FAMILY in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_PROTOCOL in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_PING_COUNT in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_DNS_RECORD_TYPE in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_HTTP_METHOD in globalping.editableFields)
        assertTrue(MonitorEditorField.WEBSOCKET_HEADERS in globalping.editableFields)
        assertTrue(MonitorEditorField.WEBSOCKET_BASIC_PASSWORD in globalping.sensitiveFields)
        assertTrue(MonitorEditorField.WEBSOCKET_BEARER_TOKEN in globalping.sensitiveFields)
        assertTrue(MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_SECRET in globalping.sensitiveFields)
        assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, globalping.transferEligibility)
    }

    @Test
    fun sftpDeclaresItsFullFidelityAndSecretContract() {
        val sftp = requireNotNull(MonitorEditorRegistry.find("sftp"))

        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, sftp.fidelity)
        assertEquals(MonitorEditorCodec.SFTP, sftp.codec)
        assertEquals(MonitorEditorValidation.SFTP, sftp.validation)
        assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, sftp.transferEligibility)
        assertEquals(22, sftp.defaults.port)
        assertEquals(10, sftp.defaults.timeoutSeconds)
        assertTrue(MonitorEditorField.SFTP_PRIVATE_KEY in sftp.sensitiveFields)
        assertTrue(MonitorEditorField.SFTP_PASSWORD in sftp.sensitiveFields)
        assertTrue(
            sftp.conditions.any {
                it.field == MonitorEditorField.SFTP_PASSWORD &&
                    SftpAuthMethod.PASSWORD.wireValue in it.acceptedValues
            },
        )
        assertTrue(
            sftp.dependencies.any {
                it.field == MonitorEditorField.SFTP_PASSPHRASE &&
                    MonitorEditorField.SFTP_PRIVATE_KEY in it.requires
            },
        )
        assertTrue(
            MonitorEditorRegistry.all
                .filter {
                    it.type !in setOf(
                        "sftp",
                        "globalping",
                        "grpc-keyword",
                        "rabbitmq",
                        "kafka-producer",
                        "docker",
                        "real-browser",
                        "postgres",
                        "mongodb",
                        "redis",
                    )
                }
                .all { it.fidelity == MonitorEditorFidelity.SAFE_COMMON_EDIT },
        )
    }

    @Test
    fun databaseWaveDeclaresOneSharedSecretContractWithoutOverstatingConditions() {
        val definitions = listOf("postgres", "mysql", "sqlserver", "mongodb", "oracledb", "redis")
            .map { requireNotNull(MonitorEditorRegistry.find(it)) }

        definitions.forEach { definition ->
            assertTrue(definition.createSupported)
            assertEquals(MonitorEndpointKind.NONE, definition.endpointKind)
            assertEquals(MonitorEditorCodec.DATABASE, definition.codec)
            assertEquals(MonitorEditorValidation.DATABASE, definition.validation)
            assertTrue(MonitorEditorField.DATABASE_CONNECTION_STRING in definition.sensitiveFields)
            assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, definition.transferEligibility)
        }
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, MonitorEditorRegistry.find("mysql")!!.fidelity)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, MonitorEditorRegistry.find("sqlserver")!!.fidelity)
        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, MonitorEditorRegistry.find("postgres")!!.fidelity)
        assertTrue(MonitorEditorField.DATABASE_PASSWORD in MonitorEditorRegistry.find("mysql")!!.sensitiveFields)
        assertTrue(MonitorEditorField.DATABASE_USERNAME in MonitorEditorRegistry.find("oracledb")!!.editableFields)
        assertTrue(MonitorEditorField.DATABASE_PASSWORD in MonitorEditorRegistry.find("oracledb")!!.sensitiveFields)
        assertTrue(MonitorEditorField.DATABASE_IGNORE_TLS in MonitorEditorRegistry.find("redis")!!.editableFields)
        assertTrue(MonitorEditorField.DATABASE_JSON_QUERY_EXPRESSION in MonitorEditorRegistry.find("mongodb")!!.editableFields)
    }

    @Test
    fun grpcDeclaresItsSensitiveBodyAndFullNativeContract() {
        val definition = requireNotNull(MonitorEditorRegistry.find("grpc-keyword"))

        assertTrue(definition.createSupported)
        assertEquals(MonitorEndpointKind.NONE, definition.endpointKind)
        assertEquals(MonitorEditorCodec.GRPC, definition.codec)
        assertEquals(MonitorEditorValidation.GRPC, definition.validation)
        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, definition.fidelity)
        assertTrue(MonitorEditorField.GRPC_BODY in definition.sensitiveFields)
        assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, definition.transferEligibility)
    }

    @Test
    fun snmpDeclaresItsCommunityAndVersionLimitedNativeContract() {
        val definition = requireNotNull(MonitorEditorRegistry.find("snmp"))

        assertTrue(definition.createSupported)
        assertEquals(MonitorEndpointKind.HOST_PORT, definition.endpointKind)
        assertEquals(161, definition.defaults.port)
        assertEquals(5, definition.defaults.timeoutSeconds)
        assertEquals(MonitorEditorCodec.SNMP, definition.codec)
        assertEquals(MonitorEditorValidation.SNMP, definition.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, definition.fidelity)
        assertTrue(MonitorEditorField.SNMP_COMMUNITY in definition.sensitiveFields)
        assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, definition.transferEligibility)
    }

    @Test
    fun brokerMonitorsDeclareTheirCompleteSecretAwareContracts() {
        val rabbitmq = requireNotNull(MonitorEditorRegistry.find("rabbitmq"))
        assertTrue(rabbitmq.createSupported)
        assertEquals(MonitorEditorCodec.RABBITMQ, rabbitmq.codec)
        assertEquals(MonitorEditorValidation.RABBITMQ, rabbitmq.validation)
        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, rabbitmq.fidelity)
        assertTrue(MonitorEditorField.RABBITMQ_PASSWORD in rabbitmq.sensitiveFields)

        val kafka = requireNotNull(MonitorEditorRegistry.find("kafka-producer"))
        assertTrue(kafka.createSupported)
        assertEquals(1, kafka.defaults.timeoutSeconds)
        assertEquals(MonitorEditorCodec.KAFKA, kafka.codec)
        assertEquals(MonitorEditorValidation.KAFKA, kafka.validation)
        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, kafka.fidelity)
        assertTrue(MonitorEditorField.KAFKA_SASL_OPTIONS in kafka.sensitiveFields)
        assertEquals(MonitorTransferEligibility.REQUIRES_SECRET_REENTRY, kafka.transferEligibility)
    }

    @Test
    fun dockerDeclaresItsCompleteServerBoundContract() {
        val docker = requireNotNull(MonitorEditorRegistry.find("docker"))

        assertTrue(docker.createSupported)
        assertEquals(MonitorEditorCodec.DOCKER, docker.codec)
        assertEquals(MonitorEditorValidation.DOCKER, docker.validation)
        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, docker.fidelity)
        assertEquals(MonitorTransferEligibility.NOT_ELIGIBLE, docker.transferEligibility)
        assertTrue(MonitorEditorField.DOCKER_CONTAINER in docker.editableFields)
        assertTrue(MonitorEditorField.DOCKER_HOST in docker.editableFields)
        assertTrue(docker.sensitiveFields.isEmpty())
    }

    @Test
    fun realBrowserDeclaresItsCompleteServerBoundContract() {
        val browser = requireNotNull(MonitorEditorRegistry.find("real-browser"))

        assertTrue(browser.createSupported)
        assertEquals(MonitorEditorCodec.REAL_BROWSER, browser.codec)
        assertEquals(MonitorEditorValidation.REAL_BROWSER, browser.validation)
        assertEquals(MonitorEditorFidelity.FULL_FIDELITY, browser.fidelity)
        assertEquals(MonitorTransferEligibility.NOT_ELIGIBLE, browser.transferEligibility)
        assertTrue(MonitorEditorField.ENDPOINT in browser.editableFields)
        assertTrue(MonitorEditorField.REAL_BROWSER_REMOTE_BROWSER in browser.editableFields)
        assertTrue(MonitorEditorField.REAL_BROWSER_SCREENSHOT_DELAY in browser.editableFields)
        assertTrue(browser.sensitiveFields.isEmpty())
    }

    @Test
    fun guardedPatchPreservesUnknownFieldsAndRejectsTypeDrift() {
        val raw = Json.parseToJsonElement(
            """{
                "id":7,"type":"http","name":"Before","url":"https://before.example",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,
                "active":true,"notificationIDList":{},"future":{"nested":[1,2,3]},
                "basic_auth_pass":"secret"
            }""",
        ).jsonObject
        val draft = MonitorDraftCodec.from(raw)!!.copy(
            name = "After",
            endpoint = "https://after.example",
        )

        val updated = MonitorDraftCodec.safeExistingPayload(raw, draft)

        assertTrue(updated != null)
        assertEquals(raw["future"], updated!!["future"])
        assertEquals(raw["basic_auth_pass"], updated["basic_auth_pass"])
        assertNull(MonitorDraftCodec.safeExistingPayload(raw, draft.copy(type = "port")))
    }

    @Test
    fun guardDetectsUnexpectedChangesOutsideTheDeclaredWirePatch() {
        val before = Json.parseToJsonElement(
            """{"type":"http","name":"Before","url":"https://example.com","future":1}""",
        ).jsonObject
        val after = JsonObject(before + ("future" to JsonPrimitive(2)))

        assertFalse(MonitorRoundTripGuard.preservesUnrelatedFields(before, after, "http"))
        assertFalse(MonitorRoundTripGuard.preservesUnrelatedFields(before, before, "future-type"))
    }
}
