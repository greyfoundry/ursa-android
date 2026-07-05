package dev.astoris.ursa.wear

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

/**
 * Minimal watch config: enter the public Kuma status-page URL the tile polls. Kept as a
 * plain Activity (no Compose/GMS) to stay light and FOSS. Entering a URL on a watch is
 * clunky by nature; this is a one-time setup.
 */
class WearConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        val field = findViewById<EditText>(R.id.status_url)
        field.setText(WearPrefs.statusUrl(this) ?: "")

        findViewById<Button>(R.id.save).setOnClickListener {
            WearPrefs.setStatusUrl(this, field.text.toString())
            Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
