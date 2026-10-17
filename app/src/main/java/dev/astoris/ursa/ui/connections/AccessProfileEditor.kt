package dev.astoris.ursa.ui.connections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.ui.labelRes

@Composable
internal fun AccessProfileEditor(
    profile: AccessProfile,
    customCapabilities: Set<AccessCapability>,
    onProfileChange: (AccessProfile) -> Unit,
    onCapabilityChange: (AccessCapability, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.access_profile_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.access_profile_scope_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AccessProfile.entries.forEach { option ->
                FilterChip(
                    selected = profile == option,
                    onClick = { onProfileChange(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
        Text(
            stringResource(profile.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profile == AccessProfile.CUSTOM) {
            CAPABILITY_GROUPS.forEach { group ->
                Text(
                    stringResource(group.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                group.capabilities.forEach { item ->
                    val checked = item.capability in customCapabilities
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onCapabilityChange(item.capability, it) },
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(
                            stringResource(item.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun AccessProfile.descriptionRes(): Int = when (this) {
    AccessProfile.VIEW_ONLY -> R.string.access_profile_view_only_desc
    AccessProfile.MANAGE -> R.string.access_profile_manage_desc
    AccessProfile.CUSTOM -> R.string.access_profile_custom_desc
}

private data class CapabilityItem(
    val capability: AccessCapability,
    @StringRes val labelRes: Int,
)

private data class CapabilityGroup(
    @StringRes val labelRes: Int,
    val capabilities: List<CapabilityItem>,
)

private val CAPABILITY_GROUPS = listOf(
    CapabilityGroup(
        R.string.access_group_monitors,
        listOf(
            CapabilityItem(AccessCapability.MONITOR_STATE, R.string.access_capability_monitor_state),
            CapabilityItem(AccessCapability.MONITOR_CREATE, R.string.access_capability_monitor_create),
            CapabilityItem(AccessCapability.MONITOR_EDIT, R.string.access_capability_monitor_edit),
            CapabilityItem(AccessCapability.MONITOR_DELETE, R.string.access_capability_monitor_delete),
            CapabilityItem(AccessCapability.BULK_WRITE, R.string.access_capability_bulk_write),
        ),
    ),
    CapabilityGroup(
        R.string.access_group_operations,
        listOf(
            CapabilityItem(AccessCapability.MAINTENANCE_WRITE, R.string.access_capability_maintenance),
            CapabilityItem(AccessCapability.PUSH_SETUP, R.string.access_capability_push_setup),
        ),
    ),
    CapabilityGroup(
        R.string.access_group_status_pages,
        listOf(
            CapabilityItem(AccessCapability.STATUS_INCIDENT_WRITE, R.string.access_capability_status_incidents),
        ),
    ),
)
