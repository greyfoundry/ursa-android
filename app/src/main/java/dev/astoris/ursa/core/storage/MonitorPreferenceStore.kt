package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import dev.astoris.ursa.ui.monitors.MonitorViewCodec
import dev.astoris.ursa.ui.monitors.SavedMonitorView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    suspend fun favoriteSnapshot(serverUrl: String): Set<Int> = favorites(serverUrl).first()

    suspend fun mergeFavorites(serverUrl: String, monitorIds: Set<Int>) {
        if (monitorIds.isEmpty()) return
        appContext.monitorPreferenceDataStore.edit { preferences ->
            val key = favoritesKey(serverUrl)
            val merged = preferences[key].orEmpty() + monitorIds.filter { it > 0 }.map(Int::toString)
            preferences[key] = merged
        }
    }

    fun savedViews(serverUrl: String): Flow<List<SavedMonitorView>> =
        appContext.monitorPreferenceDataStore.data.map { preferences ->
            preferences[viewsKey(serverUrl)].orEmpty()
                .mapNotNull(MonitorViewCodec::decode)
                .sortedBy { it.name.lowercase() }
                .take(MAX_SAVED_VIEWS)
        }

    suspend fun saveView(serverUrl: String, view: SavedMonitorView): Boolean {
        if (!MonitorViewCodec.isValidName(view.name)) return false
        appContext.monitorPreferenceDataStore.edit { preferences ->
            val key = viewsKey(serverUrl)
            val existing = preferences[key].orEmpty().mapNotNull(MonitorViewCodec::decode)
            val updated = (existing.filterNot { it.name.equals(view.name.trim(), ignoreCase = true) } +
                view.copy(name = view.name.trim())).takeLast(MAX_SAVED_VIEWS)
            preferences[key] = updated.map(MonitorViewCodec::encode).toSet()
        }
        return true
    }

    suspend fun deleteView(serverUrl: String, name: String) {
        appContext.monitorPreferenceDataStore.edit { preferences ->
            val key = viewsKey(serverUrl)
            preferences[key] = preferences[key].orEmpty().filterTo(mutableSetOf()) { raw ->
                MonitorViewCodec.decode(raw)?.name?.equals(name, ignoreCase = true) != true
            }
        }
    }

    private fun favoritesKey(serverUrl: String) =
        stringSetPreferencesKey("favorites_${serverUrl.sha256().take(16)}")

    private fun viewsKey(serverUrl: String) =
        stringSetPreferencesKey("views_${serverUrl.sha256().take(16)}")

    private companion object { const val MAX_SAVED_VIEWS = 10 }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
