package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.astoris.ursa.data.model.ServerConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "ursa")

/**
 * Persists configured servers and which one is active. The connections blob (which
 * holds the JWT) is encrypted at rest with Tink AES-256-GCM, master key in the
 * Android Keystore. Only the JWT is persisted, never the password.
 */
class ConnectionStore(context: Context) {

    private val context = context.applicationContext
    private val crypto = Crypto(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val connectionsKey = stringPreferencesKey("connections")
    private val activeUrlKey = stringPreferencesKey("active_url")

    val connections: Flow<List<ServerConnection>> =
        context.dataStore.data.map { prefs -> decode(prefs) }

    val activeUrl: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[activeUrlKey] }

    /** Add or replace a server (matched by URL) and make it active. */
    suspend fun upsert(conn: ServerConnection) {
        context.dataStore.edit { prefs ->
            val next = decode(prefs).filterNot { it.url == conn.url } + conn
            prefs[connectionsKey] = encode(next)
            prefs[activeUrlKey] = conn.url
        }
    }

    suspend fun remove(url: String) {
        context.dataStore.edit { prefs ->
            val next = decode(prefs).filterNot { it.url == url }
            prefs[connectionsKey] = encode(next)
            if (prefs[activeUrlKey] == url) {
                val fallback = next.firstOrNull()?.url
                if (fallback != null) prefs[activeUrlKey] = fallback else prefs.remove(activeUrlKey)
            }
        }
    }

    suspend fun setActive(url: String) {
        context.dataStore.edit { prefs -> prefs[activeUrlKey] = url }
    }

    /** Rename a saved server without changing which connection is active. */
    suspend fun rename(url: String, alias: String?) {
        context.dataStore.edit { prefs ->
            val next = decode(prefs).map { connection ->
                if (connection.url == url) connection.copy(alias = alias?.trim()?.takeIf { it.isNotEmpty() })
                else connection
            }
            prefs[connectionsKey] = encode(next)
        }
    }

    suspend fun snapshot(): List<ServerConnection> = connections.first()

    /** Merge a validated portable backup by URL, preserving local sessions when omitted. */
    suspend fun mergeImported(imported: List<ServerConnection>) {
        context.dataStore.edit { prefs ->
            val merged = decode(prefs).toMutableList()
            imported.forEach { incoming ->
                val index = merged.indexOfFirst { it.url == incoming.url }
                if (index >= 0) {
                    val existing = merged[index]
                    merged[index] = incoming.copy(jwt = incoming.jwt ?: existing.jwt)
                } else {
                    merged += incoming
                }
            }
            prefs[connectionsKey] = encode(merged)
            val active = prefs[activeUrlKey]
            if (active == null || merged.none { it.url == active }) {
                merged.firstOrNull()?.let { prefs[activeUrlKey] = it.url }
            }
        }
    }

    private fun encode(list: List<ServerConnection>): String =
        crypto.encrypt(json.encodeToString(list))

    private fun decode(prefs: Preferences): List<ServerConnection> {
        val cipher = prefs[connectionsKey] ?: return emptyList()
        val plain = crypto.decrypt(cipher) ?: return emptyList() // undecryptable -> treat as empty
        return runCatching { json.decodeFromString<List<ServerConnection>>(plain) }.getOrDefault(emptyList())
    }
}
