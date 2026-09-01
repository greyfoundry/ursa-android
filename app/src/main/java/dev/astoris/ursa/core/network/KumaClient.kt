package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.ManagedPushNotification
import dev.astoris.ursa.data.model.KumaNotification
import dev.astoris.ursa.data.model.KumaTag
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorChartPoint
import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.data.model.MonitorTagAssignment
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import java.util.UUID
import kotlin.coroutines.resume

enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Authenticated,
    AuthenticationFailed,
    Error,
}

/**
 * Wraps a single Socket.IO connection to one Uptime Kuma server. Parsing of the
 * (internal, unstable) wire protocol lives in [KumaParse]; this class handles the
 * transport, auth, and flow plumbing only.
 */
class KumaClient(
    private val baseUrl: String,
    private val insecure: Boolean = false,
    private val headers: List<RequestHeader> = emptyList(),
) {

    private var socket: Socket? = null

    /** Last known JWT, kept so we can silently re-authenticate after a reconnect. */
    @Volatile private var jwt: String? = null

    private val _state = MutableStateFlow(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _monitors = MutableStateFlow<Map<Int, Monitor>>(emptyMap())
    val monitors: StateFlow<Map<Int, Monitor>> = _monitors.asStateFlow()

    private val _heartbeats = MutableSharedFlow<Heartbeat>(extraBufferCapacity = 64)
    val heartbeats: SharedFlow<Heartbeat> = _heartbeats.asSharedFlow()

    private val _certs = MutableStateFlow<Map<Int, CertInfo>>(emptyMap())
    val certs: StateFlow<Map<Int, CertInfo>> = _certs.asStateFlow()

    private val _managedPushNotifications = MutableStateFlow<List<ManagedPushNotification>>(emptyList())
    val managedPushNotifications: StateFlow<List<ManagedPushNotification>> =
        _managedPushNotifications.asStateFlow()
    private val _notifications = MutableStateFlow<List<KumaNotification>>(emptyList())
    val notifications: StateFlow<List<KumaNotification>> = _notifications.asStateFlow()
    private val _notificationListReady = MutableStateFlow(false)
    val notificationListReady: StateFlow<Boolean> = _notificationListReady.asStateFlow()

    /** Recent heartbeat history per monitor, for the list sparklines. Seeded by
     *  `heartbeatList` on connect and appended from live `heartbeat` events. */
    private val _beatHistory = MutableStateFlow<Map<Int, List<Heartbeat>>>(emptyMap())
    val beatHistory: StateFlow<Map<Int, List<Heartbeat>>> = _beatHistory.asStateFlow()

    fun connect() {
        if (socket != null) return
        _state.value = ConnectionState.Connecting
        val opts = IO.Options().apply {
            transports = arrayOf("polling", "websocket") // polling first, then upgrade
            reconnection = true
            extraHeaders = headers.mapNotNull { it.normalizedOrNull() }
                .associate { it.name to listOf(it.value) }
        }
        if (insecure) {
            val ok = TlsTrust.sessionPinnedClient()
            opts.callFactory = ok
            opts.webSocketFactory = ok
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
        s.on(Socket.EVENT_CONNECT) { _ ->
            _state.value = ConnectionState.Connected
            // On (re)connect, silently re-authenticate if we already have a token so a
            // dropped socket doesn't kick the user back to the login screen.
            jwt?.let { token ->
                s.emit("loginByToken", token, Ack { args ->
                    if ((args.getOrNull(0) as? JSONObject)?.optBoolean("ok") == true) {
                        _state.value = ConnectionState.Authenticated
                    } else {
                        _state.value = ConnectionState.AuthenticationFailed
                    }
                })
            }
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { _ -> _state.value = ConnectionState.Error }
        s.on(Socket.EVENT_DISCONNECT) { _ -> _state.value = ConnectionState.Disconnected }

        s.on("monitorList") { args ->
            args.jsonAt(0)?.let { _monitors.value = KumaParse.monitorList(it) }
        }
        s.on("notificationList") { args ->
            args.jsonArrayAt(0)?.let {
                _notifications.value = KumaParse.notifications(it)
                _managedPushNotifications.value = KumaParse.managedPushNotifications(it)
                _notificationListReady.value = true
            }
        }
        s.on("updateMonitorIntoList") { args ->
            args.jsonAt(0)?.let { obj ->
                val updates = KumaParse.monitorList(obj)
                _monitors.update { it + updates }
            }
        }
        s.on("deleteMonitorFromList") { args ->
            KumaParse.positionalInt(args.getOrNull(0))?.let { id -> _monitors.update { it - id } }
        }

        // heartbeat is an OBJECT (camelCase)
        s.on("heartbeat") { args ->
            args.jsonAt(0)?.let { obj ->
                val beat = KumaParse.heartbeat(obj) ?: return@let
                _heartbeats.tryEmit(beat)
                updateMonitor(beat.monitorId) { it.copy(status = beat.status, ping = beat.ping) }
                _beatHistory.update { current ->
                    current + (beat.monitorId to (current[beat.monitorId].orEmpty() + beat).takeLast(100))
                }
            }
        }

        // heartbeatList: POSITIONAL (monitorID, snake_case beat rows, overwrite) on connect.
        s.on("heartbeatList") { args ->
            val id = KumaParse.positionalInt(args.getOrNull(0)) ?: return@on
            args.jsonArrayAt(1)?.let { arr ->
                _beatHistory.update { it + (id to KumaParse.beatRows(arr)) }
            }
        }

        // POSITIONAL events: (monitorID, value...) - NOT objects
        s.on("avgPing") { args ->
            val id = KumaParse.positionalInt(args.getOrNull(0)) ?: return@on
            val avg = KumaParse.positionalDouble(args.getOrNull(1))?.toInt()
            updateMonitor(id) { it.copy(avgPing = avg) }
        }
        s.on("uptime") { args ->
            val id = KumaParse.positionalInt(args.getOrNull(0)) ?: return@on
            val period = KumaParse.positionalInt(args.getOrNull(1))
            val fraction = KumaParse.positionalDouble(args.getOrNull(2))
            if (period == 24 && fraction != null) updateMonitor(id) { it.copy(uptime24h = fraction) }
        }
        s.on("certInfo") { args ->
            val id = KumaParse.positionalInt(args.getOrNull(0)) ?: return@on
            val text = when (val raw = args.getOrNull(1)) {
                is JSONObject -> raw.toString()
                is String -> raw
                else -> null
            } ?: return@on
            runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()?.let { obj ->
                _certs.update { it + (id to KumaParse.cert(obj)) }
            }
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
        val res = emitAck("login", payload)
            ?: return LoginResult.Failure("Server did not respond within 15 seconds")
        return when {
            res.optBoolean("tokenRequired") -> LoginResult.TwoFactorRequired
            res.optBoolean("ok") -> {
                jwt = res.optString("token").ifEmpty { null }
                _state.value = ConnectionState.Authenticated
                LoginResult.Success(jwt)
            }
            else -> {
                _state.value = ConnectionState.AuthenticationFailed
                LoginResult.Failure(res.optString("msg").ifEmpty { "Login failed" })
            }
        }
    }

    suspend fun loginByToken(token: String): Boolean {
        val res = emitAck("loginByToken", token) ?: return false
        val ok = res.optBoolean("ok")
        if (ok) {
            jwt = token
            _state.value = ConnectionState.Authenticated
        } else {
            _state.value = ConnectionState.AuthenticationFailed
        }
        return ok
    }

    suspend fun pauseMonitor(id: Int): Boolean = emitAck("pauseMonitor", id)?.optBoolean("ok") == true
    suspend fun resumeMonitor(id: Int): Boolean = emitAck("resumeMonitor", id)?.optBoolean("ok") == true

    suspend fun monitorDraft(id: Int): MonitorDraft? {
        val raw = rawMonitor(id) ?: return null
        val json = runCatching { Json.parseToJsonElement(raw.toString()).jsonObject }.getOrNull() ?: return null
        return MonitorDraftCodec.from(json)
    }

    suspend fun serverTags(): List<KumaTag>? {
        val response = emitAck("getTags") ?: return null
        if (!response.optBoolean("ok")) return null
        val raw = response.optJSONArray("tags") ?: return emptyList()
        val json = runCatching { Json.parseToJsonElement(raw.toString()).jsonArray }.getOrNull()
            ?: return null
        return KumaParse.tagDefinitions(json)
    }

    /** Creates a supported monitor or safely patches common fields on any known Kuma 2.5.3 type. */
    suspend fun saveMonitor(draft: MonitorDraft): MonitorMutationResult {
        MonitorDraftCodec.validate(draft)?.let { error ->
            return MonitorMutationResult(false, message = error.name)
        }
        val currentTags: List<MonitorTagAssignment>
        val payload = if (draft.isNew) {
            currentTags = emptyList()
            MonitorDraftCodec.newPayload(draft)
        } else {
            val raw = rawMonitor(draft.id ?: return MonitorMutationResult(false, message = "MONITOR_UNAVAILABLE"))
                ?: return MonitorMutationResult(false, message = "MONITOR_UNAVAILABLE")
            val json = runCatching { Json.parseToJsonElement(raw.toString()).jsonObject }.getOrNull()
                ?: return MonitorMutationResult(false, message = "MONITOR_UNAVAILABLE")
            currentTags = KumaParse.tagAssignments(json)
            MonitorDraftCodec.applyToExisting(json, draft)
        }
        val event = if (draft.isNew) "add" else "editMonitor"
        val response = emitAck(event, JSONObject(payload.toString()))
            ?: return MonitorMutationResult(false, message = "Server did not respond within 15 seconds")
        if (!response.optBoolean("ok")) {
            return MonitorMutationResult(false, message = response.optString("msg").ifBlank { "Save failed" })
        }
        val id = response.optInt("monitorID").takeIf { it > 0 } ?: draft.id
        if (id != null && !reconcileMonitorTags(id, currentTags, draft.tagAssignments)) {
            return MonitorMutationResult(false, id, "Saved, but tag assignments could not be fully updated")
        }
        if (!draft.isNew && id != null) {
            val originalActive = _monitors.value[id]?.active
            if (originalActive != null && originalActive != draft.active) {
                val activeChanged = if (draft.active) resumeMonitor(id) else pauseMonitor(id)
                if (!activeChanged) {
                    return MonitorMutationResult(false, id, "Saved, but the active state could not be changed")
                }
            }
        }
        return MonitorMutationResult(true, id)
    }

    private suspend fun reconcileMonitorTags(
        monitorId: Int,
        current: List<MonitorTagAssignment>,
        desired: List<MonitorTagAssignment>,
    ): Boolean {
        fun MonitorTagAssignment.key() = tagId to value
        val currentByKey = current.associateBy(MonitorTagAssignment::key)
        val desiredByKey = desired.filter { it.tagId > 0 }.associateBy(MonitorTagAssignment::key)
        var ok = true
        (currentByKey.keys - desiredByKey.keys).forEach { key ->
            if (emitAck("deleteMonitorTag", key.first, monitorId, key.second)?.optBoolean("ok") != true) ok = false
        }
        (desiredByKey.keys - currentByKey.keys).forEach { key ->
            if (emitAck("addMonitorTag", key.first, monitorId, key.second)?.optBoolean("ok") != true) ok = false
        }
        return ok
    }

    suspend fun deleteMonitor(id: Int, deleteChildren: Boolean = false): MonitorMutationResult {
        if (id <= 0) return MonitorMutationResult(false, message = "Invalid monitor")
        val response = emitAck("deleteMonitor", id, deleteChildren)
            ?: return MonitorMutationResult(false, message = "Server did not respond within 15 seconds")
        return if (response.optBoolean("ok")) {
            MonitorMutationResult(true, id)
        } else {
            MonitorMutationResult(false, id, response.optString("msg").ifBlank { "Delete failed" })
        }
    }

    /** Recent heartbeat history for the detail view. Rows are snake_case (see KumaParse). */
    suspend fun getBeats(id: Int, hours: Int = 24): List<Heartbeat> {
        val res = emitAck("getMonitorBeats", id, hours) ?: return emptyList()
        if (!res.optBoolean("ok")) return emptyList()
        val data = res.optJSONArray("data") ?: return emptyList()
        val arr = runCatching { Json.parseToJsonElement(data.toString()).jsonArray }.getOrNull() ?: return emptyList()
        return KumaParse.beatRows(arr)
    }

    /** Newest durable state transitions across the account, fetched only when requested. */
    suspend fun getImportantBeats(limit: Int = IMPORTANT_BEAT_LIMIT): List<Heartbeat>? {
        val bounded = limit.coerceIn(1, IMPORTANT_BEAT_LIMIT)
        val res = emitAck(
            "monitorImportantHeartbeatListPaged",
            JSONObject.NULL,
            0,
            bounded,
        ) ?: return null
        if (!res.optBoolean("ok")) return null
        val data = res.optJSONArray("data") ?: return null
        val arr = runCatching { Json.parseToJsonElement(data.toString()).jsonArray }.getOrNull()
            ?: return null
        return KumaParse.heartbeatRows(arr).sortedBy(Heartbeat::time)
    }

    suspend fun saveManagedPushNotification(
        webhookUrl: String,
        isDefault: Boolean,
    ): ManagedPushNotification? {
        val existing = _managedPushNotifications.value.firstOrNull()
        val serverId = existing?.serverId?.takeIf(ManagedPushNotification::isValidServerId)
            ?: UUID.randomUUID().toString().replace("-", "")
        val notification = managedPushNotification(webhookUrl, isDefault, serverId)
        val existingId = existing?.id
        val res = emitAck("addNotification", notification, existingId ?: JSONObject.NULL) ?: return null
        if (!res.optBoolean("ok")) return null
        val id = res.optInt("id").takeIf { it > 0 } ?: existingId ?: return null
        return ManagedPushNotification(
            id = id,
            name = ManagedPushNotification.MANAGED_NAME,
            webhookUrl = webhookUrl,
            isDefault = isDefault,
            serverId = serverId,
            schemaVersion = ManagedPushNotification.CURRENT_SCHEMA,
        )
    }

    suspend fun testManagedPushNotification(name: String): Boolean {
        val managed = _managedPushNotifications.value.firstOrNull() ?: return false
        val serverId = managed.serverId?.takeIf(ManagedPushNotification::isValidServerId) ?: return false
        val notification = managedPushNotification(managed.webhookUrl, managed.isDefault, serverId, name)
        return emitAck("testNotification", notification)?.optBoolean("ok") == true
    }

    suspend fun deleteManagedPushNotification(id: Int): Boolean =
        emitAck("deleteNotification", id)?.optBoolean("ok") == true

    /** Notification IDs attached to a monitor. Sensitive monitor fields never leave this method. */
    suspend fun monitorNotificationIds(monitorId: Int): Set<Int>? {
        val monitor = rawMonitor(monitorId) ?: return null
        val ids = monitor.optJSONObject("notificationIDList") ?: return emptySet()
        return ids.keys().asSequence().mapNotNull { key ->
            key.toIntOrNull()?.takeIf { ids.optBoolean(key) }
        }.toSet()
    }

    /** Updates only the notification relation while round-tripping Kuma's complete monitor object. */
    suspend fun setMonitorNotification(
        monitorId: Int,
        notificationId: Int,
        enabled: Boolean,
    ): Boolean {
        val monitor = rawMonitor(monitorId) ?: return false
        val ids = monitor.optJSONObject("notificationIDList") ?: JSONObject().also {
            monitor.put("notificationIDList", it)
        }
        val alreadyEnabled = ids.optBoolean(notificationId.toString())
        if (alreadyEnabled == enabled) return true
        if (enabled) ids.put(notificationId.toString(), true) else ids.remove(notificationId.toString())
        return emitAck("editMonitor", monitor)?.optBoolean("ok") == true
    }

    private suspend fun rawMonitor(monitorId: Int): JSONObject? {
        val res = emitAck("getMonitor", monitorId) ?: return null
        if (!res.optBoolean("ok")) return null
        return res.optJSONObject("monitor")
    }

    private fun managedPushNotification(
        webhookUrl: String,
        isDefault: Boolean,
        serverId: String,
        name: String = ManagedPushNotification.MANAGED_NAME,
    ) = JSONObject().apply {
        put("name", name)
        put("type", "webhook")
        put("isDefault", isDefault)
        put("applyExisting", false)
        put("webhookURL", webhookUrl)
        put("httpMethod", "post")
        put("webhookContentType", "custom")
        put("webhookCustomBody", ManagedPushNotification.customWebhookBody(serverId))
        put(ManagedPushNotification.MANAGED_MARKER, true)
        put(ManagedPushNotification.SERVER_ID_FIELD, serverId)
        put(ManagedPushNotification.SCHEMA_FIELD, ManagedPushNotification.CURRENT_SCHEMA)
    }

    /** Server-aggregated uptime and latency buckets, or null when the request failed. */
    suspend fun getChartData(id: Int, hours: Int): List<MonitorChartPoint>? {
        val res = emitAck("getMonitorChartData", id, hours) ?: return null
        if (!res.optBoolean("ok")) return null
        val data = res.optJSONArray("data") ?: return null
        val arr = runCatching { Json.parseToJsonElement(data.toString()).jsonArray }.getOrNull()
            ?: return null
        return KumaParse.chartRows(arr)
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        jwt = null
        _managedPushNotifications.value = emptyList()
        _notifications.value = emptyList()
        _notificationListReady.value = false
        _state.value = ConnectionState.Disconnected
    }

    /** Emit with an ack callback, exposed as a suspend fun returning the `{ ok, ... }` reply. */
    private suspend fun emitAck(event: String, vararg data: Any): JSONObject? =
        withTimeoutOrNull(ACK_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val s = socket
                if (s == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                val ack = Ack { args -> if (cont.isActive) cont.resume(args.getOrNull(0) as? JSONObject) }
                s.emit(event, data, ack)
            }
        }

    private inline fun updateMonitor(id: Int, transform: (Monitor) -> Monitor) {
        _monitors.update { current -> current[id]?.let { current + (id to transform(it)) } ?: current }
    }

    /** Convert a Socket.IO org.json payload at [index] into a kotlinx JsonObject for [KumaParse]. */
    private fun Array<Any?>.jsonAt(index: Int): kotlinx.serialization.json.JsonObject? {
        val raw = getOrNull(index) as? JSONObject ?: return null
        return runCatching { Json.parseToJsonElement(raw.toString()).jsonObject }.getOrNull()
    }

    /** Convert a Socket.IO org.json array at [index] into a kotlinx JsonArray for [KumaParse]. */
    private fun Array<Any?>.jsonArrayAt(index: Int): kotlinx.serialization.json.JsonArray? {
        val raw = getOrNull(index) as? org.json.JSONArray ?: return null
        return runCatching { Json.parseToJsonElement(raw.toString()).jsonArray }.getOrNull()
    }

    private companion object {
        const val ACK_TIMEOUT_MS = 15_000L
        const val IMPORTANT_BEAT_LIMIT = 500
    }
}
