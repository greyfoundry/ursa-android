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

enum class MonitorEndpointKind { NONE, URL, HOST, HOST_PORT }

data class MonitorTypeOption(
    val key: String,
    val label: String,
    val endpointKind: MonitorEndpointKind = MonitorEndpointKind.NONE,
    val createSupported: Boolean = false,
    val defaultPort: Int? = null,
)

/** Uptime Kuma 2.5.3's monitor-type catalogue. */
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
            active = raw["active"]?.jsonPrimitive?.booleanOrNull ?: raw.int("active") != 0,
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
        applyEndpoint(values, draft)
        return JsonObject(values)
    }

    fun newPayload(draft: MonitorDraft): JsonObject {
        val mutable = buildJsonObject {
            put("type", draft.type)
            put("name", draft.name.trim())
            put("description", draft.description.trim())
            put("parent", JsonNull)
            put("url", "")
            put("method", "GET")
            put("interval", draft.intervalSeconds)
            put("retryInterval", draft.retryIntervalSeconds)
            put("resendInterval", draft.resendIntervalSeconds)
            put("maxretries", draft.maxRetries)
            put("retryOnlyOnStatusCodeFailure", false)
            put("notificationIDList", JsonObject(emptyMap()))
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
            put("timeout", if (draft.type == "ping") 10 else 48)
            put("manual_status", 1)
            if (draft.type == "push") put("pushToken", UUID.randomUUID().toString().replace("-", ""))
        }.toMutableMap()
        applyEndpoint(mutable, draft)
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

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}

data class MonitorMutationResult(
    val ok: Boolean,
    val monitorId: Int? = null,
    val message: String? = null,
)
