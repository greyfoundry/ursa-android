package dev.astoris.ursa.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.R
import dev.astoris.ursa.core.access.AccessDecision
import dev.astoris.ursa.core.access.AccessPolicy
import dev.astoris.ursa.data.model.AccessCapability
import dev.astoris.ursa.data.model.AccessProfile
import dev.astoris.ursa.data.model.ServerConnection

fun ServerConnection?.allows(vararg capabilities: AccessCapability): Boolean =
    this != null && AccessPolicy.evaluate(this, *capabilities) is AccessDecision.Allowed

@Composable
fun AccessProfileNotice(connection: ServerConnection?, modifier: Modifier = Modifier) {
    val profile = connection?.accessProfile ?: return
    if (profile == AccessProfile.MANAGE) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            stringResource(R.string.access_profile_active_notice, stringResource(profile.labelRes())),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@StringRes
fun AccessProfile.labelRes(): Int = when (this) {
    AccessProfile.VIEW_ONLY -> R.string.access_profile_view_only
    AccessProfile.MANAGE -> R.string.access_profile_manage
    AccessProfile.CUSTOM -> R.string.access_profile_custom
}

@StringRes
fun AccessCapability.labelRes(): Int = when (this) {
    AccessCapability.MONITOR_STATE -> R.string.access_capability_monitor_state
    AccessCapability.MONITOR_CREATE -> R.string.access_capability_monitor_create
    AccessCapability.MONITOR_EDIT -> R.string.access_capability_monitor_edit
    AccessCapability.MONITOR_DELETE -> R.string.access_capability_monitor_delete
    AccessCapability.BULK_WRITE -> R.string.access_capability_bulk_write
    AccessCapability.MAINTENANCE_WRITE -> R.string.access_capability_maintenance
    AccessCapability.PUSH_SETUP -> R.string.access_capability_push_setup
    AccessCapability.STATUS_INCIDENT_WRITE -> R.string.access_capability_status_incidents
}
