package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorImportTest {
    private val compatibility = KumaCapabilities.evaluate("2.5.5")

    @Test
    fun csvDryRunParsesQuotedFieldsAndKeepsNewMonitorInactive() {
        val preview = MonitorImport.preview(
            "type,name,endpoint,description,interval\r\nhttp,Site,https://example.com,\"Primary, public\",30",
            emptyList(),
            compatibility,
        )

        assertNull(preview.documentError)
        val row = preview.rows.single()
        assertEquals(MonitorImportStatus.READY, row.status)
        assertEquals("Primary, public", row.draft?.description)
        assertEquals(30, row.draft?.intervalSeconds)
        assertFalse(row.draft!!.active)
        assertTrue(row.draft.transferReview)
        assertTrue(row.draft.notificationIds.isEmpty())
    }

    @Test
    fun tabDelimitedTextAndEscapedQuotesAreSupported() {
        val preview = MonitorImport.preview(
            "type\tname\tendpoint\tdescription\nhttp\tSite\thttps://example.com\t\"Says \"\"hello\"\"\"",
            emptyList(),
            compatibility,
        )

        assertEquals("Says \"hello\"", preview.rows.single().draft?.description)
    }

    @Test
    fun dryRunFindsExistingAndInDocumentDuplicates() {
        val existing = listOf(monitor("Site", "https://example.com"))
        val preview = MonitorImport.preview(
            "type,name,endpoint\nhttp,Site,https://example.com\nhttp,Other,https://other.example\nhttp,Other,https://other.example",
            existing,
            compatibility,
        )

        assertEquals(
            listOf(MonitorImportStatus.DUPLICATE, MonitorImportStatus.READY, MonitorImportStatus.DUPLICATE),
            preview.rows.map(MonitorImportRow::status),
        )
    }

    @Test
    fun secretAndIncompleteTypesRequireExplicitEditorReview() {
        val preview = MonitorImport.preview(
            "type,name,endpoint\nmqtt,Broker,mqtt://broker.example\nkeyword,Homepage,https://example.com",
            emptyList(),
            compatibility,
        )

        assertTrue(preview.rows.all { it.status == MonitorImportStatus.REVIEW_REQUIRED })
        assertTrue(preview.rows.all { it.draft?.active == false })
    }

    @Test
    fun missingRequiredNameIsInvalidInsteadOfReviewable() {
        val preview = MonitorImport.preview(
            "type,name,endpoint\nhttp,,https://example.com",
            emptyList(),
            compatibility,
        )

        assertEquals(MonitorImportStatus.INVALID, preview.rows.single().status)
        assertNull(preview.rows.single().draft)
    }

    @Test
    fun malformedHeadersNumbersAndQuotesFailClosed() {
        assertEquals(
            "HEADER_INVALID",
            MonitorImport.preview("type,name,password\nhttp,Site,secret", emptyList(), compatibility).documentError,
        )
        assertEquals(
            MonitorImportStatus.INVALID,
            MonitorImport.preview("type,name,endpoint,interval\nhttp,Site,https://example.com,nope", emptyList(), compatibility)
                .rows.single().status,
        )
        assertEquals(
            "DOCUMENT_INVALID",
            MonitorImport.preview("type,name\nhttp,\"Site", emptyList(), compatibility).documentError,
        )
    }

    @Test
    fun documentAndRowLimitsFailClosed() {
        assertEquals(
            "DOCUMENT_INVALID",
            MonitorImport.preview("x".repeat(MonitorImport.MAX_DOCUMENT_CHARS + 1), emptyList(), compatibility).documentError,
        )
        val rows = buildString {
            appendLine("type,name")
            repeat(MonitorImport.MAX_ROWS + 1) { appendLine("manual,Monitor $it") }
        }
        assertEquals("ROW_COUNT_INVALID", MonitorImport.preview(rows, emptyList(), compatibility).documentError)
    }

    @Test
    fun automaticCreateBoundaryRejectsSecretsAssignmentsAndActiveDrafts() {
        val safe = MonitorImport.preview(
            "type,name,endpoint\nhttp,Site,https://example.com",
            emptyList(),
            compatibility,
        ).rows.single()
        val draft = requireNotNull(safe.draft)

        assertTrue(safe.safeAutomaticDraftOrNull() != null)
        assertNull(safe.copy(draft = draft.copy(active = true)).safeAutomaticDraftOrNull())
        assertNull(safe.copy(draft = draft.copy(notificationIds = setOf(3))).safeAutomaticDraftOrNull())
        assertNull(
            MonitorImportRow(
                2,
                MonitorDraft.create("mqtt").copy(name = "Broker", endpoint = "mqtt://broker", transferReview = true),
                MonitorImportStatus.READY,
            ).safeAutomaticDraftOrNull(),
        )
    }

    private fun monitor(name: String, url: String) = Monitor(
        id = 1,
        name = name,
        url = url,
        type = "http",
        active = true,
        status = MonitorStatus.UP,
    )
}
