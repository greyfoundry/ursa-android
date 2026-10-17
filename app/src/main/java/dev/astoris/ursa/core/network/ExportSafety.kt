package dev.astoris.ursa.core.network

private val ipv4Address = Regex("(?:^|\\s)(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?(?:$|\\s)")
private val hostAddress = Regex("^[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+(?::\\d+)?$")

fun credentialFreeServerLabel(displayName: String, serverScope: String): String {
    val candidate = displayName.trim()
    val addressLike = candidate.isBlank() ||
        "://" in candidate ||
        '/' in candidate ||
        '\\' in candidate ||
        '@' in candidate ||
        ipv4Address.containsMatchIn(candidate) ||
        hostAddress.matches(candidate)
    if (!addressLike) return candidate
    val shortScope = serverScope.filter(Char::isLetterOrDigit).take(12)
    return if (shortScope.isBlank()) "Saved server" else "Server $shortScope"
}
