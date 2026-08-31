package dev.astoris.ursa.core.network

import java.net.URI

/** Builds credential-free links to Uptime Kuma's web UI. */
object ServerWebLink {
    fun dashboard(baseUrl: String): String? = build(baseUrl, "dashboard")

    fun monitor(baseUrl: String, monitorId: Int): String? {
        if (monitorId <= 0) return null
        return build(baseUrl, "dashboard/$monitorId")
    }

    private fun build(baseUrl: String, relativePath: String): String? {
        val source = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return null
        val scheme = source.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        if (
            source.host.isNullOrBlank() || source.userInfo != null || source.rawQuery != null ||
            source.rawFragment != null || source.rawPath.containsEncodedSeparator()
        ) {
            return null
        }
        val normalized = source.normalize()
        if (normalized.rawPath != source.rawPath) return null
        val basePath = source.path.orEmpty().trimEnd('/')
        val path = "$basePath/$relativePath"
        return runCatching {
            URI(scheme, null, source.host, source.port, path, null, null).toASCIIString()
        }.getOrNull()
    }

    private fun String.containsEncodedSeparator(): Boolean {
        val lower = lowercase()
        return "%2f" in lower || "%5c" in lower || '\\' in this || '\r' in this || '\n' in this
    }
}
