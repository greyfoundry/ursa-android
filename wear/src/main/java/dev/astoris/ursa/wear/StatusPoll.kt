package dev.astoris.ursa.wear

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get

/**
 * Polls a public Kuma status page without authentication and reduces it to
 * up/down/total counts for compact surfaces. Wear OS routes this network call through the phone
 * when the watch has no direct connectivity, so no Data Layer is needed.
 */
object StatusPoll {
    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 8_000 }
        }
    }
    suspend fun fetchSnapshot(statusUrl: String): WearSnapshot? {
        val address = StatusPageAddress.parse(statusUrl) ?: return null
        return runCatching {
            val config: String = client.get(address.configUrl).body()
            val heartbeat: String = client.get(address.heartbeatUrl).body()
            WearStatusParser.parse(config, heartbeat)
        }.getOrNull()
    }
}
