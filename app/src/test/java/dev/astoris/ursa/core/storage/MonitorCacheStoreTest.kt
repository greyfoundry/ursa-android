package dev.astoris.ursa.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MonitorCacheStoreTest {

    @Test fun missing_cipher_is_distinct_from_unreadable_cache() {
        assertSame(MonitorCacheRead.Missing, decodeCachedSnapshot(null) { error("not called") })
        assertEquals(
            MonitorCacheRead.Unavailable(MonitorCacheFailure.DECRYPTION_FAILED),
            decodeCachedSnapshot("cipher") { null },
        )
        assertEquals(
            MonitorCacheRead.Unavailable(MonitorCacheFailure.INVALID_SNAPSHOT),
            decodeCachedSnapshot("cipher") { "not json" },
        )
    }

    @Test fun valid_cipher_decodes_to_available_snapshot() {
        val snapshot = MonitorSnapshot(emptyList(), updatedAt = 42L)

        assertEquals(
            MonitorCacheRead.Available(snapshot),
            decodeCachedSnapshot("cipher") { SnapshotCodec.encode(snapshot) },
        )
    }
}
