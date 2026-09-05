package dev.astoris.ursa.wear

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Minimal watch config. Public monitoring remains standalone; private actions are paired
 * from the signed phone app so credentials are never typed on the watch.
 */
class WearConfigActivity : Activity() {

    private lateinit var pairedState: TextView
    private lateinit var clearPairing: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearUi.secure(this)
        setContentView(R.layout.activity_config)

        val statusField = findViewById<EditText>(R.id.status_url)
        pairedState = findViewById(R.id.paired_state)
        clearPairing = findViewById(R.id.clear_pairing)
        statusField.setText(WearPrefs.statusUrl(this) ?: "")
        renderPairingState()

        clearPairing.setOnClickListener {
            WearPrefs.clearPairedSession(this)
            Toast.makeText(this, R.string.config_pairing_cleared, Toast.LENGTH_SHORT).show()
            renderPairingState()
            WearSurfaceUpdates.request(this)
        }

        findViewById<Button>(R.id.save).setOnClickListener {
            val statusUrl = statusField.text.toString().trim()
            if (StatusPageAddress.parse(statusUrl) == null) {
                statusField.error = getString(R.string.config_invalid_status_url)
                return@setOnClickListener
            }
            WearPrefs.setStatusUrl(this, statusUrl)
            WearSnapshotMemory.latest = null
            WearSurfaceUpdates.request(this)
            Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::pairedState.isInitialized) renderPairingState()
    }

    private fun renderPairingState() {
        val paired = WearPrefs.pairedSession(this)
        pairedState.text = if (paired == null) {
            getString(R.string.config_not_paired)
        } else {
            getString(R.string.config_paired_to, paired.serverName)
        }
        clearPairing.isEnabled = paired != null
    }
}
