package dev.astoris.ursa.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.astoris.ursa.ui.UrsaViewModel

/**
 * Full-screen gate shown when the app lock is enabled and the app is locked. Prompts
 * for biometric / device credential automatically, with a manual retry button.
 */
@Composable
fun LockScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val activity = LocalActivity.current as? FragmentActivity

    fun authenticate() {
        activity?.let { BiometricGate.prompt(it, onSuccess = { vm.unlock() }) }
    }

    // Prompt once when the lock screen appears.
    LaunchedEffect(Unit) { authenticate() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("URSA is locked", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Unlock to view your monitors.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = { authenticate() }) { Text("Unlock") }
    }
}
