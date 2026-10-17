package dev.astoris.ursa.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorGroupPlanTest {
    @Test fun planTrimsNameAndOrdersUniquePositiveChildren() {
        val plan = MonitorGroupPlan.create("  Production  ", setOf(3, 1, 2))!!

        assertEquals("Production", plan.name)
        assertEquals(listOf(1, 2, 3), plan.childIds.toList())
    }

    @Test fun planAllowsEmptyGroupForCreatingChildrenNext() {
        assertTrue(MonitorGroupPlan.create("New group", emptySet())?.childIds?.isEmpty() == true)
    }

    @Test fun planRejectsBlankInvalidAndUnboundedInput() {
        assertNull(MonitorGroupPlan.create("   ", emptySet()))
        assertNull(MonitorGroupPlan.create("Group", setOf(0, 1)))
        assertNull(MonitorGroupPlan.create("Group", (1..1_001).toSet()))
    }
}
