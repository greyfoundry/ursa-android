package dev.astoris.ursa.benchmark

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.data.model.CleartextPolicy
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.ServerConnection
import kotlinx.coroutines.runBlocking

/** Shell-only deterministic data for the non-shipping Baseline Profile variant. */
class BenchmarkFixtureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != METHOD_SEED) return result(seeded = false, count = 0)
        val count = arg?.toIntOrNull()?.takeIf { it in SUPPORTED_COUNTS }
            ?: return result(seeded = false, count = 0)
        val appContext = requireNotNull(context).applicationContext
        runBlocking {
            val connections = ConnectionStore(appContext)
            val cache = MonitorCacheStore(appContext)
            connections.snapshot().forEach { connection ->
                cache.clear(connection.url)
                connections.remove(connection.url)
            }
            val connection = ServerConnection(
                url = BENCHMARK_URL,
                username = "benchmark",
                jwt = "benchmark-session",
                alias = "Benchmark fleet",
                accessProfile = AccessProfile.VIEW_ONLY,
                cleartextPolicy = CleartextPolicy.ALLOW,
            )
            cache.save(
                BENCHMARK_URL,
                MonitorSnapshot(
                    monitors = benchmarkMonitors(count),
                    updatedAt = System.currentTimeMillis(),
                    serverVersion = "2.5.6",
                ),
            )
            connections.upsert(connection)
        }
        return result(seeded = true, count = count)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun result(seeded: Boolean, count: Int) = Bundle().apply {
        putBoolean("seeded", seeded)
        putInt("count", count)
    }

    private companion object {
        const val METHOD_SEED = "seed"
        const val BENCHMARK_URL = "http://127.0.0.1:9"
        val SUPPORTED_COUNTS = setOf(100, 500, 1_000)
    }
}

private fun benchmarkMonitors(count: Int): List<Monitor> = (1..count).map { id ->
    val groupId = id - ((id - 1) % GROUP_SIZE)
    val isGroup = id == groupId
    val status = when {
        id % 17 == 0 -> MonitorStatus.DOWN
        id % 13 == 0 -> MonitorStatus.PENDING
        id % 11 == 0 -> MonitorStatus.MAINTENANCE
        else -> MonitorStatus.UP
    }
    val type = if (isGroup) "group" else TYPES[id % TYPES.size]
    Monitor(
        id = id,
        name = if (isGroup) "Group ${groupId / GROUP_SIZE + 1}" else "Monitor 1-$id",
        url = if (type == "http") "https://service-$id.example.test/health" else null,
        type = type,
        active = isGroup || id % 37 != 0,
        tags = listOf(if (id % 2 == 0) "production" else "internal", "server-1"),
        parentId = groupId.takeUnless { isGroup },
        weight = id,
        status = status,
        ping = (id * 7).mod(500),
        avgPing = (id * 5).mod(400),
        uptime24h = when (status) {
            MonitorStatus.UP -> 0.999
            MonitorStatus.DOWN -> 0.94
            MonitorStatus.PENDING -> null
            MonitorStatus.MAINTENANCE -> 1.0
        },
    )
}

private const val GROUP_SIZE = 25
private val TYPES = listOf("http", "port", "ping", "dns", "push", "keyword", "json-query")
