package dev.astoris.ursa.ui.incidents

import dev.astoris.ursa.core.storage.LocalEvent
import dev.astoris.ursa.core.storage.LocalEventKind
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAlertGroupsTest {

    @Test
    fun groupsOneAlertLifecycleAndOrdersTiedEventsDeterministically() {
        val alertId = "50ff702d-2e16-48c8-9861-aeab6a7aa08d"
        val groups = localAlertGroups(
            listOf(
                event("delivered", alertId, LocalEventKind.PUSH_ALERT, 200),
                event("received", alertId, LocalEventKind.PUSH_RECEIVED, 200),
                event("recovered", alertId, LocalEventKind.PUSH_RECOVERED, 300),
                event("legacy", null, LocalEventKind.PUSH_ALERT, 400),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(LocalAlertGroupState.RECOVERED, groups.single().state)
        assertEquals(
            listOf(LocalEventKind.PUSH_RECEIVED, LocalEventKind.PUSH_ALERT, LocalEventKind.PUSH_RECOVERED),
            groups.single().events.map(LocalEvent::kind),
        )
    }

    @Test
    fun newestLifecycleIsFirstAndCorrelationDoesNotReplaceDeliveryState() {
        val older = "50ff702d-2e16-48c8-9861-aeab6a7aa08d"
        val newer = "a5c977d2-7c4e-4101-b4e8-99b0d6f0ca86"

        val groups = localAlertGroups(
            listOf(
                event("old", older, LocalEventKind.PUSH_ACKNOWLEDGED, 100),
                event("new", newer, LocalEventKind.PUSH_ALERT, 300),
                event("correlated", newer, LocalEventKind.PUSH_CORRELATED, 301),
            ),
        )

        assertEquals(listOf(newer, older), groups.map(LocalAlertGroup::id))
        assertEquals(LocalAlertGroupState.ACTIVE, groups.first().state)
        assertEquals(LocalAlertGroupState.ACKNOWLEDGED, groups.last().state)
    }

    private fun event(
        id: String,
        alertId: String?,
        kind: LocalEventKind,
        atMillis: Long,
    ) = LocalEvent(
        id = id,
        monitorId = 7,
        monitorName = "API",
        kind = kind,
        atMillis = atMillis,
        alertId = alertId,
    )
}
