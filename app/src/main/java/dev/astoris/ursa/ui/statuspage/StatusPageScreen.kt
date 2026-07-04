package dev.astoris.ursa.ui.statuspage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.StatusPageView
import dev.astoris.ursa.ui.StatusUi
import dev.astoris.ursa.ui.StatusPageUiState
import dev.astoris.ursa.ui.UrsaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPageScreen(vm: UrsaViewModel, modifier: Modifier = Modifier) {
    val ui by vm.statusPage.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var insecure by remember { mutableStateOf(false) }

    BackHandler { vm.exitStatusPage() }

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statuspage_title)) },
                navigationIcon = { IconButton(onClick = { vm.exitStatusPage() }) { Text("‹", style = MaterialTheme.typography.headlineSmall) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text(stringResource(R.string.login_server_url)) }, placeholder = { Text(stringResource(R.string.login_server_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = slug, onValueChange = { slug = it }, singleLine = true,
                label = { Text(stringResource(R.string.statuspage_slug)) }, placeholder = { Text(stringResource(R.string.statuspage_slug_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = insecure, onCheckedChange = { insecure = it })
                Text(stringResource(R.string.login_trust_self_signed), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { vm.loadStatusPage(url, slug, insecure) },
                enabled = url.isNotBlank() && slug.isNotBlank() && ui !is StatusPageUiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.statuspage_load)) }

            when (val s = ui) {
                StatusPageUiState.Loading -> CircularProgressIndicator()
                is StatusPageUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is StatusPageUiState.Loaded -> StatusPageContent(s.view)
                StatusPageUiState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun StatusPageContent(view: StatusPageView) {
    Text(view.title.ifEmpty { stringResource(R.string.statuspage_untitled) }, style = MaterialTheme.typography.headlineSmall)
    view.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

    if (view.groups.isEmpty()) {
        Text(stringResource(R.string.statuspage_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    view.groups.forEach { group ->
        Text(group.name, style = MaterialTheme.typography.titleSmall)
        group.monitors.forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(StatusUi.color(m.status)))
                Text(m.name, modifier = Modifier.weight(1f))
                m.uptime24h?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
