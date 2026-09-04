package dev.astoris.ursa.core.network

import java.net.URI

/** Android 17 gates direct LAN connections behind a runtime permission. */
object LocalNetworkAccess {
    fun requiresPermission(url: String, sdkInt: Int): Boolean {
        if (sdkInt < 37) return false
        val host = runCatching { URI(url.trim()).host?.lowercase()?.trim('[', ']') }.getOrNull() ?: return false
        if (host == "localhost" || host == "::1" || host.startsWith("127.")) return false
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home") ||
            host.endsWith(".internal") || '.' !in host && ':' !in host
        ) return true

        val ipv4 = host.split('.').mapNotNull(String::toIntOrNull)
        if (ipv4.size == 4 && ipv4.all { it in 0..255 }) {
            return ipv4[0] == 10 ||
                ipv4[0] == 192 && ipv4[1] == 168 ||
                ipv4[0] == 172 && ipv4[1] in 16..31 ||
                ipv4[0] == 169 && ipv4[1] == 254
        }

        return host.startsWith("fc") || host.startsWith("fd") ||
            host.startsWith("fe8") || host.startsWith("fe9") ||
            host.startsWith("fea") || host.startsWith("feb")
    }
}
