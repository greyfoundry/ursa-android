package dev.astoris.ursa.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.incidentNoteDataStore by preferencesDataStore(name = "ursa_incident_notes")

@Serializable
data class IncidentNote(
    val serverUrl: String,
    val monitorId: Int,
    val startedAt: String,
    val text: String,
    val updatedAtMillis: Long,
)

/** Pure codec and bounds for user-authored local incident notes. */
object IncidentNoteCodec {
    const val MAX_NOTES = 500
    const val MAX_TEXT_LENGTH = 2_000
    private const val MAX_URL_LENGTH = 2_048
    private const val MAX_STARTED_AT_LENGTH = 64
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(notes: List<IncidentNote>, nowMillis: Long = System.currentTimeMillis()): String =
        json.encodeToString(normalized(notes, nowMillis))

    fun decode(raw: String?, nowMillis: Long = System.currentTimeMillis()): List<IncidentNote> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<IncidentNote>>(raw) }
            .getOrDefault(emptyList())
            .let { normalized(it, nowMillis) }
    }

    fun normalized(notes: List<IncidentNote>, nowMillis: Long): List<IncidentNote> =
        notes.asSequence()
            .filter { note ->
                note.serverUrl.isNotBlank() && note.serverUrl.length <= MAX_URL_LENGTH &&
                    note.monitorId > 0 && note.startedAt.isNotBlank() &&
                    note.startedAt.length <= MAX_STARTED_AT_LENGTH && note.text.isNotBlank() &&
                    note.updatedAtMillis in 1L..(nowMillis + 5L * 60L * 1_000L)
            }
            .map { it.copy(text = it.text.trim().take(MAX_TEXT_LENGTH)) }
            .sortedByDescending(IncidentNote::updatedAtMillis)
            .distinctBy { Triple(it.serverUrl, it.monitorId, it.startedAt) }
            .take(MAX_NOTES)
            .toList()
}

/** Encrypted local-only notes keyed to one server, monitor, and known outage start. */
class IncidentNoteStore(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = Crypto(appContext)
    private val notesKey = stringPreferencesKey("notes")

    val notes: Flow<List<IncidentNote>> =
        appContext.incidentNoteDataStore.data.map(::decode)

    suspend fun save(
        serverUrl: String,
        monitorId: Int,
        startedAt: String,
        text: String,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        appContext.incidentNoteDataStore.edit { preferences ->
            val retained = decode(preferences).filterNot {
                it.serverUrl == serverUrl && it.monitorId == monitorId && it.startedAt == startedAt
            }
            val updated = text.trim().take(IncidentNoteCodec.MAX_TEXT_LENGTH).takeIf { it.isNotBlank() }
                ?.let { retained + IncidentNote(serverUrl, monitorId, startedAt, it, atMillis) }
                ?: retained
            preferences[notesKey] = crypto.encrypt(IncidentNoteCodec.encode(updated, atMillis))
        }
    }

    private fun decode(preferences: Preferences): List<IncidentNote> {
        val cipher = preferences[notesKey] ?: return emptyList()
        return IncidentNoteCodec.decode(crypto.decrypt(cipher))
    }
}
