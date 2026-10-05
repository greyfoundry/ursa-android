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
    fun globalpingDeclaresCredentialFreePingFieldsAndDefaults() {
        val globalping = requireNotNull(MonitorEditorRegistry.find("globalping"))

        assertTrue(globalping.createSupported)
        assertEquals(MonitorEndpointKind.NONE, globalping.endpointKind)
        assertEquals(MonitorEditorCodec.GLOBALPING, globalping.codec)
        assertEquals(MonitorEditorValidation.GLOBALPING, globalping.validation)
        assertEquals(MonitorEditorFidelity.SAFE_COMMON_EDIT, globalping.fidelity)
        assertEquals(80, globalping.defaults.port)
        assertEquals(48, globalping.defaults.timeoutSeconds)
        assertTrue(MonitorEditorField.GLOBALPING_SUBTYPE in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_LOCATION in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_IP_FAMILY in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_PROTOCOL in globalping.editableFields)
        assertTrue(MonitorEditorField.GLOBALPING_PING_COUNT in globalping.editableFields)
        assertTrue(globalping.sensitiveFields.isEmpty())
        assertEquals(MonitorTransferEligibility.CREDENTIAL_FREE, globalping.transferEligibility)
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
                .filter { it.type != "sftp" }
                .all { it.fidelity == MonitorEditorFidelity.SAFE_COMMON_EDIT },
        )
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
