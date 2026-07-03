package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import kotlin.coroutines.resume

enum class ConnectionState { Disconnected, Connecting, Connected, Authenticated, Error }

/**
 * Wraps a single Socket.IO connection to one Uptime Kuma server. Parsing of the
 * (internal, unstable) wire protocol lives in [KumaParse]; this class handles the
 * transport, auth, and flow plumbing only.
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

        s.on("monitorList") { args ->
            args.jsonAt(0)?.let { _monitors.value = KumaParse.monitorList(it) }
        }
        s.on("updateMonitorIntoList") { args ->
            args.jsonAt(0)?.let { obj ->
                val updates = KumaParse.monitorList(obj)
                _monitors.update { it + updates }
            }
        }
        s.on("deleteMonitorFromList") { args ->
            (args.getOrNull(0) as? Number)?.toInt()?.let { id -> _monitors.update { it - id } }
        }

        // heartbeat is an OBJECT (camelCase)
        s.on("heartbeat") { args ->
            args.jsonAt(0)?.let { obj ->
                val beat = KumaParse.heartbeat(obj) ?: return@let
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

    /** Convert a Socket.IO org.json payload at [index] into a kotlinx JsonObject for [KumaParse]. */
    private fun Array<Any?>.jsonAt(index: Int): kotlinx.serialization.json.JsonObject? {
        val raw = getOrNull(index) as? JSONObject ?: return null
        return runCatching { Json.parseToJsonElement(raw.toString()).jsonObject }.getOrNull()
    }
}
