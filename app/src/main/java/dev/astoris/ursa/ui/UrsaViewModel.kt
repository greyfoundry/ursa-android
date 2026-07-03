package dev.astoris.ursa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.StatusPageClient
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.data.model.CertInfo
import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.data.model.StatusPageView
import dev.astoris.ursa.data.repository.MonitorRepository
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

class UrsaViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ConnectionStore(app)
    private val repo = MonitorRepository(store, viewModelScope)

    val monitors: StateFlow<List<Monitor>> = repo.monitors
    val state: StateFlow<ConnectionState> = repo.state
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

    private val statusClient = StatusPageClient()
    private val _statusPageMode = MutableStateFlow(false)
    val statusPageMode: StateFlow<Boolean> = _statusPageMode.asStateFlow()
    private val _statusPage = MutableStateFlow<StatusPageUiState>(StatusPageUiState.Idle)
    val statusPage: StateFlow<StatusPageUiState> = _statusPage.asStateFlow()

    init {
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

    fun login(url: String, username: String, password: String, token: String = "") {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _login.value = LoginUiState.Loading
            _login.value = when (val r = repo.addServerAndLogin(normalized, username, password, token)) {
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

    fun enterStatusPage() { _statusPageMode.value = true }
    fun exitStatusPage() {
        _statusPageMode.value = false
        _statusPage.value = StatusPageUiState.Idle
    }

    fun loadStatusPage(url: String, slug: String) {
        val normalized = normalizeUrl(url)
        viewModelScope.launch {
            _statusPage.value = StatusPageUiState.Loading
            _statusPage.value = try {
                StatusPageUiState.Loaded(statusClient.fetch(normalized, slug.trim()))
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
