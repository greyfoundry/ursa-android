package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

// Separate DataStore file from ConnectionStore's "ursa" (one instance per file).
private val Context.cacheDataStore by preferencesDataStore(name = "ursa_cache")

/**
 * Persists the last-known [MonitorSnapshot] per server so the app can paint
 * immediately (offline or while reconnecting). Encrypted at rest with the same
 * [Crypto] as credentials: monitor names and URLs are infrastructure detail and get
 * the same protection. Keyed by server URL, so switching servers loads that server's
 * cache.
 */
class MonitorCacheStore(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = Crypto(appContext)

    private fun keyFor(url: String) = stringPreferencesKey("snapshot_$url")

    suspend fun save(url: String, snapshot: MonitorSnapshot) {
        val cipher = crypto.encrypt(SnapshotCodec.encode(snapshot))
        appContext.cacheDataStore.edit { it[keyFor(url)] = cipher }
    }

    /**
     * Reads one cache while retaining the distinction between no snapshot and an
     * unreadable snapshot. Existing callers can continue to use [load].
     */
    suspend fun read(url: String): MonitorCacheRead = readAll(listOf(url)).getValue(url)

    /** Reads all requested servers from one DataStore snapshot. */
    suspend fun readAll(urls: Collection<String>): Map<String, MonitorCacheRead> {
        val distinctUrls = urls.distinct()
        if (distinctUrls.isEmpty()) return emptyMap()
        val preferences = try {
            appContext.cacheDataStore.data.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return distinctUrls.associateWith {
                MonitorCacheRead.Unavailable(MonitorCacheFailure.STORE_UNAVAILABLE)
            }
        }
        return distinctUrls.associateWith { url ->
            decodeCachedSnapshot(preferences[keyFor(url)], crypto::decrypt)
        }
    }

    /** Returns null if there is no cache for [url] or it cannot be decrypted/decoded. */
    suspend fun load(url: String): MonitorSnapshot? {
        return (read(url) as? MonitorCacheRead.Available)?.snapshot
    }

    suspend fun clear(url: String) {
        appContext.cacheDataStore.edit { it.remove(keyFor(url)) }
    }
}

enum class MonitorCacheFailure {
    STORE_UNAVAILABLE,
    DECRYPTION_FAILED,
    INVALID_SNAPSHOT,
}

sealed interface MonitorCacheRead {
    data class Available(val snapshot: MonitorSnapshot) : MonitorCacheRead
    data object Missing : MonitorCacheRead
    data class Unavailable(val reason: MonitorCacheFailure) : MonitorCacheRead
}

internal fun decodeCachedSnapshot(
    cipher: String?,
    decrypt: (String) -> String?,
): MonitorCacheRead {
    if (cipher == null) return MonitorCacheRead.Missing
    val plain = decrypt(cipher)
        ?: return MonitorCacheRead.Unavailable(MonitorCacheFailure.DECRYPTION_FAILED)
    val snapshot = SnapshotCodec.decode(plain)
        ?: return MonitorCacheRead.Unavailable(MonitorCacheFailure.INVALID_SNAPSHOT)
    return MonitorCacheRead.Available(snapshot)
}
