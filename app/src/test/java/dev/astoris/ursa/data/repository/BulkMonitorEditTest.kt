package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.network.MonitorDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BulkMonitorEditTest {

    @Test
    fun requiresAtLeastOneValidCommonField() {
        assertFalse(BulkMonitorEdit().isValid)
        assertFalse(BulkMonitorEdit(intervalSeconds = 0).isValid)
        assertFalse(BulkMonitorEdit(retryIntervalSeconds = 0).isValid)
        assertFalse(BulkMonitorEdit(maxRetries = 101).isValid)
        assertTrue(BulkMonitorEdit(intervalSeconds = 30, maxRetries = 0).isValid)
    }

    @Test
    fun previewChangesOnlyRequestedFields() {
        val draft = MonitorDraft(
            id = 7,
            name = "API",
            intervalSeconds = 60,
            retryIntervalSeconds = 30,
            maxRetries = 2,
            description = "keep me",
        )

        val row = draft.previewBulkEdit(BulkMonitorEdit(intervalSeconds = 120, maxRetries = 4))!!
        val updated = draft.applyBulkEdit(row.after)

        assertEquals(BulkMonitorEditValues(60, 30, 2), row.before)
        assertEquals(BulkMonitorEditValues(120, 30, 4), row.after)
        assertEquals("keep me", updated.description)
        assertEquals(30, updated.retryIntervalSeconds)
    }

    @Test
    fun unchangedDraftProducesNoPreviewRow() {
        val draft = MonitorDraft(id = 7, intervalSeconds = 60)

        assertNull(draft.previewBulkEdit(BulkMonitorEdit(intervalSeconds = 60)))
    }

    @Test
    fun beforeAndAfterSnapshotsSupportDriftGuardAndRepeat() {
        val draft = MonitorDraft(id = 7, intervalSeconds = 60, retryIntervalSeconds = 30, maxRetries = 2)
        val row = draft.previewBulkEdit(BulkMonitorEdit(intervalSeconds = 120))!!

        assertTrue(draft.matchesBulkEdit(row.before))
        assertFalse(draft.copy(retryIntervalSeconds = 31).matchesBulkEdit(row.before))
        assertTrue(draft.applyBulkEdit(row.after).matchesBulkEdit(row.after))
    }
}
