package dev.astoris.ursa.core.access

import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.data.model.ServerConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationExecutorTest {

    @Test fun no_active_connection_does_not_run_remote_block() = runSuspend {
        var invoked = false
        val execution = MutationExecutor { null }.execute(
            setOf(AccessCapability.MONITOR_STATE),
        ) {
            invoked = true
        }

        assertSame(MutationExecution.NoActiveConnection, execution)
        assertFalse(invoked)
    }

    @Test fun denied_capability_does_not_run_remote_block() = runSuspend {
        var invoked = false
        val execution = MutationExecutor { connection(AccessProfile.VIEW_ONLY) }.execute(
            setOf(AccessCapability.MONITOR_DELETE),
        ) {
            invoked = true
        }

        assertEquals(
            MutationExecution.Denied(
                AccessDecision.Denied(
                    AccessProfile.VIEW_ONLY,
                    setOf(AccessCapability.MONITOR_DELETE),
                ),
            ),
            execution,
        )
        assertFalse(invoked)
    }

    @Test fun allowed_block_runs_once_with_the_evaluated_connection() = runSuspend {
        val connection = connection(
            AccessProfile.CUSTOM,
            setOf(AccessCapability.MONITOR_STATE),
        )
        var calls = 0
        val execution = MutationExecutor { connection }.execute(
            setOf(AccessCapability.MONITOR_STATE),
        ) { evaluated ->
            calls++
            evaluated.url
        }

        assertEquals(MutationExecution.Completed(connection.url), execution)
        assertEquals(1, calls)
    }

    @Test fun completed_false_is_not_confused_with_policy_denial() = runSuspend {
        val execution = MutationExecutor { connection(AccessProfile.MANAGE) }.execute(
            setOf(AccessCapability.PUSH_SETUP),
        ) { false }

        assertEquals(MutationExecution.Completed(false), execution)
        assertTrue(execution is MutationExecution.Completed)
    }

    private fun connection(
        profile: AccessProfile,
        custom: Set<AccessCapability> = emptySet(),
    ) = ServerConnection(
        url = "https://kuma.example.test",
        username = "operator",
        accessProfile = profile,
        customCapabilities = custom,
    )

    private fun runSuspend(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
