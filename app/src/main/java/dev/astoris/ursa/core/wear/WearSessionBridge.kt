package dev.astoris.ursa.core.wear

import android.content.Context
import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.data.model.ServerConnection
import java.net.URI
import java.util.Locale
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

class WearSessionTransfer private constructor(
    val serverUrl: String,
    val sessionToken: String,
    val serverName: String,
    val headers: List<RequestHeader>,
) {
    fun encode(): ByteArray = buildJsonObject {
        put("serverUrl", serverUrl)
        put("sessionToken", sessionToken)
        put("serverName", serverName)
        put("headers", buildJsonArray {
            headers.forEach { header ->
                add(buildJsonObject {
                    put("name", header.name)
                    put("value", header.value)
                })
            }
        })
    }.toString().encodeToByteArray()

    companion object {
        const val MESSAGE_PATH = "/ursa/session/v1"
        const val CAPABILITY = "ursa_session_receiver"
        const val MAX_MESSAGE_BYTES = 16_384
        private const val MAX_TOKEN_LENGTH = 8_192
        private const val MAX_SERVER_NAME_LENGTH = 80
        private const val MAX_HEADERS = 8
        private const val MAX_HEADER_NAME_LENGTH = 128
        private const val MAX_HEADER_VALUE_LENGTH = 4_096

        fun from(connection: ServerConnection): WearSessionTransfer? {
            if (connection.insecure) return null
            val serverUrl = normalizeServerUrl(connection.url) ?: return null
            val token = connection.jwt?.trim()?.takeIf {
                it.isNotEmpty() && it.length <= MAX_TOKEN_LENGTH
            } ?: return null
            if (connection.headers.size > MAX_HEADERS) return null
            val headers = connection.headers.map { header ->
                header.normalizedOrNull()?.takeIf {
                    it.name.length <= MAX_HEADER_NAME_LENGTH &&
                        it.value.length <= MAX_HEADER_VALUE_LENGTH
                } ?: return null
            }
            if (headers.map { it.name.lowercase(Locale.ROOT) }.distinct().size != headers.size) {
                return null
            }
            val serverName = connection.displayName.replace(Regex("\\s+"), " ").trim()
                .take(MAX_SERVER_NAME_LENGTH).ifEmpty { "Kuma server" }
            val transfer = WearSessionTransfer(serverUrl, token, serverName, headers)
            return transfer.takeIf { it.encode().size <= MAX_MESSAGE_BYTES }
        }

        private fun normalizeServerUrl(value: String): String? {
            val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme != "http" && scheme != "https") return null
            if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) {
                return null
            }
            return value.trim().trimEnd('/')
        }
    }
}

enum class WearSessionSendError {
    NO_REACHABLE_WATCH,
    TRANSFER_FAILED,
}

sealed interface WearSessionSendResult {
    data class Success(val watchCount: Int) : WearSessionSendResult
    data class Failure(val reason: WearSessionSendError) : WearSessionSendResult
}

interface WearSessionSender {
    suspend fun send(context: Context, transfer: WearSessionTransfer): WearSessionSendResult
}

object WearSessionBridge {
    private const val IMPLEMENTATION = "dev.astoris.ursa.core.wear.PlayWearSessionSender"

    private val sender: WearSessionSender? by lazy {
        runCatching {
            Class.forName(IMPLEMENTATION).getDeclaredConstructor().newInstance() as WearSessionSender
        }.getOrNull()
    }

    val isAvailable: Boolean get() = sender != null

    suspend fun send(context: Context, transfer: WearSessionTransfer): WearSessionSendResult =
        sender?.send(context.applicationContext, transfer)
            ?: WearSessionSendResult.Failure(WearSessionSendError.TRANSFER_FAILED)
}
