package dev.astoris.ursa

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import dev.astoris.ursa.ui.UrsaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Treat the whole app as sensitive: block screenshots/screen recording and
        // keep monitor data + server URLs out of the recents thumbnail.
        // (OWASP MASVS-PLATFORM-3; backlog item R1.)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                UrsaApp()
            }
        }
    }
}
