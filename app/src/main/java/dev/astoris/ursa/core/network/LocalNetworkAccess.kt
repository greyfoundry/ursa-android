package dev.astoris.ursa.core.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/** Android 17 gates direct LAN connections behind a runtime permission. */
object LocalNetworkAccess {
    fun requiresPermission(url: String, sdkInt: Int): Boolean {
        if (sdkInt < 37) return false
        val host = runCatching {
            URI(url.trim()).host?.lowercase()?.trim('[', ']')?.removeSuffix(".")
        }.getOrNull() ?: return false
        if (host == "localhost" || host == "::1" || host.startsWith("127.")) return false
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home") ||
            host.endsWith(".internal") || host.endsWith(".ts.net") || '.' !in host && ':' !in host
        ) return true

        val ipv4Parts = host.split('.')
        val ipv4 = ipv4Parts.takeIf { it.size == 4 }?.mapNotNull(String::toIntOrNull).orEmpty()
        if (ipv4.size == 4 && ipv4.all { it in 0..255 }) {
            return ipv4[0] == 10 ||
                ipv4[0] == 192 && ipv4[1] == 168 ||
                ipv4[0] == 172 && ipv4[1] in 16..31 ||
                ipv4[0] == 169 && ipv4[1] == 254 ||
                // Carrier-grade NAT space (RFC 6598), used by Tailscale and similar
                // mesh VPNs for peer addresses.
                ipv4[0] == 100 && ipv4[1] in 64..127
        }

        val ipv6 = if (':' in host) {
            runCatching { InetAddress.getByName(host) as? Inet6Address }.getOrNull()
        } else {
            null
        }
        val bytes = ipv6?.address ?: return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first in 0xfc..0xfd || first == 0xfe && second and 0xc0 == 0x80
    }
}
