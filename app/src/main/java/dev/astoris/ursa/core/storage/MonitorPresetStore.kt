package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.astoris.ursa.core.network.MonitorDraft
import dev.astoris.ursa.core.network.MonitorDraftCodec
import dev.astoris.ursa.core.network.MonitorDraftTransfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val Context.monitorPresetDataStore by preferencesDataStore(name = "ursa_monitor_presets")

@Serializable
data class MonitorPreset(
    val id: String,
    val title: String,
    val type: String,
    val payload: String,
    val updatedAtMillis: Long,
)

/** Pure validation and canonicalization for local credential-free monitor templates. */
object MonitorPresetCodec {
    const val MAX_PRESETS = 50
    const val MAX_TITLE_LENGTH = 80
    private const val MAX_ID_LENGTH = 64
    private const val MAX_PAYLOAD_LENGTH = 64 * 1_024
    private val json = Json { ignoreUnknownKeys = true }

    fun create(
        id: String,
        title: String,
        draft: MonitorDraft,
        updatedAtMillis: Long = System.currentTimeMillis(),
    ): MonitorPreset? {
        val safe = MonitorDraftTransfer.preset(draft) ?: return null
        val cleanId = id.trim().takeIf { it.isNotEmpty() && it.length <= MAX_ID_LENGTH } ?: return null
        val cleanTitle = title.trim().take(MAX_TITLE_LENGTH).takeIf(String::isNotEmpty) ?: return null
        return MonitorPreset(
            id = cleanId,
            title = cleanTitle,
            type = safe.type,
            payload = json.encodeToString(MonitorDraftCodec.newPayload(safe)),
            updatedAtMillis = updatedAtMillis,
        )
    }

    fun draft(preset: MonitorPreset): MonitorDraft? {
        if (preset.id.isBlank() || preset.id.length > MAX_ID_LENGTH ||
            preset.title.isBlank() || preset.title.length > MAX_TITLE_LENGTH ||
            preset.payload.length > MAX_PAYLOAD_LENGTH
        ) {
            return null
        }
        val payload = runCatching { json.parseToJsonElement(preset.payload) as? JsonObject }.getOrNull() ?: return null
        val decoded = MonitorDraftCodec.fromNewPayload(payload) ?: return null
        if (decoded.type != preset.type) return null
        return MonitorDraftTransfer.preset(decoded)
    }

    fun normalized(
        presets: List<MonitorPreset>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<MonitorPreset> = presets.asSequence()
        .filter { it.updatedAtMillis in 1L..(nowMillis + 5L * 60L * 1_000L) }
        .mapNotNull { preset ->
            val draft = draft(preset) ?: return@mapNotNull null
            create(preset.id, preset.title, draft, preset.updatedAtMillis)
        }
        .sortedByDescending(MonitorPreset::updatedAtMillis)
        .distinctBy(MonitorPreset::id)
        .take(MAX_PRESETS)
        .toList()
}

/** Encrypted local-only storage for bounded, credential-free monitor presets. */
class MonitorPresetStore(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = Crypto(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val presetsKey = stringPreferencesKey("presets")

    val presets: Flow<List<MonitorPreset>> = appContext.monitorPresetDataStore.data.map(::decode)

    suspend fun save(id: String, title: String, draft: MonitorDraft): Boolean {
        val preset = MonitorPresetCodec.create(id, title, draft) ?: return false
        appContext.monitorPresetDataStore.edit { preferences ->
            val next = decode(preferences).filterNot { it.id == preset.id } + preset
            preferences[presetsKey] = crypto.encrypt(json.encodeToString(MonitorPresetCodec.normalized(next)))
        }
        return true
    }

    suspend fun remove(id: String) {
        appContext.monitorPresetDataStore.edit { preferences ->
            val next = decode(preferences).filterNot { it.id == id }
            preferences[presetsKey] = crypto.encrypt(json.encodeToString(next))
        }
    }

    private fun decode(preferences: Preferences): List<MonitorPreset> {
        val cipher = preferences[presetsKey] ?: return emptyList()
        val plain = crypto.decrypt(cipher) ?: return emptyList()
        return runCatching { json.decodeFromString<List<MonitorPreset>>(plain) }
            .getOrDefault(emptyList())
            .let(MonitorPresetCodec::normalized)
    }
}
