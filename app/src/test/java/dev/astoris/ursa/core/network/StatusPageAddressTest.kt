package dev.astoris.ursa.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusPageAddressTest {

    @Test
    fun resolve_derivesBaseAndSlugFromPublicPageUrl() {
        assertValid("https://kuma.example.com/status/home/", "https://kuma.example.com", "home")
        assertValid(
            "https://kuma.example.com/internal/api/status-page/ops",
            "https://kuma.example.com/internal",
            "ops",
        )
    }

    @Test
    fun resolve_acceptsBaseWithExplicitSlugAndAddsHttps() {
        val result = StatusPageAddress.resolve("kuma.example.com/internal", "team-1")

        assertEquals(
            StatusPageAddressResult.Valid(
                ResolvedStatusPageAddress("https://kuma.example.com/internal", "team-1"),
            ),
            result,
        )
    }

    @Test
    fun resolve_leavesSlugOpenForServerEntryDiscovery() {
        assertValid("https://status.example.com", "https://status.example.com", null)
    }

    @Test
    fun resolve_rejectsUnsafeOrAmbiguousAddresses() {
        assertInvalid("ftp://kuma.example.com/status/home", error = StatusPageAddressError.UNSUPPORTED_SCHEME)
        assertInvalid("https://user@kuma.example.com/status/home", error = StatusPageAddressError.CREDENTIALS_NOT_ALLOWED)
        assertInvalid("https://kuma.example.com/status/home?token=x", error = StatusPageAddressError.QUERY_OR_FRAGMENT_NOT_ALLOWED)
        assertInvalid("https://kuma.example.com/status/bad_slug", error = StatusPageAddressError.INVALID_SLUG)
        assertInvalid("https://kuma.example.com/%2fstatus/home", error = StatusPageAddressError.INVALID_URL)
        assertInvalid(
            "https://kuma.example.com/status/home",
            "different",
            StatusPageAddressError.CONFLICTING_SLUG,
        )
    }

    private fun assertValid(input: String, base: String, slug: String?) {
        assertEquals(
            StatusPageAddressResult.Valid(ResolvedStatusPageAddress(base, slug)),
            StatusPageAddress.resolve(input),
        )
    }

    private fun assertInvalid(
        input: String,
        slug: String = "",
        error: StatusPageAddressError,
    ) {
        val result = StatusPageAddress.resolve(input, slug)
        assertTrue(result is StatusPageAddressResult.Invalid)
        assertEquals(error, (result as StatusPageAddressResult.Invalid).error)
    }
}
