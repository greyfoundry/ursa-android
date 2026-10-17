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
        if (cached == null) renderMessage(getString(R.string.loading_status)) else renderSnapshot(cached, cached = true)
        val request = ++generation
        Thread {
            val snapshot = runBlocking { StatusPoll.fetchSnapshot(statusUrl) }
            runOnUiThread {
                if (request != generation || isFinishing || isDestroyed) return@runOnUiThread
                if (snapshot == null) {
                    if (cached == null) renderError() else renderSnapshot(cached, cached = true, offline = true)
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
        root.addView(WearUi.title(this, getString(R.string.app_name)))
        root.addView(WearUi.body(this, getString(R.string.setup_intro)))
        root.addView(WearUi.button(this, getString(R.string.setup_action), primary = true) { openSettings() })
    }

    private fun renderMessage(message: String) {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, getString(R.string.app_name)))
        root.addView(WearUi.body(this, message))
    }

    private fun renderError() {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, getString(R.string.no_data)))
        root.addView(WearUi.body(this, getString(R.string.no_data_desc)))
        root.addView(WearUi.button(this, getString(R.string.action_retry), primary = true) { load() })
        root.addView(WearUi.button(this, getString(R.string.action_settings)) { openSettings() })
    }

    private fun renderSnapshot(
        snapshot: WearSnapshot,
        cached: Boolean = false,
        offline: Boolean = false,
    ) {
        val root = WearUi.root(this)
        root.addView(WearUi.title(this, snapshot.title.ifBlank { getString(R.string.app_name) }))
        val summaryColor = when {
            snapshot.down > 0 -> WearUi.DOWN
            snapshot.pending > 0 || snapshot.maintenance > 0 -> WearUi.PENDING
            else -> WearUi.PRIMARY
        }
        root.addView(WearUi.body(this, WearDisplay.fleetSummary(resources, snapshot), summaryColor))
        root.addView(WearUi.body(this, WearDisplay.sourceSummary(resources, snapshot, cached, offline)))
        root.addView(WearUi.button(this, getString(R.string.action_refresh)) { load() })
        snapshot.attentionFirst().take(MAX_MONITORS).forEach { monitor ->
            val card = WearUi.card(this).apply {
                isClickable = true
                isFocusable = true
                contentDescription = buildString {
                    append(monitor.name)
                    append(", ${WearDisplay.statusLabel(resources, monitor.status)}")
                    append(", ${WearDisplay.metrics(resources, monitor)}")
                }
                setOnClickListener { openMonitor(monitor.id) }
            }
            card.addView(WearUi.label(this, monitor.name))
            card.addView(
                WearUi.body(
                    this,
                    "${WearDisplay.statusLabel(resources, monitor.status)} · ${WearDisplay.metrics(resources, monitor)}",
                    WearUi.statusColor(monitor.status),
                ),
            )
            monitor.group?.let { card.addView(WearUi.body(this, it)) }
            root.addView(card)
        }
        if (snapshot.monitors.size > MAX_MONITORS) {
            root.addView(
                WearUi.body(
                    this,
                    resources.getQuantityString(
                        R.plurals.showing_monitors,
                        snapshot.monitors.size,
                        MAX_MONITORS,
                        snapshot.monitors.size,
                    ),
                ),
            )
        }
        root.addView(WearUi.spacer(this, 4))
        root.addView(WearUi.button(this, getString(R.string.action_settings)) { openSettings() })
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
