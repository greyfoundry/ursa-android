package dev.astoris.ursa

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.astoris.ursa.core.storage.DisplayPreferenceStore
import dev.astoris.ursa.core.storage.ScreenshotProtectionMode
import dev.astoris.ursa.core.storage.shouldSecure
import dev.astoris.ursa.ui.UrsaApp
import dev.astoris.ursa.ui.AppDeepLink
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.theme.UrsaTheme
import kotlinx.coroutines.launch

// FragmentActivity (not ComponentActivity) so BiometricPrompt can attach to it.
class MainActivity : FragmentActivity() {

    private val vm: UrsaViewModel by viewModels()
    private var screenshotProtection = ScreenshotProtectionMode.ALWAYS
    private var sensitiveContent = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start secure so restored sensitive content cannot appear before preferences load.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        DisplayPreferenceStore.load(this)
        screenshotProtection = DisplayPreferenceStore.screenshotProtection.value
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.screenshotProtection.collect { mode ->
                    screenshotProtection = mode
                    applyScreenshotProtection()
                }
            }
        }
        enableEdgeToEdge()
        val launchIntent = intent
        setContent {
            val dynamicColor by vm.dynamicColorEnabled.collectAsStateWithLifecycle()
            UrsaTheme(dynamicColor = dynamicColor) {
                UrsaApp(vm, onSensitiveContentChanged = ::setSensitiveContent)
                // Apply cold-start routes after Navigation has restored its saved stack.
                LaunchedEffect(launchIntent) { handleRoute(launchIntent) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRoute(intent)
    }

    /** Validated credential-free routes from shortcuts, notifications, widgets, and links. */
    private fun handleRoute(intent: Intent?) {
        AppDeepLink.parse(intent?.dataString)?.let(vm::handleRoute)
    }

    private fun setSensitiveContent(sensitive: Boolean) {
        sensitiveContent = sensitive
        applyScreenshotProtection()
    }

    private fun applyScreenshotProtection() {
        if (screenshotProtection.shouldSecure(sensitiveContent)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
