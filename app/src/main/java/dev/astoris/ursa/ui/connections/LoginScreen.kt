package dev.astoris.ursa.ui.connections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
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
    var insecure by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text(stringResource(R.string.login_server_url)) }, singleLine = true,
            placeholder = { Text(stringResource(R.string.login_server_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text(stringResource(R.string.login_username)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text(stringResource(R.string.login_password)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (needs2fa) {
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text(stringResource(R.string.login_2fa_code)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = insecure, onCheckedChange = { insecure = it })
            Text(stringResource(R.string.login_trust_self_signed), style = MaterialTheme.typography.bodyMedium)
        }
        (loginState as? LoginUiState.Error)?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { vm.login(url, user, pass, token, insecure) },
            enabled = !loading && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            else Text(stringResource(if (needs2fa) R.string.login_verify else R.string.login_connect))
        }
        TextButton(onClick = { vm.enterStatusPage() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.login_view_status_page))
        }
    }
}
