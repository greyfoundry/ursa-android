package dev.astoris.ursa.core.access

import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.ServerConnection

sealed interface MutationExecution<out T> {
    data class Completed<T>(val value: T) : MutationExecution<T>
    data class Denied(val decision: AccessDecision.Denied) : MutationExecution<Nothing>
    data object NoActiveConnection : MutationExecution<Nothing>
}

/** Applies connection policy before a remote mutation block can run. */
class MutationExecutor(
    private val activeConnection: suspend () -> ServerConnection?,
) {
    suspend fun <T> execute(
        required: Set<AccessCapability>,
        block: suspend (ServerConnection) -> T,
    ): MutationExecution<T> {
        val connection = activeConnection() ?: return MutationExecution.NoActiveConnection
        return when (val decision = AccessPolicy.evaluate(
            profile = connection.accessProfile,
            customCapabilities = connection.customCapabilities,
            required = required,
        )) {
            AccessDecision.Allowed -> MutationExecution.Completed(block(connection))
            is AccessDecision.Denied -> MutationExecution.Denied(decision)
        }
    }
}
