package dev.astoris.ursa.core.push

import android.content.Context
import androidx.core.content.edit
import dev.astoris.ursa.core.storage.Crypto
import dev.astoris.ursa.data.model.ManagedPushNotification
import dev.astoris.ursa.data.model.Monitor
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ManagedPushMonitor(
    val id: Int,
    val name: String,
    val type: String,
    val parentId: Int?,
    val tags: List<String>,
    val active: Boolean,
)

data class ManagedPushScope(
    val serverId: String,
    val serverUrl: String,
    val monitors: List<ManagedPushMonitor>,
    val updatedAtMillis: Long,
) {
    fun monitor(id: Int?): ManagedPushMonitor? = id?.let { target -> monitors.firstOrNull { it.id == target } }
}

enum class ManagedPushScopeIssue {
    UNKNOWN_PROVIDER,
    UNKNOWN_MONITOR,
}

object ManagedPushScopePolicy {
    fun issue(
        scope: ManagedPushScope?,
        serverId: String?,
        monitorId: Int?,
    ): ManagedPushScopeIssue? {
        if (!ManagedPushNotification.isValidServerId(serverId)) return null
        if (scope == null) return ManagedPushScopeIssue.UNKNOWN_PROVIDER
        return ManagedPushScopeIssue.UNKNOWN_MONITOR.takeIf { scope.monitor(monitorId) == null }
    }
}

/** Versioned, bounded wire-free representation of the encrypted push scope registry. */
object ManagedPushScopeCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun create(
        serverId: String,
        serverUrl: String,
        monitors: List<Monitor>,
        updatedAtMillis: Long = System.currentTimeMillis(),
    ): ManagedPushScope? {
        if (!ManagedPushNotification.isValidServerId(serverId)) return null
        val safeUrl = safeServerUrl(serverUrl) ?: return null
        return ManagedPushScope(
            serverId = serverId,
            serverUrl = safeUrl,
            monitors = monitors.asSequence()
                .mapNotNull(::safeMonitor)
                .distinctBy(ManagedPushMonitor::id)
                .take(MAX_MONITORS)
                .toList(),
            updatedAtMillis = updatedAtMillis.coerceAtLeast(0L),
        )
    }

    fun encode(scope: ManagedPushScope): String = json.encodeToString(
        PersistedScope(
            version = VERSION,
            serverId = scope.serverId,
            serverUrl = scope.serverUrl,
            monitors = scope.monitors.map {
                PersistedMonitor(it.id, it.name, it.type, it.parentId, it.tags, it.active)
            },
            updatedAtMillis = scope.updatedAtMillis,
        ),
    )

    fun decode(raw: String?): ManagedPushScope? {
        val saved = raw?.let { runCatching { json.decodeFromString<PersistedScope>(it) }.getOrNull() }
            ?: return null
        if (saved.version != VERSION || !ManagedPushNotification.isValidServerId(saved.serverId)) return null
        val safeUrl = safeServerUrl(saved.serverUrl) ?: return null
        val monitors = saved.monitors.asSequence()
            .mapNotNull { row ->
                safeMonitor(
                    Monitor(
                        id = row.id,
                        name = row.name,
                        url = null,
                        type = row.type,
                        active = row.active,
                        tags = row.tags,
                        parentId = row.parentId,
                    ),
                )
            }
            .distinctBy(ManagedPushMonitor::id)
            .take(MAX_MONITORS)
            .toList()
        return ManagedPushScope(
            serverId = saved.serverId,
            serverUrl = safeUrl,
            monitors = monitors,
            updatedAtMillis = saved.updatedAtMillis.coerceAtLeast(0L),
        )
    }

    private fun safeMonitor(monitor: Monitor): ManagedPushMonitor? {
        if (monitor.id <= 0) return null
        val name = monitor.name.trim().take(MAX_NAME_LENGTH).ifBlank { "Monitor ${monitor.id}" }
        val type = monitor.type.trim().take(MAX_TYPE_LENGTH).ifBlank { "unknown" }
        val tags = monitor.tags.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.take(MAX_TAG_LENGTH) }
            .distinct()
            .take(MAX_TAGS)
            .toList()
        return ManagedPushMonitor(
            id = monitor.id,
            name = name,
            type = type,
            parentId = monitor.parentId?.takeIf { it > 0 && it != monitor.id },
            tags = tags,
            active = monitor.active,
        )
    }

    private fun safeServerUrl(raw: String): String? {
        val value = raw.trim().removeSuffix("/")
        if (value.isEmpty() || value.length > MAX_URL_LENGTH || '\r' in value || '\n' in value) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.rawAuthority.isNullOrBlank()) return null
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        return value
    }

    @Serializable
    private data class PersistedScope(
        val version: Int,
        val serverId: String,
        val serverUrl: String,
        val monitors: List<PersistedMonitor>,
        val updatedAtMillis: Long,
    )

    @Serializable
    private data class PersistedMonitor(
        val id: Int,
        val name: String,
        val type: String,
        val parentId: Int?,
        val tags: List<String>,
        val active: Boolean,
    )

    private const val VERSION = 1
    private const val MAX_MONITORS = 10_000
    private const val MAX_NAME_LENGTH = 120
    private const val MAX_TYPE_LENGTH = 80
    private const val MAX_TAGS = 32
    private const val MAX_TAG_LENGTH = 80
    private const val MAX_URL_LENGTH = 2_048
}

/** Synchronous encrypted lookup used by both the foreground setup flow and push service. */
class ManagedPushScopeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val crypto = Crypto(context.applicationContext)

    fun bind(serverId: String, serverUrl: String, monitors: List<Monitor>): ManagedPushScope? {
        val scope = ManagedPushScopeCodec.create(serverId, serverUrl, monitors) ?: return null
        return runCatching {
            prefs.edit { putString(key(serverId), crypto.encrypt(ManagedPushScopeCodec.encode(scope))) }
            scope
        }.getOrNull()
    }

    fun load(serverId: String?): ManagedPushScope? {
        val validId = serverId?.takeIf(ManagedPushNotification::isValidServerId) ?: return null
        val cipher = prefs.getString(key(validId), null) ?: return null
        return crypto.decrypt(cipher)
            ?.let(ManagedPushScopeCodec::decode)
            ?.takeIf { it.serverId == validId }
    }

    fun remove(serverId: String?): Boolean {
        val validId = serverId?.takeIf(ManagedPushNotification::isValidServerId) ?: return false
        prefs.edit { remove(key(validId)) }
        return true
    }

    /** Removes encrypted scopes mapped to a connection the user explicitly deleted. */
    fun removeServer(serverUrl: String): List<ManagedPushScope> {
        val normalized = serverUrl.trim().removeSuffix("/")
        val matches = prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_PREFIX)) return@mapNotNull null
            (value as? String)?.let(crypto::decrypt)?.let(ManagedPushScopeCodec::decode)
        }.filter { it.serverUrl == normalized }
        if (matches.isNotEmpty()) prefs.edit { matches.forEach { remove(key(it.serverId)) } }
        return matches
    }

    private companion object {
        const val PREFS = "ursa_managed_push_scopes"
        const val KEY_PREFIX = "scope:"
        fun key(serverId: String) = "$KEY_PREFIX$serverId"
    }
}
