package dev.astoris.ursa.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PublicIncidentTarget(
    val serverUrl: String,
    val statusPageId: String,
    val statusPageSlug: String,
)

enum class PublicIncidentStyle(val wireValue: String) {
    INFO("info"),
    WARNING("warning"),
    DANGER("danger"),
    PRIMARY("primary"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromWire(value: String?): PublicIncidentStyle =
            entries.firstOrNull { it.wireValue == value } ?: WARNING
    }
}

data class PublicIncidentDraft(
    val target: PublicIncidentTarget,
    val id: Int? = null,
    val title: String,
    val content: String,
    val style: PublicIncidentStyle = PublicIncidentStyle.WARNING,
    val pinned: Boolean = true,
)

data class PublicIncident(
    val target: PublicIncidentTarget,
    val id: Int,
    val title: String,
    val content: String,
    val style: PublicIncidentStyle,
    val pinned: Boolean,
    val active: Boolean,
    val createdDate: String,
    val lastUpdatedDate: String?,
) {
    fun toDraft(): PublicIncidentDraft = PublicIncidentDraft(
        target = target,
        id = id,
        title = title,
        content = content,
        style = style,
        pinned = pinned,
    )
}

data class PublicIncidentHistory(
    val incidents: List<PublicIncident>,
    val total: Int,
    val hasMore: Boolean,
)

enum class IncidentMutationOutcome {
    APPLIED,
    REJECTED,
    INDETERMINATE,
}

data class IncidentMutationResult(
    val outcome: IncidentMutationOutcome,
    val incident: PublicIncident? = null,
    val message: String? = null,
) {
    val applied: Boolean get() = outcome == IncidentMutationOutcome.APPLIED
    val retrySafe: Boolean get() = outcome == IncidentMutationOutcome.REJECTED
}

object PublicIncidentCodec {
    fun validate(draft: PublicIncidentDraft, requireId: Boolean): String? = when {
        draft.target.serverUrl.isBlank() || draft.target.statusPageId.isBlank() -> "Status page unavailable"
        !StatusPageAddress.isValidSlug(draft.target.statusPageSlug) -> "Invalid status page"
        requireId && (draft.id ?: 0) <= 0 -> "Incident unavailable"
        !requireId && draft.id != null -> "Existing incident cannot be created again"
        draft.title.isBlank() -> "Please input title"
        draft.content.isBlank() -> "Please input content"
        else -> null
    }

    fun payload(draft: PublicIncidentDraft): JsonObject = JsonObject(
        buildMap {
            draft.id?.let { put("id", JsonPrimitive(it)) }
            put("title", JsonPrimitive(draft.title))
            put("content", JsonPrimitive(draft.content))
            put("style", JsonPrimitive(draft.style.wireValue))
            put("pin", JsonPrimitive(draft.pinned))
        },
    )

    fun incident(raw: JsonObject, target: PublicIncidentTarget): PublicIncident? {
        val id = raw["id"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return null
        return PublicIncident(
            target = target,
            id = id,
            title = raw.string("title").orEmpty(),
            content = raw.string("content").orEmpty(),
            style = PublicIncidentStyle.fromWire(raw.string("style")),
            pinned = raw["pin"]?.jsonPrimitive?.booleanOrNull ?: false,
            active = raw["active"]?.jsonPrimitive?.booleanOrNull ?: false,
            createdDate = raw.string("createdDate").orEmpty(),
            lastUpdatedDate = raw.string("lastUpdatedDate"),
        )
    }

    fun incidents(raw: JsonArray, target: PublicIncidentTarget): List<PublicIncident> =
        raw.mapNotNull { (it as? JsonObject)?.let { row -> incident(row, target) } }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
