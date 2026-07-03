package dev.astoris.ursa.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.ui.LoginUiState
import dev.astoris.ursa.ui.UrsaViewModel

@Composable
fun LoginScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val loginState by vm.login.collectAsStateWithLifecycle()
    val loading = loginState is LoginUiState.Loading
    val needs2fa = loginState is LoginUiState.NeedsTwoFactor

    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("URSA", style = MaterialTheme.typography.headlineMedium)
        Text("Connect to an Uptime Kuma server", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Server URL") }, singleLine = true,
            placeholder = { Text("https://kuma.example.com") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Username") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (needs2fa) {
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("2FA code") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        (loginState as? LoginUiState.Error)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { vm.login(url, user, pass, token) },
            enabled = !loading && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            else Text(if (needs2fa) "Verify" else "Connect")
        }
    }
}
