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
import dev.astoris.ursa.core.storage.LockStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.ResponseAlertStore
import dev.astoris.ursa.core.work.CertExpiryWorker
import dev.astoris.ursa.core.work.ResponseAlertWorker
import dev.astoris.ursa.core.work.ResponseAlertUtil
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object NeedsTwoFactor : LoginUiState
    data class Error(val message: String) : LoginUiState
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
    private val certExpiryStore = CertExpiryStore(app)
    private val repo = MonitorRepository(store, cacheStore, certExpiryStore, viewModelScope)

    val monitors: StateFlow<List<Monitor>> = repo.monitors
    val state: StateFlow<ConnectionState> = repo.state
    val lastUpdated: StateFlow<Long?> = repo.lastUpdated
    val showingCache: StateFlow<Boolean> = repo.showingCache
    val connections: StateFlow<List<ServerConnection>> =
        repo.connections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _login = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

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
        if (LockStore.enabled.value) _locked.value = true // start locked if enabled
        CertExpiryWorker.schedule(getApplication()) // daily TLS-expiry reminder
        ResponseAlertWorker.schedule(getApplication()) // periodic slow-response check (#1813)
        viewModelScope.launch {
            _slowAlertEnabled.value = alertStore.isEnabled()
            _slowThresholdMs.value = alertStore.globalThresholdMs()
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
    }

    fun login(url: String, username: String, password: String, token: String = "", insecure: Boolean = false) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _login.value = LoginUiState.Loading
            _login.value = when (val r = repo.addServerAndLogin(normalized, username, password, token, insecure)) {
                is LoginResult.Success -> LoginUiState.Idle
                LoginResult.TwoFactorRequired -> LoginUiState.NeedsTwoFactor
                is LoginResult.Failure -> LoginUiState.Error(r.message)
            }
        }
    }

    fun switchTo(conn: ServerConnection) = viewModelScope.launch { repo.switchTo(conn) }
    fun pause(id: Int) = viewModelScope.launch { repo.pause(id) }
    fun resume(id: Int) = viewModelScope.launch { repo.resume(id) }

    fun select(id: Int) {
        _selectedId.value = id
        viewModelScope.launch { _beats.value = repo.beats(id) }
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
