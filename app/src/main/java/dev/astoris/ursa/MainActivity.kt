package dev.astoris.ursa

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import dev.astoris.ursa.ui.UrsaApp
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.theme.UrsaTheme

// FragmentActivity (not ComponentActivity) so BiometricPrompt can attach to it.
class MainActivity : FragmentActivity() {

    private val vm: UrsaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Treat the whole app as sensitive: block screenshots/screen recording and
        // keep monitor data + server URLs out of the recents thumbnail.
        // (OWASP MASVS-PLATFORM-3; backlog item R1.) Skipped in debuggable builds so
        // screenshots work during development; always on in release.
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        enableEdgeToEdge()
        handleRoute(intent)
        setContent {
            UrsaTheme {
                UrsaApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRoute(intent)
    }

    /** App-shortcut deep links: ursa://push, ursa://settings. */
    private fun handleRoute(intent: Intent?) {
        when (intent?.data?.host) {
            "push" -> vm.enterPush()
            "settings" -> vm.enterSettings()
        }
    }
}
