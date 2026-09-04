package dev.astoris.ursa.wear

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.runBlocking

class WearMonitorDetailActivity : Activity() {
    private val monitorId: Int by lazy {
        intent.data?.lastPathSegment?.toIntOrNull()?.takeIf { it > 0 } ?: -1
    }
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearUi.secure(this)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onDestroy() {
        generation++
        super.onDestroy()
    }

    private fun load() {
        if (monitorId <= 0) {
            renderMissing()
            return
        }
        WearSnapshotMemory.latest?.monitors?.firstOrNull { it.id == monitorId }?.let(::render)
            ?: renderLoading()
        val statusUrl = WearPrefs.statusUrl(this) ?: return renderMissing()
        val request = ++generation
        Thread {
            val snapshot = runBlocking { StatusPoll.fetchSnapshot(statusUrl) }
            runOnUiThread {
                if (request != generation || isFinishing || isDestroyed) return@runOnUiThread
                if (snapshot != null) WearSnapshotMemory.latest = snapshot
                snapshot?.monitors?.firstOrNull { it.id == monitorId }?.let(::render)
                    ?: renderMissing()
            }
        }.start()
    }

    private fun renderLoading() {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, "Monitor"))
        root.addView(WearUi.body(this, "Loading details…"))
    }

    private fun renderMissing() {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, "Monitor unavailable"))
        root.addView(WearUi.body(this, "This monitor is not on the configured public status page."))
        root.addView(WearUi.button(this, "Back") { finish() })
    }

    private fun render(monitor: WearMonitor) {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, monitor.name))
        root.addView(
            WearUi.body(
                this,
                WearDisplay.statusLabel(monitor.status),
                WearUi.statusColor(monitor.status),
            ),
        )
        root.addView(WearUi.label(this, WearDisplay.metrics(monitor)))
        monitor.group?.let {
            root.addView(WearUi.spacer(this, 6))
            root.addView(WearUi.body(this, "Group · $it"))
        }
        if (monitor.tags.isNotEmpty()) {
            root.addView(WearUi.spacer(this, 6))
            root.addView(WearUi.body(this, "Tags · ${monitor.tags.joinToString(" · ")}"))
        }
        if (WearPrefs.actionConfig(this) != null) {
            root.addView(WearUi.spacer(this, 8))
            root.addView(WearUi.button(this, getString(R.string.action_pause)) {
                confirmAction(monitor, WearMonitorAction.PAUSE)
            })
            root.addView(WearUi.button(this, getString(R.string.action_resume), primary = true) {
                confirmAction(monitor, WearMonitorAction.RESUME)
            })
        } else {
            root.addView(WearUi.spacer(this, 8))
            root.addView(WearUi.body(this, "Pause/resume is off. Pair this watch from URSA Settings on your phone."))
        }
        root.addView(WearUi.button(this, "Back") { finish() })
    }

    private fun confirmAction(monitor: WearMonitor, action: WearMonitorAction) {
        val title = when (action) {
            WearMonitorAction.PAUSE -> getString(R.string.action_confirm_pause, monitor.name)
            WearMonitorAction.RESUME -> getString(R.string.action_confirm_resume, monitor.name)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(R.string.action_confirm_desc)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(
                if (action == WearMonitorAction.PAUSE) R.string.action_pause else R.string.action_resume,
            ) { _, _ -> executeAction(action) }
            .show()
    }

    private fun executeAction(action: WearMonitorAction) {
        val config = WearPrefs.actionConfig(this) ?: return
        Toast.makeText(this, "Contacting Kuma…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runBlocking { WearActionClient.execute(config, monitorId, action) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                if (result.success) load()
            }
        }.start()
    }
}
