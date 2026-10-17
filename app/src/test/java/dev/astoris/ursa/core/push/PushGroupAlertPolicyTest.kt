package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushGroupAlertPolicyTest {
    @Test fun thresholdSuppressesUntilEnoughConfiguredMembersAreDown() {
        val rules = PushGroupAlertRules(
            mapOf(10 to PushGroupAlertRule(10, setOf(1, 2, 3), requiredDown = 2)),
        )

        val oneDown = PushGroupAlertPolicy.evaluate(rules, 1, mapOf(1 to 0, 2 to 1, 3 to null))!!
        val twoDown = PushGroupAlertPolicy.evaluate(rules, 2, mapOf(1 to 0, 2 to 0, 3 to 1))!!

        assertTrue(oneDown.suppressed)
        assertEquals(1, oneDown.downCount)
        assertFalse(twoDown.suppressed)
        assertEquals(2, twoDown.downCount)
        assertNull(PushGroupAlertPolicy.evaluate(rules, 4, mapOf(1 to 0, 2 to 0)))
    }

    @Test fun invalidThresholdsOverlapsAndCorruptCodecsFailOpen() {
        assertNull(
            PushGroupAlertPolicy.validated(
                mapOf(10 to PushGroupAlertRule(10, setOf(1, 2), requiredDown = 1)),
            ),
        )
        assertNull(
            PushGroupAlertPolicy.validated(
                mapOf(
                    10 to PushGroupAlertRule(10, setOf(1, 2), requiredDown = 2),
                    20 to PushGroupAlertRule(20, setOf(2, 3), requiredDown = 2),
                ),
            ),
        )
        assertNull(
            PushGroupAlertRulesCodec.decode(
                """{"version":1,"rules":[{"groupId":10,"memberIds":[1,2],"requiredDown":3}]}""",
            ),
        )
    }

    @Test fun codecIsStableAndReconciliationTracksCurrentManagedChildren() {
        val original = PushGroupAlertRules(
            mapOf(10 to PushGroupAlertRule(10, setOf(3, 1, 2), requiredDown = 2)),
        )
        val decoded = PushGroupAlertRulesCodec.decode(PushGroupAlertRulesCodec.encode(original))!!

        assertEquals(setOf(1, 2, 3), decoded.byGroupId.getValue(10).memberIds)
        val reconciled = PushGroupAlertPolicy.reconciled(decoded, mapOf(10 to setOf(1, 3, 4, 5)))
        assertEquals(setOf(1, 3, 4, 5), reconciled.byGroupId.getValue(10).memberIds)
        assertEquals(2, reconciled.byGroupId.getValue(10).requiredDown)
        assertTrue(PushGroupAlertPolicy.reconciled(decoded, mapOf(10 to setOf(1))).byGroupId.isEmpty())
    }

    @Test fun updateAddsChangesAndRemovesOneGroupRule() {
        val added = PushGroupAlertPolicy.updated(PushGroupAlertRules(), 10, setOf(1, 2, 3), 2)!!
        val changed = PushGroupAlertPolicy.updated(added, 10, setOf(1, 2, 3), 3)!!
        val removed = PushGroupAlertPolicy.updated(changed, 10, setOf(1, 2, 3), null)!!

        assertEquals(2, added.byGroupId.getValue(10).requiredDown)
        assertEquals(3, changed.byGroupId.getValue(10).requiredDown)
        assertTrue(removed.byGroupId.isEmpty())
    }
}
