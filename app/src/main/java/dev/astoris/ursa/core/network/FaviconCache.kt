package dev.astoris.ursa.core.network

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Best-effort favicon fetcher for the monitor list (upstream #443). Icons are decorative
 * only, so this stays deliberately light: standard TLS (self-signed internal hosts just
 * fall back to the status circle), an in-memory cache, and a miss-set so a host without a
 * usable icon is not retried. Android's BitmapFactory cannot decode ICO, so PNG
 * candidates are tried first and the classic favicon.ico last.
 */
object FaviconCache {

    private val hits = ConcurrentHashMap<String, ImageBitmap>()
    private val misses = Collections.synchronizedSet(mutableSetOf<String>())
    private val candidates = listOf("/apple-touch-icon.png", "/favicon.png", "/favicon.ico")

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 8_000 }
        }
    }

    /** Favicon for the host of [monitorUrl], or null when none is usable. */
    suspend fun get(monitorUrl: String): ImageBitmap? {
        val origin = originOf(monitorUrl) ?: return null
        hits[origin]?.let { return it }
        if (origin in misses) return null
        return withContext(Dispatchers.IO) {
            for (path in candidates) {
                val bitmap = runCatching {
                    val bytes: ByteArray = client.get("$origin$path").body()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
                if (bitmap != null) {
                    hits[origin] = bitmap
                    return@withContext bitmap
                }
            }
            misses.add(origin)
            null
        }
    }

    /** scheme://host[:port] for http(s) URLs only; null otherwise. */
    private fun originOf(monitorUrl: String): String? {
        val uri = runCatching { monitorUrl.trim().toUri() }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        return if (uri.port != -1) "$scheme://$host:${uri.port}" else "$scheme://$host"
    }
}
