package dev.astoris.ursa.core.push

import java.net.URI

/** Builds the delivery URL Kuma should use for the current UnifiedPush distributor. */
object KumaWebhook {
    fun deliveryUrl(endpoint: String, distributor: String?): String? {
        val raw = endpoint.trim()
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) return null
        if (!distributor.orEmpty().contains("ntfy", ignoreCase = true)) return uri.toASCIIString()
        if (uri.rawQuery.orEmpty().split('&').any { it.substringBefore('=').equals("up", ignoreCase = true) }) {
            return uri.toASCIIString()
        }
        val query = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "$it&up=1" } ?: "up=1"
        return URI(uri.scheme, null, uri.host, uri.port, uri.rawPath, query, null).toASCIIString()
    }
}
