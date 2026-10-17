package dev.astoris.ursa.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotProtectionModeTest {
    @Test fun missingOrInvalidValuesPreserveExistingProtection() {
        assertSame(ScreenshotProtectionMode.ALWAYS, ScreenshotProtectionMode.fromStored(null))
        assertSame(ScreenshotProtectionMode.ALWAYS, ScreenshotProtectionMode.fromStored("invalid"))
    }

    @Test fun storedModesRoundTrip() {
        ScreenshotProtectionMode.entries.forEach { mode ->
            assertSame(mode, ScreenshotProtectionMode.fromStored(mode.name))
        }
    }

    @Test fun modesApplyExpectedPrecedence() {
        assertFalse(ScreenshotProtectionMode.OFF.shouldSecure(sensitiveContent = true))
        assertFalse(ScreenshotProtectionMode.SENSITIVE.shouldSecure(sensitiveContent = false))
        assertTrue(ScreenshotProtectionMode.SENSITIVE.shouldSecure(sensitiveContent = true))
        assertTrue(ScreenshotProtectionMode.ALWAYS.shouldSecure(sensitiveContent = false))
    }
}
