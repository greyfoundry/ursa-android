package dev.astoris.ursa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.ResolvedStatusPageAddress
import dev.astoris.ursa.core.network.StatusPageAddress
import dev.astoris.ursa.core.network.StatusPageAddressError
import dev.astoris.ursa.core.network.StatusPageAddressResult
import dev.astoris.ursa.core.network.StatusPageClient
import dev.astoris.ursa.core.push.PushStore
import dev.astoris.ursa.core.push.KumaWebhook
import dev.astoris.ursa.core.push.UrsaPushService
import dev.astoris.ursa.core.storage.CertExpiryStore
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.BackupDecodeResult
import dev.astoris.ursa.core.storage.BackupError
import dev.astoris.ursa.core.storage.ConnectionBackupCodec
import dev.astoris.ursa.core.storage.ConnectionBackupData
import dev.astoris.ursa.core.storage.PortablePreferences
import dev.astoris.ursa.core.storage.DynamicColorStore
import dev.astoris.ursa.core.storage.EventLogStore
import dev.astoris.ursa.core.storage.IncidentNote
import dev.astoris.ursa.core.storage.IncidentNoteStore
import dev.astoris.ursa.core.storage.LocalEvent
import dev.astoris.ursa.core.storage.LocalEventKind
import dev.astoris.ursa.core.storage.LockStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.MonitorPreferenceStore
import dev.astoris.ursa.core.storage.ResponseAlertStore
import dev.astoris.ursa.core.storage.StatusPageStore
import dev.astoris.ursa.core.work.CertExpiryWorker
import dev.astoris.ursa.core.work.ResponseAlertNotifier
import dev.astoris.ursa.core.work.ResponseAlertWorker
import dev.astoris.ursa.core.work.ResponseAlertUtil
import dev.astoris.ursa.ui.monitors.HeartbeatRange
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorChartPoint
import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.data.model.SavedStatusPage
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.data.model.StatusPageView
import dev.astoris.ursa.data.repository.MonitorRepository
import org.unifiedpush.android.connector.UnifiedPush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object NeedsTwoFactor : LoginUiState
    data class Error(val message: String) : LoginUiState
}

sealed interface ConnectionTestUiState {
    data object Idle : ConnectionTestUiState
    data object Loading : ConnectionTestUiState
    data object Success : ConnectionTestUiState
    data object NeedsTwoFactor : ConnectionTestUiState
    data class Error(val message: String) : ConnectionTestUiState
}

sealed interface ConnectionBackupResult {
    data class Document(val content: String) : ConnectionBackupResult
    data class Imported(val count: Int) : ConnectionBackupResult
    data class Error(val reason: BackupError) : ConnectionBackupResult
}

sealed interface StatusPageUiState {
    data object Idle : StatusPageUiState
    data object Loading : StatusPageUiState
    data class Loaded(val view: StatusPageView) : StatusPageUiState
    data class Error(val message: String) : StatusPageUiState
}

sealed interface StatusPageFormResult {
    data object Saved : StatusPageFormResult
    data class Verified(val title: String) : StatusPageFormResult
    data class ValidationError(val error: StatusPageAddressError) : StatusPageFormResult
    data object NoDiscoverablePage : StatusPageFormResult
    data class NetworkError(val message: String) : StatusPageFormResult
}

enum class KumaPushSetupError { INVALID_ENDPOINT, SERVER_UNAVAILABLE, SAVE_FAILED, DELETE_FAILED }

sealed interface KumaPushSetupUiState {
    data object Idle : KumaPushSetupUiState
    data object Loading : KumaPushSetupUiState
    data class Ready(
        val notificationId: Int?,
        val endpointCurrent: Boolean,
        val isDefault: Boolean,
        val selectedMonitorIds: Set<Int>,
        val unavailableMonitorIds: Set<Int> = emptySet(),
        val recentlySaved: Boolean = false,
    ) : KumaPushSetupUiState
    data class Error(val reason: KumaPushSetupError) : KumaPushSetupUiState
}

private sealed interface StatusPageResolution {
    data class Success(
        val address: ResolvedStatusPageAddress,
        val headers: List<RequestHeader>,
    ) : StatusPageResolution
    data class Failure(val result: StatusPageFormResult) : StatusPageResolution
}

/** The three primary destinations in the bottom navigation bar. */
enum class MainTab { MONITORS, NOTIFICATIONS, SETTINGS }

class UrsaViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ConnectionStore(app)
    private val cacheStore = MonitorCacheStore(app)
    private val monitorPreferenceStore = MonitorPreferenceStore(app)
    private val certExpiryStore = CertExpiryStore(app)
    private val eventLogStore = EventLogStore(app)
    private val incidentNoteStore = IncidentNoteStore(app)
    private val repo = MonitorRepository(store, cacheStore, certExpiryStore, viewModelScope)

    val monitors: StateFlow<List<Monitor>> = repo.monitors
    val state: StateFlow<ConnectionState> = repo.state
    val lastUpdated: StateFlow<Long?> = repo.lastUpdated
    val showingCache: StateFlow<Boolean> = repo.showingCache
    private val _favorites = MutableStateFlow<Set<Int>>(emptySet())
    val favorites: StateFlow<Set<Int>> = _favorites.asStateFlow()
    val connections: StateFlow<List<ServerConnection>> =
        repo.connections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeUrl: StateFlow<String?> =
        repo.activeUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val localEvents: StateFlow<List<LocalEvent>> =
        combine(eventLogStore.events, repo.activeUrl) { events, url ->
            events.filter { it.serverUrl == null || it.serverUrl == url }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val incidentNotes: StateFlow<List<IncidentNote>> =
        combine(incidentNoteStore.notes, repo.activeUrl) { notes, url ->
            notes.filter { it.serverUrl == url }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _login = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val login: StateFlow<LoginUiState> = _login.asStateFlow()
    private val _connectionTest = MutableStateFlow<ConnectionTestUiState>(ConnectionTestUiState.Idle)
    val connectionTest: StateFlow<ConnectionTestUiState> = _connectionTest.asStateFlow()

    /** True once a server session exists; keeps the list visible across reconnects. */
    private val _hasSession = MutableStateFlow(false)
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

    /** True after the initial encrypted connection restore has chosen a route. */
    private val _startupReady = MutableStateFlow(false)
    val startupReady: StateFlow<Boolean> = _startupReady.asStateFlow()

    private val _selectedId = MutableStateFlow<Int?>(null)
    val selectedId: StateFlow<Int?> = _selectedId.asStateFlow()

    val selectedMonitor: StateFlow<Monitor?> =
        combine(_selectedId, monitors) { id, list -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _beats = MutableStateFlow<List<Heartbeat>>(emptyList())
    val beats: StateFlow<List<Heartbeat>> = _beats.asStateFlow()
    private val _beatRange = MutableStateFlow(HeartbeatRange.DAY)
    val beatRange: StateFlow<HeartbeatRange> = _beatRange.asStateFlow()

    val certs: StateFlow<Map<Int, CertInfo>> = repo.certs
    val beatHistory: StateFlow<Map<Int, List<Heartbeat>>> = repo.beatHistory

    private val statusClient = StatusPageClient()
    private val statusPageStore = StatusPageStore(app)
    private val _statusPageMode = MutableStateFlow(false)
    val statusPageMode: StateFlow<Boolean> = _statusPageMode.asStateFlow()
    private val _statusPage = MutableStateFlow<StatusPageUiState>(StatusPageUiState.Idle)
    val statusPage: StateFlow<StatusPageUiState> = _statusPage.asStateFlow()
    val savedStatusPages: StateFlow<List<SavedStatusPage>> =
        statusPageStore.pages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selectedStatusPageId = MutableStateFlow<String?>(null)
    val selectedStatusPageId: StateFlow<String?> = _selectedStatusPageId.asStateFlow()

    // --- Push (UnifiedPush) ---
    val pushEndpoint: StateFlow<String?> = PushStore.endpoint
    val pushDistributor: StateFlow<String?> = PushStore.distributor
    private val _distributors = MutableStateFlow<List<String>>(emptyList())
    val distributors: StateFlow<List<String>> = _distributors.asStateFlow()
    private val _kumaPushSetup = MutableStateFlow<KumaPushSetupUiState>(KumaPushSetupUiState.Idle)
    val kumaPushSetup: StateFlow<KumaPushSetupUiState> = _kumaPushSetup.asStateFlow()

    // Which bottom-nav tab is selected.
    private val _tab = MutableStateFlow(MainTab.MONITORS)
    val tab: StateFlow<MainTab> = _tab.asStateFlow()

    private val _connectionManagerMode = MutableStateFlow(false)
    val connectionManagerMode: StateFlow<Boolean> = _connectionManagerMode.asStateFlow()
    private val _addingConnection = MutableStateFlow(false)
    val addingConnection: StateFlow<Boolean> = _addingConnection.asStateFlow()
    private val _editingConnection = MutableStateFlow<ServerConnection?>(null)
    val editingConnection: StateFlow<ServerConnection?> = _editingConnection.asStateFlow()

    // --- App lock ---
    val lockEnabled: StateFlow<Boolean> = LockStore.enabled
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val alertStore = ResponseAlertStore(getApplication())
    private val _slowAlertEnabled = MutableStateFlow(false)
    val slowAlertEnabled: StateFlow<Boolean> = _slowAlertEnabled.asStateFlow()
    private val _slowThresholdMs = MutableStateFlow(ResponseAlertUtil.DEFAULT_GLOBAL_THRESHOLD_MS)
    val slowThresholdMs: StateFlow<Int> = _slowThresholdMs.asStateFlow()

    init {
        PushStore.load(getApplication())
        UrsaPushService.ensureChannel(getApplication())
        LockStore.load(getApplication())
        DynamicColorStore.load(getApplication())
        if (LockStore.enabled.value) _locked.value = true // start locked if enabled
        CertExpiryWorker.schedule(getApplication()) // daily TLS-expiry reminder
        ResponseAlertWorker.schedule(getApplication()) // periodic slow-response check (#1813)
        viewModelScope.launch {
            _slowAlertEnabled.value = alertStore.isEnabled()
            _slowThresholdMs.value = alertStore.globalThresholdMs()
        }
        // Live foreground half of "Both": evaluate incoming beats while connected. The
        // shared cooldown in alertStore keeps this from double-firing with the worker.
        viewModelScope.launch {
            repo.heartbeats.collect { beat ->
                if (!_slowAlertEnabled.value) return@collect
                val url = store.activeUrl.first() ?: return@collect
                val key = ResponseAlertUtil.monitorKey(url, beat.monitorId)
                val threshold = ResponseAlertUtil.effectiveThreshold(
                    alertStore.thresholdFor(key), _slowThresholdMs.value,
                )
                val now = System.currentTimeMillis()
                if (ResponseAlertUtil.shouldAlert(beat.status.code, beat.ping, threshold, alertStore.lastAlerted()[key], now)) {
                    val name = monitors.value.firstOrNull { it.id == beat.monitorId }?.name ?: "Monitor ${beat.monitorId}"
                    val ping = beat.ping ?: 0
                    if (ResponseAlertNotifier.notify(getApplication(), name, ping, threshold, key)) {
                        eventLogStore.append(
                            serverUrl = url,
                            monitorId = beat.monitorId,
                            monitorName = name,
                            kind = LocalEventKind.SLOW_RESPONSE,
                            detail = getApplication<Application>().getString(
                                dev.astoris.ursa.R.string.event_slow_response_detail,
                                ping,
                                threshold,
                            ),
                            atMillis = now,
                        )
                        alertStore.markAlerted(key, now)
                    }
                }
            }
        }
        // Keep hasSession true whenever we reach an authenticated state.
        viewModelScope.launch {
            state.collect { if (it == ConnectionState.Authenticated) _hasSession.value = true }
        }
        // Auto-reconnect to the last active server if we have a stored session.
        viewModelScope.launch {
            try {
                val conns = store.connections.first()
                val active = store.activeUrl.first()
                val conn = conns.firstOrNull { it.url == active } ?: conns.firstOrNull()
                if (conn?.jwt != null) {
                    _hasSession.value = true
                    // Release startup routing once the client and cache restore are active;
                    // the socket authentication ack can legitimately take up to 15 seconds.
                    repo.switchTo(conn) { _startupReady.value = true }
                }
            } finally {
                // Missing, corrupt, or unreadable saved state must fall through to login.
                _startupReady.value = true
            }
        }
        viewModelScope.launch {
            repo.activeUrl.collectLatest { url ->
                if (url == null) {
                    _favorites.value = emptySet()
                } else {
                    monitorPreferenceStore.favorites(url).collect { _favorites.value = it }
                }
            }
        }
    }

    fun login(
        url: String,
        username: String,
        password: String,
        token: String = "",
        insecure: Boolean = false,
        alias: String? = null,
        headers: List<RequestHeader> = emptyList(),
        onSuccess: () -> Unit = {},
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _login.value = LoginUiState.Loading
            _login.value = when (
                val r = repo.addServerAndLogin(normalized, username, password, token, insecure, alias, headers)
            ) {
                is LoginResult.Success -> {
                    onSuccess()
                    LoginUiState.Idle
                }
                LoginResult.TwoFactorRequired -> LoginUiState.NeedsTwoFactor
                is LoginResult.Failure -> LoginUiState.Error(r.message)
            }
        }
    }

    fun loginWithSessionToken(
        url: String,
        sessionToken: String,
        insecure: Boolean = false,
        alias: String? = null,
        headers: List<RequestHeader> = emptyList(),
        onSuccess: () -> Unit = {},
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _login.value = LoginUiState.Loading
            _login.value = when (
                val result = repo.addServerByToken(normalized, sessionToken.trim(), insecure, alias, headers)
            ) {
                is LoginResult.Success -> {
                    onSuccess()
                    LoginUiState.Idle
                }
                LoginResult.TwoFactorRequired -> LoginUiState.NeedsTwoFactor
                is LoginResult.Failure -> LoginUiState.Error(result.message)
            }
        }
    }

    fun switchTo(conn: ServerConnection) = viewModelScope.launch { repo.switchTo(conn) }

    fun testConnection(
        url: String,
        username: String,
        password: String,
        token: String = "",
        insecure: Boolean = false,
        headers: List<RequestHeader> = emptyList(),
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _connectionTest.value = ConnectionTestUiState.Loading
            _connectionTest.value = when (
                val result = repo.testServer(normalized, username, password, token, insecure, headers)
            ) {
                is LoginResult.Success -> ConnectionTestUiState.Success
                LoginResult.TwoFactorRequired -> ConnectionTestUiState.NeedsTwoFactor
                is LoginResult.Failure -> ConnectionTestUiState.Error(result.message)
            }
        }
    }

    fun testSessionToken(
        url: String,
        sessionToken: String,
        insecure: Boolean = false,
        headers: List<RequestHeader> = emptyList(),
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _connectionTest.value = ConnectionTestUiState.Loading
            _connectionTest.value = when (
                val result = repo.testServerToken(normalized, sessionToken.trim(), insecure, headers)
            ) {
                is LoginResult.Success -> ConnectionTestUiState.Success
                LoginResult.TwoFactorRequired -> ConnectionTestUiState.NeedsTwoFactor
                is LoginResult.Failure -> ConnectionTestUiState.Error(result.message)
            }
        }
    }

    fun renameConnection(url: String, alias: String?) =
        viewModelScope.launch { repo.renameServer(url, alias) }

    fun removeConnection(url: String) {
        viewModelScope.launch {
            val fallback = repo.removeServer(url)
            _hasSession.value = fallback?.jwt != null
            if (fallback == null) {
                _addingConnection.value = false
                _connectionManagerMode.value = false
            }
        }
    }
    fun pause(id: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val succeeded = repo.pause(id)
        if (succeeded) recordMonitorAction(id, LocalEventKind.PAUSED)
        onResult(succeeded)
    }

    fun resume(id: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val succeeded = repo.resume(id)
        if (succeeded) recordMonitorAction(id, LocalEventKind.RESUMED)
        onResult(succeeded)
    }

    private suspend fun recordMonitorAction(id: Int, kind: LocalEventKind) {
        val url = repo.activeUrl.first() ?: return
        val name = monitors.value.firstOrNull { it.id == id }?.name
            ?: getApplication<Application>().getString(dev.astoris.ursa.R.string.monitor_fallback_name, id)
        eventLogStore.append(url, id, name, kind)
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch {
            val url = repo.activeUrl.first() ?: return@launch
            monitorPreferenceStore.toggleFavorite(url, id)
        }
    }

    fun saveIncidentNote(monitorId: Int, startedAt: String?, text: String) {
        val knownStart = startedAt?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val url = repo.activeUrl.first() ?: return@launch
            incidentNoteStore.save(url, monitorId, knownStart, text)
        }
    }

    fun select(id: Int) {
        _selectedId.value = id
        refetchBeats()
    }

    fun setBeatRange(range: HeartbeatRange) {
        _beatRange.value = range
        refetchBeats()
    }

    /** On-demand 30-day server aggregates for the fleet summary; never polled in the background. */
    suspend fun fleetChartData(monitorIds: List<Int>): Map<Int, List<MonitorChartPoint>?> =
        repo.chartData(monitorIds, FLEET_SUMMARY_HOURS)

    suspend fun importantHeartbeatHistory(): List<Heartbeat>? = repo.importantBeats()

    private fun refetchBeats() {
        val id = _selectedId.value ?: return
        viewModelScope.launch { _beats.value = repo.beats(id, _beatRange.value.hours) }
    }

    fun back() {
        _selectedId.value = null
        _beats.value = emptyList()
    }

    private companion object {
        const val FLEET_SUMMARY_HOURS = 30 * 24
    }

    fun resetLogin() { _login.value = LoginUiState.Idle }

    fun logout() {
        repo.disconnect()
        _hasSession.value = false
        _selectedId.value = null
        _beats.value = emptyList()
        _login.value = LoginUiState.Idle
    }

    // --- Tab navigation ---
    fun selectTab(t: MainTab) {
        if (t == MainTab.NOTIFICATIONS) {
            refreshDistributors()
            refreshKumaPushSetup()
        }
        _tab.value = t
    }
    // Deep-link entry points (app shortcuts) map onto tabs.
    fun enterPush() = selectTab(MainTab.NOTIFICATIONS)
    fun enterSettings() = selectTab(MainTab.SETTINGS)

    fun enterConnectionManager() {
        resetLogin()
        resetConnectionTest()
        _addingConnection.value = false
        _editingConnection.value = null
        _connectionManagerMode.value = true
    }

    fun exitConnectionManager() {
        resetLogin()
        resetConnectionTest()
        _addingConnection.value = false
        _editingConnection.value = null
        _connectionManagerMode.value = false
    }

    fun startAddingConnection() {
        resetLogin()
        resetConnectionTest()
        _editingConnection.value = null
        _addingConnection.value = true
    }

    fun reauthenticate(connection: ServerConnection) {
        resetLogin()
        resetConnectionTest()
        _editingConnection.value = connection
        _addingConnection.value = true
    }

    fun cancelAddingConnection() {
        resetLogin()
        resetConnectionTest()
        _addingConnection.value = false
        _editingConnection.value = null
    }

    fun finishAddingConnection() {
        resetConnectionTest()
        _addingConnection.value = false
        _editingConnection.value = null
    }

    fun resetConnectionTest() {
        _connectionTest.value = ConnectionTestUiState.Idle
    }

    fun createConnectionBackup(
        password: String,
        includeSessions: Boolean,
        onResult: (ConnectionBackupResult) -> Unit,
    ) {
        viewModelScope.launch {
            val chars = password.toCharArray()
            val result = try {
                withContext(Dispatchers.Default) {
                    val snapshot = store.snapshot()
                    val preferences = PortablePreferences(
                        dynamicColor = DynamicColorStore.enabled.value,
                        slowAlertsEnabled = alertStore.isEnabled(),
                        slowAlertThresholdMs = alertStore.globalThresholdMs(),
                        perMonitorThresholds = alertStore.perMonitorThresholds(),
                        favoritesByServer = snapshot.associate { connection ->
                            connection.url to monitorPreferenceStore.favoriteSnapshot(connection.url)
                        }.filterValues { it.isNotEmpty() },
                    )
                    ConnectionBackupResult.Document(
                        ConnectionBackupCodec.encrypt(
                            ConnectionBackupData(snapshot, preferences),
                            chars,
                            includeSessions,
                        ),
                    )
                }
            } catch (_: Exception) {
                ConnectionBackupResult.Error(BackupError.INVALID_CONTENT)
            } finally {
                chars.fill('\u0000')
            }
            onResult(result)
        }
    }

    fun importConnectionBackup(
        document: String,
        password: String,
        onResult: (ConnectionBackupResult) -> Unit,
    ) {
        viewModelScope.launch {
            val chars = password.toCharArray()
            val decoded = try {
                withContext(Dispatchers.Default) { ConnectionBackupCodec.decrypt(document, chars) }
            } catch (_: Exception) {
                BackupDecodeResult.Error(BackupError.INVALID_DOCUMENT)
            } finally {
                chars.fill('\u0000')
            }
            when (decoded) {
                is BackupDecodeResult.Error -> onResult(ConnectionBackupResult.Error(decoded.reason))
                is BackupDecodeResult.Success -> {
                    val data = decoded.data
                    store.mergeImported(data.connections)
                    if (!_hasSession.value) {
                        data.connections.firstOrNull { it.jwt != null }?.let { connection ->
                            repo.switchTo(connection)
                            _hasSession.value = true
                        }
                    }
                    DynamicColorStore.setEnabled(getApplication(), data.preferences.dynamicColor)
                    alertStore.setEnabled(data.preferences.slowAlertsEnabled)
                    alertStore.setGlobalThresholdMs(data.preferences.slowAlertThresholdMs)
                    alertStore.mergePerMonitorThresholds(data.preferences.perMonitorThresholds)
                    data.preferences.favoritesByServer.forEach { (url, ids) ->
                        monitorPreferenceStore.mergeFavorites(url, ids)
                    }
                    _slowAlertEnabled.value = data.preferences.slowAlertsEnabled
                    _slowThresholdMs.value = data.preferences.slowAlertThresholdMs
                    onResult(ConnectionBackupResult.Imported(data.connections.size))
                }
            }
        }
    }

    // --- Push actions ---

    /** Re-scan installed UnifiedPush distributors (e.g. ntfy). */
    fun refreshDistributors() {
        _distributors.value = UnifiedPush.getDistributors(getApplication())
    }

    /** Save the chosen distributor and request an endpoint (arrives async via the service). */
    fun registerPush(distributor: String) {
        val app = getApplication<Application>()
        PushStore.setDistributor(app, distributor)
        UnifiedPush.saveDistributor(app, distributor)
        UnifiedPush.register(app) // no VAPID: Kuma posts plain JSON, not web-push-encrypted
    }

    fun unregisterPush() {
        val app = getApplication<Application>()
        UnifiedPush.unregister(app)
        UnifiedPush.removeDistributor(app)
        PushStore.clear(app)
        _kumaPushSetup.value = KumaPushSetupUiState.Idle
    }

    fun refreshKumaPushSetup() {
        val endpoint = pushEndpoint.value ?: run {
            _kumaPushSetup.value = KumaPushSetupUiState.Idle
            return
        }
        val deliveryUrl = KumaWebhook.deliveryUrl(endpoint, pushDistributor.value) ?: run {
            _kumaPushSetup.value = KumaPushSetupUiState.Error(KumaPushSetupError.INVALID_ENDPOINT)
            return
        }
        viewModelScope.launch {
            _kumaPushSetup.value = KumaPushSetupUiState.Loading
            val ids = monitors.value.map(Monitor::id)
            val snapshot = repo.managedPushAssignments(ids) ?: run {
                _kumaPushSetup.value = KumaPushSetupUiState.Error(KumaPushSetupError.SERVER_UNAVAILABLE)
                return@launch
            }
            val notification = snapshot.notification
            _kumaPushSetup.value = KumaPushSetupUiState.Ready(
                notificationId = notification?.id,
                endpointCurrent = notification?.webhookUrl == deliveryUrl,
                isDefault = notification?.isDefault ?: true,
                selectedMonitorIds = if (notification == null) ids.toSet() else snapshot.selectedMonitorIds,
                unavailableMonitorIds = snapshot.unavailableMonitorIds,
            )
        }
    }

    fun saveKumaPushSetup(selectedMonitorIds: Set<Int>, isDefault: Boolean) {
        val endpoint = pushEndpoint.value
        val deliveryUrl = endpoint?.let { KumaWebhook.deliveryUrl(it, pushDistributor.value) }
        if (deliveryUrl == null) {
            _kumaPushSetup.value = KumaPushSetupUiState.Error(KumaPushSetupError.INVALID_ENDPOINT)
            return
        }
        viewModelScope.launch {
            _kumaPushSetup.value = KumaPushSetupUiState.Loading
            val ids = monitors.value.map(Monitor::id)
            val result = repo.saveManagedPushSetup(deliveryUrl, isDefault, selectedMonitorIds, ids)
            if (result == null) {
                _kumaPushSetup.value = KumaPushSetupUiState.Error(KumaPushSetupError.SAVE_FAILED)
                return@launch
            }
            val snapshot = repo.managedPushAssignments(ids)
            val unavailable = result.failedMonitorIds + snapshot?.unavailableMonitorIds.orEmpty()
            _kumaPushSetup.value = KumaPushSetupUiState.Ready(
                notificationId = result.notificationId,
                endpointCurrent = true,
                isDefault = isDefault,
                selectedMonitorIds = snapshot?.selectedMonitorIds ?: (selectedMonitorIds - result.failedMonitorIds),
                unavailableMonitorIds = unavailable,
                recentlySaved = true,
            )
        }
    }

    fun deleteKumaPushSetup() {
        viewModelScope.launch {
            _kumaPushSetup.value = KumaPushSetupUiState.Loading
            if (repo.deleteManagedPushSetup()) refreshKumaPushSetup()
            else _kumaPushSetup.value = KumaPushSetupUiState.Error(KumaPushSetupError.DELETE_FAILED)
        }
    }

    // --- Lock actions ---
    fun unlock() { _locked.value = false }

    /** Re-lock when the app returns to the background, if the lock is enabled. */
    fun relock() { if (LockStore.enabled.value) _locked.value = true }

    fun setSlowAlertEnabled(enabled: Boolean) {
        _slowAlertEnabled.value = enabled
        viewModelScope.launch { alertStore.setEnabled(enabled) }
    }

    fun setSlowThresholdMs(ms: Int) {
        _slowThresholdMs.value = ms
        viewModelScope.launch { alertStore.setGlobalThresholdMs(ms) }
    }

    /** Per-monitor slow-response override in ms, or null when the global limit applies. */
    suspend fun monitorThresholdMs(monitorId: Int): Int? {
        val url = store.activeUrl.first() ?: return null
        return alertStore.thresholdFor(ResponseAlertUtil.monitorKey(url, monitorId))
    }

    fun setMonitorThresholdMs(monitorId: Int, ms: Int?) {
        viewModelScope.launch {
            val url = store.activeUrl.first() ?: return@launch
            alertStore.setThresholdFor(ResponseAlertUtil.monitorKey(url, monitorId), ms)
        }
    }

    val dynamicColorEnabled: StateFlow<Boolean> = DynamicColorStore.enabled
    fun setDynamicColorEnabled(enabled: Boolean) =
        DynamicColorStore.setEnabled(getApplication(), enabled)

    fun setLockEnabled(enabled: Boolean) {
        LockStore.setEnabled(getApplication(), enabled)
        if (!enabled) _locked.value = false
    }

    fun enterStatusPage() { _statusPageMode.value = true }
    fun exitStatusPage() {
        _statusPageMode.value = false
        _selectedStatusPageId.value = null
        _statusPage.value = StatusPageUiState.Idle
    }

    fun saveStatusPage(
        existingId: String?,
        name: String,
        address: String,
        slug: String,
        insecure: Boolean,
        onResult: (StatusPageFormResult) -> Unit,
    ) {
        viewModelScope.launch {
            when (val resolution = resolveStatusPage(address, slug, insecure)) {
                is StatusPageResolution.Failure -> onResult(resolution.result)
                is StatusPageResolution.Success -> {
                    val existing = savedStatusPages.value.firstOrNull { it.id == existingId }
                    val page = SavedStatusPage(
                        id = existingId ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        url = resolution.address.baseUrl,
                        slug = requireNotNull(resolution.address.slug),
                        insecure = insecure,
                        favorite = existing?.favorite ?: false,
                        order = existing?.order ?: savedStatusPages.value.size,
                    )
                    statusPageStore.upsert(page)
                    onResult(StatusPageFormResult.Saved)
                }
            }
        }
    }

    fun testStatusPage(
        address: String,
        slug: String,
        insecure: Boolean,
        onResult: (StatusPageFormResult) -> Unit,
    ) {
        viewModelScope.launch {
            when (val resolution = resolveStatusPage(address, slug, insecure)) {
                is StatusPageResolution.Failure -> onResult(resolution.result)
                is StatusPageResolution.Success -> {
                    onResult(
                        try {
                            val view = statusClient.fetch(
                                resolution.address.baseUrl,
                                requireNotNull(resolution.address.slug),
                                insecure,
                                resolution.headers,
                            )
                            StatusPageFormResult.Verified(view.title)
                        } catch (e: Exception) {
                            StatusPageFormResult.NetworkError(e.message ?: "Request failed")
                        },
                    )
                }
            }
        }
    }

    fun removeStatusPage(id: String) {
        if (_selectedStatusPageId.value == id) closeStatusPageView()
        viewModelScope.launch { statusPageStore.remove(id) }
    }

    fun toggleStatusPageFavorite(id: String) =
        viewModelScope.launch { statusPageStore.toggleFavorite(id) }

    fun moveStatusPage(id: String, direction: Int) =
        viewModelScope.launch { statusPageStore.move(id, direction) }

    fun openStatusPage(page: SavedStatusPage) {
        _selectedStatusPageId.value = page.id
        loadStatusPage(page)
    }

    fun refreshStatusPage() {
        val id = _selectedStatusPageId.value ?: return
        savedStatusPages.value.firstOrNull { it.id == id }?.let(::loadStatusPage)
    }

    fun closeStatusPageView() {
        _selectedStatusPageId.value = null
        _statusPage.value = StatusPageUiState.Idle
    }

    private fun loadStatusPage(page: SavedStatusPage) {
        viewModelScope.launch {
            _statusPage.value = StatusPageUiState.Loading
            _statusPage.value = try {
                val headers = store.snapshot()
                    .firstOrNull { normalizeUrl(it.url) == page.url }
                    ?.headers
                    .orEmpty()
                StatusPageUiState.Loaded(
                    statusClient.fetch(page.url, page.slug, page.insecure, headers),
                )
            } catch (e: Exception) {
                StatusPageUiState.Error(e.message ?: "Failed to load status page")
            }
        }
    }

    private suspend fun resolveStatusPage(
        rawAddress: String,
        rawSlug: String,
        insecure: Boolean,
    ): StatusPageResolution {
        val parsed = when (val result = StatusPageAddress.resolve(rawAddress, rawSlug)) {
            is StatusPageAddressResult.Invalid -> {
                return StatusPageResolution.Failure(StatusPageFormResult.ValidationError(result.error))
            }
            is StatusPageAddressResult.Valid -> result.address
        }
        val headers = store.snapshot()
            .firstOrNull { normalizeUrl(it.url) == parsed.baseUrl }
            ?.headers
            .orEmpty()
        if (parsed.slug != null) return StatusPageResolution.Success(parsed, headers)
        val discovered = try {
            statusClient.discover(parsed.baseUrl, insecure, headers)
        } catch (e: Exception) {
            return StatusPageResolution.Failure(
                StatusPageFormResult.NetworkError(e.message ?: "Discovery failed"),
            )
        }
        return if (discovered == null) {
            StatusPageResolution.Failure(StatusPageFormResult.NoDiscoverablePage)
        } else {
            StatusPageResolution.Success(parsed.copy(slug = discovered), headers)
        }
    }

    override fun onCleared() {
        repo.disconnect()
        statusClient.close()
    }

    private fun normalizeUrl(raw: String): String {
        var u = raw.trim().removeSuffix("/")
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }
}
