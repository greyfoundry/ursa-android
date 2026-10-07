package dev.astoris.ursa.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
        assertTrue(MonitorTypeCatalog.creatable.any { it.key == "globalping" })
        assertTrue(MonitorTypeCatalog.creatable.any { it.key == "postgres" })
        assertTrue(MonitorTypeCatalog.creatable.any { it.key == "redis" })
        assertTrue(MonitorTypeCatalog.creatable.any { it.key == "grpc-keyword" })
        assertTrue(MonitorTypeCatalog.creatable.any { it.key == "snmp" })
    }

    @Test
    fun grpcPayloadMapsTheKuma255Contract() {
        val draft = MonitorDraft.create("grpc-keyword").copy(
            name = "Health",
            grpcTarget = "grpc.example.com:443",
            grpcProtobuf = "syntax = \"proto3\"; service Health { rpc Check (Request) returns (Response); }",
            grpcServiceName = "Health",
            grpcMethod = "check",
            grpcBody = "{\"service\":\"api\"}",
            grpcEnableTls = true,
            keyword = "SERVING",
            invertKeyword = false,
        )

        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)
        assertEquals("grpc.example.com:443", payload["grpcUrl"]!!.jsonPrimitive.content)
        assertEquals(draft.grpcProtobuf, payload["grpcProtobuf"]!!.jsonPrimitive.content)
        assertEquals("Health", payload["grpcServiceName"]!!.jsonPrimitive.content)
        assertEquals("check", payload["grpcMethod"]!!.jsonPrimitive.content)
        assertEquals("{\"service\":\"api\"}", payload["grpcBody"]!!.jsonPrimitive.content)
        assertTrue(payload["grpcEnableTls"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("SERVING", payload["keyword"]!!.jsonPrimitive.content)
        assertFalse(payload["invertKeyword"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun grpcEditsMaskAndRetainTheSavedBodyAndLegacyMetadata() {
        val raw = Json.parseToJsonElement(
            """{
                "id":62,"type":"grpc-keyword","name":"Health","grpcUrl":"grpc:50051",
                "grpcProtobuf":"syntax = \"proto3\";","grpcServiceName":"Health","grpcMethod":"check",
                "grpcBody":"{\"service\":\"api\"}","grpcMetadata":"{\"authorization\":\"secret\"}",
                "grpcEnableTls":false,"keyword":"SERVING","invertKeyword":false,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"futureGrpc":{"mode":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.grpcHasSavedBody)
        assertTrue(loaded.grpcBody.isEmpty())
        assertNull(MonitorDraftCodec.validate(loaded))

        val retained = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(keyword = "READY"))!!
        assertEquals("{\"service\":\"api\"}", retained["grpcBody"]!!.jsonPrimitive.content)
        assertEquals(raw["grpcMetadata"], retained["grpcMetadata"])
        assertEquals(raw["futureGrpc"], retained["futureGrpc"])

        val replaced = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(grpcBody = "{\"service\":\"worker\"}"),
        )!!
        assertEquals("{\"service\":\"worker\"}", replaced["grpcBody"]!!.jsonPrimitive.content)
    }

    @Test
    fun grpcValidationRequiresTheExecutableContract() {
        val valid = MonitorDraft.create("grpc-keyword").copy(
            name = "Health",
            grpcTarget = "grpc:50051",
            grpcProtobuf = "syntax = \"proto3\";",
            grpcServiceName = "Health",
            grpcMethod = "check",
            keyword = "SERVING",
        )
        assertNull(MonitorDraftCodec.validate(valid))
        assertEquals(MonitorDraftError.GRPC_TARGET_REQUIRED, MonitorDraftCodec.validate(valid.copy(grpcTarget = "")))
        assertEquals(MonitorDraftError.GRPC_PROTOBUF_REQUIRED, MonitorDraftCodec.validate(valid.copy(grpcProtobuf = "")))
        assertEquals(MonitorDraftError.GRPC_SERVICE_REQUIRED, MonitorDraftCodec.validate(valid.copy(grpcServiceName = "")))
        assertEquals(MonitorDraftError.GRPC_METHOD_REQUIRED, MonitorDraftCodec.validate(valid.copy(grpcMethod = "")))
        assertEquals(MonitorDraftError.KEYWORD_REQUIRED, MonitorDraftCodec.validate(valid.copy(keyword = "")))
        assertEquals(MonitorDraftError.GRPC_BODY_INVALID, MonitorDraftCodec.validate(valid.copy(grpcBody = "[]")))
    }

    @Test
    fun snmpPayloadMapsTheKuma255V2cContract() {
        val draft = MonitorDraft.create("snmp").copy(
            name = "Router description",
            endpoint = "snmp.example.com",
            port = 161,
            snmpVersion = SnmpVersion.V2C,
            snmpCommunity = "private-community",
            snmpOid = "1.3.6.1.2.1.1.1.0",
            snmpTimeoutSeconds = "5",
            jsonQueryExpression = "$",
            jsonQueryOperator = "contains",
            jsonQueryExpectedValue = "Linux",
        )

        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)
        assertEquals("snmp.example.com", payload["hostname"]!!.jsonPrimitive.content)
        assertEquals("161", payload["port"]!!.jsonPrimitive.content)
        assertEquals("2c", payload["snmpVersion"]!!.jsonPrimitive.content)
        assertEquals("private-community", payload["radiusPassword"]!!.jsonPrimitive.content)
        assertEquals("1.3.6.1.2.1.1.1.0", payload["snmpOid"]!!.jsonPrimitive.content)
        assertEquals(5.0, payload["timeout"]!!.jsonPrimitive.content.toDouble(), 0.0)
        assertEquals("$", payload["jsonPath"]!!.jsonPrimitive.content)
        assertEquals("contains", payload["jsonPathOperator"]!!.jsonPrimitive.content)
        assertEquals("Linux", payload["expectedValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun snmpEditsMaskAndRetainCommunityAndFutureFields() {
        val raw = Json.parseToJsonElement(
            """{
                "id":63,"type":"snmp","name":"Router","hostname":"router","port":161,
                "snmpVersion":"2c","radiusPassword":"private-community","snmpOid":"1.3.6.1.2.1.1.1.0",
                "timeout":5,"jsonPath":"$","jsonPathOperator":"contains","expectedValue":"Linux",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"futureSnmp":{"mode":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.snmpFieldsEditable)
        assertTrue(loaded.snmpHasSavedCommunity)
        assertTrue(loaded.snmpCommunity.isEmpty())
        assertNull(MonitorDraftCodec.validate(loaded))

        val retained = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(jsonQueryExpectedValue = "Router"))!!
        assertEquals("private-community", retained["radiusPassword"]!!.jsonPrimitive.content)
        assertEquals(raw["futureSnmp"], retained["futureSnmp"])

        val replaced = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(snmpCommunity = "replacement"),
        )!!
        assertEquals("replacement", replaced["radiusPassword"]!!.jsonPrimitive.content)
    }

    @Test
    fun snmpV3AndUnknownVersionsStayOpaque() {
        listOf("3", "future").forEach { version ->
            val raw = Json.parseToJsonElement(
                """{
                    "id":64,"type":"snmp","name":"Router","hostname":"router","port":161,
                    "snmpVersion":"$version","snmp_v3_username":"operator","snmpOid":"1.3.6.1.2.1.1.1.0",
                    "timeout":5,"jsonPath":"$","jsonPathOperator":"contains","expectedValue":"Linux",
                    "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                    "notificationIDList":{}
                }""",
            ).jsonObject
            val loaded = MonitorDraftCodec.from(raw)!!

            assertFalse(loaded.snmpFieldsEditable)
            assertNull(MonitorDraftCodec.validate(loaded))
            val retained = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(name = "Renamed"))!!
            assertEquals(raw["snmpVersion"], retained["snmpVersion"])
            assertEquals(raw["snmp_v3_username"], retained["snmp_v3_username"])
            assertEquals(raw["snmpOid"], retained["snmpOid"])
        }
    }

    @Test
    fun snmpValidationRequiresItsReachableQueryContract() {
        val valid = MonitorDraft.create("snmp").copy(
            name = "Router",
            endpoint = "router",
            snmpCommunity = "public",
            snmpOid = "1.3.6.1.2.1.1.1.0",
            jsonQueryExpectedValue = "Linux",
        )

        assertNull(MonitorDraftCodec.validate(valid))
        assertEquals(MonitorDraftError.SNMP_HOST_INVALID, MonitorDraftCodec.validate(valid.copy(endpoint = "bad host")))
        assertEquals(MonitorDraftError.SNMP_COMMUNITY_REQUIRED, MonitorDraftCodec.validate(valid.copy(snmpCommunity = "")))
        assertEquals(MonitorDraftError.SNMP_OID_INVALID, MonitorDraftCodec.validate(valid.copy(snmpOid = "1.03")))
        assertEquals(MonitorDraftError.SNMP_TIMEOUT_INVALID, MonitorDraftCodec.validate(valid.copy(snmpTimeoutSeconds = "49")))
        assertEquals(
            MonitorDraftError.JSON_QUERY_EXPECTED_VALUE_REQUIRED,
            MonitorDraftCodec.validate(valid.copy(jsonQueryExpectedValue = "")),
        )
    }

    @Test
    fun rabbitmqPayloadMapsNodesCredentialsAndTimeout() {
        val draft = MonitorDraft.create("rabbitmq").copy(
            name = "RabbitMQ",
            rabbitmqNodes = "https://rabbit-a.example:15672\nhttps://rabbit-b.example:15672",
            rabbitmqUsername = "monitor",
            rabbitmqPassword = "secret",
            brokerTimeoutSeconds = "4.5",
        )

        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)
        assertEquals(
            listOf("https://rabbit-a.example:15672", "https://rabbit-b.example:15672"),
            payload["rabbitmqNodes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("monitor", payload["rabbitmqUsername"]!!.jsonPrimitive.content)
        assertEquals("secret", payload["rabbitmqPassword"]!!.jsonPrimitive.content)
        assertEquals(4.5, payload["timeout"]!!.jsonPrimitive.content.toDouble(), 0.0)
    }

    @Test
    fun rabbitmqEditMasksAndRetainsPasswordAndFutureFields() {
        val raw = Json.parseToJsonElement(
            """{
                "id":65,"type":"rabbitmq","name":"RabbitMQ","rabbitmqNodes":["http://rabbit:15672"],
                "rabbitmqUsername":"monitor","rabbitmqPassword":"secret","timeout":5,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"futureRabbit":{"mode":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.rabbitmqHasSavedPassword)
        assertTrue(loaded.rabbitmqPassword.isEmpty())
        assertNull(MonitorDraftCodec.validate(loaded))
        val retained = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(brokerTimeoutSeconds = "4"))!!
        assertEquals("secret", retained["rabbitmqPassword"]!!.jsonPrimitive.content)
        assertEquals(raw["futureRabbit"], retained["futureRabbit"])
    }

    @Test
    fun kafkaPayloadMapsAnonymousAndAuthenticatedContracts() {
        val anonymous = MonitorDraft.create("kafka-producer").copy(
            name = "Kafka",
            kafkaBrokers = "kafka-a:9092\nkafka-b:9092",
            kafkaTopic = "health",
            kafkaMessage = "probe",
            kafkaAllowAutoTopicCreation = true,
        )
        assertNull(MonitorDraftCodec.validate(anonymous))
        val anonymousPayload = MonitorDraftCodec.newPayload(anonymous)
        assertEquals(
            listOf("kafka-a:9092", "kafka-b:9092"),
            anonymousPayload["kafkaProducerBrokers"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("None", anonymousPayload["kafkaProducerSaslOptions"]!!.jsonObject["mechanism"]!!.jsonPrimitive.content)
        assertEquals(1.0, anonymousPayload["timeout"]!!.jsonPrimitive.content.toDouble(), 0.0)

        val authenticated = anonymous.copy(
            kafkaSaslMechanism = KafkaSaslMechanism.SCRAM_SHA_256,
            kafkaUsername = "monitor",
            kafkaPassword = "secret",
        )
        assertNull(MonitorDraftCodec.validate(authenticated))
        val sasl = MonitorDraftCodec.newPayload(authenticated)["kafkaProducerSaslOptions"]!!.jsonObject
        assertEquals("scram-sha-256", sasl["mechanism"]!!.jsonPrimitive.content)
        assertEquals("monitor", sasl["username"]!!.jsonPrimitive.content)
        assertEquals("secret", sasl["password"]!!.jsonPrimitive.content)
    }

    @Test
    fun kafkaEditMasksAndRetainsKnownSecretsAndUnknownSaslShapes() {
        val raw = Json.parseToJsonElement(
            """{
                "id":66,"type":"kafka-producer","name":"Kafka","kafkaProducerBrokers":["kafka:9092"],
                "kafkaProducerTopic":"health","kafkaProducerMessage":"probe","kafkaProducerSsl":false,
                "kafkaProducerAllowAutoTopicCreation":false,"timeout":1,
                "kafkaProducerSaslOptions":{"mechanism":"plain","username":"monitor","password":"secret"},
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"futureKafka":{"mode":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!
        assertTrue(loaded.kafkaHasSavedPassword)
        assertTrue(loaded.kafkaPassword.isEmpty())
        assertNull(MonitorDraftCodec.validate(loaded))
        val retained = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(kafkaMessage = "edited"))!!
        val retainedSasl = retained["kafkaProducerSaslOptions"]!!.jsonObject
        assertEquals("secret", retainedSasl["password"]!!.jsonPrimitive.content)
        assertEquals(raw["futureKafka"], retained["futureKafka"])

        val unknownRaw = JsonObject(
            raw.toMutableMap().apply {
                this["kafkaProducerSaslOptions"] = Json.parseToJsonElement(
                    """{"mechanism":"future","token":"opaque"}""",
                )
            },
        )
        val unknown = MonitorDraftCodec.from(unknownRaw)!!
        assertFalse(unknown.kafkaSaslEditable)
        assertNull(MonitorDraftCodec.validate(unknown))
        val opaque = MonitorDraftCodec.safeExistingPayload(unknownRaw, unknown.copy(kafkaMessage = "edited"))!!
        assertEquals(unknownRaw["kafkaProducerSaslOptions"], opaque["kafkaProducerSaslOptions"])
    }

    @Test
    fun kafkaAwsSecretsStayMaskedAndOptionalSessionTokenCanBeRemoved() {
        val raw = Json.parseToJsonElement(
            """{
                "id":67,"type":"kafka-producer","name":"Kafka AWS","kafkaProducerBrokers":["kafka:9092"],
                "kafkaProducerTopic":"health","kafkaProducerMessage":"probe","kafkaProducerSsl":true,
                "kafkaProducerAllowAutoTopicCreation":false,"timeout":1,
                "kafkaProducerSaslOptions":{"mechanism":"aws","authorizationIdentity":"role",
                "accessKeyId":"AKIAEXAMPLE","secretAccessKey":"secret","sessionToken":"session"},
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.kafkaHasSavedSecretAccessKey)
        assertTrue(loaded.kafkaHasSavedSessionToken)
        assertTrue(loaded.kafkaSecretAccessKey.isEmpty())
        assertTrue(loaded.kafkaSessionToken.isEmpty())
        assertNull(MonitorDraftCodec.validate(loaded))

        val retainedPayload = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(kafkaTopic = "edited"))!!
        val retained = retainedPayload["kafkaProducerSaslOptions"]!!.jsonObject
        assertEquals("secret", retained["secretAccessKey"]!!.jsonPrimitive.content)
        assertEquals("session", retained["sessionToken"]!!.jsonPrimitive.content)

        val cleared = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(kafkaClearSavedSessionToken = true),
        )!!["kafkaProducerSaslOptions"]!!.jsonObject
        assertFalse("sessionToken" in cleared)
    }

    @Test
    fun dockerPayloadRoundTripsContainerHostAndFutureFields() {
        val draft = MonitorDraft.create("docker").copy(
            name = "Worker",
            dockerContainer = "ursa-worker",
            dockerHostId = 7,
        )

        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)
        assertEquals("ursa-worker", payload["docker_container"]!!.jsonPrimitive.content)
        assertEquals(7, payload["docker_host"]!!.jsonPrimitive.content.toInt())

        val raw = Json.parseToJsonElement(
            """{
                "id":68,"type":"docker","name":"Worker","docker_container":"ursa-worker","docker_host":7,
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"futureDocker":{"mode":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!
        assertEquals("ursa-worker", loaded.dockerContainer)
        assertEquals(7, loaded.dockerHostId)
        val edited = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(dockerContainer = "ursa-worker-2"))!!
        assertEquals("ursa-worker-2", edited["docker_container"]!!.jsonPrimitive.content)
        assertEquals(7, edited["docker_host"]!!.jsonPrimitive.content.toInt())
        assertEquals(raw["futureDocker"], edited["futureDocker"])
    }

    @Test
    fun dockerValidationRequiresContainerAndConfiguredHost() {
        val valid = MonitorDraft.create("docker").copy(
            name = "Worker",
            dockerContainer = "ursa-worker",
            dockerHostId = 7,
        )

        assertNull(MonitorDraftCodec.validate(valid))
        assertEquals(
            MonitorDraftError.DOCKER_CONTAINER_REQUIRED,
            MonitorDraftCodec.validate(valid.copy(dockerContainer = "")),
        )
        assertEquals(
            MonitorDraftError.DOCKER_HOST_REQUIRED,
            MonitorDraftCodec.validate(valid.copy(dockerHostId = null)),
        )
    }

    @Test
    fun brokerValidationRejectsUnusableEndpointsAndMissingSecrets() {
        val rabbit = MonitorDraft.create("rabbitmq").copy(
            name = "Rabbit",
            rabbitmqNodes = "rabbit:15672",
            rabbitmqUsername = "monitor",
            rabbitmqPassword = "secret",
        )
        assertEquals(MonitorDraftError.RABBITMQ_NODE_INVALID, MonitorDraftCodec.validate(rabbit))
        assertEquals(
            MonitorDraftError.RABBITMQ_PASSWORD_REQUIRED,
            MonitorDraftCodec.validate(rabbit.copy(rabbitmqNodes = "http://rabbit:15672", rabbitmqPassword = "")),
        )

        val kafka = MonitorDraft.create("kafka-producer").copy(
            name = "Kafka",
            kafkaBrokers = "kafka",
            kafkaTopic = "health",
            kafkaMessage = "probe",
        )
        assertEquals(MonitorDraftError.KAFKA_BROKER_INVALID, MonitorDraftCodec.validate(kafka))
        assertEquals(
            MonitorDraftError.KAFKA_SASL_PASSWORD_REQUIRED,
            MonitorDraftCodec.validate(
                kafka.copy(
                    kafkaBrokers = "kafka:9092",
                    kafkaSaslMechanism = KafkaSaslMechanism.PLAIN,
                    kafkaUsername = "monitor",
                ),
            ),
        )
        assertEquals(
            MonitorDraftError.BROKER_TIMEOUT_INVALID,
            MonitorDraftCodec.validate(kafka.copy(kafkaBrokers = "kafka:9092", brokerTimeoutSeconds = "49")),
        )
    }

    @Test
    fun newDatabasePayloadsMapTheKuma255Contracts() {
        val sqlTypes = listOf("postgres", "sqlserver", "mysql")
        sqlTypes.forEach { type ->
            val draft = MonitorDraft.create(type).copy(
                name = type,
                databaseConnectionString = "$type://user:secret@database/service",
                databaseQuery = "SELECT 1",
                databasePassword = if (type == "mysql") "override" else "",
            )

            assertNull(MonitorDraftCodec.validate(draft))
            val payload = MonitorDraftCodec.newPayload(draft)
            assertEquals(type, payload["type"]!!.jsonPrimitive.content)
            assertEquals("$type://user:secret@database/service", payload["databaseConnectionString"]!!.jsonPrimitive.content)
            assertEquals("SELECT 1", payload["databaseQuery"]!!.jsonPrimitive.content)
            if (type == "mysql") assertEquals("override", payload["radiusPassword"]!!.jsonPrimitive.content)
        }

        val mongodb = MonitorDraft.create("mongodb").copy(
            name = "MongoDB",
            databaseConnectionString = "mongodb://user:secret@database/service",
            databaseQuery = "{\"ping\":1}",
            databaseJsonQueryExpression = "ok",
            databaseExpectedValue = "1",
        )
        assertNull(MonitorDraftCodec.validate(mongodb))
        val mongoPayload = MonitorDraftCodec.newPayload(mongodb)
        assertEquals("{\"ping\":1}", mongoPayload["databaseQuery"]!!.jsonPrimitive.content)
        assertEquals("ok", mongoPayload["jsonPath"]!!.jsonPrimitive.content)
        assertEquals("1", mongoPayload["expectedValue"]!!.jsonPrimitive.content)

        val redis = MonitorDraft.create("redis").copy(
            name = "Redis",
            databaseConnectionString = "rediss://user:secret@database:6379",
            databaseIgnoreTls = true,
        )
        assertNull(MonitorDraftCodec.validate(redis))
        val redisPayload = MonitorDraftCodec.newPayload(redis)
        assertEquals("rediss://user:secret@database:6379", redisPayload["databaseConnectionString"]!!.jsonPrimitive.content)
        assertTrue(redisPayload["ignoreTls"]!!.jsonPrimitive.content.toBoolean())
        assertNull(redisPayload["databaseQuery"])
    }

    @Test
    fun databaseEditsMaskAndRetainSavedSecretsAndUnknownFields() {
        val raw = Json.parseToJsonElement(
            """{
                "id":61,"type":"mysql","name":"Database","databaseConnectionString":"mysql://user:secret@db/app",
                "databaseQuery":"SELECT 1","radiusPassword":"saved-override",
                "interval":60,"retryInterval":60,"resendInterval":0,"maxretries":0,"active":true,
                "notificationIDList":{},"conditions":[{"type":"expression","variable":"result","operator":"equals","value":"1","andOr":"and"}],
                "futureDatabase":{"pool":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.databaseHasSavedConnectionString)
        assertTrue(loaded.databaseConnectionString.isEmpty())
        assertTrue(loaded.databaseHasSavedPassword)
        assertTrue(loaded.databasePassword.isEmpty())
        assertNull(MonitorDraftCodec.validate(loaded))

        val retained = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(databaseQuery = "SELECT 2"))!!
        assertEquals("mysql://user:secret@db/app", retained["databaseConnectionString"]!!.jsonPrimitive.content)
        assertEquals("saved-override", retained["radiusPassword"]!!.jsonPrimitive.content)
        assertEquals("SELECT 2", retained["databaseQuery"]!!.jsonPrimitive.content)
        assertEquals(raw["conditions"], retained["conditions"])
        assertEquals(raw["futureDatabase"], retained["futureDatabase"])

        val replaced = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(databaseConnectionString = "mysql://user:new@db/app", databasePassword = "replacement"),
        )!!
        assertEquals("mysql://user:new@db/app", replaced["databaseConnectionString"]!!.jsonPrimitive.content)
        assertEquals("replacement", replaced["radiusPassword"]!!.jsonPrimitive.content)
    }

    @Test
    fun databaseValidationRequiresAConnectionAndValidMongoCommand() {
        val postgres = MonitorDraft.create("postgres").copy(name = "PostgreSQL")
        assertEquals(
            MonitorDraftError.DATABASE_CONNECTION_STRING_REQUIRED,
            MonitorDraftCodec.validate(postgres),
        )
        val mongodb = MonitorDraft.create("mongodb").copy(
            name = "MongoDB",
            databaseConnectionString = "mongodb://database/app",
            databaseQuery = "{not-json}",
        )
        assertEquals(
            MonitorDraftError.DATABASE_MONGODB_COMMAND_INVALID,
            MonitorDraftCodec.validate(mongodb),
        )
        assertEquals(
            MonitorDraftError.DATABASE_MONGODB_COMMAND_INVALID,
            MonitorDraftCodec.validate(mongodb.copy(databaseQuery = "[1, 2]")),
        )
        assertNull(MonitorDraftCodec.validate(mongodb.copy(databaseQuery = "")))
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
    fun newGlobalpingPingPayloadMatchesKumaDefaultsAndWireFields() {
        val draft = MonitorDraft.create("globalping").copy(
            name = "Global edge",
            globalpingTarget = "example.com",
        )

        assertEquals(GlobalpingSubtype.PING, draft.globalpingSubtype)
        assertEquals("world", draft.globalpingLocation)
        assertEquals(GlobalpingIpFamily.AUTO, draft.globalpingIpFamily)
        assertEquals(GlobalpingPingProtocol.ICMP, draft.globalpingPingProtocol)
        assertEquals(80, draft.port)
        assertEquals(3, draft.globalpingPingCount)
        assertNull(MonitorDraftCodec.validate(draft))
        val payload = MonitorDraftCodec.newPayload(draft)

        assertEquals("globalping", payload["type"]!!.jsonPrimitive.content)
        assertEquals("ping", payload["subtype"]!!.jsonPrimitive.content)
        assertEquals("example.com", payload["hostname"]!!.jsonPrimitive.content)
        assertEquals("world", payload["location"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, payload["ipFamily"])
        assertEquals("ICMP", payload["protocol"]!!.jsonPrimitive.content)
        assertEquals(80, payload["port"]!!.jsonPrimitive.content.toInt())
        assertEquals(3, payload["ping_count"]!!.jsonPrimitive.content.toInt())
        assertEquals(48, payload["timeout"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun globalpingPingValidationMatchesKumaBounds() {
        val draft = MonitorDraft.create("globalping").copy(
            name = "Global edge",
            globalpingTarget = "example.com",
        )

        assertEquals(
            MonitorDraftError.GLOBALPING_HOST_INVALID,
            MonitorDraftCodec.validate(draft.copy(globalpingTarget = "https://example.com")),
        )
        assertEquals(
            MonitorDraftError.GLOBALPING_LOCATION_REQUIRED,
            MonitorDraftCodec.validate(draft.copy(globalpingLocation = " ")),
        )
        assertEquals(
            MonitorDraftError.GLOBALPING_LOCATION_MULTIPLE,
            MonitorDraftCodec.validate(draft.copy(globalpingLocation = "germany,france")),
        )
        assertEquals(
            MonitorDraftError.GLOBALPING_PROTOCOL_INVALID,
            MonitorDraftCodec.validate(draft.copy(globalpingPingProtocol = null)),
        )
        assertEquals(
            MonitorDraftError.GLOBALPING_PORT_REQUIRED,
            MonitorDraftCodec.validate(
                draft.copy(globalpingPingProtocol = GlobalpingPingProtocol.TCP, port = null),
            ),
        )
        assertEquals(
            MonitorDraftError.GLOBALPING_PING_COUNT_INVALID,
            MonitorDraftCodec.validate(draft.copy(globalpingPingCount = 0)),
        )
        assertNull(
            MonitorDraftCodec.validate(
                draft.copy(
                    globalpingTarget = "2001:4860:4860::8888",
                    globalpingIpFamily = GlobalpingIpFamily.IPV6,
                    globalpingPingProtocol = GlobalpingPingProtocol.TCP,
                    port = 443,
                    globalpingPingCount = 100,
                ),
            ),
        )
    }

    @Test
    fun globalpingPingEditPreservesFutureFields() {
        val raw = Json.parseToJsonElement(
            """{
                "id":47,"type":"globalping","subtype":"ping","name":"Edge",
                "hostname":"example.com","port":80,"location":"world","ipFamily":null,
                "protocol":"ICMP","ping_count":3,"interval":60,"retryInterval":60,
                "resendInterval":0,"maxretries":0,"active":true,"notificationIDList":{},
                "futureGlobalping":{"routing":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.globalpingEditable)
        val updated = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(
                globalpingLocation = "germany",
                globalpingIpFamily = GlobalpingIpFamily.IPV4,
                globalpingPingProtocol = GlobalpingPingProtocol.TCP,
                port = 443,
                globalpingPingCount = 4,
            ),
        )!!

        assertEquals("germany", updated["location"]!!.jsonPrimitive.content)
        assertEquals("ipv4", updated["ipFamily"]!!.jsonPrimitive.content)
        assertEquals("TCP", updated["protocol"]!!.jsonPrimitive.content)
        assertEquals(443, updated["port"]!!.jsonPrimitive.content.toInt())
        assertEquals(4, updated["ping_count"]!!.jsonPrimitive.content.toInt())
        assertEquals(raw["futureGlobalping"], updated["futureGlobalping"])
    }

    @Test
    fun globalpingDnsCreateAndEditUseVerifiedFields() {
        val createdDraft = MonitorDraft.create("globalping").copy(
            name = "Global DNS",
            globalpingSubtype = GlobalpingSubtype.DNS,
            globalpingTarget = "example.com",
            globalpingDnsProtocol = GlobalpingDnsProtocol.UDP,
            globalpingDnsRecordType = GlobalpingDnsRecordType.AAAA,
            globalpingResolver = "1.1.1.1",
            port = 53,
            keyword = "^2606:",
        )

        assertNull(MonitorDraftCodec.validate(createdDraft))
        val created = MonitorDraftCodec.newPayload(createdDraft)
        assertEquals("dns", created["subtype"]!!.jsonPrimitive.content)
        assertEquals("example.com", created["hostname"]!!.jsonPrimitive.content)
        assertEquals(53, created["port"]!!.jsonPrimitive.content.toInt())
        assertEquals("UDP", created["protocol"]!!.jsonPrimitive.content)
        assertEquals("AAAA", created["dns_resolve_type"]!!.jsonPrimitive.content)
        assertEquals("1.1.1.1", created["dns_resolve_server"]!!.jsonPrimitive.content)
        assertEquals("^2606:", created["keyword"]!!.jsonPrimitive.content)

        val raw = Json.parseToJsonElement(
            """{
                "id":51,"type":"globalping","subtype":"dns","name":"DNS edge",
                "hostname":"example.com","port":53,"location":"world","ipFamily":null,
                "protocol":"UDP","dns_resolve_type":"A","dns_resolve_server":"",
                "keyword":"","dns_last_result":"93.184.216.34","interval":60,"retryInterval":60,
                "resendInterval":0,"maxretries":0,"active":true,"notificationIDList":{},
                "futureGlobalping":{"routing":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!
        assertTrue(loaded.globalpingEditable)
        assertEquals(GlobalpingSubtype.DNS, loaded.globalpingSubtype)
        val updated = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(
                globalpingDnsProtocol = GlobalpingDnsProtocol.TCP,
                globalpingDnsRecordType = GlobalpingDnsRecordType.TXT,
                globalpingResolver = "8.8.8.8",
                port = 5353,
                keyword = "verification",
            ),
        )!!
        assertEquals("TCP", updated["protocol"]!!.jsonPrimitive.content)
        assertEquals("TXT", updated["dns_resolve_type"]!!.jsonPrimitive.content)
        assertEquals("8.8.8.8", updated["dns_resolve_server"]!!.jsonPrimitive.content)
        assertEquals(5353, updated["port"]!!.jsonPrimitive.content.toInt())
        assertEquals("verification", updated["keyword"]!!.jsonPrimitive.content)
        assertEquals(raw["dns_last_result"], updated["dns_last_result"])
        assertEquals(raw["futureGlobalping"], updated["futureGlobalping"])
    }

    @Test
    fun globalpingDnsValidationRejectsInvalidSubtypeFields() {
        val draft = MonitorDraft.create("globalping").copy(
            name = "Global DNS",
            globalpingSubtype = GlobalpingSubtype.DNS,
            globalpingTarget = "example.com",
            globalpingDnsProtocol = GlobalpingDnsProtocol.UDP,
            globalpingDnsRecordType = GlobalpingDnsRecordType.A,
            port = 53,
        )

        assertEquals(
            MonitorDraftError.GLOBALPING_RESOLVER_INVALID,
            MonitorDraftCodec.validate(draft.copy(globalpingResolver = "https://1.1.1.1")),
        )
        assertEquals(
            MonitorDraftError.GLOBALPING_PORT_REQUIRED,
            MonitorDraftCodec.validate(draft.copy(port = 0)),
        )
    }

    @Test
    fun globalpingHttpCreateUsesRequestAuthAndResponseFields() {
        val draft = MonitorDraft.create("globalping").copy(
            name = "Global HTTP",
            globalpingSubtype = GlobalpingSubtype.HTTP,
            globalpingTarget = "https://example.com/health?ready=1",
            globalpingResolver = "1.1.1.1",
            globalpingHttpProtocol = GlobalpingHttpProtocol.HTTP2,
            globalpingHttpMethod = GlobalpingHttpMethod.HEAD,
            globalpingHttpAcceptedCodes = "200-299, 304",
            globalpingHttpIgnoreTls = true,
            globalpingHttpExpiryNotification = true,
            globalpingHttpCacheBust = true,
            globalpingHttpResponseCheck = GlobalpingHttpResponseCheck.KEYWORD,
            keyword = "ready",
            websocketHeaders = listOf(MonitorHeaderDraft(name = "X-Tenant", value = "primary")),
            websocketAuthMethod = WebSocketAuthMethod.BASIC,
            websocketBasicUsername = "probe",
            websocketBasicPassword = "secret",
        )

        assertNull(MonitorDraftCodec.validate(draft))
        val created = MonitorDraftCodec.newPayload(draft)
        assertEquals("http", created["subtype"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/health?ready=1", created["url"]!!.jsonPrimitive.content)
        assertEquals("HTTP2", created["protocol"]!!.jsonPrimitive.content)
        assertEquals("HEAD", created["method"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("200-299", "304"),
            created["accepted_statuscodes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(created["ignoreTls"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(created["expiryNotification"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(created["cacheBust"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("ready", created["keyword"]!!.jsonPrimitive.content)
        assertEquals("basic", created["authMethod"]!!.jsonPrimitive.content)
        assertEquals("secret", created["basic_auth_pass"]!!.jsonPrimitive.content)
        assertEquals(
            "primary",
            Json.parseToJsonElement(created["headers"]!!.jsonPrimitive.content)
                .jsonObject["X-Tenant"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun globalpingHttpEditMasksSecretsAndSubtypeChangeClearsThem() {
        val raw = Json.parseToJsonElement(
            """{
                "id":52,"type":"globalping","subtype":"http","name":"HTTP edge",
                "url":"https://example.com/health","location":"world","ipFamily":null,
                "protocol":null,"method":"GET","accepted_statuscodes":["200-299"],
                "dns_resolve_server":"1.1.1.1","ignoreTls":false,"expiryNotification":true,
                "cacheBust":false,"headers":"{\"X-API-Key\":\"saved-key\"}",
                "authMethod":"basic","basic_auth_user":"probe","basic_auth_pass":"saved-password",
                "keyword":"","expectedValue":"","interval":60,"retryInterval":60,
                "resendInterval":0,"maxretries":0,"active":true,"notificationIDList":{},
                "futureGlobalping":{"routing":"strict"}
            }""",
        ).jsonObject
        val loaded = MonitorDraftCodec.from(raw)!!

        assertTrue(loaded.globalpingEditable)
        assertEquals(GlobalpingHttpProtocol.AUTO, loaded.globalpingHttpProtocol)
        assertEquals(listOf("X-API-Key"), loaded.websocketHeaders.map { it.name })
        assertTrue(loaded.websocketHeaders.single().hasSavedValue)
        assertTrue(loaded.websocketHasSavedBasicPassword)
        assertTrue(loaded.websocketBasicPassword.isEmpty())
        assertNull(MonitorDraftCodec.validate(loaded))

        val retained = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(globalpingHttpAcceptedCodes = "200-299, 304"),
        )!!
        assertEquals("saved-password", retained["basic_auth_pass"]!!.jsonPrimitive.content)
        assertEquals(raw["headers"], retained["headers"])
        assertEquals(raw["futureGlobalping"], retained["futureGlobalping"])

        val switched = MonitorDraftCodec.safeExistingPayload(
            raw,
            loaded.copy(
                globalpingSubtype = GlobalpingSubtype.DNS,
                globalpingTarget = "example.com",
                globalpingDnsProtocol = GlobalpingDnsProtocol.UDP,
                globalpingDnsRecordType = GlobalpingDnsRecordType.A,
                port = 53,
            ),
        )!!
        assertEquals("dns", switched["subtype"]!!.jsonPrimitive.content)
        assertTrue(switched["authMethod"] is JsonNull)
        assertEquals("", switched["headers"]!!.jsonPrimitive.content)
        assertEquals("", switched["basic_auth_pass"]!!.jsonPrimitive.content)
    }

    @Test
    fun globalpingHttpOAuthSecretsAndFutureRequestShapesStayOpaque() {
        val oauthRaw = Json.parseToJsonElement(
            """{
                "id":54,"type":"globalping","subtype":"http","name":"OAuth edge",
                "url":"https://example.com","location":"world","ipFamily":null,"protocol":null,
                "method":"GET","accepted_statuscodes":["200-299"],"authMethod":"oauth2-cc",
                "oauth_auth_method":"client_secret_post","oauth_token_url":"https://id.example.com/token",
                "oauth_client_id":"probe","oauth_client_secret":"saved-secret","oauth_scopes":"read",
                "oauth_audience":"globalping","interval":60,"retryInterval":60,"resendInterval":0,
                "maxretries":0,"active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val oauthDraft = MonitorDraftCodec.from(oauthRaw)!!
        assertTrue(oauthDraft.websocketAuthEditable)
        assertTrue(oauthDraft.websocketHasSavedOAuthClientSecret)
        assertTrue(oauthDraft.websocketOAuthClientSecret.isEmpty())
        assertNull(MonitorDraftCodec.validate(oauthDraft))
        val retained = MonitorDraftCodec.safeExistingPayload(oauthRaw, oauthDraft.copy(name = "Renamed"))!!
        assertEquals("saved-secret", retained["oauth_client_secret"]!!.jsonPrimitive.content)
        val replaced = MonitorDraftCodec.safeExistingPayload(
            oauthRaw,
            oauthDraft.copy(websocketOAuthClientSecret = "replacement-secret"),
        )!!
        assertEquals("replacement-secret", replaced["oauth_client_secret"]!!.jsonPrimitive.content)

        val futureRaw = Json.parseToJsonElement(
            """{
                "id":55,"type":"globalping","subtype":"http","name":"Future edge",
                "url":"https://example.com","location":"world","ipFamily":null,"protocol":null,
                "method":"GET","accepted_statuscodes":["200-299"],
                "headers":"{\"X-List\":[\"one\",\"two\"]}","authMethod":"future-auth",
                "future_secret":"opaque","interval":60,"retryInterval":60,"resendInterval":0,
                "maxretries":0,"active":true,"notificationIDList":{}
            }""",
        ).jsonObject
        val futureDraft = MonitorDraftCodec.from(futureRaw)!!
        assertTrue(futureDraft.globalpingEditable)
        assertFalse(futureDraft.websocketHeadersEditable)
        assertFalse(futureDraft.websocketAuthEditable)
        val futureUpdated = MonitorDraftCodec.safeExistingPayload(
            futureRaw,
            futureDraft.copy(globalpingLocation = "germany"),
        )!!
        assertEquals(futureRaw["headers"], futureUpdated["headers"])
        assertEquals(futureRaw["authMethod"], futureUpdated["authMethod"])
        assertEquals(futureRaw["future_secret"], futureUpdated["future_secret"])
    }

    @Test
    fun globalpingHttpJsonResponseAndStatusValidationMatchKuma() {
        val base = MonitorDraft.create("globalping").copy(
            name = "Global HTTP",
            globalpingSubtype = GlobalpingSubtype.HTTP,
            globalpingTarget = "https://example.com/health",
            globalpingHttpResponseCheck = GlobalpingHttpResponseCheck.JSON_QUERY,
            jsonQueryExpression = "status",
            jsonQueryOperator = "==",
            jsonQueryExpectedValue = "ready",
        )

        assertNull(MonitorDraftCodec.validate(base))
        listOf("", "99", "1000", "299-200", "200-299,").forEach { invalid ->
            assertTrue(
                MonitorDraftCodec.validate(base.copy(globalpingHttpAcceptedCodes = invalid)) in setOf(
                    MonitorDraftError.GLOBALPING_HTTP_STATUS_CODES_REQUIRED,
                    MonitorDraftError.GLOBALPING_HTTP_STATUS_CODE_INVALID,
                ),
            )
        }
        assertEquals(
            MonitorDraftError.GLOBALPING_HTTP_JSON_QUERY_OPERATOR_INVALID,
            MonitorDraftCodec.validate(base.copy(jsonQueryOperator = "matches")),
        )
        val payload = MonitorDraftCodec.newPayload(base)
        assertEquals("", payload["keyword"]!!.jsonPrimitive.content)
        assertEquals("status", payload["jsonPath"]!!.jsonPrimitive.content)
        assertEquals("==", payload["jsonPathOperator"]!!.jsonPrimitive.content)
        assertEquals("ready", payload["expectedValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun unsupportedGlobalpingVariantsRemainOpaque() {
        val unsupportedVariants = listOf(
            """{"id":48,"type":"globalping","subtype":"http","name":"HTTP edge","url":"https://example.com","location":"world","protocol":"GET","futureGlobalping":1}""",
            """{"id":49,"type":"globalping","subtype":"ping","name":"Future ping","hostname":"example.com","location":"world","protocol":"QUIC","futureGlobalping":2}""",
            """{"id":50,"type":"globalping","subtype":"ping","name":"Future family","hostname":"example.com","location":"world","protocol":"ICMP","ipFamily":"ipv8","futureGlobalping":3}""",
            """{"id":53,"type":"globalping","subtype":"dns","name":"Future DNS","hostname":"example.com","location":"world","protocol":"UDP","dns_resolve_type":"CAA","futureGlobalping":4}""",
        )

        unsupportedVariants.forEach { encoded ->
            val raw = Json.parseToJsonElement(encoded).jsonObject
            val loaded = MonitorDraftCodec.from(raw)!!

            assertFalse(loaded.globalpingEditable)
            val updated = MonitorDraftCodec.safeExistingPayload(raw, loaded.copy(name = "Renamed"))!!
            assertEquals("Renamed", updated["name"]!!.jsonPrimitive.content)
            assertEquals(raw["subtype"], updated["subtype"])
            assertEquals(raw["protocol"], updated["protocol"])
            assertEquals(raw["ipFamily"], updated["ipFamily"])
            assertEquals(raw["futureGlobalping"], updated["futureGlobalping"])
        }
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
