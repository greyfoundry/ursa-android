package dev.astoris.ursa.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateTest {
    @Test
    fun semanticVersionsCompareNumerically() {
        assertTrue(ReleaseVersion.parse("v1.10.0")!! > ReleaseVersion.parse("1.9.9")!!)
        assertFalse(ReleaseVersion.parse("1.2.3")!! > ReleaseVersion.parse("1.2.3")!!)
        assertNull(ReleaseVersion.parse("release-latest"))
    }

    @Test
    fun releaseJsonRejectsDraftsAndUnsafeLinks() {
        val release = ReleaseUpdate.parse(
            """{"tag_name":"v1.3.0","name":"URSA 1.3.0","body":"Good things","html_url":"https://github.com/greyfoundry/ursa-android/releases/tag/v1.3.0","draft":false,"prerelease":false}""",
            ReleaseVersion(1, 2, 3),
        )
        assertEquals("1.3.0", release?.version.toString())
        assertEquals("Good things", release?.notes)

        assertNull(
            ReleaseUpdate.parse(
                """{"tag_name":"v9.0.0","html_url":"http://example.com/file.apk","draft":false,"prerelease":false}""",
                ReleaseVersion(1, 2, 3),
            ),
        )
    }
}
