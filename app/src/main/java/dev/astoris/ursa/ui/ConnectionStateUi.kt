package dev.astoris.ursa.ui

import androidx.annotation.StringRes
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState

@get:StringRes
val ConnectionState.labelRes: Int
    get() = when (this) {
        ConnectionState.Disconnected -> R.string.connection_offline
        ConnectionState.Connecting -> R.string.connection_connecting
        ConnectionState.Connected -> R.string.connection_signing_in
        ConnectionState.Authenticated -> R.string.connection_online
        ConnectionState.AuthenticationFailed -> R.string.connection_auth_failed
        ConnectionState.Error -> R.string.connection_unavailable
    }
