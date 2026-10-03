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
enum class LocalEventKind {
    PAUSED,
    RESUMED,
    SLOW_RESPONSE,
    CERTIFICATE_EXPIRY,
    PUSH_RECEIVED,
    PUSH_DELAYED,
    PUSH_ALERT,
    PUSH_REPEATED,
    PUSH_ACKNOWLEDGED,
    PUSH_SNOOZED,
    PUSH_SUPPRESSED,
    PUSH_CORRELATED,
    PUSH_RECOVERED,
}

/** Bounded inputs and outcome needed to explain a managed alert decision later. */
@Serializable
data class LocalAlertDecision(
    val outcome: String,
    val reason: String? = null,
    val mode: String? = null,
    val configuredSeverity: String? = null,
    val effectiveSeverity: String? = null,
    val firstDelayMinutes: Int? = null,
    val repeatMinutes: Int? = null,
    val maxRepeats: Int? = null,
    val scheduledAtMillis: Long? = null,
    val deliveredCount: Int? = null,
    val correlationBasis: String? = null,
    val correlationCount: Int? = null,
)

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
    /** Opaque UUID shared by events in one managed alert lifecycle. */
    val alertId: String? = null,
    val alertDecision: LocalAlertDecision? = null,
)

/** Pure codec and retention policy, kept separate so corrupt local data is harmless and testable. */
object LocalEventCodec {
    const val MAX_EVENTS = 500
    const val RETENTION_MILLIS = 90L * 24L * 60L * 60L * 1_000L
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_DETAIL_LENGTH = 240
    private const val MAX_URL_LENGTH = 2_048
    private val alertOutcomes = setOf(
        "RECEIVED",
        "DELAYED",
        "DELIVERED",
        "REPEATED",
        "ACKNOWLEDGED",
        "SNOOZED",
        "SUPPRESSED",
        "CORRELATED",
        "RECOVERED",
    )
    private val alertModes = setOf("MUTED", "DOWN_ONLY", "DOWN_AND_RECOVERY", "ALL_TRANSITIONS")
    private val alertSeverities = setOf("CRITICAL", "STANDARD", "SILENT")
    private val alertReasons = setOf(
        "POLICY",
        "EVENT_PREFERENCE",
        "DUPLICATE",
        "DEPENDENCY",
        "NOTIFICATION_PERMISSION",
        "APP_NOTIFICATIONS_DISABLED",
        "CHANNEL_DISABLED",
    )
    private val correlationBases = setOf("SHARED_PARENT", "SHARED_TAG", "SERVER_BURST")
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
                    alertId = event.alertId?.let(::validUuidOrNull),
                    alertDecision = event.alertDecision?.normalized(),
                )
            }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<LocalEvent> { it.atMillis }.thenBy { it.id })
            .take(MAX_EVENTS)
            .toList()
    }

    private fun validUuidOrNull(value: String): String? =
        runCatching { UUID.fromString(value).toString() }.getOrNull()

    private fun LocalAlertDecision.normalized(): LocalAlertDecision? {
        if (outcome !in alertOutcomes) return null
        if (reason != null && reason !in alertReasons) return null
        if (mode != null && mode !in alertModes) return null
        if (configuredSeverity != null && configuredSeverity !in alertSeverities) return null
        if (effectiveSeverity != null && effectiveSeverity !in alertSeverities) return null
        if (correlationBasis != null && correlationBasis !in correlationBases) return null
        return copy(
            firstDelayMinutes = firstDelayMinutes?.coerceIn(0, 24 * 60),
            repeatMinutes = repeatMinutes?.coerceIn(0, 24 * 60),
            maxRepeats = maxRepeats?.coerceIn(0, 100),
            scheduledAtMillis = scheduledAtMillis?.takeIf { it >= 0L },
            deliveredCount = deliveredCount?.coerceIn(0, 101),
            correlationCount = correlationCount?.coerceIn(0, 10_000),
        )
    }
}

/** Encrypted, bounded device history for successful actions and local alert decisions. */
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
        alertId: String? = null,
        alertDecision: LocalAlertDecision? = null,
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
            alertId = alertId,
            alertDecision = alertDecision,
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
