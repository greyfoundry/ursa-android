package dev.astoris.ursa.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class MaintenanceStrategy(val wireValue: String) {
    MANUAL("manual"),
    SINGLE("single"),
    CRON("cron"),
    RECURRING_INTERVAL("recurring-interval"),
    RECURRING_WEEKDAY("recurring-weekday"),
    RECURRING_DAY_OF_MONTH("recurring-day-of-month");

    companion object {
        fun from(value: String?): MaintenanceStrategy? = entries.firstOrNull { it.wireValue == value }
    }
}

data class MaintenanceDraft(
    val id: Int? = null,
    val title: String = "",
    val description: String = "",
    val strategy: MaintenanceStrategy = MaintenanceStrategy.SINGLE,
    val active: Boolean = true,
    val status: String = "unknown",
    val resolvedTimezone: String = "",
    val timezoneOption: String = "SAME_AS_SERVER",
    val startDate: String = "",
    val endDate: String = "",
    val startTime: String = "02:00",
    val endTime: String = "03:00",
    val intervalDay: Int = 1,
    val weekdays: Set<Int> = emptySet(),
    val daysOfMonth: Set<String> = emptySet(),
    val cron: String = "30 3 * * *",
    val durationMinutes: Int = 60,
    val monitorIds: Set<Int> = emptySet(),
) {
    val isNew: Boolean get() = id == null

    companion object {
        fun create() = MaintenanceDraft()
    }
}

enum class MaintenanceDraftError {
    TITLE_REQUIRED,
    DATE_RANGE_REQUIRED,
    CRON_REQUIRED,
    DURATION_REQUIRED,
    INTERVAL_REQUIRED,
    WEEKDAYS_REQUIRED,
    DAYS_OF_MONTH_REQUIRED,
    TIME_RANGE_REQUIRED,
}

object MaintenanceCodec {
    fun list(raw: JsonObject): Map<Int, MaintenanceDraft> = buildMap {
        raw.values.forEach { value ->
            (value as? JsonObject)?.let(::from)?.let { draft -> draft.id?.let { put(it, draft) } }
        }
    }

    fun from(raw: JsonObject): MaintenanceDraft? {
        val strategy = MaintenanceStrategy.from(raw.string("strategy")) ?: return null
        return MaintenanceDraft(
            id = raw.int("id")?.takeIf { it > 0 },
            title = raw.string("title").orEmpty(),
            description = raw.string("description").orEmpty(),
            strategy = strategy,
            active = raw.boolean("active", true),
            status = raw.string("status").orEmpty().ifBlank { "unknown" },
            resolvedTimezone = raw.string("timezone").orEmpty(),
            timezoneOption = raw.string("timezoneOption").orEmpty().ifBlank { "SAME_AS_SERVER" },
            startDate = raw.array("dateRange").stringAt(0),
            endDate = raw.array("dateRange").stringAt(1),
            startTime = raw.array("timeRange").timeAt(0, "02:00"),
            endTime = raw.array("timeRange").timeAt(1, "03:00"),
            intervalDay = raw.int("intervalDay") ?: 1,
            weekdays = raw.array("weekdays").mapNotNull { it.jsonPrimitive.intOrNull }.toSet(),
            daysOfMonth = raw.array("daysOfMonth").mapNotNull { it.jsonPrimitive.contentOrNull }.toSet(),
            cron = raw.string("cron").orEmpty(),
            durationMinutes = raw.int("durationMinutes") ?: 60,
        )
    }

    fun validate(draft: MaintenanceDraft): MaintenanceDraftError? {
        if (draft.title.trim().isEmpty()) return MaintenanceDraftError.TITLE_REQUIRED
        when (draft.strategy) {
            MaintenanceStrategy.MANUAL -> Unit
            MaintenanceStrategy.SINGLE -> if (draft.startDate.isBlank() || draft.endDate.isBlank()) {
                return MaintenanceDraftError.DATE_RANGE_REQUIRED
            }
            MaintenanceStrategy.CRON -> {
                if (draft.cron.trim().isEmpty()) return MaintenanceDraftError.CRON_REQUIRED
                if (draft.durationMinutes < 1) return MaintenanceDraftError.DURATION_REQUIRED
            }
            MaintenanceStrategy.RECURRING_INTERVAL -> {
                if (draft.intervalDay !in 1..3650) return MaintenanceDraftError.INTERVAL_REQUIRED
                if (!validTimeRange(draft)) return MaintenanceDraftError.TIME_RANGE_REQUIRED
            }
            MaintenanceStrategy.RECURRING_WEEKDAY -> {
                if (draft.weekdays.isEmpty()) return MaintenanceDraftError.WEEKDAYS_REQUIRED
                if (!validTimeRange(draft)) return MaintenanceDraftError.TIME_RANGE_REQUIRED
            }
            MaintenanceStrategy.RECURRING_DAY_OF_MONTH -> {
                if (draft.daysOfMonth.isEmpty()) return MaintenanceDraftError.DAYS_OF_MONTH_REQUIRED
                if (!validTimeRange(draft)) return MaintenanceDraftError.TIME_RANGE_REQUIRED
            }
        }
        return null
    }

    fun payload(draft: MaintenanceDraft): JsonObject = buildJsonObject {
        draft.id?.let { put("id", it) }
        put("title", draft.title.trim())
        put("description", draft.description.trim())
        put("strategy", draft.strategy.wireValue)
        put("active", draft.active)
        put("timezoneOption", draft.timezoneOption)
        put("intervalDay", draft.intervalDay)
        put("cron", draft.cron.trim())
        put("durationMinutes", draft.durationMinutes)
        put("dateRange", JsonArray(listOf(nullableString(draft.startDate), nullableString(draft.endDate))))
        put("timeRange", JsonArray(listOf(timeObject(draft.startTime), timeObject(draft.endTime))))
        put("weekdays", JsonArray(draft.weekdays.sorted().map(::JsonPrimitive)))
        put("daysOfMonth", JsonArray(draft.daysOfMonth.sorted().map(::JsonPrimitive)))
    }

    fun monitorRelationPayload(ids: Set<Int>): JsonArray = JsonArray(
        ids.filter { it > 0 }.sorted().map { id -> buildJsonObject { put("id", id) } },
    )

    private fun validTimeRange(draft: MaintenanceDraft): Boolean =
        TIME.matches(draft.startTime) && TIME.matches(draft.endTime)

    private fun timeObject(value: String): JsonObject {
        val parts = value.split(':')
        return buildJsonObject {
            put("hours", parts.getOrNull(0)?.toIntOrNull() ?: 0)
            put("minutes", parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }
    }

    private fun nullableString(value: String): JsonElement = value.takeIf(String::isNotBlank)
        ?.let(::JsonPrimitive) ?: JsonNull
    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.boolean(key: String, fallback: Boolean): Boolean {
        val primitive = this[key]?.jsonPrimitive ?: return fallback
        primitive.booleanOrNull?.let { return it }
        primitive.intOrNull?.let { return it != 0 }
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> fallback
        }
    }
    private fun JsonObject.array(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonArray.stringAt(index: Int): String = getOrNull(index)?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonArray.timeAt(index: Int, fallback: String): String {
        val obj = getOrNull(index) as? JsonObject ?: return fallback
        val hours = obj["hours"]?.jsonPrimitive?.intOrNull ?: return fallback
        val minutes = obj["minutes"]?.jsonPrimitive?.intOrNull ?: return fallback
        return "%02d:%02d".format(hours, minutes)
    }

    private val TIME = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
}
