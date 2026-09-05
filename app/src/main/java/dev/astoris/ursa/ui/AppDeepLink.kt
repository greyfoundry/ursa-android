package dev.astoris.ursa.ui

import java.net.URI
import java.security.MessageDigest

sealed interface AppRoute {
    data object Push : AppRoute
    data object Settings : AppRoute
    data class Connection(val serverScope: String) : AppRoute
    data class Monitor(val serverScope: String, val monitorId: Int) : AppRoute
    data class Incident(val serverScope: String, val monitorId: Int) : AppRoute
    data class StatusPage(val pageId: String) : AppRoute
}

/** Stable, credential-free routes. Server URLs are represented only by a one-way scope. */
object AppDeepLink {
    private val scopePattern = Regex("^[a-f0-9]{16}$")

    fun serverScope(serverUrl: String): String = MessageDigest.getInstance("SHA-256")
        .digest(serverUrl.trim().removeSuffix("/").toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(16)

    fun connection(serverUrl: String) = "ursa://connection/${serverScope(serverUrl)}"
    fun monitor(serverUrl: String, monitorId: Int) = "ursa://monitor/${serverScope(serverUrl)}/$monitorId"
    fun incident(serverUrl: String, monitorId: Int) = "ursa://incident/${serverScope(serverUrl)}/$monitorId"

    fun parse(value: String?): AppRoute? {
        val uri = runCatching { URI(value ?: return null) }.getOrNull() ?: return null
        if (uri.scheme != "ursa" || uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (uri.rawPath?.contains('%') == true) return null
        val parts = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
        return when (uri.host) {
            "push" -> AppRoute.Push.takeIf { parts.isEmpty() }
            "settings" -> AppRoute.Settings.takeIf { parts.isEmpty() }
            "connection" -> parts.singleOrNull()?.takeIf(scopePattern::matches)?.let(AppRoute::Connection)
            "status-page" -> parts.singleOrNull()
                ?.takeIf { it.length in 1..100 && Regex("^[A-Za-z0-9_-]+$").matches(it) }
                ?.let(AppRoute::StatusPage)
            "monitor", "incident" -> {
                if (parts.size != 2 || !scopePattern.matches(parts[0])) return null
                val id = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
                if (uri.host == "monitor") AppRoute.Monitor(parts[0], id) else AppRoute.Incident(parts[0], id)
            }
            else -> null
        }
    }
}
