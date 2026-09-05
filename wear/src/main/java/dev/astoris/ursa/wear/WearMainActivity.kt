package dev.astoris.ursa.wear

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import kotlinx.coroutines.runBlocking

class WearMainActivity : Activity() {
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
        val statusUrl = WearPrefs.statusUrl(this)
        if (statusUrl == null) {
            renderSetup()
            return
        }
        val cached = WearSnapshotMemory.latest
        if (cached == null) renderMessage("Loading status…") else renderSnapshot(cached)
        val request = ++generation
        Thread {
            val snapshot = runBlocking { StatusPoll.fetchSnapshot(statusUrl) }
            runOnUiThread {
                if (request != generation || isFinishing || isDestroyed) return@runOnUiThread
                if (snapshot == null) {
                    if (cached == null) renderError()
                } else {
                    WearSnapshotMemory.latest = snapshot
                    val requestedId = requestedMonitorId()
                    if (requestedId != null && snapshot.monitors.any { it.id == requestedId }) {
                        intent.data = null
                        openMonitor(requestedId)
                    } else {
                        renderSnapshot(snapshot)
                    }
                }
            }
        }.start()
    }

    private fun renderSetup() {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, "URSA"))
        root.addView(WearUi.body(this, "Add a published Kuma status page to begin."))
        root.addView(WearUi.button(this, "Set up", primary = true) { openSettings() })
    }

    private fun renderMessage(message: String) {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, "URSA"))
        root.addView(WearUi.body(this, message))
    }

    private fun renderError() {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, "No data"))
        root.addView(WearUi.body(this, "Check the watch connection and published status-page URL."))
        root.addView(WearUi.button(this, "Retry", primary = true) { load() })
        root.addView(WearUi.button(this, "Settings") { openSettings() })
    }

    private fun renderSnapshot(snapshot: WearSnapshot) {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, snapshot.title))
        val summaryColor = when {
            snapshot.down > 0 -> WearUi.DOWN
            snapshot.pending > 0 || snapshot.maintenance > 0 -> WearUi.PENDING
            else -> WearUi.PRIMARY
        }
        root.addView(WearUi.body(this, WearDisplay.fleetSummary(snapshot), summaryColor))
        root.addView(WearUi.button(this, "Refresh") { load() })
        snapshot.attentionFirst().take(MAX_MONITORS).forEach { monitor ->
            val card = WearUi.card(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = buildString {
                    append(monitor.name)
                    append(", ${WearDisplay.statusLabel(monitor.status)}")
                    append(", ${WearDisplay.metrics(monitor)}")
                }
                setOnClickListener { openMonitor(monitor.id) }
            }
            card.addView(WearUi.label(this, monitor.name))
            card.addView(
                WearUi.body(
                    this,
                    "${WearDisplay.statusLabel(monitor.status)} · ${WearDisplay.metrics(monitor)}",
                    WearUi.statusColor(monitor.status),
                ),
            )
            monitor.group?.let { card.addView(WearUi.body(this, it)) }
            root.addView(card)
        }
        if (snapshot.monitors.size > MAX_MONITORS) {
            root.addView(WearUi.body(this, "Showing $MAX_MONITORS of ${snapshot.monitors.size} monitors"))
        }
        root.addView(WearUi.spacer(this, 4))
        root.addView(WearUi.button(this, "Settings") { openSettings() })
    }

    private fun openMonitor(id: Int) {
        startActivity(
            Intent(this, WearMonitorDetailActivity::class.java).apply {
                data = "ursa://wear/monitor/$id".toUri()
            },
        )
    }

    private fun openSettings() {
        startActivity(Intent(this, WearConfigActivity::class.java))
    }

    private fun requestedMonitorId(): Int? = intent.data
        ?.takeIf { it.scheme == "ursa" && it.host == "wear" }
        ?.lastPathSegment?.toIntOrNull()?.takeIf { it > 0 }

    private companion object {
        const val MAX_MONITORS = 100
    }
}
