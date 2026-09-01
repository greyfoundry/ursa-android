package dev.astoris.ursa.ui.widget

import android.content.Context
import dev.astoris.ursa.core.network.KumaClient
import dev.astoris.ursa.core.network.StatusPageClient
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.MonitorSnapshot
import dev.astoris.ursa.core.storage.StatusPageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

object WidgetRefresher {
    suspend fun refresh(context: Context, appWidgetId: Int): Boolean {
        val config = WidgetStore(context).loadConfig(appWidgetId) ?: return false
        return when (config.source) {
            WidgetSource.PRIVATE_SERVER -> refreshPrivate(context, config)
            WidgetSource.PUBLIC_PAGE -> refreshPublic(context, config)
        }
    }

    private suspend fun refreshPrivate(context: Context, config: WidgetConfig): Boolean {
        val connection = ConnectionStore(context).snapshot().firstOrNull { it.url == config.sourceId }
            ?: return false
        val token = connection.jwt ?: return false
        val client = KumaClient(connection.url, connection.insecure, connection.headers)
        return try {
            client.connect()
            if (!client.loginByToken(token)) return false
            val monitors = withTimeoutOrNull(20_000L) {
                client.monitors.first { it.isNotEmpty() }.values.toList()
            } ?: return false
            MonitorCacheStore(context).save(
                connection.url,
                MonitorSnapshot(monitors, System.currentTimeMillis()),
            )
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            client.disconnect()
        }
    }

    private suspend fun refreshPublic(context: Context, config: WidgetConfig): Boolean {
        val page = StatusPageStore(context).pages.first().firstOrNull { it.id == config.sourceId }
            ?: return false
        val connections = ConnectionStore(context).snapshot()
        val headers = connections.firstOrNull { normalize(it.url) == normalize(page.url) }?.headers.orEmpty()
        val client = StatusPageClient()
        return try {
            val view = client.fetch(page.url, page.slug, page.insecure, headers)
            WidgetStore(context).savePublicSnapshot(
                page.id,
                WidgetData.publicSnapshot(view, config.selectedMonitorIds),
            )
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }

    private fun normalize(raw: String): String = raw.trim().removeSuffix("/")
}
