package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    /** Returns null if there is no cache for [url] or it cannot be decrypted/decoded. */
    suspend fun load(url: String): MonitorSnapshot? {
        val cipher = appContext.cacheDataStore.data.first()[keyFor(url)] ?: return null
        val plain = crypto.decrypt(cipher) ?: return null
        return SnapshotCodec.decode(plain)
    }

    suspend fun clear(url: String) {
        appContext.cacheDataStore.edit { it.remove(keyFor(url)) }
    }
}
