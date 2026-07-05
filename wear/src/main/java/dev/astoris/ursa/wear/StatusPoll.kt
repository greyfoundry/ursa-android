package dev.astoris.ursa.wear

import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Polls a public Kuma status page (no auth, no Google Play Services) and reduces it to
 * up/down/total counts for the tile. Wear OS routes this network call through the phone
 * when the watch has no direct connectivity, so no Data Layer is needed.
 */
object StatusPoll {

    data class Counts(val up: Int, val down: Int, val total: Int)

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 8_000 }
        }
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Counts for [statusUrl] (e.g. https://kuma.example.com/status/home), or null on
     *  a bad URL or fetch failure. */
    suspend fun fetch(statusUrl: String): Counts? {
        val (base, slug) = parse(statusUrl) ?: return null
        return runCatching {
            val body: String = client.get("$base/api/status-page/heartbeat/$slug").body()
            val hb = json.parseToJsonElement(body).jsonObject["heartbeatList"]?.jsonObject
                ?: return@runCatching null
            var up = 0
            var down = 0
            for ((_, beats) in hb) {
                val status = beats.jsonArray.lastOrNull()?.jsonObject
                    ?.get("status")?.jsonPrimitive?.intOrNull
                when (status) {
                    1 -> up++
                    0 -> down++
                }
            }
            Counts(up = up, down = down, total = hb.size)
        }.getOrNull()
    }

    /** scheme://host[:port] plus the last path segment (the status-page slug). */
    private fun parse(statusUrl: String): Pair<String, String>? {
        val uri = runCatching { Uri.parse(statusUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        val base = if (uri.port != -1) "$scheme://$host:${uri.port}" else "$scheme://$host"
        val slug = uri.pathSegments?.lastOrNull()?.ifBlank { null } ?: return null
        return base to slug
    }
}
