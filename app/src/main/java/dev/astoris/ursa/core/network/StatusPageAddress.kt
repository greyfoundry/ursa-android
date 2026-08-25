package dev.astoris.ursa.core.network

import java.net.URI

data class ResolvedStatusPageAddress(val baseUrl: String, val slug: String?)

enum class StatusPageAddressError {
    EMPTY,
    INVALID_URL,
    UNSUPPORTED_SCHEME,
    CREDENTIALS_NOT_ALLOWED,
    QUERY_OR_FRAGMENT_NOT_ALLOWED,
    INVALID_SLUG,
    CONFLICTING_SLUG,
}

sealed interface StatusPageAddressResult {
    data class Valid(val address: ResolvedStatusPageAddress) : StatusPageAddressResult
    data class Invalid(val error: StatusPageAddressError) : StatusPageAddressResult
}

/** Strictly resolves either a Kuma base URL plus slug or a complete public-page URL. */
object StatusPageAddress {

    private val slugPattern = Regex("^[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*$")

    fun resolve(rawAddress: String, rawSlug: String = ""): StatusPageAddressResult {
        val value = rawAddress.trim().removeSuffix("/")
        if (value.isEmpty()) return StatusPageAddressResult.Invalid(StatusPageAddressError.EMPTY)
        if (value.length > MAX_ADDRESS_LENGTH) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.INVALID_URL)
        }
        val candidate = if ("://" in value) value else "https://$value"
        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return StatusPageAddressResult.Invalid(StatusPageAddressError.INVALID_URL)
        if (uri.scheme?.lowercase() !in setOf("http", "https")) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.UNSUPPORTED_SCHEME)
        }
        if (uri.host.isNullOrBlank() || uri.port !in -1..65535) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.INVALID_URL)
        }
        if (uri.userInfo != null) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.CREDENTIALS_NOT_ALLOWED)
        }
        if (uri.rawQuery != null || uri.rawFragment != null) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.QUERY_OR_FRAGMENT_NOT_ALLOWED)
        }
        val rawPath = uri.rawPath.orEmpty()
        if (ENCODED_SEPARATOR.containsMatchIn(rawPath)) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.INVALID_URL)
        }
        val segments = uri.path.orEmpty().trim('/').takeIf { it.isNotEmpty() }
            ?.split('/')
            .orEmpty()
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.INVALID_URL)
        }

        val markerSize = when {
            segments.size >= 2 && segments[segments.lastIndex - 1] == "status" -> 1
            segments.size >= 3 &&
                segments[segments.lastIndex - 2] == "api" &&
                segments[segments.lastIndex - 1] == "status-page" -> 2
            else -> 0
        }
        val derivedSlug = if (markerSize > 0) segments.last() else null
        val explicitSlug = rawSlug.trim().ifEmpty { null }
        if (derivedSlug != null && !isValidSlug(derivedSlug)) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.INVALID_SLUG)
        }
        if (explicitSlug != null && !isValidSlug(explicitSlug)) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.INVALID_SLUG)
        }
        if (derivedSlug != null && explicitSlug != null && derivedSlug != explicitSlug) {
            return StatusPageAddressResult.Invalid(StatusPageAddressError.CONFLICTING_SLUG)
        }

        val baseSegments = if (markerSize > 0) segments.dropLast(markerSize + 1) else segments
        val basePath = baseSegments.takeIf { it.isNotEmpty() }?.joinToString("/", prefix = "/") ?: ""
        val base = URI(
            uri.scheme.lowercase(),
            null,
            uri.host,
            uri.port,
            basePath,
            null,
            null,
        ).toASCIIString().removeSuffix("/")
        return StatusPageAddressResult.Valid(
            ResolvedStatusPageAddress(base, derivedSlug ?: explicitSlug),
        )
    }

    fun isValidSlug(value: String): Boolean = value.length <= MAX_SLUG_LENGTH && slugPattern.matches(value)

    private val ENCODED_SEPARATOR = Regex("%(?:2f|5c)", RegexOption.IGNORE_CASE)
    private const val MAX_ADDRESS_LENGTH = 500
    private const val MAX_SLUG_LENGTH = 120
}
