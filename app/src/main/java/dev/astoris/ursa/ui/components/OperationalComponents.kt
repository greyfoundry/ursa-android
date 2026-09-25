package dev.astoris.ursa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.ui.StatusPill

/** Compact, always-visible context that helps prevent actions on the wrong server. */
@Composable
fun ServerContextHeader(
    serverName: String,
    serverAddress: String,
    connectionLabel: String,
    freshnessState: FreshnessState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = serverAddress,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        FreshnessIndicator(
            label = connectionLabel,
            state = freshnessState,
        )
    }
    val surfaceModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
    if (onClick == null) {
        Surface(
            modifier = surfaceModifier,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun FreshnessIndicator(
    label: String,
    state: FreshnessState,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        FreshnessState.LIVE -> MaterialTheme.colorScheme.primary
        FreshnessState.RECENT -> MaterialTheme.colorScheme.tertiary
        FreshnessState.STALE -> MaterialTheme.colorScheme.error
        FreshnessState.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** Stable, dense status row for monitor and incident inventories. */
@Composable
fun CompactStatusRow(
    title: String,
    status: MonitorStatus,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metadata: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val content: @Composable RowScope.() -> Unit = {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            metadata?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailingContent?.invoke(this)
        if (trailingContent != null) Spacer(Modifier.width(8.dp))
        StatusPill(status)
    }
    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
    if (onClick == null) {
        Row(
            modifier = rowModifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    } else {
        Surface(onClick = onClick, modifier = rowModifier) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun TelemetrySummary(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    visualization: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            supportingText?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            visualization?.invoke(this)
        }
    }
}

/** One reusable treatment for loading, empty, offline, error, and partial data. */
@Composable
fun OperationalStatePanel(
    kind: OperationalStateKind,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    announcement: String? = "$title. $message",
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .operationalAnnouncement(announcement),
        color = when (kind) {
            OperationalStateKind.ERROR -> MaterialTheme.colorScheme.errorContainer
            OperationalStateKind.OFFLINE,
            OperationalStateKind.PARTIAL,
            OperationalStateKind.EMPTY,
            OperationalStateKind.LOADING,
            -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (kind == OperationalStateKind.LOADING) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                color = if (kind == OperationalStateKind.ERROR) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun CapabilityExplanation(
    title: String,
    message: String,
    capabilityLabel: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = capabilityLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun OperationalControlsRow(
    filterLabel: String,
    sortLabel: String,
    savedViewLabel: String,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    onSavedViewClick: () -> Unit,
    modifier: Modifier = Modifier,
    filterSelected: Boolean = false,
    savedViewSelected: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filterSelected,
            onClick = onFilterClick,
            label = { Text(filterLabel) },
        )
        FilterChip(
            selected = false,
            onClick = onSortClick,
            label = { Text(sortLabel) },
        )
        FilterChip(
            selected = savedViewSelected,
            onClick = onSavedViewClick,
            label = { Text(savedViewLabel) },
        )
    }
}

@Composable
fun TimelineGroupHeader(
    label: String,
    modifier: Modifier = Modifier,
    countLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        countLabel?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun TimelineEventRow(
    title: String,
    detail: String,
    timeLabel: String,
    eventLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(20.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(10.dp)
                        .background(accentColor, CircleShape),
                )
                Spacer(Modifier.height(8.dp))
                VerticalDivider(
                    modifier = Modifier
                        .height(28.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = eventLabel,
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
    if (onClick == null) {
        Box(rowModifier) { content() }
    } else {
        Surface(onClick = onClick, modifier = rowModifier) { content() }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConfirmedDestructiveActionSheet(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    inProgress: Boolean = false,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled && !inProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(confirmLabel)
            }
            TextButton(
                onClick = onDismiss,
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(cancelLabel)
            }
        }
    }
}
