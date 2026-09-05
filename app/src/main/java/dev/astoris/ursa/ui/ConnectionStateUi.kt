package dev.astoris.ursa.ui

import androidx.annotation.StringRes
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.ConnectionState
import dev.astoris.ursa.core.network.ConnectionFailureReason

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

@get:StringRes
val ConnectionFailureReason.actionRes: Int
    get() = when (this) {
        ConnectionFailureReason.DEVICE_OFFLINE -> R.string.connection_reason_device
        ConnectionFailureReason.SERVER_UNREACHABLE -> R.string.connection_reason_server
        ConnectionFailureReason.AUTHENTICATION -> R.string.connection_reason_auth
        ConnectionFailureReason.CERTIFICATE -> R.string.connection_reason_certificate
        ConnectionFailureReason.INCOMPATIBLE_RESPONSE -> R.string.connection_reason_incompatible
        ConnectionFailureReason.UNKNOWN -> R.string.connection_reason_unknown
    }
