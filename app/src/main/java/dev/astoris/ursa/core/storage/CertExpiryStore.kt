package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.certDataStore by preferencesDataStore(name = "ursa_cert")

/**
 * Persists captured [CertExpiry] entries across all servers, encrypted at rest with
 * the shared [Crypto]. The background reminder reads these; the repository refreshes
 * them per server whenever certificate info arrives over the live connection.
 */
class CertExpiryStore(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = Crypto(appContext)
    private val key = stringPreferencesKey("cert_expiry")

    suspend fun loadAll(): List<CertExpiry> {
        val cipher = appContext.certDataStore.data.first()[key] ?: return emptyList()
        val plain = crypto.decrypt(cipher) ?: return emptyList()
        return CertExpiryUtil.decode(plain)
    }

    /** Replace the entries for [url] with [entries], leaving other servers untouched. */
    suspend fun saveForServer(url: String, entries: List<CertExpiry>) {
        val next = loadAll().filterNot { it.serverUrl == url } + entries
        val cipher = crypto.encrypt(CertExpiryUtil.encode(next))
        appContext.certDataStore.edit { it[key] = cipher }
    }
}
