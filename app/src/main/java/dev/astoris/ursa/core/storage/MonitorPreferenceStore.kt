package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.monitorPreferenceDataStore by preferencesDataStore(name = "ursa_monitor_preferences")

/** Stores per-server monitor choices without retaining the server URL in preference keys. */
class MonitorPreferenceStore(context: Context) {

    private val appContext = context.applicationContext

    fun favorites(serverUrl: String): Flow<Set<Int>> =
        appContext.monitorPreferenceDataStore.data.map { preferences ->
            preferences[favoritesKey(serverUrl)]
                .orEmpty()
                .mapNotNull(String::toIntOrNull)
                .toSet()
        }

    suspend fun toggleFavorite(serverUrl: String, monitorId: Int) {
        appContext.monitorPreferenceDataStore.edit { preferences ->
            val ids = preferences[favoritesKey(serverUrl)].orEmpty().toMutableSet()
            if (!ids.add(monitorId.toString())) ids.remove(monitorId.toString())
            preferences[favoritesKey(serverUrl)] = ids
        }
    }

    private fun favoritesKey(serverUrl: String) =
        stringSetPreferencesKey("favorites_${serverUrl.sha256().take(16)}")
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
