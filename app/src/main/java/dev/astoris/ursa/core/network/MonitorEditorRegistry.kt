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
    SFTP,
}

enum class MonitorEditorValidation {
    COMMON,
    ENDPOINT,
    PUSH,
    KEYWORD,
    JSON_QUERY,
    WEBSOCKET,
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
        definition("docker", "Docker container"),
        definition("system-service", "System service"),
        definition("pm2", "PM2 process", verifiedMin = KumaVersion(2, 5, 0)),
        definition("real-browser", "Browser engine", MonitorEndpointKind.URL),
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
        definition("globalping", "Globalping"),
        definition("grpc-keyword", "gRPC(s) - keyword"),
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
        definition("kafka-producer", "Kafka producer"),
        definition("mqtt", "MQTT"),
        definition("ntp", "NTP", verifiedMin = KumaVersion(2, 5, 0)),
        definition("rabbitmq", "RabbitMQ"),
        definition("sip-options", "SIP options ping"),
        definition("smtp", "SMTP"),
        definition("snmp", "SNMP"),
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
            ),
        ),
        definition("sqlserver", "Microsoft SQL Server"),
        definition("mongodb", "MongoDB"),
        definition("mysql", "MySQL/MariaDB"),
        definition("oracledb", "Oracle Database"),
        definition("postgres", "PostgreSQL"),
        definition("radius", "RADIUS"),
        definition("redis", "Redis"),
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

    private fun definition(
        type: String,
        label: String,
        endpointKind: MonitorEndpointKind = MonitorEndpointKind.NONE,
        createSupported: Boolean = false,
        defaultPort: Int? = null,
        timeoutSeconds: Int = if (type == "ping") 10 else 48,
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
            defaults = MonitorEditorDefaults(port = defaultPort, timeoutSeconds = timeoutSeconds),
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
            }
        }
        return (before.keys + after.keys).all { key -> key in mutable || before[key] == after[key] }
    }
}
