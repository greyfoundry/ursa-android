package dev.astoris.ursa.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.data.model.MonitorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile: a glance at whether everything is up. Reads the last-known
 * cached monitor list (no live connection), shows the count of down monitors, and
 * opens the app on tap.
 */
class StatusTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val context = applicationContext
        val url = ConnectionStore(context).activeUrl.first()
        val monitors = url?.let { MonitorCacheStore(context).load(it)?.monitors }.orEmpty()
        val down = monitors.count { it.status == MonitorStatus.DOWN }
        val tile = qsTile ?: return

        tile.label = "URSA"
        when {
            monitors.isEmpty() -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat("No data")
            }
            down > 0 -> {
                tile.state = Tile.STATE_ACTIVE
                tile.setSubtitleCompat("$down down")
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.setSubtitleCompat("All up")
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
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
