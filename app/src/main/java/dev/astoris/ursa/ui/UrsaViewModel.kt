package dev.astoris.ursa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.data.model.LoginResult
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.data.repository.MonitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object NeedsTwoFactor : LoginUiState
    data class Error(val message: String) : LoginUiState
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

    init {
        // Auto-reconnect to the last active server if we have a stored session.
        viewModelScope.launch {
            val conns = store.connections.first()
            val active = store.activeUrl.first()
            val conn = conns.firstOrNull { it.url == active } ?: conns.firstOrNull()
            if (conn?.jwt != null) repo.switchTo(conn)
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

    fun resetLogin() { _login.value = LoginUiState.Idle }

    override fun onCleared() {
        repo.disconnect()
    }

    private fun normalizeUrl(raw: String): String {
        var u = raw.trim().removeSuffix("/")
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }
}
