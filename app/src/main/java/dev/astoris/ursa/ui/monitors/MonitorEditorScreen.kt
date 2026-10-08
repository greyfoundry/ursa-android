package dev.astoris.ursa.ui.monitors

import android.content.ClipData
import android.content.Intent
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.MonitorDraft
import dev.astoris.ursa.core.network.LocalServiceDiscoveryError
import dev.astoris.ursa.core.network.LocalServiceDiscoveryState
import dev.astoris.ursa.core.network.LocalServiceProtocol
import dev.astoris.ursa.core.network.GlobalpingIpFamily
import dev.astoris.ursa.core.network.GlobalpingDnsProtocol
import dev.astoris.ursa.core.network.GlobalpingDnsRecordType
import dev.astoris.ursa.core.network.GlobalpingHttpMethod
import dev.astoris.ursa.core.network.GlobalpingHttpProtocol
import dev.astoris.ursa.core.network.GlobalpingHttpResponseCheck
import dev.astoris.ursa.core.network.GlobalpingPingProtocol
import dev.astoris.ursa.core.network.GlobalpingSubtype
import dev.astoris.ursa.core.network.MonitorDraftCodec
import dev.astoris.ursa.core.network.MonitorDraftError
import dev.astoris.ursa.core.network.MonitorEditorCodec
import dev.astoris.ursa.core.network.MonitorEditorDefaults
import dev.astoris.ursa.core.network.MonitorEditorHelp
import dev.astoris.ursa.core.network.MonitorEditorRegistry
import dev.astoris.ursa.core.network.MonitorEndpointKind
import dev.astoris.ursa.core.network.MonitorHeaderDraft
import dev.astoris.ursa.core.network.MonitorTypeCatalog
import dev.astoris.ursa.core.network.KumaCompatibility
import dev.astoris.ursa.core.network.KafkaSaslMechanism
import dev.astoris.ursa.core.network.MqttCheckType
import dev.astoris.ursa.core.network.SftpAuthMethod
import dev.astoris.ursa.core.network.SmtpSecurityMode
import dev.astoris.ursa.core.network.SnmpVersion
import dev.astoris.ursa.core.network.WebSocketAuthMethod
import dev.astoris.ursa.core.network.WebSocketOAuthAuthMethod
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.KumaNotification
import dev.astoris.ursa.data.model.KumaDockerHost
import dev.astoris.ursa.data.model.KumaRemoteBrowser
import dev.astoris.ursa.data.model.KumaTag
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.MonitorTagAssignment
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.ui.AccessProfileNotice
import dev.astoris.ursa.ui.MonitorEditorUiState
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.allows
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorEditorScreen(
    vm: UrsaViewModel,
    modifier: Modifier = Modifier,
    handleSystemBack: Boolean = true,
) {
    val state by vm.monitorEditor.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val dockerHosts by vm.dockerHosts.collectAsStateWithLifecycle()
    val remoteBrowsers by vm.remoteBrowsers.collectAsStateWithLifecycle()
    val serverTags by vm.serverTags.collectAsStateWithLifecycle()
    val monitors by vm.monitors.collectAsStateWithLifecycle()
    val discoveryState by vm.localServiceDiscoveryState.collectAsStateWithLifecycle()
    val activeConnection by vm.activeConnection.collectAsStateWithLifecycle()
    val compatibility by vm.kumaCompatibility.collectAsStateWithLifecycle()
    val stateDraft = when (val current = state) {
        is MonitorEditorUiState.Ready -> current.draft
        is MonitorEditorUiState.Saving -> current.draft
        is MonitorEditorUiState.Error -> current.draft
        else -> null
    }
    var draft by remember(stateDraft?.id, stateDraft?.type) {
        mutableStateOf(stateDraft ?: MonitorDraft.create())
    }
    LaunchedEffect(discoveryState) {
        val selected = (discoveryState as? LocalServiceDiscoveryState.Selected)?.address ?: return@LaunchedEffect
        draft = when (MonitorTypeCatalog.find(draft.type)?.endpointKind) {
            MonitorEndpointKind.URL -> draft.copy(endpoint = selected.url)
            MonitorEndpointKind.HOST -> draft.copy(endpoint = selected.host)
            MonitorEndpointKind.HOST_PORT -> draft.copy(endpoint = selected.host, port = selected.port)
            else -> draft
        }
        vm.consumeLocalServiceSelection()
    }
    val saving = state is MonitorEditorUiState.Saving
    val canSave = MonitorEditorRegistry.writeVerified(draft.type, compatibility) &&
        activeConnection.allows(
            if (draft.isNew) AccessCapability.MONITOR_CREATE else AccessCapability.MONITOR_EDIT,
        )
    val serverError = (state as? MonitorEditorUiState.Error)?.message
    BackHandler(enabled = handleSystemBack && !saving) { vm.closeMonitorEditor() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (draft.isNew) R.string.monitor_add_title else R.string.monitor_edit_title))
                },
            )
        },
    ) { padding ->
        when {
            state is MonitorEditorUiState.Loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.monitor_loading_details), modifier = Modifier.padding(top = 12.dp))
            }
            stateDraft == null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(serverError ?: stringResource(R.string.monitor_load_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::closeMonitorEditor) { Text(stringResource(R.string.action_back)) }
            }
            else -> MonitorForm(
                draft = draft,
                onDraftChange = { draft = it },
                saving = saving,
                canSave = canSave,
                compatibility = compatibility,
                accessConnection = activeConnection,
                serverError = serverError,
                notifications = notifications,
                dockerHosts = dockerHosts,
                remoteBrowsers = remoteBrowsers,
                serverTags = serverTags,
                monitors = monitors,
                discoveryState = discoveryState,
                onDiscover = vm::discoverLocalService,
                onSelectService = vm::selectLocalService,
                onStopDiscovery = vm::stopLocalServiceDiscovery,
                onCancel = vm::closeMonitorEditor,
                onSave = { vm.saveMonitor(draft) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorForm(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
    saving: Boolean,
    canSave: Boolean,
    compatibility: KumaCompatibility,
    accessConnection: ServerConnection?,
    serverError: String?,
    notifications: List<KumaNotification>,
    dockerHosts: List<KumaDockerHost>,
    remoteBrowsers: List<KumaRemoteBrowser>,
    serverTags: List<KumaTag>,
    monitors: List<Monitor>,
    discoveryState: LocalServiceDiscoveryState,
    onDiscover: (LocalServiceProtocol) -> Unit,
    onSelectService: (String) -> Unit,
    onStopDiscovery: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val option = MonitorTypeCatalog.find(draft.type)
    val definition = MonitorEditorRegistry.find(draft.type)
    val validation = MonitorDraftCodec.validate(draft)
    var typeMenuOpen by remember { mutableStateOf(false) }
    var groupMenuOpen by remember { mutableStateOf(false) }
    val parentGroups = eligibleParentGroups(monitors, draft.id)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccessProfileNotice(accessConnection)
        serverError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!MonitorEditorRegistry.writeVerified(draft.type, compatibility)) {
            Text(
                stringResource(R.string.monitor_type_not_write_verified),
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it.take(250))) },
            label = { Text(stringResource(R.string.monitor_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft.isNew) {
            ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
                OutlinedTextField(
                    value = option?.label.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.monitor_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuOpen) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                    MonitorTypeCatalog.creatableFor(compatibility).forEach { next ->
                        DropdownMenuItem(
                            text = { Text(next.label) },
                            onClick = {
                                val defaults = MonitorDraft.create(next.key)
                                onDraftChange(
                                    defaults.copy(
                                        name = draft.name,
                                        description = draft.description,
                                        intervalSeconds = if (
                                            defaults.intervalSeconds != MonitorEditorDefaults().intervalSeconds
                                        ) {
                                            defaults.intervalSeconds
                                        } else {
                                            draft.intervalSeconds
                                        },
                                        retryIntervalSeconds = draft.retryIntervalSeconds,
                                        resendIntervalSeconds = draft.resendIntervalSeconds,
                                        maxRetries = draft.maxRetries,
                                        active = draft.active,
                                        notificationIds = draft.notificationIds,
                                        parentId = draft.parentId,
                                        tagAssignments = draft.tagAssignments,
                                    ),
                                )
                                typeMenuOpen = false
                            },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.monitor_create_types_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(stringResource(R.string.detail_type, option?.label ?: draft.type))
            Text(
                stringResource(
                    if (definition?.help == MonitorEditorHelp.FULL_NATIVE) {
                        R.string.monitor_full_native
                    } else {
                        R.string.monitor_advanced_preserved
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (definition?.codec == MonitorEditorCodec.GLOBALPING) {
            GlobalpingFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.DATABASE) {
            DatabaseFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.GRPC) {
            GrpcFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (draft.type == "push") {
            PushMonitorSetup(
                pushUrl = accessConnection?.url?.let { MonitorDraftCodec.pushUrl(it, draft.pushToken) },
                pushToken = draft.pushToken,
                isNew = draft.isNew,
            )
        }
        if (option?.endpointKind != MonitorEndpointKind.NONE) {
            OutlinedTextField(
                value = draft.endpoint,
                onValueChange = { onDraftChange(draft.copy(endpoint = it.take(2_048))) },
                label = {
                    Text(
                        stringResource(
                            if (option?.endpointKind == MonitorEndpointKind.URL) {
                                R.string.monitor_url_label
                            } else {
                                R.string.monitor_host_label
                            },
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (definition?.codec == MonitorEditorCodec.KEYWORD || definition?.codec == MonitorEditorCodec.GRPC) {
            OutlinedTextField(
                value = draft.keyword,
                onValueChange = { onDraftChange(draft.copy(keyword = it.take(2_000))) },
                label = { Text(stringResource(R.string.monitor_keyword_label)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = draft.invertKeyword,
                        role = Role.Checkbox,
                        onValueChange = { onDraftChange(draft.copy(invertKeyword = it)) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = draft.invertKeyword,
                    onCheckedChange = null,
                )
                Text(stringResource(R.string.monitor_keyword_invert))
            }
            Text(
                stringResource(R.string.monitor_keyword_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (definition?.codec == MonitorEditorCodec.JSON_QUERY) {
            JsonQueryFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.WEBSOCKET) {
            OutlinedTextField(
                value = draft.websocketSubprotocols,
                onValueChange = { onDraftChange(draft.copy(websocketSubprotocols = it.take(500))) },
                label = { Text(stringResource(R.string.monitor_websocket_subprotocols)) },
                supportingText = { Text(stringResource(R.string.monitor_websocket_subprotocols_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.websocketAcceptedCodes,
                onValueChange = { onDraftChange(draft.copy(websocketAcceptedCodes = it.take(250))) },
                label = { Text(stringResource(R.string.monitor_websocket_accepted_codes)) },
                supportingText = { Text(stringResource(R.string.monitor_websocket_accepted_codes_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = draft.websocketIgnoreAcceptHeader,
                        role = Role.Checkbox,
                        onValueChange = { onDraftChange(draft.copy(websocketIgnoreAcceptHeader = it)) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = draft.websocketIgnoreAcceptHeader,
                    onCheckedChange = null,
                )
                Text(stringResource(R.string.monitor_websocket_ignore_accept_header))
            }
            Text(
                stringResource(R.string.monitor_websocket_ignore_accept_header_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WebsocketHeaderFields(draft = draft, onDraftChange = onDraftChange)
            WebsocketAuthFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (option?.endpointKind == MonitorEndpointKind.HOST_PORT) {
            NumberField(
                value = draft.port,
                onValueChange = { onDraftChange(draft.copy(port = it)) },
                label = stringResource(R.string.monitor_port_label),
                maxDigits = 5,
            )
        }
        if (definition?.codec == MonitorEditorCodec.NTP) {
            NtpFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.SNMP) {
            SnmpFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.RABBITMQ) {
            RabbitmqFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.KAFKA) {
            KafkaFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.DOCKER) {
            DockerFields(draft = draft, hosts = dockerHosts, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.REAL_BROWSER) {
            RealBrowserFields(draft = draft, browsers = remoteBrowsers, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.RADIUS) {
            RadiusFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.MQTT) {
            MqttFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (definition?.codec == MonitorEditorCodec.SMTP) {
            SmtpFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (draft.type == "sftp") {
            SftpFields(draft = draft, onDraftChange = onDraftChange)
        }
        if (
            draft.isNew &&
            option?.endpointKind != MonitorEndpointKind.NONE &&
            draft.type !in setOf("mqtt", "ntp", "radius", "smtp", "snmp", "sftp", "websocket-upgrade")
        ) {
            Text(stringResource(R.string.monitor_discovery_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.monitor_discovery_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onDiscover(LocalServiceProtocol.HTTP) },
                    enabled = discoveryState !is LocalServiceDiscoveryState.Resolving,
                ) { Text(stringResource(R.string.monitor_discovery_http)) }
                OutlinedButton(
                    onClick = { onDiscover(LocalServiceProtocol.HTTPS) },
                    enabled = discoveryState !is LocalServiceDiscoveryState.Resolving,
                ) { Text(stringResource(R.string.monitor_discovery_https)) }
            }
            when (discoveryState) {
                is LocalServiceDiscoveryState.Discovering -> {
                    Text(
                        stringResource(
                            if (discoveryState.candidates.isEmpty()) {
                                R.string.monitor_discovery_searching
                            } else {
                                R.string.monitor_discovery_choose
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    discoveryState.candidates.forEach { service ->
                        OutlinedButton(
                            onClick = { onSelectService(service.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(service.name) }
                    }
                    OutlinedButton(onClick = onStopDiscovery) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                is LocalServiceDiscoveryState.Resolving -> Text(
                    stringResource(R.string.monitor_discovery_resolving, discoveryState.name),
                    style = MaterialTheme.typography.bodySmall,
                )
                is LocalServiceDiscoveryState.Error -> Text(
                    localDiscoveryError(discoveryState.reason),
                    color = MaterialTheme.colorScheme.error,
                )
                LocalServiceDiscoveryState.Idle,
                is LocalServiceDiscoveryState.Selected -> Unit
            }
        }
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onDraftChange(draft.copy(description = it.take(2_000))) },
            label = { Text(stringResource(R.string.monitor_description_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(expanded = groupMenuOpen, onExpandedChange = { groupMenuOpen = it }) {
            val parentName = parentGroups.firstOrNull { it.id == draft.parentId }?.name
            OutlinedTextField(
                value = parentName ?: stringResource(R.string.monitor_group_none),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.monitor_group_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupMenuOpen) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = groupMenuOpen, onDismissRequest = { groupMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.monitor_group_none)) },
                    onClick = { onDraftChange(draft.copy(parentId = null)); groupMenuOpen = false },
                )
                parentGroups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.name) },
                        onClick = { onDraftChange(draft.copy(parentId = group.id)); groupMenuOpen = false },
                    )
                }
            }
        }
        NumberField(
            value = draft.intervalSeconds,
            onValueChange = { onDraftChange(draft.copy(intervalSeconds = it ?: 0)) },
            label = stringResource(R.string.monitor_interval_label),
        )
        NumberField(
            value = draft.retryIntervalSeconds,
            onValueChange = { onDraftChange(draft.copy(retryIntervalSeconds = it ?: 0)) },
            label = stringResource(R.string.monitor_retry_interval_label),
        )
        NumberField(
            value = draft.maxRetries,
            onValueChange = { onDraftChange(draft.copy(maxRetries = it ?: 0)) },
            label = stringResource(R.string.monitor_retries_label),
            maxDigits = 3,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.active,
                onCheckedChange = { onDraftChange(draft.copy(active = it)) },
            )
            Text(stringResource(R.string.monitor_active_label))
        }
        Text(stringResource(R.string.monitor_notifications_title), style = MaterialTheme.typography.titleSmall)
        if (notifications.isEmpty()) {
            Text(
                stringResource(R.string.monitor_notifications_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.monitor_notifications_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val defaultSuffix = stringResource(R.string.monitor_notification_default_suffix)
            notifications.forEach { notification ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = notification.id in draft.notificationIds,
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draft.copy(
                                    notificationIds = if (checked) {
                                        draft.notificationIds + notification.id
                                    } else {
                                        draft.notificationIds - notification.id
                                    },
                                ),
                            )
                        },
                    )
                    Column {
                        Text(notification.name)
                        Text(
                            buildString {
                                append(notification.type)
                                if (notification.isDefault) append(defaultSuffix)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.monitor_tags_title), style = MaterialTheme.typography.titleSmall)
        if (serverTags.isEmpty()) {
            Text(
                stringResource(R.string.monitor_tags_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.monitor_tags_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            serverTags.forEach { tag ->
                val assignments = draft.tagAssignments.filter { it.tagId == tag.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = assignments.isNotEmpty(),
                        onCheckedChange = { checked ->
                            onDraftChange(
                                draft.copy(
                                    tagAssignments = if (checked) {
                                        draft.tagAssignments + MonitorTagAssignment(
                                            tagId = tag.id,
                                            monitorId = draft.id ?: 0,
                                            name = tag.name,
                                            color = tag.color,
                                        )
                                    } else {
                                        draft.tagAssignments.filterNot { it.tagId == tag.id }
                                    },
                                ),
                            )
                        },
                    )
                    Column {
                        Text(tag.name)
                        assignments.map(MonitorTagAssignment::value).filter(String::isNotBlank)
                            .takeIf(List<String>::isNotEmpty)?.let { values ->
                                Text(
                                    values.joinToString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
            }
        }
        validation?.let { Text(validationMessage(it), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCancel, enabled = !saving) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onSave, enabled = canSave && !saving && validation == null) {
                Text(stringResource(if (saving) R.string.action_saving else R.string.action_save))
            }
        }
    }
}

@Composable
private fun WebsocketHeaderFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    Text(
        stringResource(
            R.string.monitor_websocket_headers_title,
            draft.websocketHeaders.size,
            MonitorDraftCodec.WEBSOCKET_HEADER_LIMIT,
        ),
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        stringResource(R.string.monitor_websocket_headers_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!draft.websocketHeadersEditable) {
        Text(
            stringResource(R.string.monitor_websocket_headers_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    draft.websocketHeaders.forEachIndexed { index, header ->
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = header.name,
                    onValueChange = { value ->
                        onDraftChange(
                            draft.copy(
                                websocketHeaders = draft.websocketHeaders.replaced(
                                    index,
                                    header.copy(name = value.take(128)),
                                ),
                            ),
                        )
                    },
                    label = { Text(stringResource(R.string.monitor_websocket_header_name, index + 1)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SensitiveField(
                    value = header.value,
                    onValueChange = { value ->
                        onDraftChange(
                            draft.copy(
                                websocketHeaders = draft.websocketHeaders.replaced(
                                    index,
                                    header.copy(value = value.take(4_096)),
                                ),
                            ),
                        )
                    },
                    label = stringResource(R.string.monitor_websocket_header_value, index + 1),
                    saved = header.hasSavedValue &&
                        header.originalName?.equals(header.name.trim(), ignoreCase = true) == true,
                    savedMessage = stringResource(R.string.monitor_websocket_header_saved),
                )
                TextButton(
                    onClick = {
                        onDraftChange(
                            draft.copy(websocketHeaders = draft.websocketHeaders.filterIndexed { i, _ -> i != index }),
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.monitor_websocket_header_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (draft.websocketHeaders.size < MonitorDraftCodec.WEBSOCKET_HEADER_LIMIT) {
        TextButton(
            onClick = {
                onDraftChange(
                    draft.copy(websocketHeaders = draft.websocketHeaders + MonitorHeaderDraft()),
                )
            },
        ) {
            Text(stringResource(R.string.monitor_websocket_header_add))
        }
    }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    mapIndexed { current, item -> if (current == index) value else item }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebsocketAuthFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
    allowMtls: Boolean = true,
) {
    Text(stringResource(R.string.monitor_websocket_auth_title), style = MaterialTheme.typography.titleSmall)
    if (!draft.websocketAuthEditable) {
        Text(
            stringResource(R.string.monitor_websocket_auth_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
        OutlinedTextField(
            value = stringResource(
                when (draft.websocketAuthMethod) {
                    WebSocketAuthMethod.NONE -> R.string.monitor_websocket_auth_none
                    WebSocketAuthMethod.BASIC -> R.string.monitor_websocket_auth_basic
                    WebSocketAuthMethod.BEARER -> R.string.monitor_websocket_auth_bearer
                    WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS -> R.string.monitor_websocket_auth_oauth
                    WebSocketAuthMethod.MTLS -> R.string.monitor_websocket_auth_mtls
                },
            ),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_websocket_auth_method)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            WebSocketAuthMethod.entries.filter { allowMtls || it != WebSocketAuthMethod.MTLS }.forEach { method ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (method) {
                                    WebSocketAuthMethod.NONE -> R.string.monitor_websocket_auth_none
                                    WebSocketAuthMethod.BASIC -> R.string.monitor_websocket_auth_basic
                                    WebSocketAuthMethod.BEARER -> R.string.monitor_websocket_auth_bearer
                                    WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS ->
                                        R.string.monitor_websocket_auth_oauth
                                    WebSocketAuthMethod.MTLS -> R.string.monitor_websocket_auth_mtls
                                },
                            ),
                        )
                    },
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                websocketAuthMethod = method,
                                websocketClearSavedTlsCaCertificate = false,
                            ),
                        )
                        menuOpen = false
                    },
                )
            }
        }
    }
    when (draft.websocketAuthMethod) {
        WebSocketAuthMethod.NONE -> Text(
            stringResource(R.string.monitor_websocket_auth_none_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WebSocketAuthMethod.BASIC -> {
            OutlinedTextField(
                value = draft.websocketBasicUsername,
                onValueChange = { onDraftChange(draft.copy(websocketBasicUsername = it.take(256))) },
                label = { Text(stringResource(R.string.monitor_websocket_basic_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SensitiveField(
                value = draft.websocketBasicPassword,
                onValueChange = { onDraftChange(draft.copy(websocketBasicPassword = it.take(4_096))) },
                label = stringResource(R.string.monitor_websocket_basic_password),
                saved = draft.websocketOriginalAuthMethod == WebSocketAuthMethod.BASIC &&
                    draft.websocketHasSavedBasicPassword,
            )
        }
        WebSocketAuthMethod.BEARER -> SensitiveField(
            value = draft.websocketBearerToken,
            onValueChange = { onDraftChange(draft.copy(websocketBearerToken = it.take(8_192))) },
            label = stringResource(R.string.monitor_websocket_bearer_token),
            saved = draft.websocketOriginalAuthMethod == WebSocketAuthMethod.BEARER &&
                draft.websocketHasSavedBearerToken,
        )
        WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS -> WebsocketOAuthFields(draft, onDraftChange)
        WebSocketAuthMethod.MTLS -> if (allowMtls) WebsocketMtlsFields(draft, onDraftChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebsocketOAuthFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    var methodMenuOpen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = methodMenuOpen, onExpandedChange = { methodMenuOpen = it }) {
        OutlinedTextField(
            value = stringResource(
                if (draft.websocketOAuthAuthMethod == WebSocketOAuthAuthMethod.AUTHORIZATION_HEADER) {
                    R.string.monitor_websocket_oauth_authorization_header
                } else {
                    R.string.monitor_websocket_oauth_form_body
                },
            ),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_websocket_oauth_auth_method)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodMenuOpen) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = methodMenuOpen, onDismissRequest = { methodMenuOpen = false }) {
            WebSocketOAuthAuthMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (method == WebSocketOAuthAuthMethod.AUTHORIZATION_HEADER) {
                                    R.string.monitor_websocket_oauth_authorization_header
                                } else {
                                    R.string.monitor_websocket_oauth_form_body
                                },
                            ),
                        )
                    },
                    onClick = {
                        onDraftChange(draft.copy(websocketOAuthAuthMethod = method))
                        methodMenuOpen = false
                    },
                )
            }
        }
    }
    OutlinedTextField(
        value = draft.websocketOAuthTokenUrl,
        onValueChange = { onDraftChange(draft.copy(websocketOAuthTokenUrl = it.take(2_048))) },
        label = { Text(stringResource(R.string.monitor_websocket_oauth_token_url)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.websocketOAuthClientId,
        onValueChange = { onDraftChange(draft.copy(websocketOAuthClientId = it.take(1_024))) },
        label = { Text(stringResource(R.string.monitor_websocket_oauth_client_id)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SensitiveField(
        value = draft.websocketOAuthClientSecret,
        onValueChange = { onDraftChange(draft.copy(websocketOAuthClientSecret = it.take(8_192))) },
        label = stringResource(R.string.monitor_websocket_oauth_client_secret),
        saved = draft.websocketOriginalAuthMethod == WebSocketAuthMethod.OAUTH2_CLIENT_CREDENTIALS &&
            draft.websocketHasSavedOAuthClientSecret,
    )
    OutlinedTextField(
        value = draft.websocketOAuthScopes,
        onValueChange = { onDraftChange(draft.copy(websocketOAuthScopes = it.take(2_048))) },
        label = { Text(stringResource(R.string.monitor_websocket_oauth_scopes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.websocketOAuthAudience,
        onValueChange = { onDraftChange(draft.copy(websocketOAuthAudience = it.take(2_048))) },
        label = { Text(stringResource(R.string.monitor_websocket_oauth_audience)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WebsocketMtlsFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    val sameMethod = draft.websocketOriginalAuthMethod == WebSocketAuthMethod.MTLS
    SensitiveField(
        value = draft.websocketTlsCertificate,
        onValueChange = { onDraftChange(draft.copy(websocketTlsCertificate = it.take(65_536))) },
        label = stringResource(R.string.monitor_websocket_mtls_certificate),
        saved = sameMethod && draft.websocketHasSavedTlsCertificate,
        minLines = 4,
    )
    SensitiveField(
        value = draft.websocketTlsPrivateKey,
        onValueChange = { onDraftChange(draft.copy(websocketTlsPrivateKey = it.take(65_536))) },
        label = stringResource(R.string.monitor_websocket_mtls_private_key),
        saved = sameMethod && draft.websocketHasSavedTlsPrivateKey,
        minLines = 4,
    )
    SensitiveField(
        value = draft.websocketTlsCaCertificate,
        onValueChange = {
            onDraftChange(
                draft.copy(
                    websocketTlsCaCertificate = it.take(65_536),
                    websocketClearSavedTlsCaCertificate = false,
                ),
            )
        },
        label = stringResource(R.string.monitor_websocket_mtls_ca_certificate),
        saved = sameMethod && draft.websocketHasSavedTlsCaCertificate,
        minLines = 4,
        enabled = !draft.websocketClearSavedTlsCaCertificate,
    )
    if (sameMethod && draft.websocketHasSavedTlsCaCertificate && draft.websocketTlsCaCertificate.isBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.websocketClearSavedTlsCaCertificate,
                onCheckedChange = {
                    onDraftChange(
                        draft.copy(
                            websocketTlsCaCertificate = "",
                            websocketClearSavedTlsCaCertificate = it,
                        ),
                    )
                },
            )
            Text(stringResource(R.string.monitor_websocket_mtls_clear_ca_certificate))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SftpFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    var authMenuOpen by remember { mutableStateOf(false) }
    Text(stringResource(R.string.monitor_sftp_auth_title), style = MaterialTheme.typography.titleSmall)
    ExposedDropdownMenuBox(expanded = authMenuOpen, onExpandedChange = { authMenuOpen = it }) {
        OutlinedTextField(
            value = stringResource(
                if (draft.sftpAuthMethod == SftpAuthMethod.PASSWORD) {
                    R.string.monitor_sftp_auth_password
                } else {
                    R.string.monitor_sftp_auth_private_key
                },
            ),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_sftp_auth_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(authMenuOpen) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = authMenuOpen, onDismissRequest = { authMenuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.monitor_sftp_auth_password)) },
                onClick = {
                    onDraftChange(draft.copy(sftpAuthMethod = SftpAuthMethod.PASSWORD))
                    authMenuOpen = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.monitor_sftp_auth_private_key)) },
                onClick = {
                    onDraftChange(
                        draft.copy(
                            sftpAuthMethod = SftpAuthMethod.PRIVATE_KEY,
                            sftpClearSavedPassphrase = false,
                        ),
                    )
                    authMenuOpen = false
                },
            )
        }
    }
    OutlinedTextField(
        value = draft.sftpUsername,
        onValueChange = { onDraftChange(draft.copy(sftpUsername = it.take(256))) },
        label = { Text(stringResource(R.string.monitor_sftp_username_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    when (draft.sftpAuthMethod) {
        SftpAuthMethod.PASSWORD -> SensitiveField(
            value = draft.sftpPassword,
            onValueChange = { onDraftChange(draft.copy(sftpPassword = it.take(4_096))) },
            label = stringResource(R.string.monitor_sftp_password_label),
            saved = draft.sftpOriginalAuthMethod == SftpAuthMethod.PASSWORD && draft.sftpHasSavedPassword,
        )
        SftpAuthMethod.PRIVATE_KEY -> {
            SensitiveField(
                value = draft.sftpPrivateKey,
                onValueChange = {
                    onDraftChange(
                        draft.copy(
                            sftpPrivateKey = it.take(65_536),
                            sftpClearSavedPassphrase = false,
                        ),
                    )
                },
                label = stringResource(R.string.monitor_sftp_private_key_label),
                saved = draft.sftpOriginalAuthMethod == SftpAuthMethod.PRIVATE_KEY &&
                    draft.sftpHasSavedPrivateKey,
                minLines = 4,
            )
            SensitiveField(
                value = draft.sftpPassphrase,
                onValueChange = {
                    onDraftChange(
                        draft.copy(
                            sftpPassphrase = it.take(4_096),
                            sftpClearSavedPassphrase = false,
                        ),
                    )
                },
                label = stringResource(R.string.monitor_sftp_passphrase_label),
                saved = draft.sftpOriginalAuthMethod == SftpAuthMethod.PRIVATE_KEY &&
                    draft.sftpHasSavedPassphrase,
                savedMessage = stringResource(R.string.monitor_sftp_saved_passphrase),
                enabled = !draft.sftpClearSavedPassphrase,
            )
            if (
                draft.sftpOriginalAuthMethod == SftpAuthMethod.PRIVATE_KEY &&
                draft.sftpHasSavedPassphrase &&
                draft.sftpPrivateKey.isBlank()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.sftpClearSavedPassphrase,
                        onCheckedChange = {
                            onDraftChange(
                                draft.copy(
                                    sftpPassphrase = "",
                                    sftpClearSavedPassphrase = it,
                                ),
                            )
                        },
                    )
                    Text(stringResource(R.string.monitor_sftp_clear_passphrase))
                }
            }
        }
    }
    OutlinedTextField(
        value = draft.sftpPath,
        onValueChange = { onDraftChange(draft.copy(sftpPath = it.take(2_048))) },
        label = { Text(stringResource(R.string.monitor_sftp_path_label)) },
        supportingText = { Text(stringResource(R.string.monitor_sftp_path_desc)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MqttFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    if (!draft.mqttFieldsEditable) {
        Text(
            stringResource(R.string.monitor_mqtt_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    var checkTypeMenuOpen by remember { mutableStateOf(false) }
    Text(stringResource(R.string.monitor_mqtt_connection_title), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.monitor_mqtt_host_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = draft.mqttUsername,
        onValueChange = { onDraftChange(draft.copy(mqttUsername = it.take(1_024))) },
        label = { Text(stringResource(R.string.monitor_mqtt_username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SensitiveField(
        value = draft.mqttPassword,
        onValueChange = {
            onDraftChange(
                draft.copy(
                    mqttPassword = it.take(8_192),
                    mqttClearSavedPassword = false,
                ),
            )
        },
        label = stringResource(R.string.monitor_mqtt_password),
        saved = draft.mqttHasSavedPassword,
        enabled = !draft.mqttClearSavedPassword,
    )
    if (draft.mqttHasSavedPassword && draft.mqttPassword.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.mqttClearSavedPassword,
                onCheckedChange = {
                    onDraftChange(
                        draft.copy(
                            mqttPassword = "",
                            mqttClearSavedPassword = it,
                        ),
                    )
                },
            )
            Text(stringResource(R.string.monitor_mqtt_clear_password))
        }
    }
    OutlinedTextField(
        value = draft.mqttTopic,
        onValueChange = { onDraftChange(draft.copy(mqttTopic = it.take(2_048))) },
        label = { Text(stringResource(R.string.monitor_mqtt_topic)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val usesWebsocket = draft.endpoint.trim().lowercase().let { it.startsWith("ws://") || it.startsWith("wss://") }
    if (usesWebsocket) {
        OutlinedTextField(
            value = draft.mqttWebsocketPath,
            onValueChange = { onDraftChange(draft.copy(mqttWebsocketPath = it.take(2_048))) },
            label = { Text(stringResource(R.string.monitor_mqtt_websocket_path)) },
            supportingText = { Text(stringResource(R.string.monitor_mqtt_websocket_path_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(stringResource(R.string.monitor_mqtt_check_title), style = MaterialTheme.typography.titleSmall)
    ExposedDropdownMenuBox(
        expanded = checkTypeMenuOpen,
        onExpandedChange = { checkTypeMenuOpen = it },
    ) {
        OutlinedTextField(
            value = stringResource(
                if (draft.mqttCheckType == MqttCheckType.KEYWORD) {
                    R.string.monitor_mqtt_check_keyword
                } else {
                    R.string.monitor_mqtt_check_json_query
                },
            ),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_mqtt_check_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(checkTypeMenuOpen) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = checkTypeMenuOpen,
            onDismissRequest = { checkTypeMenuOpen = false },
        ) {
            MqttCheckType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (type == MqttCheckType.KEYWORD) {
                                    R.string.monitor_mqtt_check_keyword
                                } else {
                                    R.string.monitor_mqtt_check_json_query
                                },
                            ),
                        )
                    },
                    onClick = {
                        onDraftChange(draft.copy(mqttCheckType = type))
                        checkTypeMenuOpen = false
                    },
                )
            }
        }
    }
    when (draft.mqttCheckType) {
        MqttCheckType.KEYWORD -> OutlinedTextField(
            value = draft.mqttSuccessMessage,
            onValueChange = { onDraftChange(draft.copy(mqttSuccessMessage = it.take(2_048))) },
            label = { Text(stringResource(R.string.monitor_mqtt_success_message)) },
            supportingText = { Text(stringResource(R.string.monitor_mqtt_success_message_help)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        MqttCheckType.JSON_QUERY -> {
            OutlinedTextField(
                value = draft.mqttJsonQueryExpression,
                onValueChange = { onDraftChange(draft.copy(mqttJsonQueryExpression = it.take(2_000))) },
                label = { Text(stringResource(R.string.monitor_json_query_expression)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.mqttJsonQueryExpectedValue,
                onValueChange = { onDraftChange(draft.copy(mqttJsonQueryExpectedValue = it.take(2_000))) },
                label = { Text(stringResource(R.string.monitor_json_query_expected_value)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmtpFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    if (!draft.smtpSecurityEditable) {
        Text(
            stringResource(R.string.monitor_smtp_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    var securityMenuOpen by remember { mutableStateOf(false) }
    Text(stringResource(R.string.monitor_smtp_security_title), style = MaterialTheme.typography.titleSmall)
    ExposedDropdownMenuBox(
        expanded = securityMenuOpen,
        onExpandedChange = { securityMenuOpen = it },
    ) {
        OutlinedTextField(
            value = draft.smtpSecurityMode?.let { stringResource(it.labelRes) }
                ?: stringResource(R.string.monitor_smtp_security_choose),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_smtp_security_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(securityMenuOpen) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = securityMenuOpen,
            onDismissRequest = { securityMenuOpen = false },
        ) {
            SmtpSecurityMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes)) },
                    onClick = {
                        onDraftChange(draft.copy(smtpSecurityMode = mode))
                        securityMenuOpen = false
                    },
                )
            }
        }
    }
    Text(
        stringResource(R.string.monitor_smtp_security_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NtpFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    Text(stringResource(R.string.monitor_ntp_thresholds_title), style = MaterialTheme.typography.titleSmall)
    NumberField(
        value = draft.ntpStratumThreshold,
        onValueChange = { onDraftChange(draft.copy(ntpStratumThreshold = it)) },
        label = stringResource(R.string.monitor_ntp_stratum_threshold),
        maxDigits = 2,
    )
    Text(
        stringResource(R.string.monitor_ntp_stratum_threshold_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NumberField(
        value = draft.ntpTimeOffsetThreshold,
        onValueChange = { onDraftChange(draft.copy(ntpTimeOffsetThreshold = it)) },
        label = stringResource(R.string.monitor_ntp_offset_threshold),
    )
    Text(
        stringResource(R.string.monitor_ntp_offset_threshold_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NumberField(
        value = draft.ntpRootDispersionThreshold,
        onValueChange = { onDraftChange(draft.copy(ntpRootDispersionThreshold = it)) },
        label = stringResource(R.string.monitor_ntp_dispersion_threshold),
    )
    Text(
        stringResource(R.string.monitor_ntp_dispersion_threshold_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DatabaseFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    Text(stringResource(R.string.monitor_database_connection_title), style = MaterialTheme.typography.titleSmall)
    SensitiveField(
        value = draft.databaseConnectionString,
        onValueChange = { onDraftChange(draft.copy(databaseConnectionString = it.take(4_096))) },
        label = stringResource(R.string.monitor_database_connection_string),
        saved = draft.databaseHasSavedConnectionString,
        savedMessage = stringResource(R.string.monitor_database_connection_saved),
    )
    Text(
        stringResource(
            R.string.monitor_database_connection_example,
            when (draft.type) {
                "sqlserver" -> "Server=host,1433;Database=db;User Id=user;Password=password;Encrypt=true"
                "postgres" -> "postgres://user:password@host:5432/database"
                "mysql" -> "mysql://user@host:3306/database"
                "mongodb" -> "mongodb://user:password@host:27017/database"
                "oracledb" -> "host:1521/FREEPDB1"
                else -> "redis://user:password@host:6379"
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (draft.type == "mysql") {
        SensitiveField(
            value = draft.databasePassword,
            onValueChange = { onDraftChange(draft.copy(databasePassword = it.take(2_048))) },
            label = stringResource(R.string.monitor_database_password_override),
            saved = draft.databaseHasSavedPassword,
        )
        Text(
            stringResource(R.string.monitor_database_password_override_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (draft.type == "oracledb") {
        OutlinedTextField(
            value = draft.databaseUsername,
            onValueChange = { onDraftChange(draft.copy(databaseUsername = it.take(256))) },
            label = { Text(stringResource(R.string.monitor_database_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SensitiveField(
            value = draft.databasePassword,
            onValueChange = { onDraftChange(draft.copy(databasePassword = it.take(2_048))) },
            label = stringResource(R.string.monitor_database_password),
            saved = draft.databaseHasSavedPassword,
        )
    }
    if (draft.type != "redis") {
        OutlinedTextField(
            value = draft.databaseQuery,
            onValueChange = { onDraftChange(draft.copy(databaseQuery = it.take(10_000))) },
            label = {
                Text(
                    stringResource(
                        if (draft.type == "mongodb") {
                            R.string.monitor_database_mongodb_command
                        } else {
                            R.string.monitor_database_query
                        },
                    ),
                )
            },
            supportingText = {
                Text(
                    stringResource(
                        when (draft.type) {
                            "mongodb" -> R.string.monitor_database_mongodb_command_help
                            "oracledb" -> R.string.monitor_database_oracle_query_help
                            else -> R.string.monitor_database_query_help
                        },
                    ),
                )
            },
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (draft.type == "mongodb") {
        OutlinedTextField(
            value = draft.databaseJsonQueryExpression,
            onValueChange = { onDraftChange(draft.copy(databaseJsonQueryExpression = it.take(2_000))) },
            label = { Text(stringResource(R.string.monitor_json_query_expression)) },
            supportingText = { Text(stringResource(R.string.monitor_database_mongodb_jsonata_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.databaseExpectedValue,
            onValueChange = { onDraftChange(draft.copy(databaseExpectedValue = it.take(2_000))) },
            label = { Text(stringResource(R.string.monitor_json_query_expected_value)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (draft.type == "redis") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = draft.databaseIgnoreTls,
                    role = Role.Checkbox,
                    onValueChange = { onDraftChange(draft.copy(databaseIgnoreTls = it)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = draft.databaseIgnoreTls, onCheckedChange = null)
            Text(stringResource(R.string.monitor_database_redis_ignore_tls))
        }
    }
    if (draft.type == "mysql" || draft.type == "sqlserver" || draft.type == "oracledb") {
        Text(
            stringResource(R.string.monitor_database_conditions_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JsonQueryFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
    enabled: Boolean = true,
) {
    var operatorMenuOpen by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = draft.jsonQueryExpression,
        onValueChange = { onDraftChange(draft.copy(jsonQueryExpression = it.take(2_000))) },
        label = { Text(stringResource(R.string.monitor_json_query_expression)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    ExposedDropdownMenuBox(
        expanded = operatorMenuOpen,
        onExpandedChange = { if (enabled) operatorMenuOpen = it },
    ) {
        OutlinedTextField(
            value = draft.jsonQueryOperator,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.monitor_json_query_operator)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(operatorMenuOpen) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = operatorMenuOpen,
            onDismissRequest = { operatorMenuOpen = false },
        ) {
            MonitorDraftCodec.JSON_QUERY_OPERATORS.forEach { operator ->
                DropdownMenuItem(
                    text = { Text(operator) },
                    onClick = {
                        onDraftChange(draft.copy(jsonQueryOperator = operator))
                        operatorMenuOpen = false
                    },
                )
            }
        }
    }
    OutlinedTextField(
        value = draft.jsonQueryExpectedValue,
        onValueChange = { onDraftChange(draft.copy(jsonQueryExpectedValue = it.take(2_000))) },
        label = { Text(stringResource(R.string.monitor_json_query_expected_value)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.monitor_json_query_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BrokerTimeoutField(draft: MonitorDraft, onDraftChange: (MonitorDraft) -> Unit) {
    OutlinedTextField(
        value = draft.brokerTimeoutSeconds,
        onValueChange = { onDraftChange(draft.copy(brokerTimeoutSeconds = it.take(12))) },
        label = { Text(stringResource(R.string.monitor_broker_timeout)) },
        supportingText = { Text(stringResource(R.string.monitor_broker_timeout_help)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RabbitmqFields(draft: MonitorDraft, onDraftChange: (MonitorDraft) -> Unit) {
    Text(stringResource(R.string.monitor_rabbitmq_title), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = draft.rabbitmqNodes,
        onValueChange = { onDraftChange(draft.copy(rabbitmqNodes = it.take(10_000))) },
        label = { Text(stringResource(R.string.monitor_rabbitmq_nodes)) },
        supportingText = { Text(stringResource(R.string.monitor_rabbitmq_nodes_help)) },
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.rabbitmqUsername,
        onValueChange = { onDraftChange(draft.copy(rabbitmqUsername = it.take(1_000))) },
        label = { Text(stringResource(R.string.monitor_rabbitmq_username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SensitiveField(
        value = draft.rabbitmqPassword,
        onValueChange = { onDraftChange(draft.copy(rabbitmqPassword = it.take(4_000))) },
        label = stringResource(R.string.monitor_rabbitmq_password),
        saved = draft.rabbitmqHasSavedPassword,
        savedMessage = stringResource(R.string.monitor_broker_secret_saved),
    )
    BrokerTimeoutField(draft, onDraftChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KafkaFields(draft: MonitorDraft, onDraftChange: (MonitorDraft) -> Unit) {
    var mechanismMenuOpen by remember { mutableStateOf(false) }
    Text(stringResource(R.string.monitor_kafka_title), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = draft.kafkaBrokers,
        onValueChange = { onDraftChange(draft.copy(kafkaBrokers = it.take(10_000))) },
        label = { Text(stringResource(R.string.monitor_kafka_brokers)) },
        supportingText = { Text(stringResource(R.string.monitor_kafka_brokers_help)) },
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.kafkaTopic,
        onValueChange = { onDraftChange(draft.copy(kafkaTopic = it.take(1_000))) },
        label = { Text(stringResource(R.string.monitor_kafka_topic)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.kafkaMessage,
        onValueChange = { onDraftChange(draft.copy(kafkaMessage = it.take(20_000))) },
        label = { Text(stringResource(R.string.monitor_kafka_message)) },
        minLines = 2,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    BooleanEditorRow(
        checked = draft.kafkaSsl,
        label = stringResource(R.string.monitor_kafka_ssl),
        onCheckedChange = { onDraftChange(draft.copy(kafkaSsl = it)) },
    )
    BooleanEditorRow(
        checked = draft.kafkaAllowAutoTopicCreation,
        label = stringResource(R.string.monitor_kafka_auto_topic),
        onCheckedChange = { onDraftChange(draft.copy(kafkaAllowAutoTopicCreation = it)) },
    )
    BrokerTimeoutField(draft, onDraftChange)
    Text(stringResource(R.string.monitor_kafka_sasl_title), style = MaterialTheme.typography.titleSmall)
    if (!draft.kafkaSaslEditable) {
        Text(
            stringResource(R.string.monitor_kafka_sasl_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    ExposedDropdownMenuBox(
        expanded = mechanismMenuOpen,
        onExpandedChange = { if (draft.kafkaSaslEditable) mechanismMenuOpen = it },
    ) {
        OutlinedTextField(
            value = stringResource(draft.kafkaSaslMechanism.labelRes),
            onValueChange = {},
            readOnly = true,
            enabled = draft.kafkaSaslEditable,
            label = { Text(stringResource(R.string.monitor_kafka_sasl_mechanism)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mechanismMenuOpen) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = mechanismMenuOpen, onDismissRequest = { mechanismMenuOpen = false }) {
            KafkaSaslMechanism.entries.filter { it != KafkaSaslMechanism.UNSUPPORTED }.forEach { mechanism ->
                DropdownMenuItem(
                    text = { Text(stringResource(mechanism.labelRes)) },
                    onClick = {
                        onDraftChange(
                            draft.copy(
                                kafkaSaslMechanism = mechanism,
                                kafkaPassword = "",
                                kafkaSecretAccessKey = "",
                                kafkaSessionToken = "",
                                kafkaClearSavedSessionToken = false,
                            ),
                        )
                        mechanismMenuOpen = false
                    },
                )
            }
        }
    }
    when (draft.kafkaSaslMechanism) {
        KafkaSaslMechanism.PLAIN,
        KafkaSaslMechanism.SCRAM_SHA_256,
        KafkaSaslMechanism.SCRAM_SHA_512,
        -> {
            OutlinedTextField(
                value = draft.kafkaUsername,
                onValueChange = { onDraftChange(draft.copy(kafkaUsername = it.take(1_000))) },
                label = { Text(stringResource(R.string.monitor_kafka_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SensitiveField(
                value = draft.kafkaPassword,
                onValueChange = { onDraftChange(draft.copy(kafkaPassword = it.take(4_000))) },
                label = stringResource(R.string.monitor_kafka_password),
                saved = draft.kafkaOriginalSaslMechanism == draft.kafkaSaslMechanism &&
                    draft.kafkaHasSavedPassword,
                savedMessage = stringResource(R.string.monitor_broker_secret_saved),
            )
        }
        KafkaSaslMechanism.AWS -> {
            OutlinedTextField(
                value = draft.kafkaAuthorizationIdentity,
                onValueChange = { onDraftChange(draft.copy(kafkaAuthorizationIdentity = it.take(1_000))) },
                label = { Text(stringResource(R.string.monitor_kafka_authorization_identity)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.kafkaAccessKeyId,
                onValueChange = { onDraftChange(draft.copy(kafkaAccessKeyId = it.take(1_000))) },
                label = { Text(stringResource(R.string.monitor_kafka_access_key)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SensitiveField(
                value = draft.kafkaSecretAccessKey,
                onValueChange = { onDraftChange(draft.copy(kafkaSecretAccessKey = it.take(4_000))) },
                label = stringResource(R.string.monitor_kafka_secret_key),
                saved = draft.kafkaOriginalSaslMechanism == KafkaSaslMechanism.AWS &&
                    draft.kafkaHasSavedSecretAccessKey,
                savedMessage = stringResource(R.string.monitor_broker_secret_saved),
            )
            SensitiveField(
                value = draft.kafkaSessionToken,
                onValueChange = {
                    onDraftChange(draft.copy(kafkaSessionToken = it.take(8_000), kafkaClearSavedSessionToken = false))
                },
                label = stringResource(R.string.monitor_kafka_session_token),
                saved = draft.kafkaOriginalSaslMechanism == KafkaSaslMechanism.AWS &&
                    draft.kafkaHasSavedSessionToken && !draft.kafkaClearSavedSessionToken,
                savedMessage = stringResource(R.string.monitor_broker_secret_saved),
            )
            if (draft.kafkaHasSavedSessionToken) {
                BooleanEditorRow(
                    checked = draft.kafkaClearSavedSessionToken,
                    label = stringResource(R.string.monitor_kafka_clear_session_token),
                    onCheckedChange = {
                        onDraftChange(draft.copy(kafkaClearSavedSessionToken = it, kafkaSessionToken = ""))
                    },
                )
            }
        }
        KafkaSaslMechanism.NONE,
        KafkaSaslMechanism.UNSUPPORTED,
        -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DockerFields(
    draft: MonitorDraft,
    hosts: List<KumaDockerHost>,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    var hostMenuOpen by remember { mutableStateOf(false) }
    val selectedHost = hosts.firstOrNull { it.id == draft.dockerHostId }
    Text(stringResource(R.string.monitor_docker_title), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = draft.dockerContainer,
        onValueChange = { onDraftChange(draft.copy(dockerContainer = it.take(1_000))) },
        label = { Text(stringResource(R.string.monitor_docker_container)) },
        supportingText = { Text(stringResource(R.string.monitor_docker_container_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ExposedDropdownMenuBox(
        expanded = hostMenuOpen,
        onExpandedChange = { if (hosts.isNotEmpty()) hostMenuOpen = it },
    ) {
        OutlinedTextField(
            value = selectedHost?.name ?: draft.dockerHostId?.let {
                stringResource(R.string.monitor_docker_unavailable_host, it)
            }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = hosts.isNotEmpty(),
            label = { Text(stringResource(R.string.monitor_docker_host)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(hostMenuOpen) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = hostMenuOpen, onDismissRequest = { hostMenuOpen = false }) {
            hosts.forEach { host ->
                DropdownMenuItem(
                    text = { Text(host.name) },
                    onClick = {
                        onDraftChange(draft.copy(dockerHostId = host.id))
                        hostMenuOpen = false
                    },
                )
            }
        }
    }
    if (hosts.isEmpty()) {
        Text(
            stringResource(R.string.monitor_docker_no_hosts),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealBrowserFields(
    draft: MonitorDraft,
    browsers: List<KumaRemoteBrowser>,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    var browserMenuOpen by remember { mutableStateOf(false) }
    val selectedBrowser = browsers.firstOrNull { it.id == draft.remoteBrowserId }
    val maxDelay = draft.intervalSeconds.coerceAtLeast(0) * 500L
    Text(stringResource(R.string.monitor_real_browser_title), style = MaterialTheme.typography.titleSmall)
    if (browsers.isEmpty() && draft.remoteBrowserId == null) {
        Text(
            stringResource(R.string.monitor_real_browser_local_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        BooleanEditorRow(
            checked = draft.remoteBrowserId != null,
            label = stringResource(R.string.monitor_real_browser_use_remote),
            onCheckedChange = { enabled ->
                onDraftChange(
                    draft.copy(
                        remoteBrowserId = if (enabled) {
                            selectedBrowser?.id ?: browsers.firstOrNull()?.id
                        } else {
                            null
                        },
                    ),
                )
            },
        )
    }
    if (draft.remoteBrowserId != null) {
        ExposedDropdownMenuBox(
            expanded = browserMenuOpen,
            onExpandedChange = { if (browsers.isNotEmpty()) browserMenuOpen = it },
        ) {
            OutlinedTextField(
                value = selectedBrowser?.name ?: stringResource(
                    R.string.monitor_real_browser_unavailable,
                    draft.remoteBrowserId,
                ),
                onValueChange = {},
                readOnly = true,
                enabled = browsers.isNotEmpty(),
                label = { Text(stringResource(R.string.monitor_real_browser_remote)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(browserMenuOpen) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = browserMenuOpen, onDismissRequest = { browserMenuOpen = false }) {
                browsers.forEach { browser ->
                    DropdownMenuItem(
                        text = { Text(browser.name) },
                        onClick = {
                            onDraftChange(draft.copy(remoteBrowserId = browser.id))
                            browserMenuOpen = false
                        },
                    )
                }
            }
        }
    }
    OutlinedTextField(
        value = draft.screenshotDelayMs.toString(),
        onValueChange = { value ->
            value.filter(Char::isDigit).take(9).toIntOrNull()?.let { delay ->
                onDraftChange(draft.copy(screenshotDelayMs = delay))
            }
        },
        label = { Text(stringResource(R.string.monitor_real_browser_screenshot_delay)) },
        supportingText = { Text(stringResource(R.string.monitor_real_browser_screenshot_delay_help, maxDelay)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RadiusFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    Text(stringResource(R.string.monitor_radius_title), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = draft.radiusUsername,
        onValueChange = { onDraftChange(draft.copy(radiusUsername = it.take(256))) },
        label = { Text(stringResource(R.string.monitor_radius_username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SensitiveField(
        value = draft.radiusPassword,
        onValueChange = { onDraftChange(draft.copy(radiusPassword = it.take(2_048))) },
        label = stringResource(R.string.monitor_radius_password),
        saved = draft.radiusHasSavedPassword,
    )
    SensitiveField(
        value = draft.radiusSecret,
        onValueChange = { onDraftChange(draft.copy(radiusSecret = it.take(2_048))) },
        label = stringResource(R.string.monitor_radius_secret),
        saved = draft.radiusHasSavedSecret,
    )
    Text(
        stringResource(R.string.monitor_radius_secret_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = draft.radiusCalledStationId,
        onValueChange = { onDraftChange(draft.copy(radiusCalledStationId = it.take(256))) },
        label = { Text(stringResource(R.string.monitor_radius_called_station_id)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.radiusCallingStationId,
        onValueChange = { onDraftChange(draft.copy(radiusCallingStationId = it.take(256))) },
        label = { Text(stringResource(R.string.monitor_radius_calling_station_id)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BooleanEditorRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnmpFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    var versionMenuOpen by remember { mutableStateOf(false) }
    val enabled = draft.snmpFieldsEditable
    Text(stringResource(R.string.monitor_snmp_title), style = MaterialTheme.typography.titleSmall)
    if (!enabled) {
        Text(
            stringResource(R.string.monitor_snmp_v3_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    ExposedDropdownMenuBox(
        expanded = versionMenuOpen,
        onExpandedChange = { if (enabled) versionMenuOpen = it },
    ) {
        OutlinedTextField(
            value = stringResource(draft.snmpVersion.labelRes),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.monitor_snmp_version)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(versionMenuOpen) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = versionMenuOpen, onDismissRequest = { versionMenuOpen = false }) {
            listOf(SnmpVersion.V1, SnmpVersion.V2C).forEach { version ->
                DropdownMenuItem(
                    text = { Text(stringResource(version.labelRes)) },
                    onClick = {
                        onDraftChange(draft.copy(snmpVersion = version))
                        versionMenuOpen = false
                    },
                )
            }
        }
    }
    SensitiveField(
        value = draft.snmpCommunity,
        onValueChange = { onDraftChange(draft.copy(snmpCommunity = it.take(2_000))) },
        label = stringResource(R.string.monitor_snmp_community),
        saved = draft.snmpHasSavedCommunity,
        savedMessage = stringResource(R.string.monitor_snmp_community_saved),
        enabled = enabled,
    )
    OutlinedTextField(
        value = draft.snmpOid,
        onValueChange = { onDraftChange(draft.copy(snmpOid = it.take(512))) },
        label = { Text(stringResource(R.string.monitor_snmp_oid)) },
        supportingText = { Text(stringResource(R.string.monitor_snmp_oid_help)) },
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.snmpTimeoutSeconds,
        onValueChange = { onDraftChange(draft.copy(snmpTimeoutSeconds = it.take(12))) },
        label = { Text(stringResource(R.string.monitor_snmp_timeout)) },
        supportingText = { Text(stringResource(R.string.monitor_snmp_timeout_help)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    JsonQueryFields(draft = draft, onDraftChange = onDraftChange, enabled = enabled)
}

@Composable
private fun GrpcFields(
    draft: MonitorDraft,
    onDraftChange: (MonitorDraft) -> Unit,
) {
    Text(stringResource(R.string.monitor_grpc_title), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = draft.grpcTarget,
        onValueChange = { onDraftChange(draft.copy(grpcTarget = it.take(2_048))) },
        label = { Text(stringResource(R.string.monitor_grpc_target)) },
        supportingText = { Text(stringResource(R.string.monitor_grpc_target_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.grpcServiceName,
        onValueChange = { onDraftChange(draft.copy(grpcServiceName = it.take(512))) },
        label = { Text(stringResource(R.string.monitor_grpc_service)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.grpcMethod,
        onValueChange = { onDraftChange(draft.copy(grpcMethod = it.take(256))) },
        label = { Text(stringResource(R.string.monitor_grpc_method)) },
        supportingText = { Text(stringResource(R.string.monitor_grpc_method_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.grpcProtobuf,
        onValueChange = { onDraftChange(draft.copy(grpcProtobuf = it.take(100_000))) },
        label = { Text(stringResource(R.string.monitor_grpc_protobuf)) },
        minLines = 5,
        maxLines = 12,
        modifier = Modifier.fillMaxWidth(),
    )
    SensitiveField(
        value = draft.grpcBody,
        onValueChange = { onDraftChange(draft.copy(grpcBody = it.take(20_000))) },
        label = stringResource(R.string.monitor_grpc_body),
        saved = draft.grpcHasSavedBody,
        savedMessage = stringResource(R.string.monitor_grpc_body_saved),
        minLines = 3,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = draft.grpcEnableTls,
                role = Role.Checkbox,
                onValueChange = { onDraftChange(draft.copy(grpcEnableTls = it)) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = draft.grpcEnableTls, onCheckedChange = null)
        Text(stringResource(R.string.monitor_grpc_tls))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalpingFields(draft: MonitorDraft, onDraftChange: (MonitorDraft) -> Unit) {
    if (!draft.globalpingEditable) {
        Text(
            stringResource(R.string.monitor_globalping_browser_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    var subtypeMenuOpen by remember { mutableStateOf(false) }
    var ipFamilyMenuOpen by remember { mutableStateOf(false) }
    var protocolMenuOpen by remember { mutableStateOf(false) }
    var recordTypeMenuOpen by remember { mutableStateOf(false) }
    var methodMenuOpen by remember { mutableStateOf(false) }
    var responseMenuOpen by remember { mutableStateOf(false) }
    var jsonOperatorMenuOpen by remember { mutableStateOf(false) }
    val subtype = draft.globalpingSubtype ?: GlobalpingSubtype.PING

    Text(stringResource(R.string.monitor_globalping_title), style = MaterialTheme.typography.titleSmall)
    ExposedDropdownMenuBox(expanded = subtypeMenuOpen, onExpandedChange = { subtypeMenuOpen = it }) {
        OutlinedTextField(
            value = stringResource(subtype.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_globalping_subtype)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subtypeMenuOpen) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = subtypeMenuOpen, onDismissRequest = { subtypeMenuOpen = false }) {
            GlobalpingSubtype.entries.forEach { next ->
                DropdownMenuItem(
                    text = { Text(stringResource(next.labelRes)) },
                    onClick = {
                        onDraftChange(draft.forGlobalpingSubtype(next))
                        subtypeMenuOpen = false
                    },
                )
            }
        }
    }
    OutlinedTextField(
        value = draft.globalpingTarget,
        onValueChange = { onDraftChange(draft.copy(globalpingTarget = it.take(2_048))) },
        label = {
            Text(
                stringResource(
                    if (subtype == GlobalpingSubtype.HTTP) R.string.monitor_url_label else R.string.monitor_host_label,
                ),
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.globalpingLocation,
        onValueChange = { onDraftChange(draft.copy(globalpingLocation = it.take(255))) },
        label = { Text(stringResource(R.string.monitor_globalping_location)) },
        supportingText = { Text(stringResource(R.string.monitor_globalping_location_help)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ExposedDropdownMenuBox(expanded = ipFamilyMenuOpen, onExpandedChange = { ipFamilyMenuOpen = it }) {
        OutlinedTextField(
            value = stringResource((draft.globalpingIpFamily ?: GlobalpingIpFamily.AUTO).labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_globalping_ip_family)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ipFamilyMenuOpen) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = ipFamilyMenuOpen, onDismissRequest = { ipFamilyMenuOpen = false }) {
            GlobalpingIpFamily.entries.forEach { family ->
                DropdownMenuItem(
                    text = { Text(stringResource(family.labelRes)) },
                    onClick = {
                        onDraftChange(draft.copy(globalpingIpFamily = family))
                        ipFamilyMenuOpen = false
                    },
                )
            }
        }
    }
    Text(
        stringResource(R.string.monitor_globalping_ip_family_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (subtype != GlobalpingSubtype.PING) {
        OutlinedTextField(
            value = draft.globalpingResolver,
            onValueChange = { onDraftChange(draft.copy(globalpingResolver = it.take(253))) },
            label = { Text(stringResource(R.string.monitor_globalping_resolver)) },
            supportingText = { Text(stringResource(R.string.monitor_globalping_resolver_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    when (subtype) {
        GlobalpingSubtype.PING -> {
            GlobalpingProtocolField(
                value = draft.globalpingPingProtocol?.wireValue.orEmpty(),
                options = GlobalpingPingProtocol.entries.map(GlobalpingPingProtocol::wireValue),
                expanded = protocolMenuOpen,
                onExpandedChange = { protocolMenuOpen = it },
                onSelect = { value ->
                    onDraftChange(
                        draft.copy(
                            globalpingPingProtocol = GlobalpingPingProtocol.fromWire(value),
                            port = draft.port ?: 80,
                        ),
                    )
                    protocolMenuOpen = false
                },
            )
            if (draft.globalpingPingProtocol == GlobalpingPingProtocol.TCP) {
                NumberField(
                    value = draft.port,
                    onValueChange = { onDraftChange(draft.copy(port = it)) },
                    label = stringResource(R.string.monitor_port_label),
                    maxDigits = 5,
                )
            }
            NumberField(
                value = draft.globalpingPingCount,
                onValueChange = { onDraftChange(draft.copy(globalpingPingCount = it)) },
                label = stringResource(R.string.monitor_globalping_ping_count),
                maxDigits = 3,
            )
            Text(
                stringResource(R.string.monitor_globalping_ping_count_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlobalpingSubtype.DNS -> {
            NumberField(
                value = draft.port,
                onValueChange = { onDraftChange(draft.copy(port = it)) },
                label = stringResource(R.string.monitor_port_label),
                maxDigits = 5,
            )
            ExposedDropdownMenuBox(
                expanded = recordTypeMenuOpen,
                onExpandedChange = { recordTypeMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = draft.globalpingDnsRecordType?.wireValue.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.monitor_globalping_dns_record_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(recordTypeMenuOpen) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = recordTypeMenuOpen,
                    onDismissRequest = { recordTypeMenuOpen = false },
                ) {
                    GlobalpingDnsRecordType.entries.forEach { recordType ->
                        DropdownMenuItem(
                            text = { Text(recordType.wireValue) },
                            onClick = {
                                onDraftChange(draft.copy(globalpingDnsRecordType = recordType))
                                recordTypeMenuOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft.keyword,
                onValueChange = { onDraftChange(draft.copy(keyword = it.take(2_000))) },
                label = { Text(stringResource(R.string.monitor_globalping_dns_match)) },
                supportingText = { Text(stringResource(R.string.monitor_globalping_dns_match_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            GlobalpingProtocolField(
                value = draft.globalpingDnsProtocol?.wireValue.orEmpty(),
                options = GlobalpingDnsProtocol.entries.map(GlobalpingDnsProtocol::wireValue),
                expanded = protocolMenuOpen,
                onExpandedChange = { protocolMenuOpen = it },
                onSelect = { value ->
                    onDraftChange(draft.copy(globalpingDnsProtocol = GlobalpingDnsProtocol.fromWire(value)))
                    protocolMenuOpen = false
                },
            )
        }
        GlobalpingSubtype.HTTP -> {
            GlobalpingProtocolField(
                value = stringResource(
                    if (draft.globalpingHttpProtocol == GlobalpingHttpProtocol.HTTP2) {
                        R.string.monitor_globalping_http2
                    } else {
                        R.string.monitor_globalping_ip_auto
                    },
                ),
                options = listOf(
                    stringResource(R.string.monitor_globalping_ip_auto),
                    stringResource(R.string.monitor_globalping_http2),
                ),
                expanded = protocolMenuOpen,
                onExpandedChange = { protocolMenuOpen = it },
                onSelect = { value ->
                    onDraftChange(
                        draft.copy(
                            globalpingHttpProtocol = if (value == "HTTP2") {
                                GlobalpingHttpProtocol.HTTP2
                            } else {
                                GlobalpingHttpProtocol.AUTO
                            },
                        ),
                    )
                    protocolMenuOpen = false
                },
            )
            ExposedDropdownMenuBox(expanded = methodMenuOpen, onExpandedChange = { methodMenuOpen = it }) {
                OutlinedTextField(
                    value = draft.globalpingHttpMethod?.wireValue.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.monitor_globalping_http_method)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodMenuOpen) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = methodMenuOpen, onDismissRequest = { methodMenuOpen = false }) {
                    GlobalpingHttpMethod.entries.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method.wireValue) },
                            onClick = {
                                onDraftChange(draft.copy(globalpingHttpMethod = method))
                                methodMenuOpen = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft.globalpingHttpAcceptedCodes,
                onValueChange = { onDraftChange(draft.copy(globalpingHttpAcceptedCodes = it.take(500))) },
                label = { Text(stringResource(R.string.monitor_globalping_http_status_codes)) },
                supportingText = { Text(stringResource(R.string.monitor_globalping_http_status_codes_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            GlobalpingHttpToggle(
                checked = draft.globalpingHttpIgnoreTls,
                label = stringResource(R.string.monitor_globalping_http_ignore_tls),
                onCheckedChange = { onDraftChange(draft.copy(globalpingHttpIgnoreTls = it)) },
            )
            GlobalpingHttpToggle(
                checked = draft.globalpingHttpExpiryNotification,
                label = stringResource(R.string.monitor_globalping_http_expiry),
                onCheckedChange = { onDraftChange(draft.copy(globalpingHttpExpiryNotification = it)) },
            )
            GlobalpingHttpToggle(
                checked = draft.globalpingHttpCacheBust,
                label = stringResource(R.string.monitor_globalping_http_cache_bust),
                onCheckedChange = { onDraftChange(draft.copy(globalpingHttpCacheBust = it)) },
            )
            WebsocketHeaderFields(draft = draft, onDraftChange = onDraftChange)
            WebsocketAuthFields(draft = draft, onDraftChange = onDraftChange, allowMtls = false)
            ExposedDropdownMenuBox(expanded = responseMenuOpen, onExpandedChange = { responseMenuOpen = it }) {
                OutlinedTextField(
                    value = stringResource(draft.globalpingHttpResponseCheck.labelRes),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.monitor_globalping_http_response_check)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(responseMenuOpen) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = responseMenuOpen, onDismissRequest = { responseMenuOpen = false }) {
                    GlobalpingHttpResponseCheck.entries.forEach { check ->
                        DropdownMenuItem(
                            text = { Text(stringResource(check.labelRes)) },
                            onClick = {
                                onDraftChange(draft.copy(globalpingHttpResponseCheck = check))
                                responseMenuOpen = false
                            },
                        )
                    }
                }
            }
            when (draft.globalpingHttpResponseCheck) {
                GlobalpingHttpResponseCheck.NONE -> Unit
                GlobalpingHttpResponseCheck.KEYWORD -> {
                    OutlinedTextField(
                        value = draft.keyword,
                        onValueChange = { onDraftChange(draft.copy(keyword = it.take(2_000))) },
                        label = { Text(stringResource(R.string.monitor_keyword_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GlobalpingHttpToggle(
                        checked = draft.invertKeyword,
                        label = stringResource(R.string.monitor_keyword_invert),
                        onCheckedChange = { onDraftChange(draft.copy(invertKeyword = it)) },
                    )
                }
                GlobalpingHttpResponseCheck.JSON_QUERY -> {
                    OutlinedTextField(
                        value = draft.jsonQueryExpression,
                        onValueChange = { onDraftChange(draft.copy(jsonQueryExpression = it.take(2_000))) },
                        label = { Text(stringResource(R.string.monitor_json_query_expression)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ExposedDropdownMenuBox(
                        expanded = jsonOperatorMenuOpen,
                        onExpandedChange = { jsonOperatorMenuOpen = it },
                    ) {
                        OutlinedTextField(
                            value = draft.jsonQueryOperator,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.monitor_json_query_operator)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(jsonOperatorMenuOpen) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = jsonOperatorMenuOpen,
                            onDismissRequest = { jsonOperatorMenuOpen = false },
                        ) {
                            MonitorDraftCodec.JSON_QUERY_OPERATORS.forEach { operator ->
                                DropdownMenuItem(
                                    text = { Text(operator) },
                                    onClick = {
                                        onDraftChange(draft.copy(jsonQueryOperator = operator))
                                        jsonOperatorMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft.jsonQueryExpectedValue,
                        onValueChange = { onDraftChange(draft.copy(jsonQueryExpectedValue = it.take(2_000))) },
                        label = { Text(stringResource(R.string.monitor_json_query_expected_value)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlobalpingProtocolField(
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.monitor_globalping_protocol)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option) })
            }
        }
    }
}

@Composable
private fun GlobalpingHttpToggle(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

private fun MonitorDraft.forGlobalpingSubtype(subtype: GlobalpingSubtype): MonitorDraft = when (subtype) {
    GlobalpingSubtype.PING -> copy(
        globalpingSubtype = subtype,
        globalpingPingProtocol = GlobalpingPingProtocol.ICMP,
        globalpingPingCount = globalpingPingCount ?: 3,
        port = 80,
    )
    GlobalpingSubtype.DNS -> copy(
        globalpingSubtype = subtype,
        globalpingDnsProtocol = GlobalpingDnsProtocol.UDP,
        globalpingDnsRecordType = globalpingDnsRecordType ?: GlobalpingDnsRecordType.A,
        port = 53,
    )
    GlobalpingSubtype.HTTP -> copy(
        globalpingSubtype = subtype,
        globalpingHttpProtocol = GlobalpingHttpProtocol.AUTO,
        globalpingHttpMethod = globalpingHttpMethod ?: GlobalpingHttpMethod.GET,
        globalpingHttpAcceptedCodes = globalpingHttpAcceptedCodes.ifBlank { "200-299" },
    )
}

private val GlobalpingSubtype.labelRes: Int
    get() = when (this) {
        GlobalpingSubtype.PING -> R.string.monitor_globalping_subtype_ping
        GlobalpingSubtype.HTTP -> R.string.monitor_globalping_subtype_http
        GlobalpingSubtype.DNS -> R.string.monitor_globalping_subtype_dns
    }

private val GlobalpingHttpResponseCheck.labelRes: Int
    get() = when (this) {
        GlobalpingHttpResponseCheck.NONE -> R.string.monitor_globalping_http_response_none
        GlobalpingHttpResponseCheck.KEYWORD -> R.string.monitor_globalping_http_response_keyword
        GlobalpingHttpResponseCheck.JSON_QUERY -> R.string.monitor_globalping_http_response_json
    }

private val GlobalpingIpFamily.labelRes: Int
    get() = when (this) {
        GlobalpingIpFamily.AUTO -> R.string.monitor_globalping_ip_auto
        GlobalpingIpFamily.IPV4 -> R.string.monitor_globalping_ip_v4
        GlobalpingIpFamily.IPV6 -> R.string.monitor_globalping_ip_v6
    }

private val SmtpSecurityMode.labelRes: Int
    get() = when (this) {
        SmtpSecurityMode.SMTPS -> R.string.monitor_smtp_security_smtps
        SmtpSecurityMode.PLAINTEXT -> R.string.monitor_smtp_security_plaintext
        SmtpSecurityMode.STARTTLS -> R.string.monitor_smtp_security_starttls
    }

private val SnmpVersion.labelRes: Int
    get() = when (this) {
        SnmpVersion.V1 -> R.string.monitor_snmp_version_1
        SnmpVersion.V2C -> R.string.monitor_snmp_version_2c
        SnmpVersion.V3 -> R.string.monitor_snmp_version_3
        SnmpVersion.UNSUPPORTED -> R.string.monitor_snmp_version_unsupported
    }

private val KafkaSaslMechanism.labelRes: Int
    get() = when (this) {
        KafkaSaslMechanism.NONE -> R.string.monitor_kafka_sasl_none
        KafkaSaslMechanism.PLAIN -> R.string.monitor_kafka_sasl_plain
        KafkaSaslMechanism.SCRAM_SHA_256 -> R.string.monitor_kafka_sasl_scram_256
        KafkaSaslMechanism.SCRAM_SHA_512 -> R.string.monitor_kafka_sasl_scram_512
        KafkaSaslMechanism.AWS -> R.string.monitor_kafka_sasl_aws
        KafkaSaslMechanism.UNSUPPORTED -> R.string.monitor_kafka_sasl_unsupported
    }

@Composable
private fun PushMonitorSetup(pushUrl: String?, pushToken: String, isNew: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var revealed by remember(pushToken) { mutableStateOf(false) }

    Text(stringResource(R.string.monitor_push_url_title), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(if (isNew) R.string.monitor_push_url_new_desc else R.string.monitor_push_url_existing_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (pushUrl == null) {
        Text(stringResource(R.string.monitor_push_url_unavailable), color = MaterialTheme.colorScheme.error)
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Text(
            if (revealed) pushUrl else pushUrl.replace(pushToken, "••••••••"),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
    val shareChooserTitle = stringResource(R.string.monitor_push_share_chooser)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                scope.launch {
                    val clip = ClipData.newPlainText("Uptime Kuma push URL", pushUrl).apply {
                        description.extras = PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                    }
                    clipboard.setClipEntry(ClipEntry(clip))
                }
            },
        ) { Text(stringResource(R.string.push_copy)) }
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, pushUrl)
                }
                context.startActivity(
                    Intent.createChooser(send, shareChooserTitle),
                )
            },
        ) { Text(stringResource(R.string.monitor_push_share)) }
        TextButton(onClick = { revealed = !revealed }) {
            Text(stringResource(if (revealed) R.string.action_hide else R.string.action_show))
        }
    }
    Text(
        stringResource(R.string.monitor_push_delivery_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.monitor_push_security_desc),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun SensitiveField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    saved: Boolean,
    savedMessage: String? = null,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    var visible by remember(label) { mutableStateOf(false) }
    val helper = savedMessage ?: stringResource(R.string.monitor_sftp_saved_secret)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = if (saved && value.isEmpty()) {
            { Text(helper) }
        } else {
            null
        },
        trailingIcon = {
            TextButton(onClick = { visible = !visible }, enabled = enabled) {
                Text(stringResource(if (visible) R.string.action_hide else R.string.action_show))
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = minLines == 1,
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 8,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumberField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    label: String,
    maxDigits: Int = 7,
) {
    OutlinedTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(maxDigits).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun validationMessage(error: MonitorDraftError): String = stringResource(
    when (error) {
        MonitorDraftError.NAME_REQUIRED -> R.string.monitor_error_name
        MonitorDraftError.TYPE_UNAVAILABLE -> R.string.monitor_error_type
        MonitorDraftError.ENDPOINT_REQUIRED -> R.string.monitor_error_endpoint
        MonitorDraftError.INVALID_URL -> R.string.monitor_error_url
        MonitorDraftError.PORT_REQUIRED -> R.string.monitor_error_port
        MonitorDraftError.INVALID_INTERVAL -> R.string.monitor_error_interval
        MonitorDraftError.INVALID_RETRIES -> R.string.monitor_error_retries
        MonitorDraftError.INVALID_PUSH_TOKEN -> R.string.monitor_error_push_token
        MonitorDraftError.KEYWORD_REQUIRED -> R.string.monitor_error_keyword
        MonitorDraftError.JSON_QUERY_EXPRESSION_REQUIRED -> R.string.monitor_error_json_query_expression
        MonitorDraftError.JSON_QUERY_OPERATOR_INVALID -> R.string.monitor_error_json_query_operator
        MonitorDraftError.JSON_QUERY_EXPECTED_VALUE_REQUIRED -> R.string.monitor_error_json_query_expected_value
        MonitorDraftError.WEBSOCKET_ACCEPTED_CODES_REQUIRED -> R.string.monitor_error_websocket_accepted_codes_required
        MonitorDraftError.WEBSOCKET_ACCEPTED_CODE_INVALID -> R.string.monitor_error_websocket_accepted_code_invalid
        MonitorDraftError.WEBSOCKET_TOO_MANY_HEADERS -> R.string.monitor_error_websocket_too_many_headers
        MonitorDraftError.WEBSOCKET_HEADER_INVALID -> R.string.monitor_error_websocket_header_invalid
        MonitorDraftError.WEBSOCKET_HEADER_VALUE_REQUIRED -> R.string.monitor_error_websocket_header_value
        MonitorDraftError.WEBSOCKET_HEADER_DUPLICATE -> R.string.monitor_error_websocket_header_duplicate
        MonitorDraftError.WEBSOCKET_BASIC_PASSWORD_REQUIRED -> R.string.monitor_error_websocket_basic_password
        MonitorDraftError.WEBSOCKET_BEARER_TOKEN_REQUIRED -> R.string.monitor_error_websocket_bearer_token
        MonitorDraftError.WEBSOCKET_OAUTH_TOKEN_URL_REQUIRED -> R.string.monitor_error_websocket_oauth_token_url
        MonitorDraftError.WEBSOCKET_OAUTH_TOKEN_URL_INVALID -> R.string.monitor_error_websocket_oauth_token_url_invalid
        MonitorDraftError.WEBSOCKET_OAUTH_CLIENT_ID_REQUIRED -> R.string.monitor_error_websocket_oauth_client_id
        MonitorDraftError.WEBSOCKET_OAUTH_CLIENT_SECRET_REQUIRED -> R.string.monitor_error_websocket_oauth_client_secret
        MonitorDraftError.WEBSOCKET_MTLS_CERTIFICATE_REQUIRED -> R.string.monitor_error_websocket_mtls_certificate
        MonitorDraftError.WEBSOCKET_MTLS_PRIVATE_KEY_REQUIRED -> R.string.monitor_error_websocket_mtls_private_key
        MonitorDraftError.MQTT_ENDPOINT_INVALID -> R.string.monitor_error_mqtt_endpoint
        MonitorDraftError.MQTT_TOPIC_REQUIRED -> R.string.monitor_error_mqtt_topic
        MonitorDraftError.MQTT_WEBSOCKET_PATH_INVALID -> R.string.monitor_error_mqtt_websocket_path
        MonitorDraftError.MQTT_JSON_QUERY_EXPRESSION_REQUIRED ->
            R.string.monitor_error_mqtt_json_query_expression
        MonitorDraftError.MQTT_JSON_QUERY_EXPECTED_VALUE_REQUIRED ->
            R.string.monitor_error_mqtt_json_query_expected_value
        MonitorDraftError.SMTP_HOST_INVALID -> R.string.monitor_error_smtp_host
        MonitorDraftError.SMTP_SECURITY_REQUIRED -> R.string.monitor_error_smtp_security
        MonitorDraftError.NTP_HOST_INVALID -> R.string.monitor_error_ntp_host
        MonitorDraftError.NTP_STRATUM_THRESHOLD_INVALID -> R.string.monitor_error_ntp_stratum_threshold
        MonitorDraftError.NTP_TIME_OFFSET_THRESHOLD_INVALID -> R.string.monitor_error_ntp_offset_threshold
        MonitorDraftError.NTP_ROOT_DISPERSION_THRESHOLD_INVALID ->
            R.string.monitor_error_ntp_dispersion_threshold
        MonitorDraftError.GLOBALPING_HOST_INVALID -> R.string.monitor_error_globalping_host
        MonitorDraftError.GLOBALPING_LOCATION_REQUIRED -> R.string.monitor_error_globalping_location
        MonitorDraftError.GLOBALPING_LOCATION_MULTIPLE -> R.string.monitor_error_globalping_location_multiple
        MonitorDraftError.GLOBALPING_PROTOCOL_INVALID -> R.string.monitor_error_globalping_protocol
        MonitorDraftError.GLOBALPING_PORT_REQUIRED -> R.string.monitor_error_globalping_port
        MonitorDraftError.GLOBALPING_PING_COUNT_INVALID -> R.string.monitor_error_globalping_ping_count
        MonitorDraftError.GLOBALPING_RESOLVER_INVALID -> R.string.monitor_error_globalping_resolver
        MonitorDraftError.GLOBALPING_DNS_RECORD_TYPE_INVALID -> R.string.monitor_error_globalping_dns_record_type
        MonitorDraftError.GLOBALPING_HTTP_URL_INVALID -> R.string.monitor_error_globalping_http_url
        MonitorDraftError.GLOBALPING_HTTP_METHOD_INVALID -> R.string.monitor_error_globalping_http_method
        MonitorDraftError.GLOBALPING_HTTP_STATUS_CODES_REQUIRED ->
            R.string.monitor_error_globalping_http_status_codes_required
        MonitorDraftError.GLOBALPING_HTTP_STATUS_CODE_INVALID ->
            R.string.monitor_error_globalping_http_status_code_invalid
        MonitorDraftError.GLOBALPING_HTTP_KEYWORD_REQUIRED -> R.string.monitor_error_globalping_http_keyword
        MonitorDraftError.GLOBALPING_HTTP_JSON_QUERY_EXPRESSION_REQUIRED ->
            R.string.monitor_error_globalping_http_json_expression
        MonitorDraftError.GLOBALPING_HTTP_JSON_QUERY_OPERATOR_INVALID ->
            R.string.monitor_error_globalping_http_json_operator
        MonitorDraftError.GLOBALPING_HTTP_JSON_QUERY_EXPECTED_VALUE_REQUIRED ->
            R.string.monitor_error_globalping_http_json_value
        MonitorDraftError.DATABASE_CONNECTION_STRING_REQUIRED ->
            R.string.monitor_error_database_connection_string
        MonitorDraftError.DATABASE_USERNAME_REQUIRED -> R.string.monitor_error_database_username
        MonitorDraftError.DATABASE_PASSWORD_REQUIRED -> R.string.monitor_error_database_password
        MonitorDraftError.DATABASE_MONGODB_COMMAND_INVALID ->
            R.string.monitor_error_database_mongodb_command
        MonitorDraftError.GRPC_TARGET_REQUIRED -> R.string.monitor_error_grpc_target
        MonitorDraftError.GRPC_PROTOBUF_REQUIRED -> R.string.monitor_error_grpc_protobuf
        MonitorDraftError.GRPC_SERVICE_REQUIRED -> R.string.monitor_error_grpc_service
        MonitorDraftError.GRPC_METHOD_REQUIRED -> R.string.monitor_error_grpc_method
        MonitorDraftError.GRPC_BODY_INVALID -> R.string.monitor_error_grpc_body
        MonitorDraftError.SNMP_HOST_INVALID -> R.string.monitor_error_snmp_host
        MonitorDraftError.SNMP_COMMUNITY_REQUIRED -> R.string.monitor_error_snmp_community
        MonitorDraftError.SNMP_OID_INVALID -> R.string.monitor_error_snmp_oid
        MonitorDraftError.SNMP_TIMEOUT_INVALID -> R.string.monitor_error_snmp_timeout
        MonitorDraftError.BROKER_TIMEOUT_INVALID -> R.string.monitor_error_broker_timeout
        MonitorDraftError.RABBITMQ_NODES_REQUIRED -> R.string.monitor_error_rabbitmq_nodes
        MonitorDraftError.RABBITMQ_NODE_INVALID -> R.string.monitor_error_rabbitmq_node
        MonitorDraftError.RABBITMQ_USERNAME_REQUIRED -> R.string.monitor_error_rabbitmq_username
        MonitorDraftError.RABBITMQ_PASSWORD_REQUIRED -> R.string.monitor_error_rabbitmq_password
        MonitorDraftError.KAFKA_BROKERS_REQUIRED -> R.string.monitor_error_kafka_brokers
        MonitorDraftError.KAFKA_BROKER_INVALID -> R.string.monitor_error_kafka_broker
        MonitorDraftError.KAFKA_TOPIC_REQUIRED -> R.string.monitor_error_kafka_topic
        MonitorDraftError.KAFKA_MESSAGE_REQUIRED -> R.string.monitor_error_kafka_message
        MonitorDraftError.KAFKA_SASL_USERNAME_REQUIRED -> R.string.monitor_error_kafka_username
        MonitorDraftError.KAFKA_SASL_PASSWORD_REQUIRED -> R.string.monitor_error_kafka_password
        MonitorDraftError.KAFKA_AWS_IDENTITY_REQUIRED -> R.string.monitor_error_kafka_identity
        MonitorDraftError.KAFKA_AWS_ACCESS_KEY_REQUIRED -> R.string.monitor_error_kafka_access_key
        MonitorDraftError.KAFKA_AWS_SECRET_REQUIRED -> R.string.monitor_error_kafka_secret_key
        MonitorDraftError.DOCKER_CONTAINER_REQUIRED -> R.string.monitor_error_docker_container
        MonitorDraftError.DOCKER_HOST_REQUIRED -> R.string.monitor_error_docker_host
        MonitorDraftError.REAL_BROWSER_INVALID -> R.string.monitor_error_real_browser
        MonitorDraftError.REAL_BROWSER_SCREENSHOT_DELAY_INVALID ->
            R.string.monitor_error_real_browser_screenshot_delay
        MonitorDraftError.RADIUS_USERNAME_REQUIRED -> R.string.monitor_error_radius_username
        MonitorDraftError.RADIUS_PASSWORD_REQUIRED -> R.string.monitor_error_radius_password
        MonitorDraftError.RADIUS_SECRET_REQUIRED -> R.string.monitor_error_radius_secret
        MonitorDraftError.RADIUS_CALLED_STATION_ID_REQUIRED -> R.string.monitor_error_radius_called_station_id
        MonitorDraftError.RADIUS_CALLING_STATION_ID_REQUIRED -> R.string.monitor_error_radius_calling_station_id
        MonitorDraftError.SFTP_USERNAME_REQUIRED -> R.string.monitor_error_sftp_username
        MonitorDraftError.SFTP_PASSWORD_REQUIRED -> R.string.monitor_error_sftp_password
        MonitorDraftError.SFTP_PRIVATE_KEY_REQUIRED -> R.string.monitor_error_sftp_private_key
    },
)

@Composable
private fun localDiscoveryError(error: LocalServiceDiscoveryError): String = stringResource(
    when (error) {
        LocalServiceDiscoveryError.START_FAILED -> R.string.monitor_discovery_start_failed
        LocalServiceDiscoveryError.RESOLVE_FAILED -> R.string.monitor_discovery_resolve_failed
        LocalServiceDiscoveryError.INVALID_ADDRESS -> R.string.monitor_discovery_invalid
    },
)
