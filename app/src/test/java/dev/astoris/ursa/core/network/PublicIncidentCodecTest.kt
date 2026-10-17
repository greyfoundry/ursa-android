package dev.astoris.ursa.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicIncidentCodecTest {
    private val target = PublicIncidentTarget(
        serverUrl = "https://kuma.example.com",
        statusPageId = "saved-page-id",
        statusPageSlug = "operations",
    )

    @Test fun validatesCreateAndEditIdentity() {
        val create = draft()

        assertNull(PublicIncidentCodec.validate(create, requireId = false))
        assertEquals(
            "Existing incident cannot be created again",
            PublicIncidentCodec.validate(create.copy(id = 7), requireId = false),
        )
        assertEquals("Incident unavailable", PublicIncidentCodec.validate(create, requireId = true))
        assertEquals(
            "Invalid status page",
            PublicIncidentCodec.validate(
                create.copy(target = target.copy(statusPageSlug = "bad_slug")),
                requireId = false,
            ),
        )
    }

    @Test fun payloadContainsOnlySupportedMutableFields() {
        val payload = PublicIncidentCodec.payload(draft().copy(id = 7, pinned = false))

        assertEquals(setOf("id", "title", "content", "style", "pin"), payload.keys)
        assertEquals("warning", payload["style"]?.jsonPrimitive?.content)
        assertFalse(payload["pin"]!!.jsonPrimitive.boolean)
    }

    @Test fun parsesAcknowledgedIncidentWithTargetIdentity() {
        val raw = Json.parseToJsonElement(
            """{"id":7,"title":"API issue","content":"Investigating","style":"danger","pin":true,"active":true,"createdDate":"2026-09-26 10:00:00.000","lastUpdatedDate":null}""",
        ).jsonObject

        val incident = PublicIncidentCodec.incident(raw, target)!!

        assertEquals(target, incident.target)
        assertEquals(PublicIncidentStyle.DANGER, incident.style)
        assertTrue(incident.pinned)
        assertTrue(incident.active)
        assertEquals(7, incident.toDraft().id)
    }

    @Test fun incompleteAcknowledgementIsNotAcceptedAsAnIncident() {
        assertNull(PublicIncidentCodec.incident(Json.parseToJsonElement("{}").jsonObject, target))
    }

    @Test fun historyDropsMalformedRowsWithoutLosingValidIncidents() {
        val raw = Json.parseToJsonElement(
            """[{"id":7,"title":"API issue","active":true}, {"title":"missing id"}]""",
        ).jsonArray

        assertEquals(listOf(7), PublicIncidentCodec.incidents(raw, target).map(PublicIncident::id))
    }

    @Test fun unknownStyleFallsBackToKumaWarningDefault() {
        val raw = Json.parseToJsonElement("""{"id":3,"style":"future"}""").jsonObject
        assertEquals(PublicIncidentStyle.WARNING, PublicIncidentCodec.incident(raw, target)?.style)
    }

    @Test fun indeterminateResultRequiresRefreshInsteadOfBlindRetry() {
        val result = IncidentMutationResult(IncidentMutationOutcome.INDETERMINATE)

        assertFalse(result.applied)
        assertFalse(result.retrySafe)
    }

    private fun draft() = PublicIncidentDraft(
        target = target,
        title = "API issue",
        content = "Investigating",
    )
}
