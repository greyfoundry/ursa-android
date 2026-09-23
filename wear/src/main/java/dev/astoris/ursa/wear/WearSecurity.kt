package dev.astoris.ursa.wear

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import org.json.JSONObject

object WearSecretCodec {
    private val aad = "ursa-wear-session".encodeToByteArray()

    fun encrypt(aead: Aead, plain: String): String = Base64.getEncoder().encodeToString(
        aead.encrypt(plain.encodeToByteArray(), aad),
    )

    fun decrypt(aead: Aead, cipherText: String): String? = runCatching {
        aead.decrypt(Base64.getDecoder().decode(cipherText), aad).decodeToString()
    }.getOrNull()
}

class WearCrypto(context: Context) {
    private val appContext = context.applicationContext

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    fun encrypt(plain: String): String = WearSecretCodec.encrypt(aead, plain)

    fun decrypt(cipherText: String): String? = WearSecretCodec.decrypt(aead, cipherText)

    private companion object {
        const val KEYSET_NAME = "ursa_wear_keyset"
        const val KEYSET_PREFS = "ursa_wear_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://ursa_wear_master_key"
    }
}

enum class WearMonitorAction(val eventName: String) {
    PAUSE("pauseMonitor"),
    RESUME("resumeMonitor"),
}

data class WearActionResult(
    val success: Boolean,
    val message: String,
)

object WearActionMessage {
    fun clean(value: String): String = value.replace(Regex("\\s+"), " ").trim().take(160)
}

data class WearActionHeader(
    val name: String,
    val value: String,
) {
    fun normalizedOrNull(): WearActionHeader? {
        val normalizedName = name.trim()
        val normalizedValue = value.trim()
        if (normalizedName.isEmpty() || normalizedName.length > MAX_NAME_LENGTH) return null
        if (!normalizedName.matches(HEADER_NAME)) return null
        if (normalizedValue.isEmpty() || normalizedValue.length > MAX_VALUE_LENGTH) return null
        if ('\r' in normalizedValue || '\n' in normalizedValue) return null
        return WearActionHeader(normalizedName, normalizedValue)
    }

    private companion object {
        const val MAX_NAME_LENGTH = 128
        const val MAX_VALUE_LENGTH = 4_096
        val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
    }
}

enum class WearAccessCapability {
    MONITOR_STATE,
}

data class WearPairingPayload(
    val serverUrl: String,
    val sessionToken: String,
    val serverName: String,
    val headers: List<WearActionHeader> = emptyList(),
    val protocolVersion: Int = LEGACY_PROTOCOL_VERSION,
    val policyVersion: Int = LEGACY_POLICY_VERSION,
    val allowedCapabilities: Set<WearAccessCapability> = WearAccessCapability.entries.toSet(),
) {
    fun encode(): ByteArray = buildJsonObject {
        if (protocolVersion >= CURRENT_PROTOCOL_VERSION) {
            put("protocolVersion", protocolVersion)
            put("policyVersion", policyVersion)
        }
        put("serverUrl", serverUrl)
        put("sessionToken", sessionToken)
        put("serverName", WearActionMessage.clean(serverName).take(MAX_NAME_LENGTH))
        put("headers", buildJsonArray {
            headers.forEach { header ->
                add(buildJsonObject {
                    put("name", header.name)
                    put("value", header.value)
                })
            }
        })
        if (protocolVersion >= CURRENT_PROTOCOL_VERSION) {
            put("allowedCapabilities", buildJsonArray {
                WearAccessCapability.entries.filter { it in allowedCapabilities }.forEach { add(it.name) }
            })
        }
    }.toString().encodeToByteArray()

    fun allows(action: WearMonitorAction): Boolean = when (action) {
        WearMonitorAction.PAUSE,
        WearMonitorAction.RESUME,
        -> WearAccessCapability.MONITOR_STATE in allowedCapabilities
    }

    companion object {
        const val MESSAGE_PATH = "/ursa/session/v1"
        const val V2_MESSAGE_PATH = "/ursa/session/v2"
        const val CLEAR_MESSAGE_PATH = "/ursa/session/clear/v1"
        const val CAPABILITY = "ursa_session_receiver"
        const val V2_CAPABILITY = "ursa_session_receiver_v2"
        const val CURRENT_PROTOCOL_VERSION = 2
        const val CURRENT_POLICY_VERSION = 1
        const val LEGACY_PROTOCOL_VERSION = 1
        const val LEGACY_POLICY_VERSION = 0
        private const val MAX_MESSAGE_BYTES = 16_384
        private const val MAX_NAME_LENGTH = 80

        fun parse(bytes: ByteArray): WearPairingPayload? = parse(bytes, expectedProtocol = null)

        fun parseVersion1(bytes: ByteArray): WearPairingPayload? =
            parse(bytes, expectedProtocol = LEGACY_PROTOCOL_VERSION)

        fun parseVersion2(bytes: ByteArray): WearPairingPayload? =
            parse(bytes, expectedProtocol = CURRENT_PROTOCOL_VERSION)

        private fun parse(bytes: ByteArray, expectedProtocol: Int?): WearPairingPayload? {
            if (bytes.isEmpty() || bytes.size > MAX_MESSAGE_BYTES) return null
            val json = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull()
                ?: return null
            val protocolVersion = json.int("protocolVersion") ?: LEGACY_PROTOCOL_VERSION
            if (protocolVersion !in LEGACY_PROTOCOL_VERSION..CURRENT_PROTOCOL_VERSION) return null
            if (expectedProtocol != null && protocolVersion != expectedProtocol) return null
            val policyVersion: Int
            val allowedCapabilities: Set<WearAccessCapability>
            if (protocolVersion == LEGACY_PROTOCOL_VERSION) {
                policyVersion = LEGACY_POLICY_VERSION
                allowedCapabilities = WearAccessCapability.entries.toSet()
            } else {
                policyVersion = json.int("policyVersion") ?: return null
                if (policyVersion != CURRENT_POLICY_VERSION) return null
                allowedCapabilities = parseCapabilities(json) ?: return null
            }
            val serverUrl = json.string("serverUrl")
            val sessionToken = json.string("sessionToken")
            val serverName = WearActionMessage.clean(
                json.string("serverName"),
            ).take(MAX_NAME_LENGTH)
            val headers = parseHeaders(json) ?: return null
            val action = WearActionConfig(serverUrl, sessionToken, headers)
            val normalized = action.normalizedServerUrl ?: return null
            if (!action.isReady) return null
            return WearPairingPayload(
                serverUrl = normalized,
                sessionToken = sessionToken.trim(),
                serverName = serverName.ifEmpty { "Kuma server" },
                headers = headers.mapNotNull(WearActionHeader::normalizedOrNull),
                protocolVersion = protocolVersion,
                policyVersion = policyVersion,
                allowedCapabilities = allowedCapabilities,
            )
        }

        private fun parseCapabilities(json: JsonObject): Set<WearAccessCapability>? {
            val array = json["allowedCapabilities"] as? JsonArray ?: return null
            if (array.size > WearAccessCapability.entries.size) return null
            return array.mapTo(linkedSetOf()) { item ->
                val name = (item as? JsonPrimitive)?.contentOrNull ?: return null
                WearAccessCapability.entries.firstOrNull { it.name == name } ?: return null
            }
        }

        private fun parseHeaders(json: JsonObject): List<WearActionHeader>? {
            val element = json["headers"] ?: return emptyList()
            val array = element as? JsonArray ?: return null
            if (array.size > WearActionConfig.MAX_HEADERS) return null
            return array.map { item ->
                val header = item as? JsonObject ?: return null
                WearActionHeader(
                    name = header.string("name"),
                    value = header.string("value"),
                ).normalizedOrNull() ?: return null
            }
        }

        private fun JsonObject.string(key: String): String =
            (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

        private fun JsonObject.int(key: String): Int? =
            (this[key] as? JsonPrimitive)?.intOrNull
    }
}

object WearActionClient {
    suspend fun execute(
        config: WearActionConfig,
        monitorId: Int,
        action: WearMonitorAction,
    ): WearActionResult {
        val serverUrl = config.normalizedServerUrl
        if (!config.isReady || serverUrl == null || monitorId <= 0) {
            return WearActionResult(false, "Private actions are not configured")
        }
        if (!config.allows(action)) {
            return WearActionResult(false, "This action is not allowed by the phone access profile")
        }
        return withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                val socket = try {
                    IO.socket(
                        serverUrl,
                        IO.Options().apply {
                            transports = arrayOf("polling", "websocket")
                            reconnection = false
                            timeout = CONNECT_TIMEOUT_MS
                            extraHeaders = config.headers
                                .mapNotNull(WearActionHeader::normalizedOrNull)
                                .associate { it.name to listOf(it.value) }
                        },
                    )
                } catch (_: Exception) {
                    continuation.resume(WearActionResult(false, "Invalid server address"))
                    return@suspendCancellableCoroutine
                }

                fun finish(result: WearActionResult) {
                    if (finished.compareAndSet(false, true)) {
                        socket.off()
                        socket.disconnect()
                        if (continuation.isActive) continuation.resume(result)
                    }
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        socket.off()
                        socket.disconnect()
                    }
                }
                socket.on(Socket.EVENT_CONNECT) {
                    socket.emit("loginByToken", config.sessionToken.trim(), Ack { loginArgs ->
                        val login = loginArgs.getOrNull(0) as? JSONObject
                        if (login?.optBoolean("ok") != true) {
                            finish(WearActionResult(false, "Session token was rejected"))
                            return@Ack
                        }
                        socket.emit(action.eventName, monitorId, Ack { actionArgs ->
                            val response = actionArgs.getOrNull(0) as? JSONObject
                            val success = response?.optBoolean("ok") == true
                            val serverMessage = WearActionMessage.clean(response?.optString("msg").orEmpty())
                            val fallback = if (success) "Monitor updated" else "Monitor action failed"
                            finish(WearActionResult(success, serverMessage.ifEmpty { fallback }))
                        })
                    })
                }
                socket.on(Socket.EVENT_CONNECT_ERROR) {
                    finish(WearActionResult(false, "Could not reach the server"))
                }
                socket.connect()
            }
        } ?: WearActionResult(false, "Server did not respond in time")
    }

    private const val CONNECT_TIMEOUT_MS = 8_000L
    private const val ACTION_TIMEOUT_MS = 15_000L
}
