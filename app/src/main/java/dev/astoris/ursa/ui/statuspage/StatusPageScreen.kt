package dev.astoris.ursa.ui.statuspage

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.StatusPageAddressError
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.SavedStatusPage
import dev.astoris.ursa.data.model.StatusCheckView
import dev.astoris.ursa.data.model.StatusIncidentView
import dev.astoris.ursa.data.model.StatusPageView
import dev.astoris.ursa.ui.StatusPageUiState
import dev.astoris.ursa.ui.StatusPageFormResult
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.components.UrsaPressableCard
import dev.astoris.ursa.ui.theme.KumaBlue
import dev.astoris.ursa.ui.theme.KumaGreen
import dev.astoris.ursa.ui.theme.KumaOrange
import dev.astoris.ursa.ui.theme.KumaRed

private data class StatusPageDraft(
    val name: String,
    val address: String,
    val slug: String,
    val insecure: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPageScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val pages by vm.savedStatusPages.collectAsStateWithLifecycle()
    val selectedId by vm.selectedStatusPageId.collectAsStateWithLifecycle()
    val ui by vm.statusPage.collectAsStateWithLifecycle()
    val selected = pages.firstOrNull { it.id == selectedId }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<SavedStatusPage?>(null) }
    val editing = pages.firstOrNull { it.id == editingId }
    val editorOpen = adding || editing != null

    fun navigateBack() {
        when {
            editorOpen -> { adding = false; editingId = null }
            selectedId != null -> vm.closeStatusPageView()
            else -> vm.exitStatusPage()
        }
    }
    BackHandler(onBack = ::navigateBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    Text(
                        when {
                            editorOpen -> stringResource(if (editing == null) R.string.statuspage_add else R.string.statuspage_edit)
                            selected != null -> selected.name
                            else -> stringResource(R.string.statuspage_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::navigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
                actions = {
                    when {
                        selected != null -> TextButton(
                            onClick = vm::refreshStatusPage,
                            enabled = ui !is StatusPageUiState.Loading,
                        ) { Text(stringResource(R.string.statuspage_refresh)) }
                        !editorOpen -> TextButton(onClick = { adding = true }) {
                            Text(stringResource(R.string.statuspage_add))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            editorOpen -> StatusPageEditor(
                page = editing,
                modifier = Modifier.padding(padding),
                onSave = { draft, callback ->
                    vm.saveStatusPage(
                        editing?.id,
                        draft.name,
                        draft.address,
                        draft.slug,
                        draft.insecure,
                    ) { result ->
                        callback(result)
                        if (result == StatusPageFormResult.Saved) {
                            adding = false
                            editingId = null
                        }
                    }
                },
                onTest = { draft, callback ->
                    vm.testStatusPage(draft.address, draft.slug, draft.insecure, callback)
                },
            )
            selected != null -> StatusPageViewer(ui, Modifier.padding(padding), vm::refreshStatusPage)
            else -> SavedStatusPageList(
                pages = pages,
                modifier = Modifier.padding(padding),
                onOpen = vm::openStatusPage,
                onFavorite = { vm.toggleStatusPageFavorite(it.id) },
                onEdit = { editingId = it.id },
                onMove = { page, direction -> vm.moveStatusPage(page.id, direction) },
                onRemove = { pendingRemove = it },
                onAdd = { adding = true },
            )
        }
    }

    pendingRemove?.let { page ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(stringResource(R.string.statuspage_remove_title)) },
            text = { Text(stringResource(R.string.statuspage_remove_message, page.name)) },
            confirmButton = {
                TextButton(onClick = { vm.removeStatusPage(page.id); pendingRemove = null }) {
                    Text(stringResource(R.string.statuspage_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SavedStatusPageList(
    pages: List<SavedStatusPage>,
    modifier: Modifier,
    onOpen: (SavedStatusPage) -> Unit,
    onFavorite: (SavedStatusPage) -> Unit,
    onEdit: (SavedStatusPage) -> Unit,
    onMove: (SavedStatusPage, Int) -> Unit,
    onRemove: (SavedStatusPage) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.statuspage_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (pages.isEmpty()) item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.statuspage_none), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.statuspage_none_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onAdd) { Text(stringResource(R.string.statuspage_add)) }
                }
            }
        }
        itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
            SavedStatusPageCard(
                page = page,
                canMoveUp = index > 0,
                canMoveDown = index < pages.lastIndex,
                onOpen = { onOpen(page) },
                onFavorite = { onFavorite(page) },
                onEdit = { onEdit(page) },
                onMoveUp = { onMove(page, -1) },
                onMoveDown = { onMove(page, 1) },
                onRemove = { onRemove(page) },
            )
        }
    }
}

@Composable
private fun SavedStatusPageCard(
    page: SavedStatusPage,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    UrsaPressableCard(onClick = onOpen) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(page.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    page.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.statuspage_slug_value, page.slug),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val favoriteLabel = stringResource(if (page.favorite) R.string.statuspage_unfavorite else R.string.statuspage_favorite)
            IconButton(onClick = onFavorite, modifier = Modifier.semantics { contentDescription = favoriteLabel }) {
                Text(
                    if (page.favorite) "★" else "☆",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (page.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vertical), stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.statuspage_edit)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.statuspage_move_up)) },
                        onClick = { menuOpen = false; onMoveUp() },
                        enabled = canMoveUp,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.statuspage_move_down)) },
                        onClick = { menuOpen = false; onMoveDown() },
                        enabled = canMoveDown,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.statuspage_remove), color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPageEditor(
    page: SavedStatusPage?,
    modifier: Modifier,
    onSave: (StatusPageDraft, (StatusPageFormResult) -> Unit) -> Unit,
    onTest: (StatusPageDraft, (StatusPageFormResult) -> Unit) -> Unit,
) {
    var name by rememberSaveable(page?.id) { mutableStateOf(page?.name.orEmpty()) }
    var url by rememberSaveable(page?.id) { mutableStateOf(page?.url.orEmpty()) }
    var slug by rememberSaveable(page?.id) { mutableStateOf(page?.slug.orEmpty()) }
    var insecure by rememberSaveable(page?.id) { mutableStateOf(page?.insecure ?: false) }
    var busy by rememberSaveable(page?.id) { mutableStateOf(false) }
    var result by remember(page?.id) { mutableStateOf<StatusPageFormResult?>(null) }
    val valid = name.isNotBlank() && url.isNotBlank() && !busy
    val draft = StatusPageDraft(name.trim(), url.trim(), slug.trim(), insecure)
    fun complete(next: StatusPageFormResult) {
        result = next
        busy = false
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.statuspage_editor_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                singleLine = true,
                label = { Text(stringResource(R.string.statuspage_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.take(500); result = null },
                singleLine = true,
                label = { Text(stringResource(R.string.statuspage_address)) },
                placeholder = { Text(stringResource(R.string.statuspage_address_placeholder)) },
                supportingText = { Text(stringResource(R.string.statuspage_address_desc)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = slug,
                onValueChange = { slug = it.take(120); result = null },
                singleLine = true,
                label = { Text(stringResource(R.string.statuspage_slug_optional)) },
                placeholder = { Text(stringResource(R.string.statuspage_slug_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = insecure, onCheckedChange = { insecure = it })
                Column {
                    Text(stringResource(R.string.login_trust_self_signed), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.login_trust_self_signed_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        result?.let { current ->
            item {
                Text(
                    statusPageFormMessage(current),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (current) {
                        is StatusPageFormResult.Verified -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { busy = true; result = null; onTest(draft, ::complete) },
                    enabled = url.isNotBlank() && !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(if (busy) R.string.statuspage_checking else R.string.statuspage_test)) }
                Button(
                    onClick = { busy = true; result = null; onSave(draft, ::complete) },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun statusPageFormMessage(result: StatusPageFormResult): String = when (result) {
    StatusPageFormResult.Saved -> stringResource(R.string.action_save)
    is StatusPageFormResult.Verified -> stringResource(
        R.string.statuspage_verified,
        result.title.ifBlank { stringResource(R.string.statuspage_untitled) },
    )
    StatusPageFormResult.NoDiscoverablePage -> stringResource(R.string.statuspage_error_not_discoverable)
    is StatusPageFormResult.NetworkError -> stringResource(R.string.statuspage_error_network, result.message)
    is StatusPageFormResult.ValidationError -> stringResource(
        when (result.error) {
            StatusPageAddressError.EMPTY -> R.string.statuspage_error_empty
            StatusPageAddressError.INVALID_URL -> R.string.statuspage_error_invalid_url
            StatusPageAddressError.UNSUPPORTED_SCHEME -> R.string.statuspage_error_scheme
            StatusPageAddressError.CREDENTIALS_NOT_ALLOWED -> R.string.statuspage_error_credentials
            StatusPageAddressError.QUERY_OR_FRAGMENT_NOT_ALLOWED -> R.string.statuspage_error_query
            StatusPageAddressError.INVALID_SLUG -> R.string.statuspage_error_slug
            StatusPageAddressError.CONFLICTING_SLUG -> R.string.statuspage_error_conflict
        },
    )
}

@Composable
private fun StatusPageViewer(ui: StatusPageUiState, modifier: Modifier, onRetry: () -> Unit) {
    when (ui) {
        StatusPageUiState.Idle, StatusPageUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is StatusPageUiState.Error -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(ui.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.statuspage_retry)) }
        }
        is StatusPageUiState.Loaded -> StatusPageContent(ui.view, modifier)
    }
}

@Composable
private fun StatusPageContent(view: StatusPageView, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Text(view.title.ifEmpty { stringResource(R.string.statuspage_untitled) }, style = MaterialTheme.typography.headlineSmall)
                view.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(
                        R.string.statuspage_refreshed,
                        DateUtils.getRelativeTimeSpanString(
                            view.refreshedAtMillis,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE,
                        ),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (view.maintenances.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.statuspage_maintenance)) }
            items(view.maintenances, key = { "maintenance-${it.id}" }) { maintenance ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(maintenance.title, style = MaterialTheme.typography.titleSmall)
                        maintenance.description.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        val activeIncidents = view.incidents.filter { it.active }
        if (activeIncidents.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.statuspage_active_incidents)) }
            items(activeIncidents, key = { "active-incident-${it.id}" }) { incident ->
                StatusIncidentCard(incident)
            }
        }
        if (view.groups.isEmpty()) item {
            Text(
                stringResource(R.string.statuspage_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        view.groups.forEach { group ->
            item(key = "group-${group.name}") {
                SectionLabel(group.name)
            }
            items(group.monitors, key = { "${group.name}-${it.id}" }) { monitor ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(StatusUi.color(monitor.status)))
                            Text(monitor.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            monitor.uptime24h?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelMedium) }
                        }
                        PublicHeartbeatBar(monitor.recentChecks)
                    }
                }
            }
        }
        val resolvedIncidents = view.incidents.filterNot { it.active }
        item { SectionLabel(stringResource(R.string.statuspage_incident_history)) }
        if (resolvedIncidents.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.statuspage_no_incidents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(resolvedIncidents, key = { "resolved-incident-${it.id}" }) { incident ->
                StatusIncidentCard(incident)
            }
        }
        if (view.incidentHasMore) item {
            Text(
                pluralStringResource(
                    R.plurals.statuspage_incident_partial,
                    view.incidentTotal,
                    view.incidents.size,
                    view.incidentTotal,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun PublicHeartbeatBar(checks: List<StatusCheckView>) {
    if (checks.isEmpty()) {
        Text(
            stringResource(R.string.statuspage_no_recent_checks),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val recent = checks.takeLast(30)
    val upCount = recent.count { it.status == MonitorStatus.UP }
    val downCount = recent.count { it.status == MonitorStatus.DOWN }
    val otherCount = recent.size - upCount - downCount
    val summary = stringResource(
        R.string.heartbeat_summary,
        pluralStringResource(R.plurals.heartbeat_up_count, upCount, upCount),
        pluralStringResource(R.plurals.heartbeat_down_count, downCount, downCount),
        pluralStringResource(R.plurals.heartbeat_other_count, otherCount, otherCount),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        recent.forEach { check ->
            Box(
                Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(StatusUi.color(check.status)),
            )
        }
    }
}

@Composable
private fun StatusIncidentCard(incident: StatusIncidentView) {
    val accent = when (incident.style) {
        "danger" -> KumaRed
        "warning" -> KumaOrange
        "info" -> KumaBlue
        "primary" -> KumaGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = if (incident.active) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                Text(incident.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (incident.active) R.string.statuspage_incident_active else R.string.statuspage_incident_resolved),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
            incident.content.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            incident.createdDate.takeIf { it.isNotBlank() }?.let {
                Text(
                    stringResource(R.string.statuspage_incident_created, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
