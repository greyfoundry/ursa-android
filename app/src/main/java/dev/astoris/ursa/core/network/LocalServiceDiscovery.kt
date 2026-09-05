package dev.astoris.ursa.core.network

import android.content.Context
import android.net.nsd.DiscoveryRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocalServiceProtocol(
    val scheme: String,
    val serviceType: String,
) {
    HTTP("http", "_http._tcp"),
    HTTPS("https", "_https._tcp"),
}

data class LocalServiceCandidate(
    val id: String,
    val name: String,
)

data class LocalServiceAddress(
    val name: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val url: String,
)

enum class LocalServiceDiscoveryError {
    START_FAILED,
    RESOLVE_FAILED,
    INVALID_ADDRESS,
}

sealed interface LocalServiceDiscoveryState {
    data object Idle : LocalServiceDiscoveryState
    data class Discovering(
        val protocol: LocalServiceProtocol,
        val candidates: List<LocalServiceCandidate> = emptyList(),
    ) : LocalServiceDiscoveryState
    data class Resolving(val name: String) : LocalServiceDiscoveryState
    data class Selected(val address: LocalServiceAddress) : LocalServiceDiscoveryState
    data class Error(val reason: LocalServiceDiscoveryError) : LocalServiceDiscoveryState
}

fun localServiceUrl(scheme: String, rawHost: String, port: Int): String? {
    if (scheme !in setOf("http", "https") || port !in 1..65_535) return null
    val host = rawHost.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
    if (host.isBlank() || host.any { it.isWhitespace() || it in "/\\?#@" }) return null
    val authority = if (':' in host) "[$host]" else host
    val defaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
    return "$scheme://$authority${if (defaultPort) "" else ":$port"}"
}

/** Foreground-only, user-triggered DNS-SD discovery for monitor endpoint prefilling. */
class LocalServiceDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val _state = MutableStateFlow<LocalServiceDiscoveryState>(LocalServiceDiscoveryState.Idle)
    val state: StateFlow<LocalServiceDiscoveryState> = _state.asStateFlow()

    private var generation = 0
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    // Kept as Any so loading this class on API 26-33 never resolves the API 34 callback type.
    private var pickerCallback: Any? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val candidates = linkedMapOf<String, NsdServiceInfo>()
    private var activeProtocol = LocalServiceProtocol.HTTP

    fun start(protocol: LocalServiceProtocol) {
        stop()
        activeProtocol = protocol
        val run = generation
        _state.value = LocalServiceDiscoveryState.Discovering(protocol)
        runCatching {
            if (Build.VERSION.SDK_INT >= 37) startPicker(protocol, run) else startLegacy(protocol, run)
        }.onFailure {
            if (run == generation) fail(LocalServiceDiscoveryError.START_FAILED)
        }
    }

    fun select(candidateId: String) {
        val service = candidates[candidateId] ?: return
        val run = generation
        _state.value = LocalServiceDiscoveryState.Resolving(service.serviceName)
        @Suppress("DEPRECATION")
        runCatching {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    if (run == generation) fail(LocalServiceDiscoveryError.RESOLVE_FAILED)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (run == generation) finish(serviceInfo)
                }
            })
        }.onFailure {
            if (run == generation) fail(LocalServiceDiscoveryError.RESOLVE_FAILED)
        }
    }

    fun consumeSelection() {
        if (_state.value is LocalServiceDiscoveryState.Selected) _state.value = LocalServiceDiscoveryState.Idle
    }

    fun stop() {
        generation += 1
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        pickerCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= 37) runCatching {
                nsdManager.unregisterServiceInfoCallback(callback as NsdManager.ServiceInfoCallback)
            }
        }
        discoveryListener = null
        pickerCallback = null
        candidates.clear()
        multicastLock?.let { lock -> if (lock.isHeld) lock.release() }
        multicastLock = null
        _state.value = LocalServiceDiscoveryState.Idle
    }

    @RequiresApi(37)
    private fun startPicker(protocol: LocalServiceProtocol, run: Int) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                if (run == generation) finish(serviceInfo)
            }

            override fun onServiceLost() = Unit

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                if (run == generation) fail(LocalServiceDiscoveryError.START_FAILED)
            }

            override fun onServiceInfoCallbackUnregistered() {
                if (run == generation && _state.value is LocalServiceDiscoveryState.Discovering) {
                    _state.value = LocalServiceDiscoveryState.Idle
                }
            }
        }
        pickerCallback = callback
        val request = DiscoveryRequest.Builder(protocol.serviceType)
            .setFlags(DiscoveryRequest.FLAG_SHOW_PICKER)
            .build()
        nsdManager.registerServiceInfoCallback(request, Runnable::run, callback)
    }

    private fun startLegacy(protocol: LocalServiceProtocol, run: Int) {
        multicastLock = wifiManager.createMulticastLock("ursa-local-service-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (run != generation) return
                val id = "${serviceInfo.serviceName}\u0000${serviceInfo.serviceType}"
                candidates[id] = serviceInfo
                _state.value = LocalServiceDiscoveryState.Discovering(
                    protocol,
                    candidates.map { (key, value) -> LocalServiceCandidate(key, value.serviceName) }
                        .sortedBy { it.name.lowercase() },
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (run != generation) return
                val id = "${serviceInfo.serviceName}\u0000${serviceInfo.serviceType}"
                candidates.remove(id)
                _state.value = LocalServiceDiscoveryState.Discovering(
                    protocol,
                    candidates.map { (key, value) -> LocalServiceCandidate(key, value.serviceName) }
                        .sortedBy { it.name.lowercase() },
                )
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (run == generation) fail(LocalServiceDiscoveryError.START_FAILED)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
        }
        discoveryListener = listener
        nsdManager.discoverServices(protocol.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun finish(serviceInfo: NsdServiceInfo) {
        val host = if (Build.VERSION.SDK_INT >= 34) {
            serviceInfo.hostAddresses.firstOrNull { it.hostAddress?.contains(':') == false }
                ?: serviceInfo.hostAddresses.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            serviceInfo.host
        }?.hostAddress
        val url = host?.let { localServiceUrl(activeProtocol.scheme, it, serviceInfo.port) }
        if (host == null || url == null) {
            fail(LocalServiceDiscoveryError.INVALID_ADDRESS)
            return
        }
        releaseOperations()
        _state.value = LocalServiceDiscoveryState.Selected(
            LocalServiceAddress(
                name = serviceInfo.serviceName,
                scheme = activeProtocol.scheme,
                host = host.substringBefore('%'),
                port = serviceInfo.port,
                url = url,
            ),
        )
    }

    private fun fail(reason: LocalServiceDiscoveryError) {
        releaseOperations()
        _state.value = LocalServiceDiscoveryState.Error(reason)
    }

    private fun releaseOperations() {
        discoveryListener?.let { listener -> runCatching { nsdManager.stopServiceDiscovery(listener) } }
        discoveryListener = null
        pickerCallback = null
        candidates.clear()
        multicastLock?.let { lock -> if (lock.isHeld) lock.release() }
        multicastLock = null
    }
}
