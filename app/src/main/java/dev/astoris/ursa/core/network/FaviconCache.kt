package dev.astoris.ursa.core.network

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import dev.astoris.ursa.core.storage.FaviconStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort favicon fetcher for monitor cards. Only small image responses are decoded,
 * cache entries are bounded, and persisted bytes are encrypted at rest. Internal hosts
 * with self-signed certificates fall back to the status circle rather than weakening TLS.
 */
object FaviconCache {

    private const val MAX_RESPONSE_BYTES = 512 * 1024
    private const val MAX_DECODE_DIMENSION = 2_048
    private const val MAX_MEMORY_ENTRIES = 32
    private val candidates = listOf("/apple-touch-icon.png", "/favicon.png", "/favicon.ico")
    private val misses = Collections.synchronizedSet(mutableSetOf<String>())
    private val hits = object : LinkedHashMap<String, ImageBitmap>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) =
            size > MAX_MEMORY_ENTRIES
    }

    private var persistentStore: FaviconStore? = null

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 8_000 }
        }
    }

    /** Favicon for the host of [monitorUrl], or null when none is usable. */
    suspend fun get(context: Context, monitorUrl: String): ImageBitmap? {
        val origin = originOf(monitorUrl) ?: return null
        synchronized(hits) { hits[origin] }?.let { return it }
        if (origin in misses) return null

        return withContext(Dispatchers.IO) {
            val store = storeFor(context)
            store.load(origin)?.let(::decodeSafely)?.also { cache(origin, it) }?.let { return@withContext it }

            for (path in candidates) {
                val result = runCatching {
                    val response = client.get("$origin$path")
                    if (response.contentLength()?.let { it > MAX_RESPONSE_BYTES } == true) return@runCatching null
                    if (response.contentType()?.match(ContentType.Image.Any) != true) return@runCatching null
                    val bytes: ByteArray = response.body()
                    if (bytes.size > MAX_RESPONSE_BYTES) return@runCatching null
                    decodeSafely(bytes)?.also { store.save(origin, bytes) }
                }.getOrNull()
                if (result != null) {
                    cache(origin, result)
                    return@withContext result
                }
            }
            misses.add(origin)
            null
        }
    }

    private fun cache(origin: String, icon: ImageBitmap) = synchronized(hits) { hits[origin] = icon }

    private fun storeFor(context: Context): FaviconStore = synchronized(this) {
        persistentStore ?: FaviconStore(context.applicationContext).also { persistentStore = it }
    }

    private fun decodeSafely(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_DECODE_DIMENSION || bounds.outHeight !in 1..MAX_DECODE_DIMENSION) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
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
