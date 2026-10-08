package dev.astoris.ursa.core.network

enum class MonitorEditorField {
    NAME,
    DESCRIPTION,
    ENDPOINT,
    PORT,
    INTERVAL,
    RETRY_INTERVAL,
    RESEND_INTERVAL,
    MAX_RETRIES,
    ACTIVE,
    NOTIFICATIONS,
    PARENT,
    TAGS,
    PUSH_TOKEN,
    KEYWORD,
    INVERT_KEYWORD,
    JSON_QUERY_EXPRESSION,
    JSON_QUERY_OPERATOR,
    JSON_QUERY_EXPECTED_VALUE,
    WEBSOCKET_SUBPROTOCOLS,
    WEBSOCKET_ACCEPTED_CODES,
    WEBSOCKET_IGNORE_ACCEPT_HEADER,
    WEBSOCKET_HEADERS,
    WEBSOCKET_AUTH_METHOD,
    WEBSOCKET_BASIC_USERNAME,
    WEBSOCKET_BASIC_PASSWORD,
    WEBSOCKET_BEARER_TOKEN,
    WEBSOCKET_OAUTH_AUTH_METHOD,
    WEBSOCKET_OAUTH_TOKEN_URL,
    WEBSOCKET_OAUTH_CLIENT_ID,
    WEBSOCKET_OAUTH_CLIENT_SECRET,
    WEBSOCKET_OAUTH_SCOPES,
    WEBSOCKET_OAUTH_AUDIENCE,
    WEBSOCKET_MTLS_CERTIFICATE,
    WEBSOCKET_MTLS_PRIVATE_KEY,
    WEBSOCKET_MTLS_CA_CERTIFICATE,
    MQTT_USERNAME,
    MQTT_PASSWORD,
    MQTT_TOPIC,
    MQTT_WEBSOCKET_PATH,
    MQTT_CHECK_TYPE,
    MQTT_SUCCESS_MESSAGE,
    MQTT_JSON_QUERY_EXPRESSION,
    MQTT_JSON_QUERY_EXPECTED_VALUE,
    SMTP_SECURITY,
    NTP_STRATUM_THRESHOLD,
    NTP_TIME_OFFSET_THRESHOLD,
    NTP_ROOT_DISPERSION_THRESHOLD,
    GLOBALPING_SUBTYPE,
    GLOBALPING_LOCATION,
    GLOBALPING_IP_FAMILY,
    GLOBALPING_PROTOCOL,
    GLOBALPING_PING_COUNT,
    GLOBALPING_RESOLVER,
    GLOBALPING_DNS_RECORD_TYPE,
    GLOBALPING_HTTP_METHOD,
    GLOBALPING_HTTP_ACCEPTED_CODES,
    GLOBALPING_HTTP_RESPONSE_CHECK,
    GLOBALPING_HTTP_IGNORE_TLS,
    GLOBALPING_HTTP_EXPIRY_NOTIFICATION,
    GLOBALPING_HTTP_CACHE_BUST,
    DATABASE_CONNECTION_STRING,
    DATABASE_QUERY,
    DATABASE_USERNAME,
    DATABASE_PASSWORD,
    DATABASE_IGNORE_TLS,
    DATABASE_JSON_QUERY_EXPRESSION,
    DATABASE_EXPECTED_VALUE,
    GRPC_TARGET,
    GRPC_PROTOBUF,
    GRPC_SERVICE_NAME,
    GRPC_METHOD,
    GRPC_BODY,
    GRPC_ENABLE_TLS,
    SNMP_VERSION,
    SNMP_COMMUNITY,
    SNMP_OID,
    SNMP_TIMEOUT,
    BROKER_TIMEOUT,
    RABBITMQ_NODES,
    RABBITMQ_USERNAME,
    RABBITMQ_PASSWORD,
    KAFKA_BROKERS,
    KAFKA_TOPIC,
    KAFKA_MESSAGE,
    KAFKA_SSL,
    KAFKA_AUTO_TOPIC_CREATION,
    KAFKA_SASL_OPTIONS,
    DOCKER_CONTAINER,
    DOCKER_HOST,
    REAL_BROWSER_REMOTE_BROWSER,
    REAL_BROWSER_SCREENSHOT_DELAY,
    RADIUS_USERNAME,
    RADIUS_PASSWORD,
    RADIUS_SECRET,
    RADIUS_CALLED_STATION_ID,
    RADIUS_CALLING_STATION_ID,
    SFTP_AUTH_METHOD,
    SFTP_USERNAME,
    SFTP_PASSWORD,
    SFTP_PRIVATE_KEY,
    SFTP_PASSPHRASE,
    SFTP_PATH,
}

enum class MonitorEditorCodec {
    COMMON,
    PUSH,
    KEYWORD,
    JSON_QUERY,
    WEBSOCKET,
    MQTT,
    SMTP,
    NTP,
    GLOBALPING,
    DATABASE,
    GRPC,
    SNMP,
    RABBITMQ,
    KAFKA,
    DOCKER,
    REAL_BROWSER,
    RADIUS,
    SFTP,
}

enum class MonitorEditorValidation {
    COMMON,
    ENDPOINT,
    PUSH,
    KEYWORD,
    JSON_QUERY,
    WEBSOCKET,
    MQTT,
    SMTP,
    NTP,
    GLOBALPING,
    DATABASE,
    GRPC,
    SNMP,
    RABBITMQ,
    KAFKA,
    DOCKER,
    REAL_BROWSER,
    RADIUS,
    SFTP,
}

enum class MonitorEditorFidelity {
    FULL_FIDELITY,
    SAFE_COMMON_EDIT,
}

enum class MonitorTransferEligibility {
    NOT_ELIGIBLE,
    CREDENTIAL_FREE,
    REQUIRES_SECRET_REENTRY,
}

enum class MonitorEditorHelp {
    FULL_NATIVE,
    COMMON_FIELDS_ONLY,
}

data class MonitorEditorDefaults(
    val port: Int? = null,
    val intervalSeconds: Int = 60,
    val retryIntervalSeconds: Int = 60,
    val resendIntervalSeconds: Int = 0,
    val maxRetries: Int = 0,
    val timeoutSeconds: Int = 48,
)

data class MonitorEditorFieldCondition(
    val field: MonitorEditorField,
    val dependsOn: MonitorEditorField,
    val acceptedValues: Set<String>,
)

data class MonitorEditorFieldDependency(
    val field: MonitorEditorField,
    val requires: Set<MonitorEditorField>,
)

data class MonitorEditorDefinition(
    val type: String,
    val label: String,
    val verifiedMin: KumaVersion,
    val verifiedMax: KumaVersion,
    val endpointKind: MonitorEndpointKind,
    val createSupported: Boolean,
    val defaults: MonitorEditorDefaults,
    val editableFields: Set<MonitorEditorField>,
    val sensitiveFields: Set<MonitorEditorField>,
    val conditions: List<MonitorEditorFieldCondition>,
    val dependencies: List<MonitorEditorFieldDependency>,
    val codec: MonitorEditorCodec,
    val validation: MonitorEditorValidation,
    val fidelity: MonitorEditorFidelity,
    val transferEligibility: MonitorTransferEligibility,
    val help: MonitorEditorHelp,
    val browserFallback: Boolean = true,
) {
    fun writeVerifiedFor(compatibility: KumaCompatibility): Boolean {
        val version = compatibility.version ?: return false
        return compatibility.writesVerified &&
            version >= verifiedMin &&
            version <= verifiedMax &&
            compatibility.supportsMonitorSchema(type)
    }
}

/**
 * Single metadata source for native monitor editing. A type is never promoted to
 * full fidelity until its complete app-owned field set has a representative
 * round-trip fixture. Other known types retain safe common-field editing because
 * their server payload is fetched immediately before save and preserved in place.
 */
object MonitorEditorRegistry {
    private val commonFields = setOf(
        MonitorEditorField.NAME,
        MonitorEditorField.DESCRIPTION,
        MonitorEditorField.INTERVAL,
        MonitorEditorField.RETRY_INTERVAL,
        MonitorEditorField.RESEND_INTERVAL,
        MonitorEditorField.MAX_RETRIES,
        MonitorEditorField.ACTIVE,
        MonitorEditorField.NOTIFICATIONS,
        MonitorEditorField.PARENT,
        MonitorEditorField.TAGS,
    )

    val all: List<MonitorEditorDefinition> = listOf(
        definition("http", "HTTP(s)", MonitorEndpointKind.URL, createSupported = true),
        definition(
            "keyword",
            "HTTP(s) - keyword",
            MonitorEndpointKind.URL,
            createSupported = true,
            codec = MonitorEditorCodec.KEYWORD,
            validation = MonitorEditorValidation.KEYWORD,
            extraFields = setOf(MonitorEditorField.KEYWORD, MonitorEditorField.INVERT_KEYWORD),
        ),
        definition("port", "TCP port", MonitorEndpointKind.HOST_PORT, createSupported = true),
        definition("ping", "Ping", MonitorEndpointKind.HOST, createSupported = true),
        definition(
            "dns",
            "DNS",
            MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            defaultPort = 53,
        ),
        definition(
            "docker",
            "Docker container",
            createSupported = true,
            codec = MonitorEditorCodec.DOCKER,
            validation = MonitorEditorValidation.DOCKER,
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            extraFields = setOf(MonitorEditorField.DOCKER_CONTAINER, MonitorEditorField.DOCKER_HOST),
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition("system-service", "System service"),
        definition("pm2", "PM2 process", verifiedMin = KumaVersion(2, 5, 0)),
        definition(
            "real-browser",
            "Browser engine",
            MonitorEndpointKind.URL,
            createSupported = true,
            codec = MonitorEditorCodec.REAL_BROWSER,
            validation = MonitorEditorValidation.REAL_BROWSER,
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            extraFields = setOf(
                MonitorEditorField.REAL_BROWSER_REMOTE_BROWSER,
                MonitorEditorField.REAL_BROWSER_SCREENSHOT_DELAY,
            ),
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition(
            "group",
            "Group",
            createSupported = true,
            transferEligibility = MonitorTransferEligibility.CREDENTIAL_FREE,
        ),
        definition(
            "push",
            "Push",
            createSupported = true,
            codec = MonitorEditorCodec.PUSH,
            validation = MonitorEditorValidation.PUSH,
            sensitiveFields = setOf(MonitorEditorField.PUSH_TOKEN),
            extraFields = setOf(MonitorEditorField.PUSH_TOKEN),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
        ),
        definition(
            "manual",
            "Manual",
            createSupported = true,
            transferEligibility = MonitorTransferEligibility.CREDENTIAL_FREE,
        ),
        definition(
            "globalping",
            "Globalping",
            createSupported = true,
            defaultPort = 80,
            codec = MonitorEditorCodec.GLOBALPING,
            validation = MonitorEditorValidation.GLOBALPING,
            extraFields = setOf(
                MonitorEditorField.ENDPOINT,
                MonitorEditorField.PORT,
                MonitorEditorField.GLOBALPING_SUBTYPE,
                MonitorEditorField.GLOBALPING_LOCATION,
                MonitorEditorField.GLOBALPING_IP_FAMILY,
                MonitorEditorField.GLOBALPING_PROTOCOL,
                MonitorEditorField.GLOBALPING_PING_COUNT,
                MonitorEditorField.GLOBALPING_RESOLVER,
                MonitorEditorField.GLOBALPING_DNS_RECORD_TYPE,
                MonitorEditorField.GLOBALPING_HTTP_METHOD,
                MonitorEditorField.GLOBALPING_HTTP_ACCEPTED_CODES,
                MonitorEditorField.GLOBALPING_HTTP_RESPONSE_CHECK,
                MonitorEditorField.GLOBALPING_HTTP_IGNORE_TLS,
                MonitorEditorField.GLOBALPING_HTTP_EXPIRY_NOTIFICATION,
                MonitorEditorField.GLOBALPING_HTTP_CACHE_BUST,
                MonitorEditorField.KEYWORD,
                MonitorEditorField.INVERT_KEYWORD,
                MonitorEditorField.JSON_QUERY_EXPRESSION,
                MonitorEditorField.JSON_QUERY_OPERATOR,
                MonitorEditorField.JSON_QUERY_EXPECTED_VALUE,
                MonitorEditorField.WEBSOCKET_HEADERS,
                MonitorEditorField.WEBSOCKET_AUTH_METHOD,
                MonitorEditorField.WEBSOCKET_BASIC_USERNAME,
                MonitorEditorField.WEBSOCKET_BASIC_PASSWORD,
                MonitorEditorField.WEBSOCKET_BEARER_TOKEN,
                MonitorEditorField.WEBSOCKET_OAUTH_AUTH_METHOD,
                MonitorEditorField.WEBSOCKET_OAUTH_TOKEN_URL,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_ID,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_SECRET,
                MonitorEditorField.WEBSOCKET_OAUTH_SCOPES,
                MonitorEditorField.WEBSOCKET_OAUTH_AUDIENCE,
            ),
            sensitiveFields = setOf(
                MonitorEditorField.WEBSOCKET_BASIC_PASSWORD,
                MonitorEditorField.WEBSOCKET_BEARER_TOKEN,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_SECRET,
            ),
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition(
            "grpc-keyword",
            "gRPC(s) - keyword",
            createSupported = true,
            codec = MonitorEditorCodec.GRPC,
            validation = MonitorEditorValidation.GRPC,
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            extraFields = setOf(
                MonitorEditorField.KEYWORD,
                MonitorEditorField.INVERT_KEYWORD,
                MonitorEditorField.GRPC_TARGET,
                MonitorEditorField.GRPC_PROTOBUF,
                MonitorEditorField.GRPC_SERVICE_NAME,
                MonitorEditorField.GRPC_METHOD,
                MonitorEditorField.GRPC_BODY,
                MonitorEditorField.GRPC_ENABLE_TLS,
            ),
            sensitiveFields = setOf(MonitorEditorField.GRPC_BODY),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition(
            "json-query",
            "HTTP(s) - JSON query",
            MonitorEndpointKind.URL,
            createSupported = true,
            codec = MonitorEditorCodec.JSON_QUERY,
            validation = MonitorEditorValidation.JSON_QUERY,
            extraFields = setOf(
                MonitorEditorField.JSON_QUERY_EXPRESSION,
                MonitorEditorField.JSON_QUERY_OPERATOR,
                MonitorEditorField.JSON_QUERY_EXPECTED_VALUE,
            ),
        ),
        definition(
            "kafka-producer",
            "Kafka producer",
            createSupported = true,
            timeoutSeconds = 1,
            codec = MonitorEditorCodec.KAFKA,
            validation = MonitorEditorValidation.KAFKA,
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            extraFields = setOf(
                MonitorEditorField.BROKER_TIMEOUT,
                MonitorEditorField.KAFKA_BROKERS,
                MonitorEditorField.KAFKA_TOPIC,
                MonitorEditorField.KAFKA_MESSAGE,
                MonitorEditorField.KAFKA_SSL,
                MonitorEditorField.KAFKA_AUTO_TOPIC_CREATION,
                MonitorEditorField.KAFKA_SASL_OPTIONS,
            ),
            sensitiveFields = setOf(MonitorEditorField.KAFKA_SASL_OPTIONS),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition(
            "mqtt",
            "MQTT",
            MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            codec = MonitorEditorCodec.MQTT,
            validation = MonitorEditorValidation.MQTT,
            extraFields = setOf(
                MonitorEditorField.MQTT_USERNAME,
                MonitorEditorField.MQTT_PASSWORD,
                MonitorEditorField.MQTT_TOPIC,
                MonitorEditorField.MQTT_WEBSOCKET_PATH,
                MonitorEditorField.MQTT_CHECK_TYPE,
                MonitorEditorField.MQTT_SUCCESS_MESSAGE,
                MonitorEditorField.MQTT_JSON_QUERY_EXPRESSION,
                MonitorEditorField.MQTT_JSON_QUERY_EXPECTED_VALUE,
            ),
            sensitiveFields = setOf(MonitorEditorField.MQTT_PASSWORD),
            conditions = listOf(
                MonitorEditorFieldCondition(
                    MonitorEditorField.MQTT_SUCCESS_MESSAGE,
                    MonitorEditorField.MQTT_CHECK_TYPE,
                    setOf(MqttCheckType.KEYWORD.wireValue),
                ),
                MonitorEditorFieldCondition(
                    MonitorEditorField.MQTT_JSON_QUERY_EXPRESSION,
                    MonitorEditorField.MQTT_CHECK_TYPE,
                    setOf(MqttCheckType.JSON_QUERY.wireValue),
                ),
                MonitorEditorFieldCondition(
                    MonitorEditorField.MQTT_JSON_QUERY_EXPECTED_VALUE,
                    MonitorEditorField.MQTT_CHECK_TYPE,
                    setOf(MqttCheckType.JSON_QUERY.wireValue),
                ),
            ),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
        ),
        definition(
            "ntp",
            "NTP",
            MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            defaultPort = 123,
            intervalSeconds = 300,
            verifiedMin = KumaVersion(2, 5, 0),
            codec = MonitorEditorCodec.NTP,
            validation = MonitorEditorValidation.NTP,
            extraFields = setOf(
                MonitorEditorField.NTP_STRATUM_THRESHOLD,
                MonitorEditorField.NTP_TIME_OFFSET_THRESHOLD,
                MonitorEditorField.NTP_ROOT_DISPERSION_THRESHOLD,
            ),
            transferEligibility = MonitorTransferEligibility.CREDENTIAL_FREE,
        ),
        definition(
            "rabbitmq",
            "RabbitMQ",
            createSupported = true,
            codec = MonitorEditorCodec.RABBITMQ,
            validation = MonitorEditorValidation.RABBITMQ,
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            extraFields = setOf(
                MonitorEditorField.BROKER_TIMEOUT,
                MonitorEditorField.RABBITMQ_NODES,
                MonitorEditorField.RABBITMQ_USERNAME,
                MonitorEditorField.RABBITMQ_PASSWORD,
            ),
            sensitiveFields = setOf(MonitorEditorField.RABBITMQ_PASSWORD),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition("sip-options", "SIP options ping"),
        definition(
            "smtp",
            "SMTP",
            MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            codec = MonitorEditorCodec.SMTP,
            validation = MonitorEditorValidation.SMTP,
            extraFields = setOf(MonitorEditorField.SMTP_SECURITY),
            transferEligibility = MonitorTransferEligibility.CREDENTIAL_FREE,
        ),
        definition(
            "snmp",
            "SNMP",
            endpointKind = MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            defaultPort = 161,
            timeoutSeconds = 5,
            codec = MonitorEditorCodec.SNMP,
            validation = MonitorEditorValidation.SNMP,
            extraFields = setOf(
                MonitorEditorField.SNMP_VERSION,
                MonitorEditorField.SNMP_COMMUNITY,
                MonitorEditorField.SNMP_OID,
                MonitorEditorField.SNMP_TIMEOUT,
                MonitorEditorField.JSON_QUERY_EXPRESSION,
                MonitorEditorField.JSON_QUERY_OPERATOR,
                MonitorEditorField.JSON_QUERY_EXPECTED_VALUE,
            ),
            sensitiveFields = setOf(MonitorEditorField.SNMP_COMMUNITY),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition(
            type = "sftp",
            label = "SFTP",
            endpointKind = MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            defaultPort = 22,
            timeoutSeconds = 10,
            verifiedMin = KumaVersion(2, 5, 4),
            codec = MonitorEditorCodec.SFTP,
            validation = MonitorEditorValidation.SFTP,
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            extraFields = setOf(
                MonitorEditorField.SFTP_AUTH_METHOD,
                MonitorEditorField.SFTP_USERNAME,
                MonitorEditorField.SFTP_PASSWORD,
                MonitorEditorField.SFTP_PRIVATE_KEY,
                MonitorEditorField.SFTP_PASSPHRASE,
                MonitorEditorField.SFTP_PATH,
            ),
            sensitiveFields = setOf(
                MonitorEditorField.SFTP_PASSWORD,
                MonitorEditorField.SFTP_PRIVATE_KEY,
                MonitorEditorField.SFTP_PASSPHRASE,
            ),
            conditions = listOf(
                MonitorEditorFieldCondition(
                    MonitorEditorField.SFTP_PASSWORD,
                    MonitorEditorField.SFTP_AUTH_METHOD,
                    setOf(SftpAuthMethod.PASSWORD.wireValue),
                ),
                MonitorEditorFieldCondition(
                    MonitorEditorField.SFTP_PRIVATE_KEY,
                    MonitorEditorField.SFTP_AUTH_METHOD,
                    setOf(SftpAuthMethod.PRIVATE_KEY.wireValue),
                ),
                MonitorEditorFieldCondition(
                    MonitorEditorField.SFTP_PASSPHRASE,
                    MonitorEditorField.SFTP_AUTH_METHOD,
                    setOf(SftpAuthMethod.PRIVATE_KEY.wireValue),
                ),
            ),
            dependencies = listOf(
                MonitorEditorFieldDependency(
                    MonitorEditorField.SFTP_PASSPHRASE,
                    setOf(MonitorEditorField.SFTP_PRIVATE_KEY),
                ),
            ),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        definition("tailscale-ping", "Tailscale ping"),
        definition(
            "websocket-upgrade",
            "WebSocket upgrade",
            MonitorEndpointKind.URL,
            createSupported = true,
            codec = MonitorEditorCodec.WEBSOCKET,
            validation = MonitorEditorValidation.WEBSOCKET,
            extraFields = setOf(
                MonitorEditorField.WEBSOCKET_SUBPROTOCOLS,
                MonitorEditorField.WEBSOCKET_ACCEPTED_CODES,
                MonitorEditorField.WEBSOCKET_IGNORE_ACCEPT_HEADER,
                MonitorEditorField.WEBSOCKET_HEADERS,
                MonitorEditorField.WEBSOCKET_AUTH_METHOD,
                MonitorEditorField.WEBSOCKET_BASIC_USERNAME,
                MonitorEditorField.WEBSOCKET_BASIC_PASSWORD,
                MonitorEditorField.WEBSOCKET_BEARER_TOKEN,
                MonitorEditorField.WEBSOCKET_OAUTH_AUTH_METHOD,
                MonitorEditorField.WEBSOCKET_OAUTH_TOKEN_URL,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_ID,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_SECRET,
                MonitorEditorField.WEBSOCKET_OAUTH_SCOPES,
                MonitorEditorField.WEBSOCKET_OAUTH_AUDIENCE,
                MonitorEditorField.WEBSOCKET_MTLS_CERTIFICATE,
                MonitorEditorField.WEBSOCKET_MTLS_PRIVATE_KEY,
                MonitorEditorField.WEBSOCKET_MTLS_CA_CERTIFICATE,
            ),
            sensitiveFields = setOf(
                MonitorEditorField.WEBSOCKET_BASIC_PASSWORD,
                MonitorEditorField.WEBSOCKET_BEARER_TOKEN,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_SECRET,
                MonitorEditorField.WEBSOCKET_MTLS_CERTIFICATE,
                MonitorEditorField.WEBSOCKET_MTLS_PRIVATE_KEY,
                MonitorEditorField.WEBSOCKET_MTLS_CA_CERTIFICATE,
            ),
            conditions = listOf(
                MonitorEditorFieldCondition(
                    MonitorEditorField.WEBSOCKET_BASIC_USERNAME,
                    MonitorEditorField.WEBSOCKET_AUTH_METHOD,
                    setOf(WebSocketAuthMethod.BASIC.wireValue.orEmpty()),
                ),
                MonitorEditorFieldCondition(
                    MonitorEditorField.WEBSOCKET_BASIC_PASSWORD,
                    MonitorEditorField.WEBSOCKET_AUTH_METHOD,
                    setOf(WebSocketAuthMethod.BASIC.wireValue.orEmpty()),
                ),
                MonitorEditorFieldCondition(
                    MonitorEditorField.WEBSOCKET_BEARER_TOKEN,
                    MonitorEditorField.WEBSOCKET_AUTH_METHOD,
                    setOf(WebSocketAuthMethod.BEARER.wireValue.orEmpty()),
                ),
            ) + listOf(
                MonitorEditorField.WEBSOCKET_OAUTH_AUTH_METHOD,
                MonitorEditorField.WEBSOCKET_OAUTH_TOKEN_URL,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_ID,
                MonitorEditorField.WEBSOCKET_OAUTH_CLIENT_SECRET,
                MonitorEditorField.WEBSOCKET_OAUTH_SCOPES,
                MonitorEditorField.WEBSOCKET_OAUTH_AUDIENCE,
            ).map { field ->
                MonitorEditorFieldCondition(
                    field,
                    MonitorEditorField.WEBSOCKET_AUTH_METHOD,
                    setOf(WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS.wireValue.orEmpty()),
                )
            } + listOf(
                MonitorEditorField.WEBSOCKET_MTLS_CERTIFICATE,
                MonitorEditorField.WEBSOCKET_MTLS_PRIVATE_KEY,
                MonitorEditorField.WEBSOCKET_MTLS_CA_CERTIFICATE,
            ).map { field ->
                MonitorEditorFieldCondition(
                    field,
                    MonitorEditorField.WEBSOCKET_AUTH_METHOD,
                    setOf(WebSocketAuthMethod.MTLS.wireValue.orEmpty()),
                )
            },
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
        ),
        databaseDefinition("sqlserver", "Microsoft SQL Server"),
        databaseDefinition(
            "mongodb",
            "MongoDB",
            extraFields = setOf(
                MonitorEditorField.DATABASE_JSON_QUERY_EXPRESSION,
                MonitorEditorField.DATABASE_EXPECTED_VALUE,
            ),
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
        ),
        databaseDefinition(
            "mysql",
            "MySQL/MariaDB",
            extraFields = setOf(MonitorEditorField.DATABASE_PASSWORD),
            sensitiveFields = setOf(MonitorEditorField.DATABASE_PASSWORD),
        ),
        databaseDefinition(
            "oracledb",
            "Oracle Database",
            extraFields = setOf(
                MonitorEditorField.DATABASE_USERNAME,
                MonitorEditorField.DATABASE_PASSWORD,
            ),
            sensitiveFields = setOf(MonitorEditorField.DATABASE_PASSWORD),
        ),
        databaseDefinition("postgres", "PostgreSQL", fidelity = MonitorEditorFidelity.FULL_FIDELITY),
        definition(
            type = "radius",
            label = "RADIUS",
            endpointKind = MonitorEndpointKind.HOST_PORT,
            createSupported = true,
            defaultPort = 1812,
            codec = MonitorEditorCodec.RADIUS,
            validation = MonitorEditorValidation.RADIUS,
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
            extraFields = setOf(
                MonitorEditorField.RADIUS_USERNAME,
                MonitorEditorField.RADIUS_PASSWORD,
                MonitorEditorField.RADIUS_SECRET,
                MonitorEditorField.RADIUS_CALLED_STATION_ID,
                MonitorEditorField.RADIUS_CALLING_STATION_ID,
            ),
            sensitiveFields = setOf(
                MonitorEditorField.RADIUS_PASSWORD,
                MonitorEditorField.RADIUS_SECRET,
            ),
            transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
            help = MonitorEditorHelp.FULL_NATIVE,
        ),
        databaseDefinition(
            "redis",
            "Redis",
            hasQuery = false,
            extraFields = setOf(MonitorEditorField.DATABASE_IGNORE_TLS),
            fidelity = MonitorEditorFidelity.FULL_FIDELITY,
        ),
        definition("gamedig", "GameDig"),
        definition("steam", "Steam game server"),
    )

    private val byType = all.associateBy(MonitorEditorDefinition::type)

    init {
        check(all.size == byType.size) { "Monitor editor types must be unique" }
    }

    fun find(type: String): MonitorEditorDefinition? = byType[type]

    fun creatableFor(compatibility: KumaCompatibility): List<MonitorEditorDefinition> =
        all.filter { it.createSupported && it.writeVerifiedFor(compatibility) }

    fun writeVerified(type: String, compatibility: KumaCompatibility): Boolean =
        find(type)?.writeVerifiedFor(compatibility) == true

    private fun databaseDefinition(
        type: String,
        label: String,
        hasQuery: Boolean = true,
        extraFields: Set<MonitorEditorField> = emptySet(),
        sensitiveFields: Set<MonitorEditorField> = emptySet(),
        fidelity: MonitorEditorFidelity = MonitorEditorFidelity.SAFE_COMMON_EDIT,
    ) = definition(
        type = type,
        label = label,
        createSupported = true,
        codec = MonitorEditorCodec.DATABASE,
        validation = MonitorEditorValidation.DATABASE,
        fidelity = fidelity,
        extraFields = setOf(MonitorEditorField.DATABASE_CONNECTION_STRING) +
            setOf(MonitorEditorField.DATABASE_QUERY).takeIf { hasQuery }.orEmpty() +
            extraFields,
        sensitiveFields = setOf(MonitorEditorField.DATABASE_CONNECTION_STRING) + sensitiveFields,
        transferEligibility = MonitorTransferEligibility.REQUIRES_SECRET_REENTRY,
        help = MonitorEditorHelp.FULL_NATIVE,
    )

    private fun definition(
        type: String,
        label: String,
        endpointKind: MonitorEndpointKind = MonitorEndpointKind.NONE,
        createSupported: Boolean = false,
        defaultPort: Int? = null,
        timeoutSeconds: Int = if (type == "ping") 10 else 48,
        intervalSeconds: Int = 60,
        verifiedMin: KumaVersion = KumaCapabilities.VERIFIED_MIN,
        codec: MonitorEditorCodec = MonitorEditorCodec.COMMON,
        validation: MonitorEditorValidation = if (endpointKind == MonitorEndpointKind.NONE) {
            MonitorEditorValidation.COMMON
        } else {
            MonitorEditorValidation.ENDPOINT
        },
        fidelity: MonitorEditorFidelity = MonitorEditorFidelity.SAFE_COMMON_EDIT,
        extraFields: Set<MonitorEditorField> = emptySet(),
        sensitiveFields: Set<MonitorEditorField> = emptySet(),
        conditions: List<MonitorEditorFieldCondition> = emptyList(),
        dependencies: List<MonitorEditorFieldDependency> = emptyList(),
        transferEligibility: MonitorTransferEligibility = MonitorTransferEligibility.NOT_ELIGIBLE,
        help: MonitorEditorHelp = MonitorEditorHelp.COMMON_FIELDS_ONLY,
    ): MonitorEditorDefinition {
        val endpointFields = when (endpointKind) {
            MonitorEndpointKind.NONE -> emptySet()
            MonitorEndpointKind.URL, MonitorEndpointKind.HOST -> setOf(MonitorEditorField.ENDPOINT)
            MonitorEndpointKind.HOST_PORT -> setOf(MonitorEditorField.ENDPOINT, MonitorEditorField.PORT)
        }
        return MonitorEditorDefinition(
            type = type,
            label = label,
            verifiedMin = verifiedMin,
            verifiedMax = KumaCapabilities.VERIFIED_MAX,
            endpointKind = endpointKind,
            createSupported = createSupported,
            defaults = MonitorEditorDefaults(
                port = defaultPort,
                intervalSeconds = intervalSeconds,
                timeoutSeconds = timeoutSeconds,
            ),
            editableFields = commonFields + endpointFields + extraFields,
            sensitiveFields = sensitiveFields,
            conditions = conditions,
            dependencies = dependencies,
            codec = codec,
            validation = validation,
            fidelity = fidelity,
            transferEligibility = transferEligibility,
            help = help,
        )
    }
}

object MonitorRoundTripGuard {
    private val commonMutableWireFields = setOf(
        "name",
        "description",
        "interval",
        "retryInterval",
        "resendInterval",
        "maxretries",
        "active",
        "notificationIDList",
        "parent",
    )

    fun preservesUnrelatedFields(
        before: kotlinx.serialization.json.JsonObject,
        after: kotlinx.serialization.json.JsonObject,
        type: String,
    ): Boolean {
        val definition = MonitorEditorRegistry.find(type) ?: return false
        if (before["type"] != after["type"]) return false
        val mutable = buildSet {
            addAll(commonMutableWireFields)
            when (definition.endpointKind) {
                MonitorEndpointKind.URL -> add("url")
                MonitorEndpointKind.HOST -> add("hostname")
                MonitorEndpointKind.HOST_PORT -> {
                    add("hostname")
                    add("port")
                }
                MonitorEndpointKind.NONE -> Unit
            }
            if (definition.codec == MonitorEditorCodec.SFTP) {
                addAll(
                    setOf(
                        "sshAuthMethod",
                        "sshUsername",
                        "sshPassword",
                        "sshPrivateKey",
                        "sshPassphrase",
                        "sftpPath",
                    ),
                )
            }
            if (definition.codec == MonitorEditorCodec.KEYWORD) {
                add("keyword")
                add("invertKeyword")
            }
            if (definition.codec == MonitorEditorCodec.JSON_QUERY) {
                add("jsonPath")
                add("jsonPathOperator")
                add("expectedValue")
            }
            if (definition.codec == MonitorEditorCodec.WEBSOCKET) {
                add("wsSubprotocol")
                add("accepted_statuscodes")
                add("wsIgnoreSecWebsocketAcceptHeader")
                add("headers")
                add("authMethod")
                add("basic_auth_user")
                add("basic_auth_pass")
                add("bearer_token")
                add("oauth_auth_method")
                add("oauth_token_url")
                add("oauth_client_id")
                add("oauth_client_secret")
                add("oauth_scopes")
                add("oauth_audience")
                add("tlsCert")
                add("tlsKey")
                add("tlsCa")
            }
            if (definition.codec == MonitorEditorCodec.MQTT) {
                add("mqttUsername")
                add("mqttPassword")
                add("mqttTopic")
                add("mqttWebsocketPath")
                add("mqttCheckType")
                add("mqttSuccessMessage")
                add("jsonPath")
                add("expectedValue")
            }
            if (definition.codec == MonitorEditorCodec.SMTP) {
                add("smtpSecurity")
            }
            if (definition.codec == MonitorEditorCodec.NTP) {
                add("ntpStratumThreshold")
                add("ntpTimeOffsetThreshold")
                add("ntpRootDispersionThreshold")
            }
            if (definition.codec == MonitorEditorCodec.GLOBALPING) {
                add("subtype")
                add("hostname")
                add("url")
                add("port")
                add("location")
                add("ipFamily")
                add("protocol")
                add("ping_count")
                add("dns_resolve_type")
                add("dns_resolve_server")
                add("method")
                add("accepted_statuscodes")
                add("ignoreTls")
                add("expiryNotification")
                add("cacheBust")
                add("keyword")
                add("invertKeyword")
                add("jsonPath")
                add("jsonPathOperator")
                add("expectedValue")
                add("headers")
                add("authMethod")
                add("basic_auth_user")
                add("basic_auth_pass")
                add("bearer_token")
                add("oauth_auth_method")
                add("oauth_token_url")
                add("oauth_client_id")
                add("oauth_client_secret")
                add("oauth_scopes")
                add("oauth_audience")
                add("tlsCert")
                add("tlsKey")
                add("tlsCa")
            }
            if (definition.codec == MonitorEditorCodec.DATABASE) {
                add("databaseConnectionString")
                if (type != "redis") add("databaseQuery")
                if (type == "mysql") add("radiusPassword")
                if (type == "oracledb") {
                    add("basic_auth_user")
                    add("basic_auth_pass")
                }
                if (type == "mongodb") {
                    add("jsonPath")
                    add("expectedValue")
                }
                if (type == "redis") add("ignoreTls")
            }
            if (definition.codec == MonitorEditorCodec.GRPC) {
                addAll(
                    setOf(
                        "grpcUrl",
                        "grpcProtobuf",
                        "grpcServiceName",
                        "grpcMethod",
                        "grpcBody",
                        "grpcEnableTls",
                        "keyword",
                        "invertKeyword",
                    ),
                )
            }
            if (definition.codec == MonitorEditorCodec.SNMP) {
                addAll(
                    setOf(
                        "snmpVersion",
                        "radiusPassword",
                        "snmpOid",
                        "timeout",
                        "jsonPath",
                        "jsonPathOperator",
                        "expectedValue",
                    ),
                )
            }
            if (definition.codec == MonitorEditorCodec.RABBITMQ) {
                addAll(setOf("rabbitmqNodes", "rabbitmqUsername", "rabbitmqPassword", "timeout"))
            }
            if (definition.codec == MonitorEditorCodec.KAFKA) {
                addAll(
                    setOf(
                        "kafkaProducerBrokers",
                        "kafkaProducerTopic",
                        "kafkaProducerMessage",
                        "kafkaProducerSsl",
                        "kafkaProducerAllowAutoTopicCreation",
                        "kafkaProducerSaslOptions",
                        "timeout",
                    ),
                )
            }
            if (definition.codec == MonitorEditorCodec.DOCKER) {
                addAll(setOf("docker_container", "docker_host"))
            }
            if (definition.codec == MonitorEditorCodec.REAL_BROWSER) {
                addAll(setOf("remote_browser", "screenshot_delay"))
            }
            if (definition.codec == MonitorEditorCodec.RADIUS) {
                addAll(
                    setOf(
                        "radiusUsername",
                        "radiusPassword",
                        "radiusSecret",
                        "radiusCalledStationId",
                        "radiusCallingStationId",
                    ),
                )
            }
        }
        return (before.keys + after.keys).all { key -> key in mutable || before[key] == after[key] }
    }
}
