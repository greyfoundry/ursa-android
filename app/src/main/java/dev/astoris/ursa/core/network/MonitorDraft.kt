package dev.astoris.ursa.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.security.SecureRandom
import dev.astoris.ursa.data.model.MonitorTagAssignment
import dev.astoris.ursa.data.model.RequestHeader

enum class MonitorEndpointKind { NONE, URL, HOST, HOST_PORT }

data class MonitorTypeOption(
    val key: String,
    val label: String,
    val endpointKind: MonitorEndpointKind = MonitorEndpointKind.NONE,
    val createSupported: Boolean = false,
    val defaultPort: Int? = null,
)

/** Uptime Kuma 2.5.5's monitor-type catalogue. */
object MonitorTypeCatalog {
    val all: List<MonitorTypeOption> = MonitorEditorRegistry.all.map { definition ->
        MonitorTypeOption(
            key = definition.type,
            label = definition.label,
            endpointKind = definition.endpointKind,
            createSupported = definition.createSupported,
            defaultPort = definition.defaults.port,
        )
    }

    val creatable: List<MonitorTypeOption> = all.filter(MonitorTypeOption::createSupported)

    fun creatableFor(compatibility: KumaCompatibility): List<MonitorTypeOption> =
        MonitorEditorRegistry.creatableFor(compatibility).mapNotNull { find(it.type) }

    fun find(type: String): MonitorTypeOption? = all.firstOrNull { it.key == type }
}

enum class SftpAuthMethod(val wireValue: String) {
    PASSWORD("password"),
    PRIVATE_KEY("privateKey");

    companion object {
        fun fromWire(value: String?): SftpAuthMethod =
            entries.firstOrNull { it.wireValue == value } ?: PASSWORD
    }
}

data class MonitorHeaderDraft(
    val name: String = "",
    val value: String = "",
    val originalName: String? = null,
    val hasSavedValue: Boolean = false,
)

data class MonitorDraft(
    val id: Int? = null,
    val type: String = "http",
    val name: String = "",
    val description: String = "",
    val endpoint: String = "",
    val port: Int? = null,
    val intervalSeconds: Int = 60,
    val retryIntervalSeconds: Int = 60,
    val resendIntervalSeconds: Int = 0,
    val maxRetries: Int = 0,
    val active: Boolean = true,
    val notificationIds: Set<Int> = emptySet(),
    val parentId: Int? = null,
    val tagAssignments: List<MonitorTagAssignment> = emptyList(),
    val pushToken: String = "",
    val keyword: String = "",
    val invertKeyword: Boolean = false,
    val jsonQueryExpression: String = "$",
    val jsonQueryOperator: String = "==",
    val jsonQueryExpectedValue: String = "",
    val websocketSubprotocols: String = "",
    val websocketAcceptedCodes: String = "1000",
    val websocketIgnoreAcceptHeader: Boolean = false,
    val websocketHeaders: List<MonitorHeaderDraft> = emptyList(),
    val websocketHeadersEditable: Boolean = true,
    val sftpAuthMethod: SftpAuthMethod = SftpAuthMethod.PASSWORD,
    val sftpUsername: String = "",
    val sftpPassword: String = "",
    val sftpPrivateKey: String = "",
    val sftpPassphrase: String = "",
    val sftpPath: String = "",
    val sftpOriginalAuthMethod: SftpAuthMethod? = null,
    val sftpHasSavedPassword: Boolean = false,
    val sftpHasSavedPrivateKey: Boolean = false,
    val sftpHasSavedPassphrase: Boolean = false,
    val sftpClearSavedPassphrase: Boolean = false,
) {
    val isNew: Boolean get() = id == null

    companion object {
        fun create(type: String = "http"): MonitorDraft {
            val option = MonitorTypeCatalog.find(type) ?: MonitorTypeCatalog.find("http")!!
            val defaults = MonitorEditorRegistry.find(option.key)?.defaults ?: MonitorEditorDefaults()
            return MonitorDraft(
                type = option.key,
                endpoint = "",
                port = defaults.port,
                intervalSeconds = defaults.intervalSeconds,
                retryIntervalSeconds = defaults.retryIntervalSeconds,
                resendIntervalSeconds = defaults.resendIntervalSeconds,
                maxRetries = defaults.maxRetries,
                pushToken = if (option.key == "push") newPushToken() else "",
            )
        }

        internal fun newPushToken(): String = buildString(PUSH_TOKEN_LENGTH) {
            repeat(PUSH_TOKEN_LENGTH) { append(PUSH_TOKEN_ALPHABET[pushTokenRandom.nextInt(PUSH_TOKEN_ALPHABET.length)]) }
        }

        private val pushTokenRandom = SecureRandom()
        private const val PUSH_TOKEN_LENGTH = 32
        private const val PUSH_TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }
}

enum class MonitorDraftError {
    NAME_REQUIRED,
    TYPE_UNAVAILABLE,
    ENDPOINT_REQUIRED,
    INVALID_URL,
    PORT_REQUIRED,
    INVALID_INTERVAL,
    INVALID_RETRIES,
    INVALID_PUSH_TOKEN,
    KEYWORD_REQUIRED,
    JSON_QUERY_EXPRESSION_REQUIRED,
    JSON_QUERY_OPERATOR_INVALID,
    JSON_QUERY_EXPECTED_VALUE_REQUIRED,
    WEBSOCKET_ACCEPTED_CODES_REQUIRED,
    WEBSOCKET_ACCEPTED_CODE_INVALID,
    WEBSOCKET_TOO_MANY_HEADERS,
    WEBSOCKET_HEADER_INVALID,
    WEBSOCKET_HEADER_VALUE_REQUIRED,
    WEBSOCKET_HEADER_DUPLICATE,
    SFTP_USERNAME_REQUIRED,
    SFTP_PASSWORD_REQUIRED,
    SFTP_PRIVATE_KEY_REQUIRED,
}

object MonitorDraftCodec {
    fun from(raw: JsonObject): MonitorDraft? {
        val id = raw.int("id")?.takeIf { it > 0 } ?: return null
        val type = raw.string("type") ?: return null
        val option = MonitorTypeCatalog.find(type)
        val websocketHeaders = parseWebsocketHeaders(raw.string("headers"), type)
        return MonitorDraft(
            id = id,
            type = type,
            name = raw.string("name").orEmpty(),
            description = raw.string("description").orEmpty(),
            endpoint = endpoint(raw, option),
            port = raw.int("port"),
            intervalSeconds = raw.int("interval") ?: 60,
            retryIntervalSeconds = raw.int("retryInterval") ?: 60,
            resendIntervalSeconds = raw.int("resendInterval") ?: 0,
            maxRetries = raw.int("maxretries") ?: 0,
            active = raw["active"]?.jsonPrimitive?.booleanOrNull
                ?: raw.int("active")?.let { it != 0 }
                ?: true,
            notificationIds = (raw["notificationIDList"] as? JsonObject)?.entries
                ?.mapNotNull { (key, value) ->
                    key.toIntOrNull()?.takeIf { value.jsonPrimitive.booleanOrNull == true }
                }?.toSet().orEmpty(),
            parentId = raw.int("parent"),
            tagAssignments = KumaParse.tagAssignments(raw),
            pushToken = raw.string("pushToken").orEmpty(),
            keyword = raw.string("keyword").orEmpty(),
            invertKeyword = raw["invertKeyword"]?.jsonPrimitive?.booleanOrNull
                ?: raw.int("invertKeyword")?.let { it != 0 }
                ?: false,
            jsonQueryExpression = raw.string("jsonPath") ?: "$",
            jsonQueryOperator = raw.string("jsonPathOperator") ?: "==",
            jsonQueryExpectedValue = raw.string("expectedValue").orEmpty(),
            websocketSubprotocols = raw.string("wsSubprotocol").orEmpty(),
            websocketAcceptedCodes = (raw["accepted_statuscodes"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.joinToString(", ")
                ?.takeIf(String::isNotBlank)
                ?: "1000",
            websocketIgnoreAcceptHeader = raw["wsIgnoreSecWebsocketAcceptHeader"]
                ?.jsonPrimitive?.booleanOrNull
                ?: raw.int("wsIgnoreSecWebsocketAcceptHeader")?.let { it != 0 }
                ?: false,
            websocketHeaders = websocketHeaders.drafts,
            websocketHeadersEditable = websocketHeaders.editable,
            sftpAuthMethod = SftpAuthMethod.fromWire(raw.string("sshAuthMethod")),
            sftpUsername = raw.string("sshUsername").orEmpty(),
            sftpPath = raw.string("sftpPath").orEmpty(),
            sftpOriginalAuthMethod = if (type == "sftp") {
                SftpAuthMethod.fromWire(raw.string("sshAuthMethod"))
            } else {
                null
            },
            sftpHasSavedPassword = raw.string("sshPassword")?.isNotEmpty() == true,
            sftpHasSavedPrivateKey = raw.string("sshPrivateKey")?.isNotEmpty() == true,
            sftpHasSavedPassphrase = raw.string("sshPassphrase")?.isNotEmpty() == true,
        )
    }

    fun validate(draft: MonitorDraft): MonitorDraftError? {
        if (draft.name.trim().isEmpty()) return MonitorDraftError.NAME_REQUIRED
        val definition = MonitorEditorRegistry.find(draft.type) ?: return MonitorDraftError.TYPE_UNAVAILABLE
        if (draft.isNew && !definition.createSupported) return MonitorDraftError.TYPE_UNAVAILABLE
        if (definition.endpointKind != MonitorEndpointKind.NONE && draft.endpoint.trim().isEmpty()) {
            return MonitorDraftError.ENDPOINT_REQUIRED
        }
        if (definition.endpointKind == MonitorEndpointKind.URL) {
            val uri = runCatching { URI(draft.endpoint.trim()) }.getOrNull()
            val allowedSchemes = if (definition.validation == MonitorEditorValidation.WEBSOCKET) {
                setOf("ws", "wss")
            } else {
                setOf("http", "https")
            }
            if (uri?.scheme?.lowercase() !in allowedSchemes || uri?.host.isNullOrBlank()) {
                return MonitorDraftError.INVALID_URL
            }
        }
        if (definition.endpointKind == MonitorEndpointKind.HOST_PORT && draft.port !in 1..65535) {
            return MonitorDraftError.PORT_REQUIRED
        }
        if (definition.validation == MonitorEditorValidation.SFTP) {
            if (draft.sftpUsername.trim().isEmpty()) return MonitorDraftError.SFTP_USERNAME_REQUIRED
            when (draft.sftpAuthMethod) {
                SftpAuthMethod.PASSWORD -> {
                    val canKeepSaved = draft.sftpOriginalAuthMethod == SftpAuthMethod.PASSWORD &&
                        draft.sftpHasSavedPassword
                    if (draft.sftpPassword.isEmpty() && !canKeepSaved) {
                        return MonitorDraftError.SFTP_PASSWORD_REQUIRED
                    }
                }
                SftpAuthMethod.PRIVATE_KEY -> {
                    val canKeepSaved = draft.sftpOriginalAuthMethod == SftpAuthMethod.PRIVATE_KEY &&
                        draft.sftpHasSavedPrivateKey
                    if (draft.sftpPrivateKey.isBlank() && !canKeepSaved) {
                        return MonitorDraftError.SFTP_PRIVATE_KEY_REQUIRED
                    }
                }
            }
        }
        if (definition.validation == MonitorEditorValidation.PUSH && !isValidPushToken(draft.pushToken)) {
            return MonitorDraftError.INVALID_PUSH_TOKEN
        }
        if (definition.validation == MonitorEditorValidation.KEYWORD && draft.keyword.isEmpty()) {
            return MonitorDraftError.KEYWORD_REQUIRED
        }
        if (definition.validation == MonitorEditorValidation.JSON_QUERY) {
            if (draft.jsonQueryExpression.isEmpty()) return MonitorDraftError.JSON_QUERY_EXPRESSION_REQUIRED
            if (draft.jsonQueryOperator !in JSON_QUERY_OPERATORS) return MonitorDraftError.JSON_QUERY_OPERATOR_INVALID
            if (draft.jsonQueryExpectedValue.isEmpty()) return MonitorDraftError.JSON_QUERY_EXPECTED_VALUE_REQUIRED
        }
        if (definition.validation == MonitorEditorValidation.WEBSOCKET) {
            val acceptedCodes = websocketAcceptedCodes(draft.websocketAcceptedCodes)
            if (draft.websocketAcceptedCodes.isBlank()) return MonitorDraftError.WEBSOCKET_ACCEPTED_CODES_REQUIRED
            if (acceptedCodes == null) return MonitorDraftError.WEBSOCKET_ACCEPTED_CODE_INVALID
            if (draft.websocketHeadersEditable) {
                if (draft.websocketHeaders.size > WEBSOCKET_HEADER_LIMIT) {
                    return MonitorDraftError.WEBSOCKET_TOO_MANY_HEADERS
                }
                val names = mutableSetOf<String>()
                draft.websocketHeaders.forEach { header ->
                    val canKeepSaved = header.hasSavedValue &&
                        header.originalName?.equals(header.name.trim(), ignoreCase = true) == true
                    if (header.value.isBlank() && !canKeepSaved) {
                        return MonitorDraftError.WEBSOCKET_HEADER_VALUE_REQUIRED
                    }
                    val value = header.value.ifBlank { "saved" }
                    val normalized = RequestHeader(header.name, value).normalizedOrNull()
                        ?: return MonitorDraftError.WEBSOCKET_HEADER_INVALID
                    if (!names.add(normalized.name.lowercase())) {
                        return MonitorDraftError.WEBSOCKET_HEADER_DUPLICATE
                    }
                }
            }
        }
        if (draft.intervalSeconds < 1 || draft.retryIntervalSeconds < 1 || draft.resendIntervalSeconds < 0) {
            return MonitorDraftError.INVALID_INTERVAL
        }
        if (draft.maxRetries !in 0..100) return MonitorDraftError.INVALID_RETRIES
        return null
    }

    fun applyToExisting(raw: JsonObject, draft: MonitorDraft): JsonObject {
        val values = raw.toMutableMap()
        values["name"] = JsonPrimitive(draft.name.trim())
        values["description"] = JsonPrimitive(draft.description.trim())
        values["interval"] = JsonPrimitive(draft.intervalSeconds)
        values["retryInterval"] = JsonPrimitive(draft.retryIntervalSeconds)
        values["resendInterval"] = JsonPrimitive(draft.resendIntervalSeconds)
        values["maxretries"] = JsonPrimitive(draft.maxRetries)
        values["active"] = JsonPrimitive(draft.active)
        values["notificationIDList"] = notificationIdObject(draft.notificationIds)
        values["parent"] = draft.parentId?.let(::JsonPrimitive) ?: JsonNull
        applyEndpoint(values, draft)
        applyKeyword(values, draft)
        applyJsonQuery(values, draft)
        applyWebsocket(values, draft)
        applySftp(values, draft, raw)
        return JsonObject(values)
    }

    fun safeExistingPayload(raw: JsonObject, draft: MonitorDraft): JsonObject? {
        if (raw.string("type") != draft.type) return null
        val updated = applyToExisting(raw, draft)
        return updated.takeIf { MonitorRoundTripGuard.preservesUnrelatedFields(raw, it, draft.type) }
    }

    fun newPayload(draft: MonitorDraft): JsonObject {
        val mutable = buildJsonObject {
            put("type", draft.type)
            put("name", draft.name.trim())
            put("description", draft.description.trim())
            put("parent", draft.parentId?.let(::JsonPrimitive) ?: JsonNull)
            put("url", "")
            put("method", "GET")
            put("interval", draft.intervalSeconds)
            put("retryInterval", draft.retryIntervalSeconds)
            put("resendInterval", draft.resendIntervalSeconds)
            put("maxretries", draft.maxRetries)
            put("retryOnlyOnStatusCodeFailure", false)
            put("notificationIDList", notificationIdObject(draft.notificationIds))
            put("ignoreTls", false)
            put("upsideDown", false)
            put("expiryNotification", false)
            put("domainExpiryNotification", true)
            put("maxredirects", 10)
            put("accepted_statuscodes", JsonArray(listOf(JsonPrimitive("200-299"))))
            put("saveResponse", false)
            put("saveErrorResponse", true)
            put("responseMaxLength", 1024)
            put("dns_resolve_type", "A")
            put("dns_resolve_server", if (draft.type == "dns") "1.1.1.1" else "")
            put("kafkaProducerBrokers", JsonArray(emptyList()))
            put("kafkaProducerSaslOptions", buildJsonObject { put("mechanism", "None") })
            put("rabbitmqNodes", JsonArray(emptyList()))
            put("conditions", JsonArray(emptyList()))
            put("active", draft.active)
            put("timeout", MonitorEditorRegistry.find(draft.type)?.defaults?.timeoutSeconds ?: 48)
            put("manual_status", 1)
            if (MonitorEditorRegistry.find(draft.type)?.codec == MonitorEditorCodec.PUSH) {
                put("pushToken", draft.pushToken)
            }
        }.toMutableMap()
        applyEndpoint(mutable, draft)
        applyKeyword(mutable, draft)
        applyJsonQuery(mutable, draft)
        applyWebsocket(mutable, draft)
        applySftp(mutable, draft)
        return JsonObject(mutable)
    }

    private fun endpoint(raw: JsonObject, option: MonitorTypeOption?): String = when (option?.endpointKind) {
        MonitorEndpointKind.URL -> raw.string("url")
        MonitorEndpointKind.HOST, MonitorEndpointKind.HOST_PORT -> raw.string("hostname")
        else -> null
    }.orEmpty()

    private fun applyEndpoint(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        when (MonitorEditorRegistry.find(draft.type)?.endpointKind) {
            MonitorEndpointKind.URL -> values["url"] = JsonPrimitive(draft.endpoint.trim())
            MonitorEndpointKind.HOST, MonitorEndpointKind.HOST_PORT ->
                values["hostname"] = JsonPrimitive(draft.endpoint.trim())
            else -> Unit
        }
        if (MonitorEditorRegistry.find(draft.type)?.endpointKind == MonitorEndpointKind.HOST_PORT) {
            values["port"] = draft.port?.let(::JsonPrimitive) ?: JsonNull
        }
    }

    private fun applySftp(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.SFTP) return
        values["sshAuthMethod"] = JsonPrimitive(draft.sftpAuthMethod.wireValue)
        values["sshUsername"] = JsonPrimitive(draft.sftpUsername.trim())
        values["sftpPath"] = JsonPrimitive(draft.sftpPath.trim())
        val sameAuthMethod = existing != null && draft.sftpOriginalAuthMethod == draft.sftpAuthMethod
        when (draft.sftpAuthMethod) {
            SftpAuthMethod.PASSWORD -> {
                val password = if (draft.sftpPassword.isNotEmpty()) {
                    draft.sftpPassword
                } else if (
                    draft.sftpOriginalAuthMethod == SftpAuthMethod.PASSWORD &&
                    draft.sftpHasSavedPassword
                ) {
                    existing?.string("sshPassword").orEmpty()
                } else {
                    ""
                }
                values["sshPassword"] = JsonPrimitive(password)
                values["sshPrivateKey"] = JsonPrimitive(
                    if (sameAuthMethod) existing.string("sshPrivateKey").orEmpty() else "",
                )
                values["sshPassphrase"] = JsonPrimitive(
                    if (sameAuthMethod) existing.string("sshPassphrase").orEmpty() else "",
                )
            }
            SftpAuthMethod.PRIVATE_KEY -> {
                val replacingKey = draft.sftpPrivateKey.isNotBlank()
                val privateKey = if (replacingKey) {
                    draft.sftpPrivateKey
                } else if (
                    draft.sftpOriginalAuthMethod == SftpAuthMethod.PRIVATE_KEY &&
                    draft.sftpHasSavedPrivateKey
                ) {
                    existing?.string("sshPrivateKey").orEmpty()
                } else {
                    ""
                }
                val passphrase = when {
                    draft.sftpClearSavedPassphrase -> ""
                    draft.sftpPassphrase.isNotEmpty() -> draft.sftpPassphrase
                    replacingKey -> ""
                    draft.sftpOriginalAuthMethod == SftpAuthMethod.PRIVATE_KEY &&
                        draft.sftpHasSavedPassphrase -> existing?.string("sshPassphrase").orEmpty()
                    else -> ""
                }
                values["sshPassword"] = JsonPrimitive(
                    if (sameAuthMethod) existing.string("sshPassword").orEmpty() else "",
                )
                values["sshPrivateKey"] = JsonPrimitive(privateKey)
                values["sshPassphrase"] = JsonPrimitive(passphrase)
            }
        }
    }

    private fun applyKeyword(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.KEYWORD) return
        values["keyword"] = JsonPrimitive(draft.keyword)
        values["invertKeyword"] = JsonPrimitive(draft.invertKeyword)
    }

    private fun applyJsonQuery(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.JSON_QUERY) return
        values["jsonPath"] = JsonPrimitive(draft.jsonQueryExpression)
        values["jsonPathOperator"] = JsonPrimitive(draft.jsonQueryOperator)
        values["expectedValue"] = JsonPrimitive(draft.jsonQueryExpectedValue)
    }

    private fun applyWebsocket(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.WEBSOCKET) return
        values["wsSubprotocol"] = JsonPrimitive(
            draft.websocketSubprotocols.split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .joinToString(", "),
        )
        values["accepted_statuscodes"] = JsonArray(
            requireNotNull(websocketAcceptedCodes(draft.websocketAcceptedCodes)).map(::JsonPrimitive),
        )
        values["wsIgnoreSecWebsocketAcceptHeader"] = JsonPrimitive(draft.websocketIgnoreAcceptHeader)
        applyWebsocketHeaders(values, draft)
    }

    private fun applyWebsocketHeaders(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (!draft.websocketHeadersEditable) return
        val rawHeaders = values["headers"]?.jsonPrimitive?.contentOrNull
        val existing = parseHeaderObject(rawHeaders)
        if (draft.websocketHeaders.isEmpty() && (rawHeaders.isNullOrBlank() || existing?.isEmpty() == true)) return
        val headers = linkedMapOf<String, JsonElement>()
        draft.websocketHeaders.forEach { header ->
            val name = header.name.trim()
            val value = if (header.value.isNotBlank()) {
                header.value.trim()
            } else {
                existing?.entries
                    ?.firstOrNull { (key, _) -> key.equals(header.originalName, ignoreCase = true) }
                    ?.value?.jsonPrimitive?.contentOrNull
                    .orEmpty()
            }
            headers[name] = JsonPrimitive(value)
        }
        values["headers"] = JsonPrimitive(JsonObject(headers).toString())
    }

    private fun notificationIdObject(ids: Set<Int>): JsonObject = JsonObject(
        ids.filter { it > 0 }.sorted().associate { it.toString() to JsonPrimitive(true) },
    )

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    fun pushUrl(serverUrl: String, pushToken: String): String? {
        if (!isValidPushToken(pushToken)) return null
        val base = serverUrl.trim().trimEnd('/')
        val uri = runCatching { URI(base) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.rawAuthority.isNullOrBlank()) return null
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        return "$base/api/push/$pushToken?status=up&msg=OK&ping="
    }

    private fun isValidPushToken(token: String): Boolean =
        token.length == 32 && token.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }

    val JSON_QUERY_OPERATORS: Set<String> = setOf(">", ">=", "<", "<=", "!=", "==", "contains")

    private fun websocketAcceptedCodes(value: String): List<String>? {
        val parts = value.split(',').map(String::trim)
        if (parts.any(String::isEmpty)) return null
        val codes = parts.map { code -> code.toIntOrNull()?.takeIf { it in 1000..4999 } ?: return null }
        return codes.distinct().map(Int::toString)
    }

    private fun parseWebsocketHeaders(raw: String?, type: String): ParsedWebsocketHeaders {
        if (type != "websocket-upgrade" || raw.isNullOrBlank()) return ParsedWebsocketHeaders()
        val parsed = parseHeaderObject(raw) ?: return ParsedWebsocketHeaders(editable = false)
        if (parsed.size > WEBSOCKET_HEADER_LIMIT) return ParsedWebsocketHeaders(editable = false)
        if (parsed.values.any { it !is JsonPrimitive || !it.isString }) {
            return ParsedWebsocketHeaders(editable = false)
        }
        val names = parsed.keys.map(String::lowercase)
        if (names.distinct().size != names.size) return ParsedWebsocketHeaders(editable = false)
        return ParsedWebsocketHeaders(
            drafts = parsed.keys.map { name ->
                MonitorHeaderDraft(name = name, originalName = name, hasSavedValue = true)
            },
        )
    }

    private fun parseHeaderObject(raw: String?): JsonObject? = raw
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private data class ParsedWebsocketHeaders(
        val drafts: List<MonitorHeaderDraft> = emptyList(),
        val editable: Boolean = true,
    )

    const val WEBSOCKET_HEADER_LIMIT = 8
}

data class MonitorMutationResult(
    val ok: Boolean,
    val monitorId: Int? = null,
    val message: String? = null,
)
