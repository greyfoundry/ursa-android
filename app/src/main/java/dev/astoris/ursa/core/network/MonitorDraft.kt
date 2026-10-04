package dev.astoris.ursa.core.network

import kotlinx.serialization.json.JsonArray
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
    SFTP_USERNAME_REQUIRED,
    SFTP_PASSWORD_REQUIRED,
    SFTP_PRIVATE_KEY_REQUIRED,
}

object MonitorDraftCodec {
    fun from(raw: JsonObject): MonitorDraft? {
        val id = raw.int("id")?.takeIf { it > 0 } ?: return null
        val type = raw.string("type") ?: return null
        val option = MonitorTypeCatalog.find(type)
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
            if (uri?.scheme?.lowercase() !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
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
}

data class MonitorMutationResult(
    val ok: Boolean,
    val monitorId: Int? = null,
    val message: String? = null,
)
