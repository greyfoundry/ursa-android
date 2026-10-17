package dev.astoris.ursa.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.net.toUri
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.R
import dev.astoris.ursa.core.push.ManagedPushScopeStore
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.LockStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.data.repository.FleetRepository
import dev.astoris.ursa.ui.AppDeepLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: a glance at whether everything is up. Reads the last-known
 * encrypted fleet cache (no live connection), labels its freshness, and opens the
 * guarded attention filter when action is required.
 */
class StatusTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var destination: String? = null

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val context = applicationContext
        LockStore.load(context)
        val snapshot = runCatching {
            FleetRepository(
                connectionStore = ConnectionStore(context),
                monitorCacheStore = MonitorCacheStore(context),
                managedPushScopeStore = ManagedPushScopeStore(context),
            ).loadCached(contentUnlocked = !LockStore.enabled.value)
        }.getOrNull()
        val data = statusTileData(snapshot)
        destination = data.targetServerUrl?.let(AppDeepLink::attention)
        val tile = qsTile ?: return

        tile.label = getString(R.string.tile_label)
        when (data.kind) {
            StatusTileKind.LOCKED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat(getString(R.string.tile_locked))
            }
            StatusTileKind.NO_DATA -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat(getString(R.string.tile_no_data))
            }
            StatusTileKind.URGENT -> {
                tile.state = Tile.STATE_ACTIVE
                val urgent = resources.getQuantityString(
                    R.plurals.tile_urgent,
                    data.urgentMonitors,
                    data.urgentMonitors,
                )
                val servers = resources.getQuantityString(
                    R.plurals.tile_affected_servers,
                    data.affectedServers,
                    data.affectedServers,
                )
                tile.setSubtitleCompat("$urgent; $servers")
            }
            StatusTileKind.OFFLINE -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat(
                    resources.getQuantityString(
                        R.plurals.tile_offline,
                        data.unavailableServers,
                        data.unavailableServers,
                    ),
                )
            }
            StatusTileKind.STALE -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat(
                    resources.getQuantityString(R.plurals.tile_stale, data.staleServers, data.staleServers),
                )
            }
            StatusTileKind.MAINTENANCE -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat(
                    resources.getQuantityString(
                        R.plurals.tile_maintenance,
                        data.maintenanceMonitors,
                        data.maintenanceMonitors,
                    ),
                )
            }
            StatusTileKind.CLEAR -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat(getString(R.string.tile_clear))
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            destination?.let {
                action = Intent.ACTION_VIEW
                data = it.toUri()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun Tile.setSubtitleCompat(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = text
    }
}
