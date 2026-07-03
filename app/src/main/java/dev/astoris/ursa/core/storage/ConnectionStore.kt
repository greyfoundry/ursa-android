package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.astoris.ursa.data.model.ServerConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "ursa")

/**
 * Persists configured servers and which one is active.
 *
 * ponytail: values are stored as plaintext JSON for M1. M2 hardening wraps this
 * with Tink AEAD (android-keystore master key) — see docs/references/datastore-tink.mdx.
 * Only the JWT is persisted, never the password.
 */
class ConnectionStore(private val context: Context) {

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
            prefs[connectionsKey] = json.encodeToString(next)
            prefs[activeUrlKey] = conn.url
        }
    }

    suspend fun remove(url: String) {
        context.dataStore.edit { prefs ->
            val next = decode(prefs).filterNot { it.url == url }
            prefs[connectionsKey] = json.encodeToString(next)
            if (prefs[activeUrlKey] == url) {
                val fallback = next.firstOrNull()?.url
                if (fallback != null) prefs[activeUrlKey] = fallback else prefs.remove(activeUrlKey)
            }
        }
    }

    suspend fun setActive(url: String) {
        context.dataStore.edit { prefs -> prefs[activeUrlKey] = url }
    }

    private fun decode(prefs: Preferences): List<ServerConnection> =
        prefs[connectionsKey]?.let {
            runCatching { json.decodeFromString<List<ServerConnection>>(it) }.getOrDefault(emptyList())
        } ?: emptyList()
}
