package dev.astoris.ursa.ui.connections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.data.model.RequestHeader
import dev.astoris.ursa.ui.ConnectionTestUiState
import dev.astoris.ursa.ui.LoginUiState
import dev.astoris.ursa.ui.UrsaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: UrsaViewModel,
    modifier: Modifier = Modifier,
    initialConnection: ServerConnection? = null,
    onBack: (() -> Unit)? = null,
    onConnected: () -> Unit = {},
) {
    val loginState by vm.login.collectAsStateWithLifecycle()
    val testState by vm.connectionTest.collectAsStateWithLifecycle()
    val loading = loginState is LoginUiState.Loading || testState is ConnectionTestUiState.Loading

    var authMode by remember(initialConnection?.url) {
        mutableStateOf(
            if (initialConnection != null && initialConnection.username.isBlank()) AuthMode.SESSION_TOKEN
            else AuthMode.PASSWORD,
        )
    }
    var alias by remember(initialConnection?.url) { mutableStateOf(initialConnection?.alias.orEmpty()) }
    var url by remember(initialConnection?.url) { mutableStateOf(initialConnection?.url.orEmpty()) }
    var user by remember(initialConnection?.url) { mutableStateOf(initialConnection?.username.orEmpty()) }
    var pass by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var sessionToken by remember { mutableStateOf("") }
    var insecure by remember(initialConnection?.url) { mutableStateOf(initialConnection?.insecure == true) }
    var headersExpanded by remember(initialConnection?.url) {
        mutableStateOf(initialConnection?.headers?.isNotEmpty() == true)
    }
    var cloudflareClientId by remember(initialConnection?.url) {
        mutableStateOf(initialConnection.headerValue(CLOUDFLARE_CLIENT_ID))
    }
    var cloudflareClientSecret by remember(initialConnection?.url) {
        mutableStateOf(initialConnection.headerValue(CLOUDFLARE_CLIENT_SECRET))
    }
    val customHeaders = remember(initialConnection?.url) {
        mutableStateListOf<HeaderDraft>().apply {
            initialConnection?.headers
                ?.filterNot { it.name.equals(CLOUDFLARE_CLIENT_ID, true) || it.name.equals(CLOUDFLARE_CLIENT_SECRET, true) }
                ?.forEach { add(HeaderDraft(it.name, it.value)) }
        }
    }
    val needs2fa = authMode == AuthMode.PASSWORD && (
        loginState is LoginUiState.NeedsTwoFactor ||
        testState is ConnectionTestUiState.NeedsTwoFactor || token.isNotEmpty()
    )
    val credentialsReady = when (authMode) {
        AuthMode.PASSWORD -> user.isNotBlank() && pass.isNotBlank()
        AuthMode.SESSION_TOKEN -> sessionToken.isNotBlank()
    }
    val hasPartialHeaders = (cloudflareClientId.isBlank() != cloudflareClientSecret.isBlank()) ||
        customHeaders.any { it.name.isBlank() != it.value.isBlank() }
    val headerCandidates = buildList {
        if (cloudflareClientId.isNotBlank() && cloudflareClientSecret.isNotBlank()) {
            add(RequestHeader(CLOUDFLARE_CLIENT_ID, cloudflareClientId))
            add(RequestHeader(CLOUDFLARE_CLIENT_SECRET, cloudflareClientSecret))
        }
        customHeaders.filter { it.name.isNotBlank() && it.value.isNotBlank() }
            .forEach { add(RequestHeader(it.name, it.value)) }
    }
    val requestHeaders = headerCandidates.mapNotNull { it.normalizedOrNull() }
    val headersValid = !hasPartialHeaders &&
        requestHeaders.size == headerCandidates.size &&
        requestHeaders.map { it.name.lowercase() }.distinct().size == requestHeaders.size

    BackHandler(enabled = onBack != null) { onBack?.invoke() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                if (initialConnection == null) R.string.servers_add
                                else R.string.servers_reauthenticate,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = CircleShape,
                ) {
                    Icon(
                        painter = painterResource(R.mipmap.ic_launcher_monochrome),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp).size(34.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(
                            if (initialConnection == null) R.string.login_title else R.string.login_reauth_title,
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        if (initialConnection == null) stringResource(R.string.login_subtitle)
                        else stringResource(R.string.login_reauth_subtitle, initialConnection.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = alias,
                            onValueChange = {
                                alias = it.take(80)
                                vm.resetConnectionTest()
                            },
                            label = { Text(stringResource(R.string.login_server_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                vm.resetConnectionTest()
                            },
                            label = { Text(stringResource(R.string.login_server_url)) },
                            placeholder = { Text(stringResource(R.string.login_server_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            readOnly = initialConnection != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AuthMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = authMode == mode,
                                    onClick = {
                                        authMode = mode
                                        vm.resetLogin()
                                        vm.resetConnectionTest()
                                    },
                                    label = {
                                        Text(
                                            stringResource(
                                                if (mode == AuthMode.PASSWORD) R.string.login_auth_password
                                                else R.string.login_auth_session_token,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        if (authMode == AuthMode.PASSWORD) {
                            OutlinedTextField(
                                value = user,
                                onValueChange = {
                                    user = it
                                    vm.resetConnectionTest()
                                },
                                label = { Text(stringResource(R.string.login_username)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = pass,
                                onValueChange = {
                                    pass = it
                                    vm.resetConnectionTest()
                                },
                                label = { Text(stringResource(R.string.login_password)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (needs2fa) {
                                OutlinedTextField(
                                    value = token,
                                    onValueChange = {
                                        token = it.filter(Char::isDigit).take(8)
                                        vm.resetConnectionTest()
                                    },
                                    label = { Text(stringResource(R.string.login_2fa_code)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = sessionToken,
                                onValueChange = {
                                    sessionToken = it.trim().take(4096)
                                    vm.resetConnectionTest()
                                },
                                label = { Text(stringResource(R.string.login_session_token)) },
                                supportingText = { Text(stringResource(R.string.login_session_token_desc)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        OutlinedButton(
                            onClick = { headersExpanded = !headersExpanded },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    R.string.login_access_headers,
                                    requestHeaders.size,
                                ),
                            )
                        }
                        if (headersExpanded) {
                            Text(
                                stringResource(R.string.login_access_headers_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = cloudflareClientId,
                                onValueChange = {
                                    cloudflareClientId = it.take(2048)
                                    vm.resetConnectionTest()
                                },
                                label = { Text(stringResource(R.string.login_cloudflare_client_id)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = cloudflareClientSecret,
                                onValueChange = {
                                    cloudflareClientSecret = it.take(2048)
                                    vm.resetConnectionTest()
                                },
                                label = { Text(stringResource(R.string.login_cloudflare_client_secret)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            customHeaders.forEachIndexed { index, draft ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = draft.name,
                                            onValueChange = {
                                                customHeaders[index] = draft.copy(name = it.take(128))
                                                vm.resetConnectionTest()
                                            },
                                            label = { Text(stringResource(R.string.login_header_name)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = draft.value,
                                            onValueChange = {
                                                customHeaders[index] = draft.copy(value = it.take(4096))
                                                vm.resetConnectionTest()
                                            },
                                            label = { Text(stringResource(R.string.login_header_value)) },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        TextButton(onClick = { customHeaders.removeAt(index) }) {
                                            Text(
                                                stringResource(R.string.login_remove_header),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                            if (customHeaders.size < MAX_CUSTOM_HEADERS) {
                                TextButton(onClick = { customHeaders.add(HeaderDraft()) }) {
                                    Text(stringResource(R.string.login_add_header))
                                }
                            }
                            if (!headersValid) {
                                Text(
                                    stringResource(R.string.login_headers_invalid),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = insecure,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        insecure = it
                                        vm.resetConnectionTest()
                                    },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = insecure, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(
                                    stringResource(R.string.login_trust_self_signed),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.login_trust_self_signed_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        (loginState as? LoginUiState.Error)?.let { error ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    error.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                )
                            }
                        }
                        when (val result = testState) {
                            ConnectionTestUiState.Success -> ConnectionTestResult(
                                message = stringResource(R.string.login_test_success),
                                success = true,
                            )
                            ConnectionTestUiState.NeedsTwoFactor -> ConnectionTestResult(
                                message = stringResource(R.string.login_test_2fa),
                                success = true,
                            )
                            is ConnectionTestUiState.Error -> ConnectionTestResult(
                                message = result.message,
                                success = false,
                            )
                            ConnectionTestUiState.Idle, ConnectionTestUiState.Loading -> Unit
                        }
                        OutlinedButton(
                            onClick = {
                                if (authMode == AuthMode.PASSWORD) {
                                    vm.testConnection(
                                        url = url,
                                        username = user,
                                        password = pass,
                                        token = token,
                                        insecure = insecure,
                                        headers = requestHeaders,
                                    )
                                } else {
                                    vm.testSessionToken(url, sessionToken, insecure, requestHeaders)
                                }
                            },
                            enabled = !loading && url.isNotBlank() && credentialsReady && headersValid,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (testState is ConnectionTestUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.login_test))
                            }
                        }
                        Button(
                            onClick = {
                                if (authMode == AuthMode.PASSWORD) {
                                    vm.login(
                                        url = url,
                                        username = user,
                                        password = pass,
                                        token = token,
                                        insecure = insecure,
                                        alias = alias,
                                        headers = requestHeaders,
                                        onSuccess = onConnected,
                                    )
                                } else {
                                    vm.loginWithSessionToken(
                                        url = url,
                                        sessionToken = sessionToken,
                                        insecure = insecure,
                                        alias = alias,
                                        headers = requestHeaders,
                                        onSuccess = onConnected,
                                    )
                                }
                            },
                            enabled = !loading && url.isNotBlank() && credentialsReady && headersValid,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(
                                    stringResource(
                                        when {
                                            needs2fa -> R.string.login_verify
                                            initialConnection != null -> R.string.login_reconnect
                                            else -> R.string.login_connect
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }

                if (onBack == null) {
                    TextButton(onClick = { vm.enterStatusPage() }) {
                        Text(stringResource(R.string.login_view_status_page))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionTestResult(message: String, success: Boolean) {
    val background = if (success) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val foreground = if (success) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = background, shape = MaterialTheme.shapes.medium) {
        Text(
            text = message,
            color = foreground,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

private data class HeaderDraft(val name: String = "", val value: String = "")

private fun ServerConnection?.headerValue(name: String): String =
    this?.headers?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value.orEmpty()

private enum class AuthMode { PASSWORD, SESSION_TOKEN }

private const val CLOUDFLARE_CLIENT_ID = "CF-Access-Client-Id"
private const val CLOUDFLARE_CLIENT_SECRET = "CF-Access-Client-Secret"
private const val MAX_CUSTOM_HEADERS = 8
