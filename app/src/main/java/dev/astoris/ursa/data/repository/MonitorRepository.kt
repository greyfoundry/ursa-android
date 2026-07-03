package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.KumaClient
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for the UI. Owns the active [KumaClient], bridges it to the
 * persisted [ConnectionStore], and exposes monitors/state as StateFlows that follow
 * whichever server is active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorRepository(
    private val store: ConnectionStore,
    scope: CoroutineScope,
) {
    private val activeClient = MutableStateFlow<KumaClient?>(null)

    val connections: Flow<List<ServerConnection>> = store.connections
    val activeUrl: Flow<String?> = store.activeUrl

    val monitors: StateFlow<List<Monitor>> = activeClient
        .flatMapLatest { client -> client?.monitors ?: flowOf(emptyMap()) }
        .map { map -> map.values.sortedBy { it.name.lowercase() } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ConnectionState> = activeClient
        .flatMapLatest { client -> client?.state ?: flowOf(ConnectionState.Disconnected) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected)

    /** Connect to a new server and log in; on success the JWT is persisted. */
    suspend fun addServerAndLogin(
        url: String,
        username: String,
        password: String,
        token: String = "",
    ): LoginResult {
        val client = connectFresh(url)
        val result = client.login(username, password, token)
        if (result is LoginResult.Success) {
            store.upsert(ServerConnection(url, username, result.jwt))
        }
        return result
    }

    /** Switch to an already-configured server, reusing its stored JWT. */
    suspend fun switchTo(conn: ServerConnection) {
        val client = connectFresh(conn.url)
        conn.jwt?.let { client.loginByToken(it) }
        store.setActive(conn.url)
    }

    suspend fun pause(id: Int): Boolean = activeClient.value?.pauseMonitor(id) ?: false
    suspend fun resume(id: Int): Boolean = activeClient.value?.resumeMonitor(id) ?: false

    fun disconnect() {
        activeClient.value?.disconnect()
        activeClient.value = null
    }

    private fun connectFresh(url: String): KumaClient {
        activeClient.value?.disconnect()
        val client = KumaClient(url)
        activeClient.value = client
        client.connect()
        return client
    }
}
