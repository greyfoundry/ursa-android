package dev.astoris.ursa.data.repository

import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.KumaClient
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for the UI. Owns the active [KumaClient], bridges it to the
 * persisted [ConnectionStore], and exposes monitors/state as StateFlows that follow
 * whichever server is active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorRepository(
    private val store: ConnectionStore,
    private val cache: MonitorCacheStore,
    private val scope: CoroutineScope,
) {
    private val activeClient = MutableStateFlow<KumaClient?>(null)
    private var activeUrlValue: String? = null

    val connections: Flow<List<ServerConnection>> = store.connections
    val activeUrl: Flow<String?> = store.activeUrl

    /** Live monitors from the active socket (empty until the server delivers a list). */
    private val liveMonitors: StateFlow<List<Monitor>> = activeClient
        .flatMapLatest { client -> client?.monitors ?: flowOf(emptyMap()) }
        .map { map -> map.values.sortedBy { it.name.lowercase() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Last-known list loaded from the encrypted cache for the active server.
    private val cachedMonitors = MutableStateFlow<List<Monitor>>(emptyList())
    private val lastUpdatedMs = MutableStateFlow<Long?>(null)

    /** What the UI shows: live data when available, otherwise the cached snapshot. */
    val monitors: StateFlow<List<Monitor>> =
        combine(liveMonitors, cachedMonitors) { live, cached -> live.ifEmpty { cached } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** When the currently shown list came from the cache, its capture time (epoch ms). */
    val lastUpdated: StateFlow<Long?> = lastUpdatedMs

    /** True while showing cached data because live monitors have not arrived yet. */
    val showingCache: StateFlow<Boolean> =
        combine(liveMonitors, cachedMonitors) { live, cached -> live.isEmpty() && cached.isNotEmpty() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Persist every live update as the new snapshot for the active server, and
        // promote it into the cache flow so it survives the next reconnect.
        scope.launch {
            liveMonitors.collect { live ->
                val url = activeUrlValue
                if (live.isNotEmpty() && url != null) {
                    val now = System.currentTimeMillis()
                    cache.save(url, MonitorSnapshot(live, now))
                    cachedMonitors.value = live
                    lastUpdatedMs.value = now
                }
            }
        }
    }

    val state: StateFlow<ConnectionState> = activeClient
        .flatMapLatest { client -> client?.state ?: flowOf(ConnectionState.Disconnected) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Disconnected)

    val certs: StateFlow<Map<Int, CertInfo>> = activeClient
        .flatMapLatest { client -> client?.certs ?: flowOf(emptyMap()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    suspend fun beats(id: Int): List<Heartbeat> = activeClient.value?.getBeats(id) ?: emptyList()

    /** Connect to a new server and log in; on success the JWT is persisted. */
    suspend fun addServerAndLogin(
        url: String,
        username: String,
        password: String,
        token: String = "",
        insecure: Boolean = false,
    ): LoginResult {
        val client = connectFresh(url, insecure)
        val result = client.login(username, password, token)
        if (result is LoginResult.Success) {
            store.upsert(ServerConnection(url, username, result.jwt, insecure))
        }
        return result
    }

    /** Switch to an already-configured server, reusing its stored JWT. */
    suspend fun switchTo(conn: ServerConnection) {
        val client = connectFresh(conn.url, conn.insecure)
        conn.jwt?.let { client.loginByToken(it) }
        store.setActive(conn.url)
    }

    suspend fun pause(id: Int): Boolean = activeClient.value?.pauseMonitor(id) ?: false
    suspend fun resume(id: Int): Boolean = activeClient.value?.resumeMonitor(id) ?: false

    fun disconnect() {
        activeClient.value?.disconnect()
        activeClient.value = null
        activeUrlValue = null
        cachedMonitors.value = emptyList()
        lastUpdatedMs.value = null
    }

    private fun connectFresh(url: String, insecure: Boolean): KumaClient {
        activeClient.value?.disconnect()
        activeUrlValue = url
        // Show this server's last-known list immediately while the socket reconnects.
        cachedMonitors.value = emptyList()
        lastUpdatedMs.value = null
        scope.launch {
            cache.load(url)?.let { snap ->
                cachedMonitors.value = snap.monitors
                lastUpdatedMs.value = snap.updatedAt
            }
        }
        val client = KumaClient(url, insecure)
        activeClient.value = client
        client.connect()
        return client
    }
}
