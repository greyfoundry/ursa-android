package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.CleartextPolicy
import dev.astoris.ursa.data.model.ServerConnection
import java.net.URI

/** Pure transport guard kept separate from TLS certificate trust decisions. */
object ConnectionTransportPolicy {
    fun isCleartext(url: String): Boolean = runCatching {
        URI(url.trim()).scheme.equals("http", ignoreCase = true)
    }.getOrDefault(false)

    fun allows(connection: ServerConnection): Boolean = allows(
        connection.url,
        connection.cleartextPolicy,
    )

    fun allows(url: String, policy: CleartextPolicy): Boolean =
        !isCleartext(url) || policy != CleartextPolicy.DENY
}
