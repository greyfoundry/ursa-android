package dev.astoris.ursa.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MaintenanceCodecTest {

    @Test
    fun parsesRecurringWeekdayAndKeepsServerTimezoneDetails() {
        val draft = MaintenanceCodec.from(
            Json.parseToJsonElement(
                """{"id":7,"title":"Deploy","description":"Release","strategy":"recurring-weekday","active":true,"status":"scheduled","timezone":"Europe/London","timezoneOption":"SAME_AS_SERVER","dateRange":["2026-09-01T00:00",null],"timeRange":[{"hours":2,"minutes":30},{"hours":3,"minutes":15}],"weekdays":[1,3,5],"daysOfMonth":[],"intervalDay":1,"cron":"30 2 * * 1,3,5","durationMinutes":45}""",
            ).jsonObject,
        )!!

        assertEquals(MaintenanceStrategy.RECURRING_WEEKDAY, draft.strategy)
        assertEquals(setOf(1, 3, 5), draft.weekdays)
        assertEquals("02:30", draft.startTime)
        assertEquals("Europe/London", draft.resolvedTimezone)
        assertEquals("SAME_AS_SERVER", draft.timezoneOption)
    }

    @Test
    fun parsesStringEncodedInactiveFlag() {
        val draft = MaintenanceCodec.from(
            Json.parseToJsonElement(
                """{"id":8,"title":"Paused","strategy":"manual","active":"0","dateRange":[],"timeRange":[],"weekdays":[],"daysOfMonth":[]}""",
            ).jsonObject,
        )!!

        assertFalse(draft.active)
    }

    @Test
    fun buildsExactCronPayloadAndAffectedMonitorObjects() {
        val draft = MaintenanceDraft.create().copy(
            title = "Database work",
            strategy = MaintenanceStrategy.CRON,
            cron = "30 3 * * *",
            durationMinutes = 90,
            timezoneOption = "UTC",
            monitorIds = setOf(9, 4),
        )

        val payload = MaintenanceCodec.payload(draft)

        assertEquals("cron", payload["strategy"]!!.jsonPrimitive.content)
        assertEquals(90, payload["durationMinutes"]!!.jsonPrimitive.content.toInt())
        assertEquals("UTC", payload["timezoneOption"]!!.jsonPrimitive.content)
        assertEquals(listOf(4, 9), MaintenanceCodec.monitorRelationPayload(draft.monitorIds).map {
            it.jsonObject["id"]!!.jsonPrimitive.content.toInt()
        })
    }

    @Test
    fun validatesOnlyFieldsRequiredByEachStrategy() {
        assertEquals(MaintenanceDraftError.TITLE_REQUIRED, MaintenanceCodec.validate(MaintenanceDraft.create()))
        assertEquals(
            MaintenanceDraftError.DATE_RANGE_REQUIRED,
            MaintenanceCodec.validate(MaintenanceDraft.create().copy(title = "Window")),
        )
        assertNull(
            MaintenanceCodec.validate(
                MaintenanceDraft.create().copy(
                    title = "Window",
                    strategy = MaintenanceStrategy.MANUAL,
                ),
            ),
        )
        assertEquals(
            MaintenanceDraftError.WEEKDAYS_REQUIRED,
            MaintenanceCodec.validate(
                MaintenanceDraft.create().copy(
                    title = "Weekly",
                    strategy = MaintenanceStrategy.RECURRING_WEEKDAY,
                ),
            ),
        )
    }
}
