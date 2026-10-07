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
import java.net.Inet6Address
import java.net.InetAddress
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

enum class WebSocketAuthMethod(val wireValue: String?) {
    NONE(null),
    BASIC("basic"),
    BEARER("bearer"),
    OAUTH2_CLIENT_CREDENTIALS("oauth2-cc"),
    MTLS("mtls");

    companion object {
        fun fromWire(value: String?): WebSocketAuthMethod? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class WebSocketOAuthAuthMethod(val wireValue: String) {
    AUTHORIZATION_HEADER("client_secret_basic"),
    FORM_BODY("client_secret_post");

    companion object {
        fun fromWire(value: String?): WebSocketOAuthAuthMethod? =
            if (value == null) AUTHORIZATION_HEADER else entries.firstOrNull { it.wireValue == value }
    }
}

enum class MqttCheckType(val wireValue: String) {
    KEYWORD("keyword"),
    JSON_QUERY("json-query");

    companion object {
        fun fromWire(value: String?): MqttCheckType? =
            if (value == null) KEYWORD else entries.firstOrNull { it.wireValue == value }
    }
}

enum class SmtpSecurityMode(val wireValue: String) {
    SMTPS("secure"),
    PLAINTEXT("nostarttls"),
    STARTTLS("starttls");

    companion object {
        fun fromWire(value: String?): SmtpSecurityMode? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class SnmpVersion(val wireValue: String) {
    V1("1"),
    V2C("2c"),
    V3("3"),
    UNSUPPORTED("");

    companion object {
        fun fromWire(value: String?): SnmpVersion =
            if (value == null) V2C else entries.firstOrNull { it.wireValue == value } ?: UNSUPPORTED
    }
}

enum class KafkaSaslMechanism(val wireValue: String) {
    NONE("None"),
    PLAIN("plain"),
    SCRAM_SHA_256("scram-sha-256"),
    SCRAM_SHA_512("scram-sha-512"),
    AWS("aws"),
    UNSUPPORTED("");

    companion object {
        fun fromWire(value: String?): KafkaSaslMechanism =
            if (value == null) NONE else entries.firstOrNull { it.wireValue == value } ?: UNSUPPORTED
    }
}

enum class GlobalpingSubtype(val wireValue: String) {
    PING("ping"),
    HTTP("http"),
    DNS("dns");

    companion object {
        fun fromWire(value: String?): GlobalpingSubtype? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class GlobalpingIpFamily(val wireValue: String?) {
    AUTO(null),
    IPV4("ipv4"),
    IPV6("ipv6");

    companion object {
        fun fromWire(value: String?): GlobalpingIpFamily? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class GlobalpingPingProtocol(val wireValue: String) {
    ICMP("ICMP"),
    TCP("TCP");

    companion object {
        fun fromWire(value: String?): GlobalpingPingProtocol? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class GlobalpingDnsProtocol(val wireValue: String) {
    UDP("UDP"),
    TCP("TCP");

    companion object {
        fun fromWire(value: String?): GlobalpingDnsProtocol? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class GlobalpingDnsRecordType(val wireValue: String) {
    A("A"),
    AAAA("AAAA"),
    ANY("ANY"),
    CNAME("CNAME"),
    DNSKEY("DNSKEY"),
    DS("DS"),
    HTTPS("HTTPS"),
    MX("MX"),
    NS("NS"),
    NSEC("NSEC"),
    PTR("PTR"),
    RRSIG("RRSIG"),
    SOA("SOA"),
    SRV("SRV"),
    SVCB("SVCB"),
    TXT("TXT");

    companion object {
        fun fromWire(value: String?): GlobalpingDnsRecordType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class GlobalpingHttpProtocol(val wireValue: String?) {
    AUTO(null),
    HTTP2("HTTP2");

    companion object {
        fun fromWire(value: String?): GlobalpingHttpProtocol? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class GlobalpingHttpMethod(val wireValue: String) {
    HEAD("HEAD"),
    GET("GET"),
    OPTIONS("OPTIONS");

    companion object {
        fun fromWire(value: String?): GlobalpingHttpMethod? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class GlobalpingHttpResponseCheck {
    NONE,
    KEYWORD,
    JSON_QUERY,
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
    val websocketAuthMethod: WebSocketAuthMethod = WebSocketAuthMethod.NONE,
    val websocketOriginalAuthMethod: WebSocketAuthMethod? = null,
    val websocketAuthEditable: Boolean = true,
    val websocketBasicUsername: String = "",
    val websocketBasicPassword: String = "",
    val websocketHasSavedBasicPassword: Boolean = false,
    val websocketBearerToken: String = "",
    val websocketHasSavedBearerToken: Boolean = false,
    val websocketOAuthAuthMethod: WebSocketOAuthAuthMethod = WebSocketOAuthAuthMethod.AUTHORIZATION_HEADER,
    val websocketOAuthTokenUrl: String = "",
    val websocketOAuthClientId: String = "",
    val websocketOAuthClientSecret: String = "",
    val websocketHasSavedOAuthClientSecret: Boolean = false,
    val websocketOAuthScopes: String = "",
    val websocketOAuthAudience: String = "",
    val websocketTlsCertificate: String = "",
    val websocketTlsPrivateKey: String = "",
    val websocketTlsCaCertificate: String = "",
    val websocketHasSavedTlsCertificate: Boolean = false,
    val websocketHasSavedTlsPrivateKey: Boolean = false,
    val websocketHasSavedTlsCaCertificate: Boolean = false,
    val websocketClearSavedTlsCaCertificate: Boolean = false,
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val mqttHasSavedPassword: Boolean = false,
    val mqttClearSavedPassword: Boolean = false,
    val mqttTopic: String = "",
    val mqttWebsocketPath: String = "",
    val mqttCheckType: MqttCheckType = MqttCheckType.KEYWORD,
    val mqttFieldsEditable: Boolean = true,
    val mqttSuccessMessage: String = "",
    val mqttJsonQueryExpression: String = "$",
    val mqttJsonQueryExpectedValue: String = "",
    val smtpSecurityMode: SmtpSecurityMode? = null,
    val smtpSecurityEditable: Boolean = true,
    val ntpStratumThreshold: Int? = null,
    val ntpTimeOffsetThreshold: Int? = null,
    val ntpRootDispersionThreshold: Int? = null,
    val globalpingSubtype: GlobalpingSubtype? = null,
    val globalpingOriginalSubtype: GlobalpingSubtype? = null,
    val globalpingTarget: String = "",
    val globalpingLocation: String = "",
    val globalpingIpFamily: GlobalpingIpFamily? = null,
    val globalpingPingProtocol: GlobalpingPingProtocol? = null,
    val globalpingPingCount: Int? = null,
    val globalpingDnsProtocol: GlobalpingDnsProtocol? = null,
    val globalpingDnsRecordType: GlobalpingDnsRecordType? = null,
    val globalpingResolver: String = "",
    val globalpingHttpProtocol: GlobalpingHttpProtocol? = null,
    val globalpingHttpMethod: GlobalpingHttpMethod? = null,
    val globalpingHttpAcceptedCodes: String = "200-299",
    val globalpingHttpResponseCheck: GlobalpingHttpResponseCheck = GlobalpingHttpResponseCheck.NONE,
    val globalpingHttpIgnoreTls: Boolean = false,
    val globalpingHttpExpiryNotification: Boolean = false,
    val globalpingHttpCacheBust: Boolean = false,
    val globalpingEditable: Boolean = true,
    val databaseConnectionString: String = "",
    val databaseHasSavedConnectionString: Boolean = false,
    val databaseQuery: String = "",
    val databasePassword: String = "",
    val databaseHasSavedPassword: Boolean = false,
    val databaseIgnoreTls: Boolean = false,
    val databaseJsonQueryExpression: String = "$",
    val databaseExpectedValue: String = "",
    val grpcTarget: String = "",
    val grpcProtobuf: String = "",
    val grpcServiceName: String = "",
    val grpcMethod: String = "",
    val grpcBody: String = "",
    val grpcHasSavedBody: Boolean = false,
    val grpcEnableTls: Boolean = false,
    val snmpVersion: SnmpVersion = SnmpVersion.V2C,
    val snmpFieldsEditable: Boolean = true,
    val snmpCommunity: String = "",
    val snmpHasSavedCommunity: Boolean = false,
    val snmpOid: String = "",
    val snmpTimeoutSeconds: String = "48",
    val brokerTimeoutSeconds: String = "48",
    val rabbitmqNodes: String = "",
    val rabbitmqUsername: String = "",
    val rabbitmqPassword: String = "",
    val rabbitmqHasSavedPassword: Boolean = false,
    val kafkaBrokers: String = "",
    val kafkaTopic: String = "",
    val kafkaMessage: String = "",
    val kafkaSsl: Boolean = false,
    val kafkaAllowAutoTopicCreation: Boolean = false,
    val kafkaSaslMechanism: KafkaSaslMechanism = KafkaSaslMechanism.NONE,
    val kafkaOriginalSaslMechanism: KafkaSaslMechanism? = null,
    val kafkaSaslEditable: Boolean = true,
    val kafkaUsername: String = "",
    val kafkaPassword: String = "",
    val kafkaHasSavedPassword: Boolean = false,
    val kafkaAuthorizationIdentity: String = "",
    val kafkaAccessKeyId: String = "",
    val kafkaSecretAccessKey: String = "",
    val kafkaHasSavedSecretAccessKey: Boolean = false,
    val kafkaSessionToken: String = "",
    val kafkaHasSavedSessionToken: Boolean = false,
    val kafkaClearSavedSessionToken: Boolean = false,
    val dockerContainer: String = "",
    val dockerHostId: Int? = null,
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
                ntpStratumThreshold = if (option.key == "ntp") 5 else null,
                ntpTimeOffsetThreshold = if (option.key == "ntp") 1_000 else null,
                ntpRootDispersionThreshold = if (option.key == "ntp") 500 else null,
                globalpingSubtype = if (option.key == "globalping") GlobalpingSubtype.PING else null,
                globalpingLocation = if (option.key == "globalping") "world" else "",
                globalpingIpFamily = if (option.key == "globalping") GlobalpingIpFamily.AUTO else null,
                globalpingPingProtocol = if (option.key == "globalping") GlobalpingPingProtocol.ICMP else null,
                globalpingPingCount = if (option.key == "globalping") 3 else null,
                globalpingDnsProtocol = if (option.key == "globalping") GlobalpingDnsProtocol.UDP else null,
                globalpingDnsRecordType = if (option.key == "globalping") GlobalpingDnsRecordType.A else null,
                globalpingHttpProtocol = if (option.key == "globalping") GlobalpingHttpProtocol.AUTO else null,
                globalpingHttpMethod = if (option.key == "globalping") GlobalpingHttpMethod.GET else null,
                databaseJsonQueryExpression = if (option.key == "mongodb") "$" else "",
                grpcBody = if (option.key == "grpc-keyword") "{}" else "",
                snmpTimeoutSeconds = defaults.timeoutSeconds.toString(),
                brokerTimeoutSeconds = defaults.timeoutSeconds.toString(),
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
    WEBSOCKET_BASIC_PASSWORD_REQUIRED,
    WEBSOCKET_BEARER_TOKEN_REQUIRED,
    WEBSOCKET_OAUTH_TOKEN_URL_REQUIRED,
    WEBSOCKET_OAUTH_TOKEN_URL_INVALID,
    WEBSOCKET_OAUTH_CLIENT_ID_REQUIRED,
    WEBSOCKET_OAUTH_CLIENT_SECRET_REQUIRED,
    WEBSOCKET_MTLS_CERTIFICATE_REQUIRED,
    WEBSOCKET_MTLS_PRIVATE_KEY_REQUIRED,
    MQTT_ENDPOINT_INVALID,
    MQTT_TOPIC_REQUIRED,
    MQTT_WEBSOCKET_PATH_INVALID,
    MQTT_JSON_QUERY_EXPRESSION_REQUIRED,
    MQTT_JSON_QUERY_EXPECTED_VALUE_REQUIRED,
    SMTP_HOST_INVALID,
    SMTP_SECURITY_REQUIRED,
    NTP_HOST_INVALID,
    NTP_STRATUM_THRESHOLD_INVALID,
    NTP_TIME_OFFSET_THRESHOLD_INVALID,
    NTP_ROOT_DISPERSION_THRESHOLD_INVALID,
    GLOBALPING_HOST_INVALID,
    GLOBALPING_LOCATION_REQUIRED,
    GLOBALPING_LOCATION_MULTIPLE,
    GLOBALPING_PROTOCOL_INVALID,
    GLOBALPING_PORT_REQUIRED,
    GLOBALPING_PING_COUNT_INVALID,
    GLOBALPING_RESOLVER_INVALID,
    GLOBALPING_DNS_RECORD_TYPE_INVALID,
    GLOBALPING_HTTP_URL_INVALID,
    GLOBALPING_HTTP_METHOD_INVALID,
    GLOBALPING_HTTP_STATUS_CODES_REQUIRED,
    GLOBALPING_HTTP_STATUS_CODE_INVALID,
    GLOBALPING_HTTP_KEYWORD_REQUIRED,
    GLOBALPING_HTTP_JSON_QUERY_EXPRESSION_REQUIRED,
    GLOBALPING_HTTP_JSON_QUERY_OPERATOR_INVALID,
    GLOBALPING_HTTP_JSON_QUERY_EXPECTED_VALUE_REQUIRED,
    DATABASE_CONNECTION_STRING_REQUIRED,
    DATABASE_MONGODB_COMMAND_INVALID,
    GRPC_TARGET_REQUIRED,
    GRPC_PROTOBUF_REQUIRED,
    GRPC_SERVICE_REQUIRED,
    GRPC_METHOD_REQUIRED,
    GRPC_BODY_INVALID,
    SNMP_HOST_INVALID,
    SNMP_COMMUNITY_REQUIRED,
    SNMP_OID_INVALID,
    SNMP_TIMEOUT_INVALID,
    BROKER_TIMEOUT_INVALID,
    RABBITMQ_NODES_REQUIRED,
    RABBITMQ_NODE_INVALID,
    RABBITMQ_USERNAME_REQUIRED,
    RABBITMQ_PASSWORD_REQUIRED,
    KAFKA_BROKERS_REQUIRED,
    KAFKA_BROKER_INVALID,
    KAFKA_TOPIC_REQUIRED,
    KAFKA_MESSAGE_REQUIRED,
    KAFKA_SASL_USERNAME_REQUIRED,
    KAFKA_SASL_PASSWORD_REQUIRED,
    KAFKA_AWS_IDENTITY_REQUIRED,
    KAFKA_AWS_ACCESS_KEY_REQUIRED,
    KAFKA_AWS_SECRET_REQUIRED,
    DOCKER_CONTAINER_REQUIRED,
    DOCKER_HOST_REQUIRED,
    SFTP_USERNAME_REQUIRED,
    SFTP_PASSWORD_REQUIRED,
    SFTP_PRIVATE_KEY_REQUIRED,
}

object MonitorDraftCodec {
    fun from(raw: JsonObject): MonitorDraft? {
        val id = raw.int("id")?.takeIf { it > 0 } ?: return null
        val type = raw.string("type") ?: return null
        val option = MonitorTypeCatalog.find(type)
        val rawGlobalpingSubtype = raw.string("subtype")
        val globalpingSubtype = GlobalpingSubtype.fromWire(rawGlobalpingSubtype)
        val usesRequestOptions = type == "websocket-upgrade" ||
            type == "globalping" && globalpingSubtype == GlobalpingSubtype.HTTP
        val websocketHeaders = parseRequestHeaders(raw.string("headers"), usesRequestOptions)
        val rawWebsocketAuthMethod = raw.string("authMethod")
        val websocketAuthMethod = WebSocketAuthMethod.fromWire(rawWebsocketAuthMethod)
        val websocketOAuthAuthMethod = WebSocketOAuthAuthMethod.fromWire(raw.string("oauth_auth_method"))
        val rawMqttCheckType = raw.string("mqttCheckType")
        val mqttCheckType = MqttCheckType.fromWire(rawMqttCheckType)
        val rawSmtpSecurity = raw.string("smtpSecurity")
        val smtpSecurityMode = SmtpSecurityMode.fromWire(rawSmtpSecurity)
        val rawSnmpVersion = raw.string("snmpVersion")
        val snmpVersion = SnmpVersion.fromWire(rawSnmpVersion)
        val kafkaSasl = raw["kafkaProducerSaslOptions"] as? JsonObject
        val rawKafkaSaslMechanism = kafkaSasl?.string("mechanism")
        val kafkaSaslMechanism = KafkaSaslMechanism.fromWire(rawKafkaSaslMechanism)
        val isDatabase = type in DATABASE_TYPES
        val rawGlobalpingIpFamily = raw.string("ipFamily")
        val globalpingIpFamily = GlobalpingIpFamily.fromWire(rawGlobalpingIpFamily)
        val rawGlobalpingProtocol = raw.string("protocol")
        val globalpingPingProtocol = GlobalpingPingProtocol.fromWire(rawGlobalpingProtocol)
        val globalpingDnsProtocol = GlobalpingDnsProtocol.fromWire(rawGlobalpingProtocol)
        val globalpingDnsRecordType = GlobalpingDnsRecordType.fromWire(raw.string("dns_resolve_type"))
        val globalpingHttpProtocol = GlobalpingHttpProtocol.fromWire(rawGlobalpingProtocol)
        val globalpingHttpMethod = GlobalpingHttpMethod.fromWire(raw.string("method"))
        val globalpingHttpAcceptedCodes = (raw["accepted_statuscodes"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.joinToString(", ")
            ?.takeIf(String::isNotBlank)
            ?: "200-299"
        val globalpingHttpResponseCheck = when {
            raw.string("keyword").isNullOrEmpty().not() -> GlobalpingHttpResponseCheck.KEYWORD
            raw.string("expectedValue").isNullOrEmpty().not() -> GlobalpingHttpResponseCheck.JSON_QUERY
            else -> GlobalpingHttpResponseCheck.NONE
        }
        val requestAuthEditable = !usesRequestOptions ||
            websocketAuthMethod != null &&
            (websocketAuthMethod != WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS || websocketOAuthAuthMethod != null) &&
            (type != "globalping" || websocketAuthMethod != WebSocketAuthMethod.MTLS)
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
            websocketAuthMethod = websocketAuthMethod ?: WebSocketAuthMethod.NONE,
            websocketOriginalAuthMethod = if (usesRequestOptions) websocketAuthMethod else null,
            websocketAuthEditable = requestAuthEditable,
            websocketBasicUsername = if (usesRequestOptions) {
                raw.string("basic_auth_user").orEmpty()
            } else {
                ""
            },
            websocketHasSavedBasicPassword = usesRequestOptions &&
                raw.string("basic_auth_pass")?.isNotEmpty() == true,
            websocketHasSavedBearerToken = usesRequestOptions &&
                raw.string("bearer_token")?.isNotEmpty() == true,
            websocketOAuthAuthMethod = websocketOAuthAuthMethod ?: WebSocketOAuthAuthMethod.AUTHORIZATION_HEADER,
            websocketOAuthTokenUrl = raw.string("oauth_token_url").orEmpty(),
            websocketOAuthClientId = raw.string("oauth_client_id").orEmpty(),
            websocketHasSavedOAuthClientSecret = usesRequestOptions &&
                raw.string("oauth_client_secret")?.isNotEmpty() == true,
            websocketOAuthScopes = raw.string("oauth_scopes").orEmpty(),
            websocketOAuthAudience = raw.string("oauth_audience").orEmpty(),
            websocketHasSavedTlsCertificate = type == "websocket-upgrade" &&
                raw.string("tlsCert")?.isNotEmpty() == true,
            websocketHasSavedTlsPrivateKey = type == "websocket-upgrade" &&
                raw.string("tlsKey")?.isNotEmpty() == true,
            websocketHasSavedTlsCaCertificate = type == "websocket-upgrade" &&
                raw.string("tlsCa")?.isNotEmpty() == true,
            mqttUsername = raw.string("mqttUsername").orEmpty(),
            mqttHasSavedPassword = type == "mqtt" && raw.string("mqttPassword")?.isNotEmpty() == true,
            mqttTopic = raw.string("mqttTopic").orEmpty(),
            mqttWebsocketPath = raw.string("mqttWebsocketPath").orEmpty(),
            mqttCheckType = mqttCheckType ?: MqttCheckType.KEYWORD,
            mqttFieldsEditable = type != "mqtt" || mqttCheckType != null,
            mqttSuccessMessage = raw.string("mqttSuccessMessage").orEmpty(),
            mqttJsonQueryExpression = raw.string("jsonPath") ?: "$",
            mqttJsonQueryExpectedValue = raw.string("expectedValue").orEmpty(),
            smtpSecurityMode = smtpSecurityMode,
            smtpSecurityEditable = type != "smtp" || smtpSecurityMode != null,
            ntpStratumThreshold = raw.int("ntpStratumThreshold"),
            ntpTimeOffsetThreshold = raw.int("ntpTimeOffsetThreshold"),
            ntpRootDispersionThreshold = raw.int("ntpRootDispersionThreshold"),
            globalpingSubtype = globalpingSubtype,
            globalpingOriginalSubtype = if (type == "globalping") globalpingSubtype else null,
            globalpingTarget = when (globalpingSubtype) {
                GlobalpingSubtype.HTTP -> raw.string("url")
                GlobalpingSubtype.PING, GlobalpingSubtype.DNS -> raw.string("hostname")
                null -> null
            }.orEmpty(),
            globalpingLocation = raw.string("location").orEmpty(),
            globalpingIpFamily = globalpingIpFamily,
            globalpingPingProtocol = globalpingPingProtocol,
            globalpingPingCount = raw.int("ping_count"),
            globalpingDnsProtocol = globalpingDnsProtocol,
            globalpingDnsRecordType = globalpingDnsRecordType,
            globalpingResolver = raw.string("dns_resolve_server").orEmpty(),
            globalpingHttpProtocol = globalpingHttpProtocol,
            globalpingHttpMethod = globalpingHttpMethod,
            globalpingHttpAcceptedCodes = globalpingHttpAcceptedCodes,
            globalpingHttpResponseCheck = globalpingHttpResponseCheck,
            globalpingHttpIgnoreTls = raw.boolean("ignoreTls"),
            globalpingHttpExpiryNotification = raw.boolean("expiryNotification"),
            globalpingHttpCacheBust = raw.boolean("cacheBust"),
            globalpingEditable = type != "globalping" ||
                (rawGlobalpingIpFamily == null || globalpingIpFamily != null) &&
                when (globalpingSubtype) {
                    GlobalpingSubtype.PING -> globalpingPingProtocol != null
                    GlobalpingSubtype.DNS -> globalpingDnsProtocol != null && globalpingDnsRecordType != null
                    GlobalpingSubtype.HTTP -> globalpingHttpProtocol != null &&
                        globalpingHttpMethod != null &&
                        httpStatusCodes(globalpingHttpAcceptedCodes) != null
                    null -> false
                },
            databaseHasSavedConnectionString = isDatabase &&
                raw.string("databaseConnectionString")?.isNotEmpty() == true,
            databaseQuery = if (type in DATABASE_QUERY_TYPES) raw.string("databaseQuery").orEmpty() else "",
            databaseHasSavedPassword = type == "mysql" && raw.string("radiusPassword")?.isNotEmpty() == true,
            databaseIgnoreTls = type == "redis" && raw.boolean("ignoreTls"),
            databaseJsonQueryExpression = if (type == "mongodb") raw.string("jsonPath") ?: "$" else "",
            databaseExpectedValue = if (type == "mongodb") raw.string("expectedValue").orEmpty() else "",
            grpcTarget = if (type == "grpc-keyword") raw.string("grpcUrl").orEmpty() else "",
            grpcProtobuf = if (type == "grpc-keyword") raw.string("grpcProtobuf").orEmpty() else "",
            grpcServiceName = if (type == "grpc-keyword") raw.string("grpcServiceName").orEmpty() else "",
            grpcMethod = if (type == "grpc-keyword") raw.string("grpcMethod").orEmpty() else "",
            grpcHasSavedBody = type == "grpc-keyword" && raw.string("grpcBody")?.isNotEmpty() == true,
            grpcEnableTls = type == "grpc-keyword" && raw.boolean("grpcEnableTls"),
            snmpVersion = snmpVersion,
            snmpFieldsEditable = type != "snmp" ||
                rawSnmpVersion == null || snmpVersion == SnmpVersion.V1 || snmpVersion == SnmpVersion.V2C,
            snmpHasSavedCommunity = type == "snmp" && raw.string("radiusPassword")?.isNotEmpty() == true,
            snmpOid = if (type == "snmp") raw.string("snmpOid").orEmpty() else "",
            snmpTimeoutSeconds = if (type == "snmp") {
                raw.string("timeout") ?: MonitorEditorRegistry.find(type)?.defaults?.timeoutSeconds?.toString().orEmpty()
            } else {
                "48"
            },
            brokerTimeoutSeconds = if (type == "rabbitmq" || type == "kafka-producer") {
                raw.string("timeout") ?: MonitorEditorRegistry.find(type)?.defaults?.timeoutSeconds?.toString().orEmpty()
            } else {
                "48"
            },
            rabbitmqNodes = if (type == "rabbitmq") raw.stringList("rabbitmqNodes").joinToString("\n") else "",
            rabbitmqUsername = if (type == "rabbitmq") raw.string("rabbitmqUsername").orEmpty() else "",
            rabbitmqHasSavedPassword = type == "rabbitmq" && raw.string("rabbitmqPassword")?.isNotEmpty() == true,
            kafkaBrokers = if (type == "kafka-producer") {
                raw.stringList("kafkaProducerBrokers").joinToString("\n")
            } else {
                ""
            },
            kafkaTopic = if (type == "kafka-producer") raw.string("kafkaProducerTopic").orEmpty() else "",
            kafkaMessage = if (type == "kafka-producer") raw.string("kafkaProducerMessage").orEmpty() else "",
            kafkaSsl = type == "kafka-producer" && raw.boolean("kafkaProducerSsl"),
            kafkaAllowAutoTopicCreation = type == "kafka-producer" &&
                raw.boolean("kafkaProducerAllowAutoTopicCreation"),
            kafkaSaslMechanism = kafkaSaslMechanism,
            kafkaOriginalSaslMechanism = if (type == "kafka-producer") kafkaSaslMechanism else null,
            kafkaSaslEditable = type != "kafka-producer" ||
                rawKafkaSaslMechanism == null || kafkaSaslMechanism != KafkaSaslMechanism.UNSUPPORTED,
            kafkaUsername = kafkaSasl?.string("username").orEmpty(),
            kafkaHasSavedPassword = type == "kafka-producer" && kafkaSasl?.string("password")?.isNotEmpty() == true,
            kafkaAuthorizationIdentity = kafkaSasl?.string("authorizationIdentity").orEmpty(),
            kafkaAccessKeyId = kafkaSasl?.string("accessKeyId").orEmpty(),
            kafkaHasSavedSecretAccessKey = type == "kafka-producer" &&
                kafkaSasl?.string("secretAccessKey")?.isNotEmpty() == true,
            kafkaHasSavedSessionToken = type == "kafka-producer" &&
                kafkaSasl?.string("sessionToken")?.isNotEmpty() == true,
            dockerContainer = if (type == "docker") raw.string("docker_container").orEmpty() else "",
            dockerHostId = if (type == "docker") raw.int("docker_host") else null,
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
        if (definition.validation == MonitorEditorValidation.MQTT && draft.mqttFieldsEditable) {
            if (!isValidMqttEndpoint(draft.endpoint)) return MonitorDraftError.MQTT_ENDPOINT_INVALID
            if (draft.mqttTopic.isEmpty()) return MonitorDraftError.MQTT_TOPIC_REQUIRED
            if (
                draft.mqttWebsocketPath.isNotEmpty() &&
                !MQTT_WEBSOCKET_PATH.matches(draft.mqttWebsocketPath)
            ) {
                return MonitorDraftError.MQTT_WEBSOCKET_PATH_INVALID
            }
            if (draft.mqttCheckType == MqttCheckType.JSON_QUERY) {
                if (draft.mqttJsonQueryExpression.isEmpty()) {
                    return MonitorDraftError.MQTT_JSON_QUERY_EXPRESSION_REQUIRED
                }
                if (draft.mqttJsonQueryExpectedValue.isEmpty()) {
                    return MonitorDraftError.MQTT_JSON_QUERY_EXPECTED_VALUE_REQUIRED
                }
            }
        }
        if (definition.validation == MonitorEditorValidation.SMTP) {
            if (!isValidHostOrIp(draft.endpoint)) return MonitorDraftError.SMTP_HOST_INVALID
            if (draft.smtpSecurityEditable && draft.smtpSecurityMode == null) {
                return MonitorDraftError.SMTP_SECURITY_REQUIRED
            }
        }
        if (definition.validation == MonitorEditorValidation.NTP) {
            if (!isValidHostOrIp(draft.endpoint)) return MonitorDraftError.NTP_HOST_INVALID
            if (draft.ntpStratumThreshold != null && draft.ntpStratumThreshold !in 1..15) {
                return MonitorDraftError.NTP_STRATUM_THRESHOLD_INVALID
            }
            if (draft.ntpTimeOffsetThreshold != null && draft.ntpTimeOffsetThreshold < 1) {
                return MonitorDraftError.NTP_TIME_OFFSET_THRESHOLD_INVALID
            }
            if (draft.ntpRootDispersionThreshold != null && draft.ntpRootDispersionThreshold < 1) {
                return MonitorDraftError.NTP_ROOT_DISPERSION_THRESHOLD_INVALID
            }
        }
        if (definition.validation == MonitorEditorValidation.GLOBALPING && draft.globalpingEditable) {
            if (draft.globalpingLocation.trim().isEmpty()) return MonitorDraftError.GLOBALPING_LOCATION_REQUIRED
            if (',' in draft.globalpingLocation) return MonitorDraftError.GLOBALPING_LOCATION_MULTIPLE
            if (draft.globalpingResolver.isNotBlank() && !isValidHostOrIp(draft.globalpingResolver)) {
                return MonitorDraftError.GLOBALPING_RESOLVER_INVALID
            }
            when (draft.globalpingSubtype) {
                GlobalpingSubtype.PING -> {
                    if (!isValidHostOrIp(draft.globalpingTarget)) {
                        return MonitorDraftError.GLOBALPING_HOST_INVALID
                    }
                    val protocol = draft.globalpingPingProtocol
                        ?: return MonitorDraftError.GLOBALPING_PROTOCOL_INVALID
                    if (protocol == GlobalpingPingProtocol.TCP && (draft.port ?: 0) !in 1..65_535) {
                        return MonitorDraftError.GLOBALPING_PORT_REQUIRED
                    }
                    if ((draft.globalpingPingCount ?: 0) !in 1..100) {
                        return MonitorDraftError.GLOBALPING_PING_COUNT_INVALID
                    }
                }
                GlobalpingSubtype.DNS -> {
                    if (!isValidHostOrIp(draft.globalpingTarget)) {
                        return MonitorDraftError.GLOBALPING_HOST_INVALID
                    }
                    if (draft.port !in 1..65_535) return MonitorDraftError.GLOBALPING_PORT_REQUIRED
                    if (draft.globalpingDnsProtocol == null) {
                        return MonitorDraftError.GLOBALPING_PROTOCOL_INVALID
                    }
                    if (draft.globalpingDnsRecordType == null) {
                        return MonitorDraftError.GLOBALPING_DNS_RECORD_TYPE_INVALID
                    }
                }
                GlobalpingSubtype.HTTP -> {
                    val uri = runCatching { URI(draft.globalpingTarget.trim()) }.getOrNull()
                    if (uri?.scheme?.lowercase() !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
                        return MonitorDraftError.GLOBALPING_HTTP_URL_INVALID
                    }
                    if (draft.globalpingHttpProtocol == null) {
                        return MonitorDraftError.GLOBALPING_PROTOCOL_INVALID
                    }
                    if (draft.globalpingHttpMethod == null) {
                        return MonitorDraftError.GLOBALPING_HTTP_METHOD_INVALID
                    }
                    if (draft.globalpingHttpAcceptedCodes.isBlank()) {
                        return MonitorDraftError.GLOBALPING_HTTP_STATUS_CODES_REQUIRED
                    }
                    if (httpStatusCodes(draft.globalpingHttpAcceptedCodes) == null) {
                        return MonitorDraftError.GLOBALPING_HTTP_STATUS_CODE_INVALID
                    }
                    when (draft.globalpingHttpResponseCheck) {
                        GlobalpingHttpResponseCheck.NONE -> Unit
                        GlobalpingHttpResponseCheck.KEYWORD -> if (draft.keyword.isEmpty()) {
                            return MonitorDraftError.GLOBALPING_HTTP_KEYWORD_REQUIRED
                        }
                        GlobalpingHttpResponseCheck.JSON_QUERY -> {
                            if (draft.jsonQueryExpression.isEmpty()) {
                                return MonitorDraftError.GLOBALPING_HTTP_JSON_QUERY_EXPRESSION_REQUIRED
                            }
                            if (draft.jsonQueryOperator !in JSON_QUERY_OPERATORS) {
                                return MonitorDraftError.GLOBALPING_HTTP_JSON_QUERY_OPERATOR_INVALID
                            }
                            if (draft.jsonQueryExpectedValue.isEmpty()) {
                                return MonitorDraftError.GLOBALPING_HTTP_JSON_QUERY_EXPECTED_VALUE_REQUIRED
                            }
                        }
                    }
                    validateRequestOptions(draft, allowMtls = false)?.let { return it }
                }
                null -> return MonitorDraftError.GLOBALPING_PROTOCOL_INVALID
            }
        }
        if (definition.validation == MonitorEditorValidation.DATABASE) {
            if (draft.databaseConnectionString.isBlank() && !draft.databaseHasSavedConnectionString) {
                return MonitorDraftError.DATABASE_CONNECTION_STRING_REQUIRED
            }
            if (
                draft.type == "mongodb" &&
                draft.databaseQuery.isNotBlank() &&
                runCatching { Json.parseToJsonElement(draft.databaseQuery) as? JsonObject }
                    .getOrNull() == null
            ) {
                return MonitorDraftError.DATABASE_MONGODB_COMMAND_INVALID
            }
        }
        if (definition.validation == MonitorEditorValidation.GRPC) {
            if (draft.grpcTarget.isBlank()) return MonitorDraftError.GRPC_TARGET_REQUIRED
            if (draft.grpcProtobuf.isBlank()) return MonitorDraftError.GRPC_PROTOBUF_REQUIRED
            if (draft.grpcServiceName.isBlank()) return MonitorDraftError.GRPC_SERVICE_REQUIRED
            if (draft.grpcMethod.isBlank()) return MonitorDraftError.GRPC_METHOD_REQUIRED
            if (draft.keyword.isEmpty()) return MonitorDraftError.KEYWORD_REQUIRED
            if (
                !draft.grpcHasSavedBody || draft.grpcBody.isNotEmpty()
            ) {
                if (
                    draft.grpcBody.isBlank() ||
                    runCatching { Json.parseToJsonElement(draft.grpcBody) as? JsonObject }
                        .getOrNull() == null
                ) {
                    return MonitorDraftError.GRPC_BODY_INVALID
                }
            }
        }
        if (definition.validation == MonitorEditorValidation.SNMP && draft.snmpFieldsEditable) {
            if (!isValidHostOrIp(draft.endpoint)) return MonitorDraftError.SNMP_HOST_INVALID
            if (draft.snmpCommunity.isEmpty() && !draft.snmpHasSavedCommunity) {
                return MonitorDraftError.SNMP_COMMUNITY_REQUIRED
            }
            if (!SNMP_OID.matches(draft.snmpOid.trim())) return MonitorDraftError.SNMP_OID_INVALID
            val timeout = draft.snmpTimeoutSeconds.trim().toDoubleOrNull()
            if (timeout == null || timeout < 0 || timeout > draft.intervalSeconds * 0.8) {
                return MonitorDraftError.SNMP_TIMEOUT_INVALID
            }
            if (draft.jsonQueryExpression.isEmpty()) return MonitorDraftError.JSON_QUERY_EXPRESSION_REQUIRED
            if (draft.jsonQueryOperator !in JSON_QUERY_OPERATORS) {
                return MonitorDraftError.JSON_QUERY_OPERATOR_INVALID
            }
            if (draft.jsonQueryExpectedValue.isEmpty()) {
                return MonitorDraftError.JSON_QUERY_EXPECTED_VALUE_REQUIRED
            }
        }
        if (definition.validation == MonitorEditorValidation.RABBITMQ) {
            val nodes = lineValues(draft.rabbitmqNodes)
            if (nodes.isEmpty()) return MonitorDraftError.RABBITMQ_NODES_REQUIRED
            if (nodes.any { !isHttpUrl(it) }) return MonitorDraftError.RABBITMQ_NODE_INVALID
            if (draft.rabbitmqUsername.trim().isEmpty()) return MonitorDraftError.RABBITMQ_USERNAME_REQUIRED
            if (draft.rabbitmqPassword.isEmpty() && !draft.rabbitmqHasSavedPassword) {
                return MonitorDraftError.RABBITMQ_PASSWORD_REQUIRED
            }
            if (!isValidBrokerTimeout(draft)) return MonitorDraftError.BROKER_TIMEOUT_INVALID
        }
        if (definition.validation == MonitorEditorValidation.KAFKA) {
            val brokers = lineValues(draft.kafkaBrokers)
            if (brokers.isEmpty()) return MonitorDraftError.KAFKA_BROKERS_REQUIRED
            if (brokers.any { !isKafkaBroker(it) }) return MonitorDraftError.KAFKA_BROKER_INVALID
            if (draft.kafkaTopic.trim().isEmpty()) return MonitorDraftError.KAFKA_TOPIC_REQUIRED
            if (draft.kafkaMessage.isEmpty()) return MonitorDraftError.KAFKA_MESSAGE_REQUIRED
            if (!isValidBrokerTimeout(draft)) return MonitorDraftError.BROKER_TIMEOUT_INVALID
            if (draft.kafkaSaslEditable) {
                when (draft.kafkaSaslMechanism) {
                    KafkaSaslMechanism.NONE -> Unit
                    KafkaSaslMechanism.PLAIN,
                    KafkaSaslMechanism.SCRAM_SHA_256,
                    KafkaSaslMechanism.SCRAM_SHA_512,
                    -> {
                        if (draft.kafkaUsername.trim().isEmpty()) {
                            return MonitorDraftError.KAFKA_SASL_USERNAME_REQUIRED
                        }
                        val canKeepSaved = draft.kafkaOriginalSaslMechanism == draft.kafkaSaslMechanism &&
                            draft.kafkaHasSavedPassword
                        if (draft.kafkaPassword.isEmpty() && !canKeepSaved) {
                            return MonitorDraftError.KAFKA_SASL_PASSWORD_REQUIRED
                        }
                    }
                    KafkaSaslMechanism.AWS -> {
                        if (draft.kafkaAuthorizationIdentity.trim().isEmpty()) {
                            return MonitorDraftError.KAFKA_AWS_IDENTITY_REQUIRED
                        }
                        if (draft.kafkaAccessKeyId.trim().isEmpty()) {
                            return MonitorDraftError.KAFKA_AWS_ACCESS_KEY_REQUIRED
                        }
                        val canKeepSaved = draft.kafkaOriginalSaslMechanism == KafkaSaslMechanism.AWS &&
                            draft.kafkaHasSavedSecretAccessKey
                        if (draft.kafkaSecretAccessKey.isEmpty() && !canKeepSaved) {
                            return MonitorDraftError.KAFKA_AWS_SECRET_REQUIRED
                        }
                    }
                    KafkaSaslMechanism.UNSUPPORTED -> Unit
                }
            }
        }
        if (definition.validation == MonitorEditorValidation.DOCKER) {
            if (draft.dockerContainer.trim().isEmpty()) return MonitorDraftError.DOCKER_CONTAINER_REQUIRED
            if (draft.dockerHostId == null || draft.dockerHostId <= 0) return MonitorDraftError.DOCKER_HOST_REQUIRED
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
            validateRequestOptions(draft, allowMtls = true)?.let { return it }
        }
        if (draft.intervalSeconds < 1 || draft.retryIntervalSeconds < 1 || draft.resendIntervalSeconds < 0) {
            return MonitorDraftError.INVALID_INTERVAL
        }
        if (draft.maxRetries !in 0..100) return MonitorDraftError.INVALID_RETRIES
        return null
    }

    private fun validateRequestOptions(draft: MonitorDraft, allowMtls: Boolean): MonitorDraftError? {
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
        if (!draft.websocketAuthEditable) return null
        return when (draft.websocketAuthMethod) {
            WebSocketAuthMethod.NONE -> null
            WebSocketAuthMethod.BASIC -> {
                val canKeepSaved = draft.websocketOriginalAuthMethod == WebSocketAuthMethod.BASIC &&
                    draft.websocketHasSavedBasicPassword
                MonitorDraftError.WEBSOCKET_BASIC_PASSWORD_REQUIRED
                    .takeIf { draft.websocketBasicPassword.isEmpty() && !canKeepSaved }
            }
            WebSocketAuthMethod.BEARER -> {
                val canKeepSaved = draft.websocketOriginalAuthMethod == WebSocketAuthMethod.BEARER &&
                    draft.websocketHasSavedBearerToken
                MonitorDraftError.WEBSOCKET_BEARER_TOKEN_REQUIRED
                    .takeIf { draft.websocketBearerToken.isEmpty() && !canKeepSaved }
            }
            WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS -> {
                val tokenUrl = draft.websocketOAuthTokenUrl.trim()
                val tokenUri = runCatching { URI(tokenUrl) }.getOrNull()
                when {
                    tokenUrl.isEmpty() -> MonitorDraftError.WEBSOCKET_OAUTH_TOKEN_URL_REQUIRED
                    tokenUri?.scheme?.lowercase() !in setOf("http", "https") || tokenUri?.host.isNullOrBlank() ->
                        MonitorDraftError.WEBSOCKET_OAUTH_TOKEN_URL_INVALID
                    draft.websocketOAuthClientId.trim().isEmpty() ->
                        MonitorDraftError.WEBSOCKET_OAUTH_CLIENT_ID_REQUIRED
                    draft.websocketOAuthClientSecret.isEmpty() &&
                        !(draft.websocketOriginalAuthMethod == WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS &&
                            draft.websocketHasSavedOAuthClientSecret) ->
                        MonitorDraftError.WEBSOCKET_OAUTH_CLIENT_SECRET_REQUIRED
                    else -> null
                }
            }
            WebSocketAuthMethod.MTLS -> when {
                !allowMtls -> MonitorDraftError.GLOBALPING_PROTOCOL_INVALID
                draft.websocketTlsCertificate.isBlank() &&
                    !(draft.websocketOriginalAuthMethod == WebSocketAuthMethod.MTLS &&
                        draft.websocketHasSavedTlsCertificate) ->
                    MonitorDraftError.WEBSOCKET_MTLS_CERTIFICATE_REQUIRED
                draft.websocketTlsPrivateKey.isBlank() &&
                    !(draft.websocketOriginalAuthMethod == WebSocketAuthMethod.MTLS &&
                        draft.websocketHasSavedTlsPrivateKey) ->
                    MonitorDraftError.WEBSOCKET_MTLS_PRIVATE_KEY_REQUIRED
                else -> null
            }
        }
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
        applyMqtt(values, draft, raw)
        applySmtp(values, draft)
        applyNtp(values, draft)
        applyGlobalping(values, draft)
        applyDatabase(values, draft, raw)
        applyGrpc(values, draft, raw)
        applySnmp(values, draft, raw)
        applyRabbitmq(values, draft, raw)
        applyKafka(values, draft, raw)
        applyDocker(values, draft)
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
        applyMqtt(mutable, draft)
        applySmtp(mutable, draft)
        applyNtp(mutable, draft)
        applyGlobalping(mutable, draft)
        applyDatabase(mutable, draft)
        applyGrpc(mutable, draft)
        applySnmp(mutable, draft)
        applyRabbitmq(mutable, draft)
        applyKafka(mutable, draft)
        applyDocker(mutable, draft)
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

    private fun applyDatabase(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.DATABASE) return
        val connectionString = draft.databaseConnectionString.trim().ifEmpty {
            existing?.string("databaseConnectionString").orEmpty()
        }
        values["databaseConnectionString"] = JsonPrimitive(connectionString)
        if (draft.type in DATABASE_QUERY_TYPES) {
            values["databaseQuery"] = JsonPrimitive(draft.databaseQuery.trim())
        }
        if (draft.type == "mysql") {
            val password = draft.databasePassword.ifEmpty { existing?.string("radiusPassword").orEmpty() }
            values["radiusPassword"] = JsonPrimitive(password)
        }
        if (draft.type == "mongodb") {
            values["jsonPath"] = JsonPrimitive(draft.databaseJsonQueryExpression.trim())
            values["expectedValue"] = JsonPrimitive(draft.databaseExpectedValue)
        }
        if (draft.type == "redis") {
            values["ignoreTls"] = JsonPrimitive(draft.databaseIgnoreTls)
        }
    }

    private fun applyGrpc(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.GRPC) return
        values["grpcUrl"] = JsonPrimitive(draft.grpcTarget.trim())
        values["grpcProtobuf"] = JsonPrimitive(draft.grpcProtobuf.trim())
        values["grpcServiceName"] = JsonPrimitive(draft.grpcServiceName.trim())
        values["grpcMethod"] = JsonPrimitive(draft.grpcMethod.trim())
        values["grpcBody"] = JsonPrimitive(
            draft.grpcBody.ifEmpty { existing?.string("grpcBody").orEmpty() },
        )
        values["grpcEnableTls"] = JsonPrimitive(draft.grpcEnableTls)
        values["keyword"] = JsonPrimitive(draft.keyword)
        values["invertKeyword"] = JsonPrimitive(draft.invertKeyword)
    }

    private fun applySnmp(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.SNMP || !draft.snmpFieldsEditable) {
            return
        }
        values["snmpVersion"] = JsonPrimitive(draft.snmpVersion.wireValue)
        values["radiusPassword"] = JsonPrimitive(
            draft.snmpCommunity.ifEmpty { existing?.string("radiusPassword").orEmpty() },
        )
        values["snmpOid"] = JsonPrimitive(draft.snmpOid.trim())
        values["timeout"] = JsonPrimitive(draft.snmpTimeoutSeconds.trim().toDouble())
        values["jsonPath"] = JsonPrimitive(draft.jsonQueryExpression)
        values["jsonPathOperator"] = JsonPrimitive(draft.jsonQueryOperator)
        values["expectedValue"] = JsonPrimitive(draft.jsonQueryExpectedValue)
    }

    private fun applyRabbitmq(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.RABBITMQ) return
        values["rabbitmqNodes"] = JsonArray(lineValues(draft.rabbitmqNodes).map(::JsonPrimitive))
        values["rabbitmqUsername"] = JsonPrimitive(draft.rabbitmqUsername.trim())
        values["rabbitmqPassword"] = JsonPrimitive(
            draft.rabbitmqPassword.ifEmpty { existing?.string("rabbitmqPassword").orEmpty() },
        )
        values["timeout"] = JsonPrimitive(draft.brokerTimeoutSeconds.trim().toDouble())
    }

    private fun applyKafka(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.KAFKA) return
        values["kafkaProducerBrokers"] = JsonArray(lineValues(draft.kafkaBrokers).map(::JsonPrimitive))
        values["kafkaProducerTopic"] = JsonPrimitive(draft.kafkaTopic.trim())
        values["kafkaProducerMessage"] = JsonPrimitive(draft.kafkaMessage)
        values["kafkaProducerSsl"] = JsonPrimitive(draft.kafkaSsl)
        values["kafkaProducerAllowAutoTopicCreation"] = JsonPrimitive(draft.kafkaAllowAutoTopicCreation)
        values["timeout"] = JsonPrimitive(draft.brokerTimeoutSeconds.trim().toDouble())
        if (!draft.kafkaSaslEditable) return

        val previous = existing?.get("kafkaProducerSaslOptions") as? JsonObject
        val options = buildJsonObject {
            put("mechanism", draft.kafkaSaslMechanism.wireValue)
            when (draft.kafkaSaslMechanism) {
                KafkaSaslMechanism.NONE -> Unit
                KafkaSaslMechanism.PLAIN,
                KafkaSaslMechanism.SCRAM_SHA_256,
                KafkaSaslMechanism.SCRAM_SHA_512,
                -> {
                    put("username", draft.kafkaUsername.trim())
                    put(
                        "password",
                        draft.kafkaPassword.ifEmpty {
                            previous?.string("password").orEmpty()
                                .takeIf { draft.kafkaOriginalSaslMechanism == draft.kafkaSaslMechanism }
                                .orEmpty()
                        },
                    )
                }
                KafkaSaslMechanism.AWS -> {
                    put("authorizationIdentity", draft.kafkaAuthorizationIdentity.trim())
                    put("accessKeyId", draft.kafkaAccessKeyId.trim())
                    put(
                        "secretAccessKey",
                        draft.kafkaSecretAccessKey.ifEmpty {
                            previous?.string("secretAccessKey").orEmpty()
                                .takeIf { draft.kafkaOriginalSaslMechanism == KafkaSaslMechanism.AWS }
                                .orEmpty()
                        },
                    )
                    val sessionToken = when {
                        draft.kafkaClearSavedSessionToken -> ""
                        draft.kafkaSessionToken.isNotEmpty() -> draft.kafkaSessionToken
                        draft.kafkaOriginalSaslMechanism == KafkaSaslMechanism.AWS ->
                            previous?.string("sessionToken").orEmpty()
                        else -> ""
                    }
                    if (sessionToken.isNotEmpty()) put("sessionToken", sessionToken)
                }
                KafkaSaslMechanism.UNSUPPORTED -> Unit
            }
        }
        values["kafkaProducerSaslOptions"] = options
    }

    private fun applyDocker(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.DOCKER) return
        values["docker_container"] = JsonPrimitive(draft.dockerContainer.trim())
        values["docker_host"] = draft.dockerHostId?.let(::JsonPrimitive) ?: JsonNull
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

    private fun applyMqtt(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
        existing: JsonObject? = null,
    ) {
        if (
            MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.MQTT ||
            !draft.mqttFieldsEditable
        ) {
            return
        }
        values["mqttUsername"] = JsonPrimitive(draft.mqttUsername)
        values["mqttPassword"] = JsonPrimitive(
            when {
                draft.mqttClearSavedPassword -> ""
                draft.mqttPassword.isNotEmpty() -> draft.mqttPassword
                draft.mqttHasSavedPassword -> existing?.string("mqttPassword").orEmpty()
                else -> ""
            },
        )
        values["mqttTopic"] = JsonPrimitive(draft.mqttTopic)
        values["mqttWebsocketPath"] = JsonPrimitive(draft.mqttWebsocketPath)
        values["mqttCheckType"] = JsonPrimitive(draft.mqttCheckType.wireValue)
        values["mqttSuccessMessage"] = JsonPrimitive(draft.mqttSuccessMessage)
        values["jsonPath"] = JsonPrimitive(draft.mqttJsonQueryExpression)
        values["expectedValue"] = JsonPrimitive(draft.mqttJsonQueryExpectedValue)
    }

    private fun applySmtp(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (
            MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.SMTP ||
            !draft.smtpSecurityEditable
        ) {
            return
        }
        draft.smtpSecurityMode?.let { values["smtpSecurity"] = JsonPrimitive(it.wireValue) }
    }

    private fun applyNtp(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.NTP) return
        values["ntpStratumThreshold"] = draft.ntpStratumThreshold?.let(::JsonPrimitive) ?: JsonNull
        values["ntpTimeOffsetThreshold"] = draft.ntpTimeOffsetThreshold?.let(::JsonPrimitive) ?: JsonNull
        values["ntpRootDispersionThreshold"] =
            draft.ntpRootDispersionThreshold?.let(::JsonPrimitive) ?: JsonNull
    }

    private fun applyGlobalping(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (
            MonitorEditorRegistry.find(draft.type)?.codec != MonitorEditorCodec.GLOBALPING ||
            !draft.globalpingEditable
        ) {
            return
        }
        val subtype = draft.globalpingSubtype ?: return
        values["subtype"] = JsonPrimitive(subtype.wireValue)
        values["location"] = JsonPrimitive(draft.globalpingLocation.trim())
        values["ipFamily"] = draft.globalpingIpFamily?.wireValue?.let(::JsonPrimitive) ?: JsonNull
        when (subtype) {
            GlobalpingSubtype.PING -> {
                values["hostname"] = JsonPrimitive(draft.globalpingTarget.trim())
                values["port"] = draft.port?.let(::JsonPrimitive) ?: JsonNull
                values["protocol"] = JsonPrimitive(draft.globalpingPingProtocol!!.wireValue)
                values["ping_count"] = draft.globalpingPingCount?.let(::JsonPrimitive) ?: JsonNull
            }
            GlobalpingSubtype.DNS -> {
                values["hostname"] = JsonPrimitive(draft.globalpingTarget.trim())
                values["port"] = draft.port?.let(::JsonPrimitive) ?: JsonNull
                values["protocol"] = JsonPrimitive(draft.globalpingDnsProtocol!!.wireValue)
                values["dns_resolve_type"] = JsonPrimitive(draft.globalpingDnsRecordType!!.wireValue)
                values["dns_resolve_server"] = JsonPrimitive(draft.globalpingResolver.trim())
                values["keyword"] = JsonPrimitive(draft.keyword)
            }
            GlobalpingSubtype.HTTP -> {
                values["url"] = JsonPrimitive(draft.globalpingTarget.trim())
                values["protocol"] = draft.globalpingHttpProtocol?.wireValue?.let(::JsonPrimitive) ?: JsonNull
                values["dns_resolve_server"] = JsonPrimitive(draft.globalpingResolver.trim())
                values["method"] = JsonPrimitive(draft.globalpingHttpMethod!!.wireValue)
                values["accepted_statuscodes"] = JsonArray(
                    requireNotNull(httpStatusCodes(draft.globalpingHttpAcceptedCodes)).map(::JsonPrimitive),
                )
                values["ignoreTls"] = JsonPrimitive(draft.globalpingHttpIgnoreTls)
                values["expiryNotification"] = JsonPrimitive(draft.globalpingHttpExpiryNotification)
                values["cacheBust"] = JsonPrimitive(draft.globalpingHttpCacheBust)
                applyGlobalpingHttpResponse(values, draft)
                applyWebsocketHeaders(values, draft)
                applyWebsocketAuth(values, draft)
            }
        }
        if (draft.globalpingOriginalSubtype == GlobalpingSubtype.HTTP && subtype != GlobalpingSubtype.HTTP) {
            clearGlobalpingHttpSecrets(values)
        }
    }

    private fun applyGlobalpingHttpResponse(
        values: MutableMap<String, JsonElement>,
        draft: MonitorDraft,
    ) {
        when (draft.globalpingHttpResponseCheck) {
            GlobalpingHttpResponseCheck.NONE -> {
                values["keyword"] = JsonPrimitive("")
                values["expectedValue"] = JsonPrimitive("")
            }
            GlobalpingHttpResponseCheck.KEYWORD -> {
                values["keyword"] = JsonPrimitive(draft.keyword)
                values["invertKeyword"] = JsonPrimitive(draft.invertKeyword)
                values["expectedValue"] = JsonPrimitive("")
            }
            GlobalpingHttpResponseCheck.JSON_QUERY -> {
                values["keyword"] = JsonPrimitive("")
                values["jsonPath"] = JsonPrimitive(draft.jsonQueryExpression)
                values["jsonPathOperator"] = JsonPrimitive(draft.jsonQueryOperator)
                values["expectedValue"] = JsonPrimitive(draft.jsonQueryExpectedValue)
            }
        }
    }

    private fun clearGlobalpingHttpSecrets(values: MutableMap<String, JsonElement>) {
        values["headers"] = JsonPrimitive("")
        values["authMethod"] = JsonNull
        listOf(
            "basic_auth_user",
            "basic_auth_pass",
            "bearer_token",
            "oauth_auth_method",
            "oauth_token_url",
            "oauth_client_id",
            "oauth_client_secret",
            "oauth_scopes",
            "oauth_audience",
            "tlsCert",
            "tlsKey",
            "tlsCa",
        ).forEach { values[it] = JsonPrimitive("") }
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
        applyWebsocketAuth(values, draft)
    }

    private fun applyWebsocketAuth(values: MutableMap<String, JsonElement>, draft: MonitorDraft) {
        if (!draft.websocketAuthEditable) return
        val basicFields = setOf("basic_auth_user", "basic_auth_pass")
        val bearerFields = setOf("bearer_token")
        val oauthFields = setOf(
            "oauth_auth_method",
            "oauth_token_url",
            "oauth_client_id",
            "oauth_client_secret",
            "oauth_scopes",
            "oauth_audience",
        )
        val tlsFields = setOf("tlsCert", "tlsKey", "tlsCa")
        val credentialFields = basicFields + bearerFields + oauthFields + tlsFields
        fun clear(fields: Set<String>) = fields.forEach { values[it] = JsonPrimitive("") }
        val methodChanged = draft.websocketOriginalAuthMethod != draft.websocketAuthMethod
        if (
            draft.websocketAuthMethod == WebSocketAuthMethod.NONE &&
            "authMethod" !in values &&
            credentialFields.none(values::containsKey)
        ) {
            return
        }
        values["authMethod"] = draft.websocketAuthMethod.wireValue?.let(::JsonPrimitive) ?: JsonNull
        when (draft.websocketAuthMethod) {
            WebSocketAuthMethod.NONE -> if (methodChanged) clear(credentialFields)
            WebSocketAuthMethod.BASIC -> {
                val password = if (draft.websocketBasicPassword.isNotEmpty()) {
                    draft.websocketBasicPassword
                } else if (
                    draft.websocketOriginalAuthMethod == WebSocketAuthMethod.BASIC &&
                    draft.websocketHasSavedBasicPassword
                ) {
                    values["basic_auth_pass"]?.jsonPrimitive?.contentOrNull.orEmpty()
                } else {
                    ""
                }
                values["basic_auth_user"] = JsonPrimitive(draft.websocketBasicUsername.trim())
                values["basic_auth_pass"] = JsonPrimitive(password)
                if (methodChanged) clear(bearerFields + oauthFields + tlsFields)
            }
            WebSocketAuthMethod.BEARER -> {
                val token = if (draft.websocketBearerToken.isNotEmpty()) {
                    draft.websocketBearerToken
                } else if (
                    draft.websocketOriginalAuthMethod == WebSocketAuthMethod.BEARER &&
                    draft.websocketHasSavedBearerToken
                ) {
                    values["bearer_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
                } else {
                    ""
                }
                values["bearer_token"] = JsonPrimitive(token)
                if (methodChanged) clear(basicFields + oauthFields + tlsFields)
            }
            WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS -> {
                val clientSecret = if (draft.websocketOAuthClientSecret.isNotEmpty()) {
                    draft.websocketOAuthClientSecret
                } else if (
                    draft.websocketOriginalAuthMethod == WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS &&
                    draft.websocketHasSavedOAuthClientSecret
                ) {
                    values["oauth_client_secret"]?.jsonPrimitive?.contentOrNull.orEmpty()
                } else {
                    ""
                }
                values["oauth_auth_method"] = JsonPrimitive(draft.websocketOAuthAuthMethod.wireValue)
                values["oauth_token_url"] = JsonPrimitive(draft.websocketOAuthTokenUrl.trim())
                values["oauth_client_id"] = JsonPrimitive(draft.websocketOAuthClientId.trim())
                values["oauth_client_secret"] = JsonPrimitive(clientSecret)
                values["oauth_scopes"] = JsonPrimitive(draft.websocketOAuthScopes.trim())
                values["oauth_audience"] = JsonPrimitive(draft.websocketOAuthAudience.trim())
                if (methodChanged) clear(basicFields + bearerFields + tlsFields)
            }
            WebSocketAuthMethod.MTLS -> {
                val sameMethod = draft.websocketOriginalAuthMethod == WebSocketAuthMethod.MTLS
                fun retained(value: String, saved: Boolean, key: String): String = when {
                    value.isNotBlank() -> value
                    sameMethod && saved -> values[key]?.jsonPrimitive?.contentOrNull.orEmpty()
                    else -> ""
                }
                values["tlsCert"] = JsonPrimitive(
                    retained(draft.websocketTlsCertificate, draft.websocketHasSavedTlsCertificate, "tlsCert"),
                )
                values["tlsKey"] = JsonPrimitive(
                    retained(draft.websocketTlsPrivateKey, draft.websocketHasSavedTlsPrivateKey, "tlsKey"),
                )
                values["tlsCa"] = JsonPrimitive(
                    if (draft.websocketClearSavedTlsCaCertificate) {
                        ""
                    } else {
                        retained(
                            draft.websocketTlsCaCertificate,
                            draft.websocketHasSavedTlsCaCertificate,
                            "tlsCa",
                        )
                    },
                )
                if (methodChanged) clear(basicFields + bearerFields + oauthFields)
            }
        }
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
    private fun JsonObject.stringList(key: String): List<String> = (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.boolean(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull
        ?: int(key)?.let { it != 0 }
        ?: false

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

    private fun lineValues(value: String): List<String> =
        value.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()

    private fun isHttpUrl(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawFragment == null
    }

    private fun isKafkaBroker(value: String): Boolean {
        if (value.any(Char::isWhitespace)) return false
        val uri = runCatching { URI("tcp://$value") }.getOrNull() ?: return false
        return !uri.host.isNullOrBlank() && uri.port in 1..65535 &&
            uri.userInfo == null && uri.rawPath.isEmpty() && uri.rawQuery == null && uri.rawFragment == null
    }

    private fun isValidBrokerTimeout(draft: MonitorDraft): Boolean {
        val timeout = draft.brokerTimeoutSeconds.trim().toDoubleOrNull() ?: return false
        return timeout >= 0 && timeout <= draft.intervalSeconds * 0.8
    }

    val JSON_QUERY_OPERATORS: Set<String> = setOf(">", ">=", "<", "<=", "!=", "==", "contains")

    private val DATABASE_TYPES = setOf("postgres", "mysql", "sqlserver", "mongodb", "redis")
    private val DATABASE_QUERY_TYPES = setOf("postgres", "mysql", "sqlserver", "mongodb")

    private fun websocketAcceptedCodes(value: String): List<String>? {
        val parts = value.split(',').map(String::trim)
        if (parts.any(String::isEmpty)) return null
        val codes = parts.map { code -> code.toIntOrNull()?.takeIf { it in 1000..4999 } ?: return null }
        return codes.distinct().map(Int::toString)
    }

    private fun httpStatusCodes(value: String): List<String>? {
        val parts = value.split(',').map(String::trim)
        if (parts.any(String::isEmpty)) return null
        return parts.map { code ->
            val range = HTTP_STATUS_RANGE.matchEntire(code)
            if (range != null) {
                val first = range.groupValues[1].toInt()
                val last = range.groupValues[2].toInt()
                code.takeIf { first in 100..999 && last in 100..999 && first <= last } ?: return null
            } else {
                code.toIntOrNull()?.takeIf { it in 100..999 }?.toString() ?: return null
            }
        }.distinct()
    }

    private fun parseRequestHeaders(raw: String?, supported: Boolean): ParsedRequestHeaders {
        if (!supported || raw.isNullOrBlank()) return ParsedRequestHeaders()
        val parsed = parseHeaderObject(raw) ?: return ParsedRequestHeaders(editable = false)
        if (parsed.size > WEBSOCKET_HEADER_LIMIT) return ParsedRequestHeaders(editable = false)
        if (parsed.values.any { it !is JsonPrimitive || !it.isString }) {
            return ParsedRequestHeaders(editable = false)
        }
        val names = parsed.keys.map(String::lowercase)
        if (names.distinct().size != names.size) return ParsedRequestHeaders(editable = false)
        return ParsedRequestHeaders(
            drafts = parsed.keys.map { name ->
                MonitorHeaderDraft(name = name, originalName = name, hasSavedValue = true)
            },
        )
    }

    private fun parseHeaderObject(raw: String?): JsonObject? = raw
        ?.takeIf(String::isNotBlank)
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private data class ParsedRequestHeaders(
        val drafts: List<MonitorHeaderDraft> = emptyList(),
        val editable: Boolean = true,
    )

    const val WEBSOCKET_HEADER_LIMIT = 8

    private val SNMP_OID = Regex("^([0-2])((\\.0)|(\\.[1-9][0-9]*))*$")
    private val HTTP_STATUS_RANGE = Regex("^(\\d{3})-(\\d{3})$")

    private fun isValidMqttEndpoint(value: String): Boolean {
        val endpoint = value.trim()
        if (endpoint.isEmpty() || endpoint.any(Char::isWhitespace)) return false
        if ("://" !in endpoint) return true
        val uri = runCatching { URI(endpoint) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in MQTT_SCHEMES && !uri.host.isNullOrBlank()
    }

    private val MQTT_SCHEMES = setOf("mqtt", "mqtts", "ws", "wss")
    private val MQTT_WEBSOCKET_PATH = Regex("^/[A-Za-z0-9-_&()*+]*$")

    private fun isValidHostOrIp(value: String): Boolean {
        val host = value.trim().removeSuffix(".")
        if (
            host.isEmpty() ||
            host.length > 253 ||
            host.any(Char::isWhitespace) ||
            host.any { it in "/?#@" } ||
            "://" in host
        ) {
            return false
        }
        if (':' in host) {
            return runCatching { InetAddress.getByName(host) is Inet6Address }.getOrDefault(false)
        }
        val labels = host.split('.')
        if (labels.size == 4 && labels.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            return labels.all { it.toIntOrNull() in 0..255 }
        }
        return labels.all { label ->
            label.length in 1..63 &&
                label.first() != '-' &&
                label.last() != '-' &&
                label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }
}

data class MonitorMutationResult(
    val ok: Boolean,
    val monitorId: Int? = null,
    val message: String? = null,
)
