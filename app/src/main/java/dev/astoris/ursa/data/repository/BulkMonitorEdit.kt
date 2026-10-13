package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.network.MonitorDraft

data class BulkMonitorEdit(
    val intervalSeconds: Int? = null,
    val retryIntervalSeconds: Int? = null,
    val maxRetries: Int? = null,
) {
    val isValid: Boolean
        get() = listOf(intervalSeconds, retryIntervalSeconds, maxRetries).any { it != null } &&
            (intervalSeconds == null || intervalSeconds >= 1) &&
            (retryIntervalSeconds == null || retryIntervalSeconds >= 1) &&
            (maxRetries == null || maxRetries in 0..100)
}

data class BulkMonitorEditValues(
    val intervalSeconds: Int,
    val retryIntervalSeconds: Int,
    val maxRetries: Int,
)

data class BulkMonitorEditRow(
    val monitorId: Int,
    val monitorName: String,
    val before: BulkMonitorEditValues,
    val after: BulkMonitorEditValues,
)

data class BulkMonitorEditPreview(
    val edit: BulkMonitorEdit,
    val rows: List<BulkMonitorEditRow>,
    val unchangedIds: Set<Int>,
    val failedIds: Set<Int>,
)

data class BulkMonitorEditResult(
    val succeededIds: Set<Int>,
    val failedIds: Set<Int>,
)

internal fun MonitorDraft.previewBulkEdit(edit: BulkMonitorEdit): BulkMonitorEditRow? {
    if (!edit.isValid || id == null) return null
    val before = bulkEditValues()
    val after = BulkMonitorEditValues(
        intervalSeconds = edit.intervalSeconds ?: before.intervalSeconds,
        retryIntervalSeconds = edit.retryIntervalSeconds ?: before.retryIntervalSeconds,
        maxRetries = edit.maxRetries ?: before.maxRetries,
    )
    return BulkMonitorEditRow(id, name, before, after).takeIf { before != after }
}

internal fun MonitorDraft.matchesBulkEdit(values: BulkMonitorEditValues): Boolean = bulkEditValues() == values

internal fun MonitorDraft.applyBulkEdit(values: BulkMonitorEditValues): MonitorDraft = copy(
    intervalSeconds = values.intervalSeconds,
    retryIntervalSeconds = values.retryIntervalSeconds,
    maxRetries = values.maxRetries,
)

private fun MonitorDraft.bulkEditValues() = BulkMonitorEditValues(
    intervalSeconds = intervalSeconds,
    retryIntervalSeconds = retryIntervalSeconds,
    maxRetries = maxRetries,
)
