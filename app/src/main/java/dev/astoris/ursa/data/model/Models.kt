package dev.astoris.ursa.data.model

import kotlinx.serialization.Serializable

/** Kuma heartbeat status codes (verified against Uptime Kuma 2.5.0). */
@Serializable
enum class MonitorStatus(val code: Int) {
    DOWN(0), UP(1), PENDING(2), MAINTENANCE(3);

    companion object {
        fun from(code: Int): MonitorStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

/**
 * A monitor as shown in the list. Status/ping/uptime are not in the `monitorList`
 * snapshot - they arrive via separate `heartbeat` / `avgPing` / `uptime` events and
 * are merged in by the client adapter. Serializable so the last-known list can be
 * cached for offline display (see core/storage/MonitorSnapshot).
 */
@Serializable
data class Monitor(
    val id: Int,
    val name: String,
    val url: String?,
    val type: String,
    val active: Boolean,
    val tags: List<String> = emptyList(),
    val status: MonitorStatus = MonitorStatus.PENDING,
    val ping: Int? = null,
    val avgPing: Int? = null,
    val uptime24h: Double? = null,
)

/** A single heartbeat (the live `heartbeat` event; camelCase on the wire). */
data class Heartbeat(
    val monitorId: Int,
    val status: MonitorStatus,
    val time: String,
    val msg: String?,
    val ping: Int?,
    val important: Boolean,
)

/** TLS certificate summary for an HTTPS monitor (from the `certInfo` event). */
data class CertInfo(
    val valid: Boolean,
    val subject: String?,
    val issuer: String?,
    /** ISO-8601 "valid to" timestamp, if the server reported one. */
    val validTo: String? = null,
    /** Days until expiry as reported at capture time. */
    val daysRemaining: Int? = null,
)

/** A configured Kuma server. JWT is only present after a successful login. */
@Serializable
data class ServerConnection(
    val url: String,
    val username: String,
    val jwt: String? = null,
    /** User opted into trusting a self-signed / internal-CA certificate. */
    val insecure: Boolean = false,
    /** Optional user-facing name; absent in records written before multi-server UI. */
    val alias: String? = null,
    /** Encrypted with the connection; values are never included in UI summaries or logs. */
    val headers: List<RequestHeader> = emptyList(),
) {
    val displayName: String
        get() = alias?.trim()?.takeIf { it.isNotEmpty() }
            ?: url.substringAfter("://", url).substringBefore('/').ifBlank { url }
}

@Serializable
data class RequestHeader(val name: String, val value: String) {
    fun normalizedOrNull(): RequestHeader? {
        val cleanName = name.trim()
        val cleanValue = value.trim()
        if (!HEADER_NAME.matches(cleanName) || cleanValue.isEmpty()) return null
        if ('\r' in cleanValue || '\n' in cleanValue) return null
        return RequestHeader(cleanName, cleanValue)
    }

    private companion object {
        val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
    }
}

/** Result of a login attempt. */
sealed interface LoginResult {
    data class Success(val jwt: String?) : LoginResult
    data object TwoFactorRequired : LoginResult
    data class Failure(val message: String) : LoginResult
}
