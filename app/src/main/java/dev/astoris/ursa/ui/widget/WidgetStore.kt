package dev.astoris.ursa.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.astoris.ursa.core.storage.Crypto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.widgetDataStore by preferencesDataStore(name = "ursa_widgets")

/** Encrypted per-widget configuration and public-page widget snapshots. */
class WidgetStore(context: Context) {
    private val appContext = context.applicationContext
    private val crypto = Crypto(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveConfig(appWidgetId: Int, config: WidgetConfig) {
        appContext.widgetDataStore.edit { preferences ->
            preferences[configKey(appWidgetId)] = crypto.encrypt(json.encodeToString(config))
        }
    }

    suspend fun loadConfig(appWidgetId: Int): WidgetConfig? {
        val cipher = appContext.widgetDataStore.data.first()[configKey(appWidgetId)] ?: return null
        return crypto.decrypt(cipher)?.let { raw ->
            runCatching { json.decodeFromString<WidgetConfig>(raw) }.getOrNull()
        }
    }

    suspend fun removeConfig(appWidgetId: Int) {
        appContext.widgetDataStore.edit { it.remove(configKey(appWidgetId)) }
    }

    suspend fun savePublicSnapshot(pageId: String, snapshot: WidgetSnapshot) {
        appContext.widgetDataStore.edit { preferences ->
            preferences[pageKey(pageId)] = crypto.encrypt(json.encodeToString(snapshot))
        }
    }

    suspend fun loadPublicSnapshot(pageId: String): WidgetSnapshot? {
        val cipher = appContext.widgetDataStore.data.first()[pageKey(pageId)] ?: return null
        return crypto.decrypt(cipher)?.let { raw ->
            runCatching { json.decodeFromString<WidgetSnapshot>(raw) }.getOrNull()
        }
    }

    private fun configKey(id: Int) = stringPreferencesKey("config_$id")
    private fun pageKey(id: String) = stringPreferencesKey("page_${id.take(100)}")
}
