package dev.astoris.ursa.core.push

/** Builds and recognizes the metadata-only marker used for a Kuma delivery test. */
object PushDeliveryTest {
    private const val NAME_PREFIX = "URSA delivery test"
    private val tokenPattern = Regex("^[a-f0-9]{12}$")

    fun notificationName(token: String): String? =
        token.takeIf(tokenPattern::matches)?.let { "$NAME_PREFIX $it" }

    fun matches(message: String, token: String): Boolean {
        val name = notificationName(token) ?: return false
        return message.trim() == "$name Testing"
    }
}
