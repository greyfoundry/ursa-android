package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.StatusGroupView
import dev.astoris.ursa.data.model.StatusHeartbeatResponse
import dev.astoris.ursa.data.model.StatusMonitorView
import dev.astoris.ursa.data.model.StatusPageResponse
import dev.astoris.ursa.data.model.StatusPageView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Fetches a public Uptime Kuma status page (no auth). Combines the config endpoint
 * (groups + monitors) with the heartbeat endpoint (latest status + 24h uptime) into
 * a flat [StatusPageView]. Shapes verified against Kuma 2.5.3.
 */
class StatusPageClient {

    // Secure requests share a client. Self-signed requests get one certificate pin
    // per base URL so separate servers cannot influence one another.
    private val clients = HashMap<String, HttpClient>()

    private fun client(baseUrl: String, insecure: Boolean): HttpClient =
        clients.getOrPut(if (insecure) "self-signed:$baseUrl" else "secure") {
            HttpClient(OkHttp) {
                engine { if (insecure) preconfigured = TlsTrust.sessionPinnedClient() }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout) { requestTimeoutMillis = 15_000 }
            }
        }

    suspend fun fetch(baseUrl: String, slug: String, insecure: Boolean = false): StatusPageView {
        val base = baseUrl.trim().removeSuffix("/")
        val http = client(base, insecure)
        val page: StatusPageResponse = http.get("$base/api/status-page/$slug").body()
        val hb: StatusHeartbeatResponse = http.get("$base/api/status-page/heartbeat/$slug").body()

        val groups = page.publicGroupList.map { group ->
            StatusGroupView(
                name = group.name,
                monitors = group.monitorList.map { m ->
                    val latest = hb.heartbeatList[m.id.toString()]?.lastOrNull()
                    StatusMonitorView(
                        id = m.id,
                        name = m.name,
                        status = MonitorStatus.from(latest?.status ?: 2),
                        uptime24h = hb.uptimeList["${m.id}_24"],
                    )
                },
            )
        }
        return StatusPageView(page.config.title, page.config.description, groups)
    }

    fun close() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
    }
}
