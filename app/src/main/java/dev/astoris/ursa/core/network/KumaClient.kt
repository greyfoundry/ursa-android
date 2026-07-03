package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

enum class ConnectionState { Disconnected, Connecting, Connected, Authenticated, Error }

/**
 * Wraps a single Socket.IO connection to one Uptime Kuma server and normalizes the
 * (internal, unstable) wire protocol into domain models. This is the ONLY place that
 * knows Kuma's quirks — see docs/references/uptime-kuma-api.mdx.
 *
 * Kuma sends several events as positional args rather than objects; that is handled
 * explicitly below.
 */
class KumaClient(private val baseUrl: String) {

    private var socket: Socket? = null

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _monitors = MutableStateFlow<Map<Int, Monitor>>(emptyMap())
    val monitors: StateFlow<Map<Int, Monitor>> = _monitors.asStateFlow()

    private val _heartbeats = MutableSharedFlow<Heartbeat>(extraBufferCapacity = 64)
    val heartbeats: SharedFlow<Heartbeat> = _heartbeats.asSharedFlow()

    fun connect() {
        if (socket != null) return
        _state.value = ConnectionState.Connecting
        val opts = IO.Options().apply {
            transports = arrayOf("polling", "websocket") // polling first, then upgrade
            reconnection = true
        }
        val s = try {
            IO.socket(baseUrl, opts)
        } catch (e: Exception) {
            _state.value = ConnectionState.Error
            return
        }
        socket = s
        wireEvents(s)
        s.connect()
    }

    private fun wireEvents(s: Socket) {
        s.on(Socket.EVENT_CONNECT) { _ -> _state.value = ConnectionState.Connected }
        s.on(Socket.EVENT_CONNECT_ERROR) { _ -> _state.value = ConnectionState.Error }
        s.on(Socket.EVENT_DISCONNECT) { _ -> _state.value = ConnectionState.Disconnected }

        // Full snapshot: { "<id>": {monitor}, ... }
        s.on("monitorList") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { obj ->
                _monitors.value = buildMap {
                    obj.keys().forEach { key ->
                        obj.optJSONObject(key)?.let { m -> parseMonitor(m)?.let { put(it.id, it) } }
                    }
                }
            }
        }
        s.on("updateMonitorIntoList") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { obj ->
                obj.keys().forEach { key ->
                    obj.optJSONObject(key)?.let { m -> parseMonitor(m)?.let { mon -> updateMonitor(mon.id) { mon } } }
                }
            }
        }
        s.on("deleteMonitorFromList") { args ->
            (args.getOrNull(0) as? Number)?.toInt()?.let { id -> _monitors.update { it - id } }
        }

        // heartbeat is an OBJECT (camelCase)
        s.on("heartbeat") { args ->
            (args.getOrNull(0) as? JSONObject)?.let { hb ->
                val beat = parseHeartbeat(hb) ?: return@let
                _heartbeats.tryEmit(beat)
                updateMonitor(beat.monitorId) { it.copy(status = beat.status, ping = beat.ping) }
            }
        }

        // POSITIONAL events: (monitorID, value...) — NOT objects
        s.on("avgPing") { args ->
            val id = (args.getOrNull(0) as? Number)?.toInt() ?: return@on
            val avg = (args.getOrNull(1) as? Number)?.toInt()
            updateMonitor(id) { it.copy(avgPing = avg) }
        }
        s.on("uptime") { args ->
            val id = (args.getOrNull(0) as? Number)?.toInt() ?: return@on
            val period = (args.getOrNull(1) as? Number)?.toInt()
            val fraction = (args.getOrNull(2) as? Number)?.toDouble()
            if (period == 24 && fraction != null) updateMonitor(id) { it.copy(uptime24h = fraction) }
        }
    }

    /** Create the first admin (fresh instance). */
    suspend fun setup(username: String, password: String): Boolean =
        emitAck("setup", username, password)?.optBoolean("ok") == true

    suspend fun login(username: String, password: String, token: String = ""): LoginResult {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("token", token)
        val res = emitAck("login", payload) ?: return LoginResult.Failure("No response")
        return when {
            res.optBoolean("tokenRequired") -> LoginResult.TwoFactorRequired
            res.optBoolean("ok") -> {
                _state.value = ConnectionState.Authenticated
                LoginResult.Success(res.optString("token").ifEmpty { null })
            }
            else -> LoginResult.Failure(res.optString("msg").ifEmpty { "Login failed" })
        }
    }

    suspend fun loginByToken(jwt: String): Boolean {
        val res = emitAck("loginByToken", jwt) ?: return false
        val ok = res.optBoolean("ok")
        if (ok) _state.value = ConnectionState.Authenticated
        return ok
    }

    suspend fun pauseMonitor(id: Int): Boolean = emitAck("pauseMonitor", id)?.optBoolean("ok") == true
    suspend fun resumeMonitor(id: Int): Boolean = emitAck("resumeMonitor", id)?.optBoolean("ok") == true

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _state.value = ConnectionState.Disconnected
    }

    /** Emit with an ack callback, exposed as a suspend fun returning the `{ ok, ... }` reply. */
    private suspend fun emitAck(event: String, vararg data: Any): JSONObject? =
        suspendCancellableCoroutine { cont ->
            val s = socket
            if (s == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val ack = Ack { args -> if (cont.isActive) cont.resume(args.getOrNull(0) as? JSONObject) }
            s.emit(event, data, ack)
        }

    private inline fun updateMonitor(id: Int, transform: (Monitor) -> Monitor) {
        _monitors.update { current -> current[id]?.let { current + (id to transform(it)) } ?: current }
    }

    private fun parseMonitor(m: JSONObject): Monitor? {
        val id = m.optInt("id", -1).takeIf { it >= 0 } ?: return null
        return Monitor(
            id = id,
            name = m.optString("name"),
            url = m.optString("url").ifEmpty { null },
            type = m.optString("type"),
            active = m.optBoolean("active", true),
            tags = parseTags(m),
        )
    }

    private fun parseTags(m: JSONObject): List<String> {
        val arr = m.optJSONArray("tags") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("name")?.ifEmpty { null }
        }
    }

    private fun parseHeartbeat(hb: JSONObject): Heartbeat? {
        val id = hb.optInt("monitorID", -1).takeIf { it >= 0 } ?: return null
        return Heartbeat(
            monitorId = id,
            status = MonitorStatus.from(hb.optInt("status", 2)),
            time = hb.optString("time"),
            msg = hb.optString("msg").ifEmpty { null },
            ping = if (hb.isNull("ping")) null else hb.optInt("ping"),
            important = hb.optBoolean("important", false),
        )
    }
}
