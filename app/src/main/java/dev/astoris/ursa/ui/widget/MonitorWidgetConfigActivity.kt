package dev.astoris.ursa.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.astoris.ursa.R
import dev.astoris.ursa.core.network.StatusPageClient
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.core.storage.StatusPageStore
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.model.SavedStatusPage
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.data.model.StatusPageView
import dev.astoris.ursa.ui.theme.UrsaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class WidgetChoice(
    val id: Int,
    val name: String,
    val status: MonitorStatus,
)

private data class WidgetConfigUiState(
    val loading: Boolean = true,
    val source: WidgetSource = WidgetSource.PRIVATE_SERVER,
    val sourceId: String = "",
    val selectedIds: Set<Int> = emptySet(),
    val connections: List<ServerConnection> = emptyList(),
    val pages: List<SavedStatusPage> = emptyList(),
    val choices: List<WidgetChoice> = emptyList(),
    val error: String? = null,
)

class MonitorWidgetConfigActivity : FragmentActivity() {
    private val state = MutableStateFlow(WidgetConfigUiState())
    private lateinit var connectionStore: ConnectionStore
    private lateinit var pageStore: StatusPageStore
    private lateinit var widgetStore: WidgetStore
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var publicView: StatusPageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, MonitorWidgetReceiver::class.java)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            manager.getAppWidgetInfo(widgetId)?.provider != provider
        ) {
            finish()
            return
        }

        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        connectionStore = ConnectionStore(this)
        pageStore = StatusPageStore(this)
        widgetStore = WidgetStore(this)
        enableEdgeToEdge()
        setContent {
            UrsaTheme {
                WidgetConfigScreen(
                    state = state.collectAsStateWithLifecycle().value,
                    onSourceChange = ::selectSource,
                    onSourceIdChange = ::selectSourceId,
                    onToggle = ::toggleMonitor,
                    onCancel = ::finish,
                    onSave = ::save,
                )
            }
        }
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val connections = connectionStore.snapshot()
        val pages = pageStore.pages.first()
        val existing = widgetStore.loadConfig(widgetId)
        val source = existing?.source ?: if (connections.isNotEmpty()) {
            WidgetSource.PRIVATE_SERVER
        } else {
            WidgetSource.PUBLIC_PAGE
        }
        val sourceId = existing?.sourceId ?: when (source) {
            WidgetSource.PRIVATE_SERVER -> connectionStore.activeUrl.first() ?: connections.firstOrNull()?.url.orEmpty()
            WidgetSource.PUBLIC_PAGE -> pages.firstOrNull()?.id.orEmpty()
        }
        state.value = WidgetConfigUiState(
            loading = true,
            source = source,
            sourceId = sourceId,
            selectedIds = existing?.selectedMonitorIds.orEmpty(),
            connections = connections,
            pages = pages,
        )
        loadChoices(source, sourceId)
    }

    private fun selectSource(source: WidgetSource) {
        val current = state.value
        val id = when (source) {
            WidgetSource.PRIVATE_SERVER -> current.connections.firstOrNull()?.url.orEmpty()
            WidgetSource.PUBLIC_PAGE -> current.pages.firstOrNull()?.id.orEmpty()
        }
        state.update { it.copy(source = source, sourceId = id, selectedIds = emptySet()) }
        lifecycleScope.launch { loadChoices(source, id) }
    }

    private fun selectSourceId(id: String) {
        state.update { it.copy(sourceId = id, selectedIds = emptySet()) }
        lifecycleScope.launch { loadChoices(state.value.source, id) }
    }

    private suspend fun loadChoices(source: WidgetSource, sourceId: String) {
        state.update { it.copy(loading = true, error = null, choices = emptyList()) }
        publicView = null
        when (source) {
            WidgetSource.PRIVATE_SERVER -> {
                val monitors = MonitorCacheStore(this).load(sourceId)?.monitors.orEmpty()
                state.update {
                    it.copy(
                        loading = false,
                        choices = monitors.map { monitor -> WidgetChoice(monitor.id, monitor.name, monitor.status) },
                        error = if (monitors.isEmpty()) getString(R.string.widget_config_private_empty) else null,
                    )
                }
            }
            WidgetSource.PUBLIC_PAGE -> loadPublicChoices(sourceId)
        }
    }

    private suspend fun loadPublicChoices(pageId: String) {
        val page = state.value.pages.firstOrNull { it.id == pageId }
        if (page == null) {
            state.update { it.copy(loading = false, error = getString(R.string.widget_config_public_empty)) }
            return
        }
        val headers = state.value.connections.firstOrNull { normalize(it.url) == normalize(page.url) }
            ?.headers.orEmpty()
        val client = StatusPageClient()
        try {
            val view = client.fetch(page.url, page.slug, page.insecure, headers)
            if (state.value.source != WidgetSource.PUBLIC_PAGE || state.value.sourceId != pageId) return
            publicView = view
            state.update {
                it.copy(
                    loading = false,
                    choices = view.groups.flatMap { group -> group.monitors }
                        .map { monitor -> WidgetChoice(monitor.id, monitor.name, monitor.status) },
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (state.value.source != WidgetSource.PUBLIC_PAGE || state.value.sourceId != pageId) return
            val cached = widgetStore.loadPublicSnapshot(page.id)
            state.update {
                it.copy(
                    loading = false,
                    choices = cached?.rows.orEmpty().map { row -> WidgetChoice(row.id, row.name, row.status) },
                    error = if (cached == null) getString(R.string.widget_config_load_failed) else null,
                )
            }
        } finally {
            client.close()
        }
    }

    private fun toggleMonitor(id: Int, checked: Boolean) {
        state.update { current ->
            val selected = if (checked) {
                if (current.selectedIds.size >= WidgetData.MAX_ROWS) current.selectedIds else current.selectedIds + id
            } else {
                current.selectedIds - id
            }
            current.copy(selectedIds = selected)
        }
    }

    private fun save() {
        val current = state.value
        if (current.sourceId.isBlank() || current.selectedIds.isEmpty()) return
        lifecycleScope.launch {
            val config = WidgetConfig(current.source, current.sourceId, current.selectedIds)
            widgetStore.saveConfig(widgetId, config)
            if (current.source == WidgetSource.PUBLIC_PAGE) {
                publicView?.let { view ->
                    widgetStore.savePublicSnapshot(
                        current.sourceId,
                        WidgetData.publicSnapshot(view, current.selectedIds),
                    )
                }
            }
            val glanceId = GlanceAppWidgetManager(this@MonitorWidgetConfigActivity).getGlanceIdBy(widgetId)
            MonitorWidget().update(this@MonitorWidgetConfigActivity, glanceId)
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            )
            finish()
        }
    }

    private fun normalize(raw: String) = raw.trim().removeSuffix("/")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WidgetConfigScreen(
    state: WidgetConfigUiState,
    onSourceChange: (WidgetSource) -> Unit,
    onSourceIdChange: (String) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.widget_config_source), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onSourceChange(WidgetSource.PRIVATE_SERVER) }) {
                    RadioButton(
                        selected = state.source == WidgetSource.PRIVATE_SERVER,
                        onClick = null,
                    )
                    Text(stringResource(R.string.widget_config_private))
                }
                OutlinedButton(onClick = { onSourceChange(WidgetSource.PUBLIC_PAGE) }) {
                    RadioButton(
                        selected = state.source == WidgetSource.PUBLIC_PAGE,
                        onClick = null,
                    )
                    Text(stringResource(R.string.widget_config_public))
                }
            }
            Text(
                stringResource(R.string.widget_config_refresh_guidance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val sources = if (state.source == WidgetSource.PRIVATE_SERVER) state.connections else state.pages
            if (sources.isEmpty()) {
                Text(
                    stringResource(
                        if (state.source == WidgetSource.PRIVATE_SERVER) {
                            R.string.widget_config_no_servers
                        } else {
                            R.string.widget_config_no_pages
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(stringResource(R.string.widget_config_choose_source), style = MaterialTheme.typography.titleSmall)
                if (state.source == WidgetSource.PRIVATE_SERVER) {
                    state.connections.forEach { connection ->
                        SourceChoice(
                            selected = state.sourceId == connection.url,
                            label = connection.displayName,
                            onClick = { onSourceIdChange(connection.url) },
                        )
                    }
                } else {
                    state.pages.forEach { page ->
                        SourceChoice(
                            selected = state.sourceId == page.id,
                            label = page.name,
                            onClick = { onSourceIdChange(page.id) },
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.widget_config_rows, state.selectedIds.size, WidgetData.MAX_ROWS),
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.widget_config_loading), modifier = Modifier.padding(start = 12.dp))
                }
            } else {
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.choices.forEach { choice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = choice.id in state.selectedIds,
                            onCheckedChange = { onToggle(choice.id, it) },
                            enabled = choice.id in state.selectedIds || state.selectedIds.size < WidgetData.MAX_ROWS,
                        )
                        Text(choice.name)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = onSave,
                    enabled = !state.loading && state.sourceId.isNotBlank() && state.selectedIds.isNotEmpty(),
                ) { Text(stringResource(R.string.widget_config_add)) }
            }
        }
    }
}

@Composable
private fun SourceChoice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
