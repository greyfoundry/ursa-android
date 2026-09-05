package dev.astoris.ursa.core.network

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

enum class ConnectionFailureReason {
    DEVICE_OFFLINE,
    SERVER_UNREACHABLE,
    AUTHENTICATION,
    CERTIFICATE,
    INCOMPATIBLE_RESPONSE,
    UNKNOWN,
}

object ConnectionFailure {
    fun classify(error: Throwable?, networkAvailable: Boolean = true): ConnectionFailureReason {
        if (!networkAvailable) return ConnectionFailureReason.DEVICE_OFFLINE
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        if (chain.any { it is SSLException || it is CertificateException }) return ConnectionFailureReason.CERTIFICATE
        if (chain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException }) {
            return ConnectionFailureReason.SERVER_UNREACHABLE
        }
        val message = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
        return when {
            listOf("certificate", "certpath", "trust anchor", "ssl handshake").any(message::contains) ->
                ConnectionFailureReason.CERTIFICATE
            listOf("unexpected response", "invalid response", "parser", "protocol").any(message::contains) ->
                ConnectionFailureReason.INCOMPATIBLE_RESPONSE
            listOf("timeout", "timed out", "refused", "unreachable", "unknown host").any(message::contains) ->
                ConnectionFailureReason.SERVER_UNREACHABLE
            else -> ConnectionFailureReason.UNKNOWN
        }
    }
}
