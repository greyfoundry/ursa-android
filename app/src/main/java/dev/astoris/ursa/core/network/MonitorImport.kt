package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Monitor

enum class MonitorImportStatus {
    READY,
    REVIEW_REQUIRED,
    DUPLICATE,
    UNSUPPORTED,
    INVALID,
}

data class MonitorImportRow(
    val lineNumber: Int,
    val draft: MonitorDraft?,
    val status: MonitorImportStatus,
)

data class MonitorImportPreview(
    val rows: List<MonitorImportRow>,
    val documentError: String? = null,
) {
    val readyRows: List<MonitorImportRow> get() = rows.filter { it.status == MonitorImportStatus.READY }
}

internal fun MonitorImportRow.safeAutomaticDraftOrNull(): MonitorDraft? {
    val candidate = draft ?: return null
    val definition = MonitorEditorRegistry.find(candidate.type) ?: return null
    return candidate.takeIf {
        status == MonitorImportStatus.READY && candidate.isNew && candidate.transferReview && !candidate.active &&
            candidate.notificationIds.isEmpty() && candidate.parentId == null && candidate.tagAssignments.isEmpty() &&
            definition.sensitiveFields.isEmpty() && MonitorDraftCodec.validate(candidate) == null
    }
}

object MonitorImport {
    const val MAX_DOCUMENT_CHARS = 512 * 1024
    const val MAX_ROWS = 500

    private val allowedHeaders = setOf(
        "type",
        "name",
        "endpoint",
        "port",
        "description",
        "interval",
        "retry_interval",
        "resend_interval",
        "max_retries",
    )

    fun preview(
        document: String,
        existing: List<Monitor>,
        compatibility: KumaCompatibility,
    ): MonitorImportPreview {
        if (document.length > MAX_DOCUMENT_CHARS || '\u0000' in document) {
            return MonitorImportPreview(emptyList(), "DOCUMENT_INVALID")
        }
        val delimiter = if (document.lineSequence().firstOrNull().orEmpty().contains('\t')) '\t' else ','
        val parsed = parseDelimited(document, delimiter)
            ?: return MonitorImportPreview(emptyList(), "DOCUMENT_INVALID")
        if (parsed.size < 2 || parsed.size - 1 > MAX_ROWS) {
            return MonitorImportPreview(emptyList(), "ROW_COUNT_INVALID")
        }
        val headers = parsed.first().values.mapIndexed { index, value ->
            value.removePrefix("\uFEFF").trim().lowercase().ifEmpty { "column_$index" }
        }
        if (headers.size != headers.distinct().size || "type" !in headers || "name" !in headers ||
            headers.any { it !in allowedHeaders }
        ) {
            return MonitorImportPreview(emptyList(), "HEADER_INVALID")
        }

        val existingKeys = existing.mapTo(mutableSetOf()) {
            duplicateKey(it.type, it.name, it.url.orEmpty())
        }
        val documentKeys = mutableSetOf<String>()
        val rows = parsed.drop(1).mapNotNull { row ->
            if (row.values.all(String::isBlank)) return@mapNotNull null
            if (row.values.size != headers.size || row.values.any { it.length > MAX_FIELD_CHARS }) {
                return@mapNotNull MonitorImportRow(row.lineNumber, null, MonitorImportStatus.INVALID)
            }
            val values = headers.zip(row.values).toMap()
            val type = values.getValue("type").trim().lowercase()
            val definition = MonitorEditorRegistry.find(type)
            if (definition == null || !definition.createVerifiedFor(compatibility)) {
                return@mapNotNull MonitorImportRow(row.lineNumber, null, MonitorImportStatus.UNSUPPORTED)
            }
            val draft = buildDraft(type, values)
                ?: return@mapNotNull MonitorImportRow(row.lineNumber, null, MonitorImportStatus.INVALID)
            val key = duplicateKey(draft.type, draft.name, draft.endpoint)
            if (key in existingKeys || !documentKeys.add(key)) {
                return@mapNotNull MonitorImportRow(row.lineNumber, draft, MonitorImportStatus.DUPLICATE)
            }
            val needsReview = definition.sensitiveFields.isNotEmpty() || MonitorDraftCodec.validate(draft) != null
            MonitorImportRow(
                lineNumber = row.lineNumber,
                draft = draft,
                status = if (needsReview) MonitorImportStatus.REVIEW_REQUIRED else MonitorImportStatus.READY,
            )
        }
        return MonitorImportPreview(rows)
    }

    private fun buildDraft(type: String, values: Map<String, String>): MonitorDraft? {
        fun optionalInt(name: String): Int? {
            val raw = values[name]?.trim().orEmpty()
            return if (raw.isEmpty()) null else raw.toIntOrNull()
        }
        val port = optionalInt("port")
        val interval = optionalInt("interval")
        val retryInterval = optionalInt("retry_interval")
        val resendInterval = optionalInt("resend_interval")
        val maxRetries = optionalInt("max_retries")
        if (listOf("port", "interval", "retry_interval", "resend_interval", "max_retries").any { name ->
                values[name]?.isNotBlank() == true && optionalInt(name) == null
            }
        ) return null
        val name = values.getValue("name").trim()
        val description = values["description"].orEmpty().trim()
        val endpoint = values["endpoint"].orEmpty().trim()
        if (name.isBlank() || name.length > MAX_NAME_CHARS || description.length > MAX_DESCRIPTION_CHARS ||
            endpoint.length > MAX_ENDPOINT_CHARS
        ) return null
        val base = MonitorDraft.create(type)
        return base.copy(
            transferReview = true,
            name = name,
            description = description,
            endpoint = endpoint,
            port = port ?: base.port,
            intervalSeconds = interval ?: base.intervalSeconds,
            retryIntervalSeconds = retryInterval ?: base.retryIntervalSeconds,
            resendIntervalSeconds = resendInterval ?: base.resendIntervalSeconds,
            maxRetries = maxRetries ?: base.maxRetries,
            active = false,
            notificationIds = emptySet(),
            parentId = null,
            tagAssignments = emptyList(),
        )
    }

    private fun duplicateKey(type: String, name: String, endpoint: String): String =
        "${type.trim().lowercase()}|${name.trim().lowercase()}|${endpoint.trim().lowercase()}"

    private fun parseDelimited(document: String, delimiter: Char): List<ParsedRow>? {
        val rows = mutableListOf<ParsedRow>()
        val values = mutableListOf<String>()
        val field = StringBuilder()
        var line = 1
        var rowLine = 1
        var quoted = false
        var quoteClosed = false
        var index = 0

        fun finishField() {
            values += field.toString()
            field.clear()
            quoteClosed = false
        }
        fun finishRow() {
            finishField()
            rows += ParsedRow(rowLine, values.toList())
            values.clear()
            rowLine = line + 1
        }

        while (index < document.length) {
            val char = document[index]
            when {
                quoted && char == '"' && document.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                quoted && char == '"' -> {
                    quoted = false
                    quoteClosed = true
                }
                quoted -> {
                    field.append(char)
                    if (char == '\n') line++
                }
                quoteClosed && char == delimiter -> finishField()
                quoteClosed && (char == '\r' || char == '\n') -> {
                    if (char == '\r' && document.getOrNull(index + 1) == '\n') index++
                    finishRow()
                    line++
                }
                quoteClosed -> return null
                char == '"' && field.isEmpty() -> quoted = true
                char == '"' -> return null
                char == delimiter -> finishField()
                char == '\r' || char == '\n' -> {
                    if (char == '\r' && document.getOrNull(index + 1) == '\n') index++
                    finishRow()
                    line++
                }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) return null
        if (field.isNotEmpty() || values.isNotEmpty()) finishRow()
        return rows
    }

    private data class ParsedRow(val lineNumber: Int, val values: List<String>)

    private const val MAX_FIELD_CHARS = 4_096
    private const val MAX_NAME_CHARS = 120
    private const val MAX_ENDPOINT_CHARS = 2_048
    private const val MAX_DESCRIPTION_CHARS = 4_096
}
