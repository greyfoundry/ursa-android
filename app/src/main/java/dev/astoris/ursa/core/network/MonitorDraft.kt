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
import java.util.UUID
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
    val all = listOf(
        MonitorTypeOption("http", "HTTP(s)", MonitorEndpointKind.URL, createSupported = true),
        MonitorTypeOption("keyword", "HTTP(s) - keyword", MonitorEndpointKind.URL),
        MonitorTypeOption("port", "TCP port", MonitorEndpointKind.HOST_PORT, true),
        MonitorTypeOption("ping", "Ping", MonitorEndpointKind.HOST, true),
        MonitorTypeOption("dns", "DNS", MonitorEndpointKind.HOST_PORT, true, 53),
        MonitorTypeOption("docker", "Docker container"),
        MonitorTypeOption("system-service", "System service"),
        MonitorTypeOption("pm2", "PM2 process"),
        MonitorTypeOption("real-browser", "Browser engine", MonitorEndpointKind.URL),
        MonitorTypeOption("group", "Group", createSupported = true),
        MonitorTypeOption("push", "Push", createSupported = true),
        MonitorTypeOption("manual", "Manual", createSupported = true),
        MonitorTypeOption("globalping", "Globalping"),
        MonitorTypeOption("grpc-keyword", "gRPC(s) - keyword"),
        MonitorTypeOption("json-query", "HTTP(s) - JSON query", MonitorEndpointKind.URL),
        MonitorTypeOption("kafka-producer", "Kafka producer"),
        MonitorTypeOption("mqtt", "MQTT"),
        MonitorTypeOption("ntp", "NTP"),
        MonitorTypeOption("rabbitmq", "RabbitMQ"),
        MonitorTypeOption("sip-options", "SIP options ping"),
        MonitorTypeOption("smtp", "SMTP"),
        MonitorTypeOption("snmp", "SNMP"),
        MonitorTypeOption(
            "sftp",
            "SFTP",
            MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            defaultPort = 22,
        ),
        MonitorTypeOption("tailscale-ping", "Tailscale ping"),
        MonitorTypeOption("websocket-upgrade", "WebSocket upgrade", MonitorEndpointKind.URL),
        MonitorTypeOption("sqlserver", "Microsoft SQL Server"),
        MonitorTypeOption("mongodb", "MongoDB"),
        MonitorTypeOption("mysql", "MySQL/MariaDB"),
        MonitorTypeOption("oracledb", "Oracle Database"),
        MonitorTypeOption("postgres", "PostgreSQL"),
        MonitorTypeOption("radius", "RADIUS"),
        MonitorTypeOption("redis", "Redis"),
        MonitorTypeOption("gamedig", "GameDig"),
        MonitorTypeOption("steam", "Steam game server"),
    )

    val creatable: List<MonitorTypeOption> = all.filter(MonitorTypeOption::createSupported)

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
            return MonitorDraft(
                type = option.key,
                endpoint = "",
                port = option.defaultPort,
            )
        }
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
        val option = MonitorTypeCatalog.find(draft.type) ?: return MonitorDraftError.TYPE_UNAVAILABLE
        if (draft.isNew && !option.createSupported) return MonitorDraftError.TYPE_UNAVAILABLE
        if (option.endpointKind != MonitorEndpointKind.NONE && draft.endpoint.trim().isEmpty()) {
            return MonitorDraftError.ENDPOINT_REQUIRED
        }
        if (option.endpointKind == MonitorEndpointKind.URL) {
            val uri = runCatching { URI(draft.endpoint.trim()) }.getOrNull()
            if (uri?.scheme?.lowercase() !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
                return MonitorDraftError.INVALID_URL
            }
        }
        if (option.endpointKind == MonitorEndpointKind.HOST_PORT && draft.port !in 1..65535) {
            return MonitorDraftError.PORT_REQUIRED
        }
        if (draft.type == "sftp") {
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
            put("timeout", if (draft.type in setOf("ping", "sftp")) 10 else 48)
            put("manual_status", 1)
            if (draft.type == "push") put("pushToken", UUID.randomUUID().toString().replace("-", ""))
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
        when (MonitorTypeCatalog.find(draft.type)?.endpointKind) {
            MonitorEndpointKind.URL -> values["url"] = JsonPrimitive(draft.endpoint.trim())
            MonitorEndpointKind.HOST, MonitorEndpointKind.HOST_PORT ->
                values["hostname"] = JsonPrimitive(draft.endpoint.trim())
            else -> Unit
        }
        if (MonitorTypeCatalog.find(draft.type)?.endpointKind == MonitorEndpointKind.HOST_PORT) {
            values["port"] = draft.port?.let(::JsonPrimitive) ?: JsonNull
        }
    }

    private fun applySftp(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (draft.type != "sftp") return
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
}

data class MonitorMutationResult(
    val ok: Boolean,
    val monitorId: Int? = null,
    val message: String? = null,
)
