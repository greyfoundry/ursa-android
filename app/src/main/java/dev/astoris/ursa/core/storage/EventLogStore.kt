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
import java.util.UUID

private val Context.eventLogDataStore by preferencesDataStore(name = "ursa_event_log")

/** Events that URSA itself can timestamp reliably. Kuma heartbeat transitions stay live-derived. */
@Serializable
enum class LocalEventKind { PAUSED, RESUMED, SLOW_RESPONSE, CERTIFICATE_EXPIRY, PUSH_ALERT }

@Serializable
data class LocalEvent(
    val id: String,
    /** Null for provider-neutral push events whose originating Kuma server is not in the payload. */
    val serverUrl: String? = null,
    val monitorId: Int? = null,
    val monitorName: String,
    val kind: LocalEventKind,
    val atMillis: Long,
    val detail: String? = null,
)

/** Pure codec and retention policy, kept separate so corrupt local data is harmless and testable. */
object LocalEventCodec {
    const val MAX_EVENTS = 500
    const val RETENTION_MILLIS = 90L * 24L * 60L * 60L * 1_000L
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_DETAIL_LENGTH = 240
    private const val MAX_URL_LENGTH = 2_048
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(events: List<LocalEvent>, nowMillis: Long = System.currentTimeMillis()): String =
        json.encodeToString(normalized(events, nowMillis))

    fun decode(raw: String?, nowMillis: Long = System.currentTimeMillis()): List<LocalEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LocalEvent>>(raw) }
            .getOrDefault(emptyList())
            .let { normalized(it, nowMillis) }
    }

    fun normalized(events: List<LocalEvent>, nowMillis: Long): List<LocalEvent> {
        val cutoff = nowMillis - RETENTION_MILLIS
        return events.asSequence()
            .filter { event ->
                event.id.isNotBlank() && event.monitorName.isNotBlank() &&
                    event.atMillis in cutoff..(nowMillis + 5L * 60L * 1_000L) &&
                    (event.serverUrl == null || event.serverUrl.length <= MAX_URL_LENGTH)
            }
            .map { event ->
                event.copy(
                    monitorName = event.monitorName.trim().take(MAX_NAME_LENGTH),
                    detail = event.detail?.trim()?.take(MAX_DETAIL_LENGTH)?.ifBlank { null },
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<LocalEvent> { it.atMillis }.thenBy { it.id })
            .take(MAX_EVENTS)
            .toList()
    }
}

/** Encrypted, bounded device history for successful actions and posted local alerts. */
class EventLogStore(context: Context) {

    private val appContext = context.applicationContext
    private val crypto = Crypto(appContext)
    private val eventsKey = stringPreferencesKey("events")

    val events: Flow<List<LocalEvent>> =
        appContext.eventLogDataStore.data.map(::decode)

    suspend fun append(
        serverUrl: String?,
        monitorId: Int?,
        monitorName: String,
        kind: LocalEventKind,
        detail: String? = null,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        val event = LocalEvent(
            id = UUID.randomUUID().toString(),
            serverUrl = serverUrl,
            monitorId = monitorId,
            monitorName = monitorName,
            kind = kind,
            atMillis = atMillis,
            detail = detail,
        )
        appContext.eventLogDataStore.edit { preferences ->
            preferences[eventsKey] = crypto.encrypt(
                LocalEventCodec.encode(decode(preferences) + event, atMillis),
            )
        }
    }

    private fun decode(preferences: Preferences): List<LocalEvent> {
        val cipher = preferences[eventsKey] ?: return emptyList()
        return LocalEventCodec.decode(crypto.decrypt(cipher))
    }
}
