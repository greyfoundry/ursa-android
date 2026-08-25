package dev.astoris.ursa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.StatusPageClient
import dev.astoris.ursa.core.push.PushStore
import dev.astoris.ursa.core.push.UrsaPushService
import dev.astoris.ursa.core.storage.CertExpiryStore
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.DynamicColorStore
import dev.astoris.ursa.core.storage.LockStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.MonitorPreferenceStore
import dev.astoris.ursa.core.storage.ResponseAlertStore
import dev.astoris.ursa.core.work.CertExpiryWorker
import dev.astoris.ursa.core.work.ResponseAlertNotifier
import dev.astoris.ursa.core.work.ResponseAlertWorker
import dev.astoris.ursa.core.work.ResponseAlertUtil
import dev.astoris.ursa.ui.monitors.HeartbeatRange
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
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
import kotlinx.coroutines.launch

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

sealed interface StatusPageUiState {
    data object Idle : StatusPageUiState
    data object Loading : StatusPageUiState
    data class Loaded(val view: StatusPageView) : StatusPageUiState
    data class Error(val message: String) : StatusPageUiState
}

/** The three primary destinations in the bottom navigation bar. */
enum class MainTab { MONITORS, NOTIFICATIONS, SETTINGS }

class UrsaViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ConnectionStore(app)
    private val cacheStore = MonitorCacheStore(app)
    private val monitorPreferenceStore = MonitorPreferenceStore(app)
    private val certExpiryStore = CertExpiryStore(app)
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

    private val _login = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val login: StateFlow<LoginUiState> = _login.asStateFlow()
    private val _connectionTest = MutableStateFlow<ConnectionTestUiState>(ConnectionTestUiState.Idle)
    val connectionTest: StateFlow<ConnectionTestUiState> = _connectionTest.asStateFlow()

    /** True once a server session exists; keeps the list visible across reconnects. */
    private val _hasSession = MutableStateFlow(false)
    val hasSession: StateFlow<Boolean> = _hasSession.asStateFlow()

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
    private val _statusPageMode = MutableStateFlow(false)
    val statusPageMode: StateFlow<Boolean> = _statusPageMode.asStateFlow()
    private val _statusPage = MutableStateFlow<StatusPageUiState>(StatusPageUiState.Idle)
    val statusPage: StateFlow<StatusPageUiState> = _statusPage.asStateFlow()

    // --- Push (UnifiedPush) ---
    val pushEndpoint: StateFlow<String?> = PushStore.endpoint
    val pushDistributor: StateFlow<String?> = PushStore.distributor
    private val _distributors = MutableStateFlow<List<String>>(emptyList())
    val distributors: StateFlow<List<String>> = _distributors.asStateFlow()

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
                    ResponseAlertNotifier.notify(getApplication(), name, beat.ping ?: 0, threshold, key)
                    alertStore.markAlerted(key, now)
                }
            }
        }
        // Keep hasSession true whenever we reach an authenticated state.
        viewModelScope.launch {
            state.collect { if (it == ConnectionState.Authenticated) _hasSession.value = true }
        }
        // Auto-reconnect to the last active server if we have a stored session.
        viewModelScope.launch {
            val conns = store.connections.first()
            val active = store.activeUrl.first()
            val conn = conns.firstOrNull { it.url == active } ?: conns.firstOrNull()
            if (conn?.jwt != null) {
                _hasSession.value = true // show the list immediately, reconnect in background
                repo.switchTo(conn)
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
        onSuccess: () -> Unit = {},
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _login.value = LoginUiState.Loading
            _login.value = when (val r = repo.addServerAndLogin(normalized, username, password, token, insecure, alias)) {
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
        onSuccess: () -> Unit = {},
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _login.value = LoginUiState.Loading
            _login.value = when (
                val result = repo.addServerByToken(normalized, sessionToken.trim(), insecure, alias)
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
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _connectionTest.value = ConnectionTestUiState.Loading
            _connectionTest.value = when (val result = repo.testServer(normalized, username, password, token, insecure)) {
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
    ) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _connectionTest.value = ConnectionTestUiState.Loading
            _connectionTest.value = when (
                val result = repo.testServerToken(normalized, sessionToken.trim(), insecure)
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
        onResult(repo.pause(id))
    }

    fun resume(id: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(repo.resume(id))
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch {
            val url = repo.activeUrl.first() ?: return@launch
            monitorPreferenceStore.toggleFavorite(url, id)
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

    private fun refetchBeats() {
        val id = _selectedId.value ?: return
        viewModelScope.launch { _beats.value = repo.beats(id, _beatRange.value.hours) }
    }

    fun back() {
        _selectedId.value = null
        _beats.value = emptyList()
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
        if (t == MainTab.NOTIFICATIONS) refreshDistributors()
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
        _statusPage.value = StatusPageUiState.Idle
    }

    fun loadStatusPage(url: String, slug: String, insecure: Boolean = false) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _statusPage.value = StatusPageUiState.Loading
            _statusPage.value = try {
                StatusPageUiState.Loaded(statusClient.fetch(normalized, slug.trim(), insecure))
            } catch (e: Exception) {
                StatusPageUiState.Error(e.message ?: "Failed to load status page")
            }
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
