package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDependencyPolicyTest {
    @Test
    fun noDependenciesNeverSuppresses() {
        assertNull(PushDependencyPolicy.suppression(PushDependencyGraph(), 1, mapOf(2 to 0)))
    }

    @Test
    fun firstDownParentSuppressesDeterministically() {
        val graph = PushDependencyGraph(mapOf(3 to setOf(2, 1)))

        val result = PushDependencyPolicy.suppression(graph, 3, mapOf(1 to 0, 2 to 0))

        assertEquals(1, result?.parentId)
        assertNull(PushDependencyPolicy.suppression(graph, 3, mapOf(1 to 1, 2 to 3)))
        assertNull(PushDependencyPolicy.suppression(graph, 3, emptyMap()))
    }

    @Test
    fun editingRejectsDirectAndTransitiveCycles() {
        val ids = setOf(1, 2, 3)
        val graph = PushDependencyPolicy.updated(PushDependencyGraph(), 2, setOf(1), ids)
        assertNotNull(graph)
        val chain = PushDependencyPolicy.updated(graph!!, 3, setOf(2), ids)
        assertNotNull(chain)

        assertNull(PushDependencyPolicy.updated(chain!!, 1, setOf(3), ids))
        assertNull(PushDependencyPolicy.updated(chain, 1, setOf(1), ids))
    }

    @Test
    fun evaluationFailsOpenForCyclicOrInvalidGraphs() {
        val cyclic = PushDependencyGraph(mapOf(1 to setOf(2), 2 to setOf(1)))
        val invalid = PushDependencyGraph(mapOf(1 to setOf(-2)))

        assertNull(PushDependencyPolicy.suppression(cyclic, 1, mapOf(2 to 0)))
        assertNull(PushDependencyPolicy.suppression(invalid, 1, mapOf(-2 to 0)))
    }

    @Test
    fun codecRoundTripsStableMultipleParentsAndRejectsCycles() {
        val graph = PushDependencyGraph(mapOf(4 to setOf(3, 1), 3 to setOf(2)))

        val decoded = PushDependencyGraphCodec.decode(PushDependencyGraphCodec.encode(graph))

        assertEquals(setOf(1, 3), decoded?.parentsOf(4))
        assertEquals(setOf(2), decoded?.parentsOf(3))
        assertNull(
            PushDependencyGraphCodec.decode(
                """{"version":1,"dependencies":[{"monitorId":1,"parentIds":[2]},{"monitorId":2,"parentIds":[1]}]}""",
            ),
        )
    }

    @Test
    fun saveValidationRejectsUnknownMonitors() {
        val result = PushDependencyPolicy.updated(
            graph = PushDependencyGraph(),
            monitorId = 1,
            parentIds = setOf(2),
            validMonitorIds = setOf(1),
        )

        assertNull(result)
        assertTrue(PushDependencyPolicy.validated(emptyMap())?.parentsByMonitor?.isEmpty() == true)
    }
}
