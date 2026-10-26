package dev.astoris.ursa.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.ui.AppDeepLink
import dev.astoris.ursa.R
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.LockStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.data.model.MonitorStatus
import dev.astoris.ursa.data.repository.FleetRepository
import kotlinx.coroutines.flow.first
import java.util.Locale

private data class WidgetViewState(
    val config: WidgetConfig?,
    val snapshot: WidgetSnapshot?,
    val locked: Boolean = false,
)

class MonitorWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context, id)
        provideContent { WidgetContent(state) }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetStore(context).removeConfig(appWidgetId)
    }

    private suspend fun loadState(context: Context, id: GlanceId): WidgetViewState {
        LockStore.load(context)
        if (LockStore.enabled.value) return WidgetViewState(null, null, locked = true)
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val store = WidgetStore(context)
        val config = store.loadConfig(appWidgetId) ?: run {
            val activeUrl = ConnectionStore(context).activeUrl.first().orEmpty()
            WidgetConfig(WidgetSource.PRIVATE_SERVER, activeUrl)
        }
        val snapshot = when (config.source) {
            WidgetSource.PRIVATE_SERVER -> {
                val connection = ConnectionStore(context).snapshot().firstOrNull { it.url == config.sourceId }
                MonitorCacheStore(context).load(config.sourceId)?.let { cached ->
                    WidgetData.privateSnapshot(
                        title = connection?.displayName ?: context.getString(R.string.app_name),
                        snapshot = cached,
                        selectedIds = config.selectedMonitorIds,
                    )
                }
            }
            WidgetSource.PUBLIC_PAGE -> store.loadPublicSnapshot(config.sourceId)
        }
        return WidgetViewState(config, snapshot)
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetRefresher.refresh(context, appWidgetId)
        MonitorWidget().update(context, glanceId)
    }
}

private val Green = ColorProvider(Color(0xFF5CDD8B))
private val Red = ColorProvider(Color(0xFFDC3545))
private val Amber = ColorProvider(Color(0xFFFFA000))
private val Muted = ColorProvider(Color(0xFF707A74))
private val OnBg = ColorProvider(Color(0xFF1B1B1B))
private val Bg = ColorProvider(Color(0xFFFAFAFA))
private val BarTrack = ColorProvider(Color(0xFFE1E8E3))

@Composable
private fun WidgetContent(state: WidgetViewState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val widthDp = size.width.value
    val heightDp = size.height.value
    val spacious = heightDp >= 150f
    val snapshot = state.snapshot
    val denseRows = WidgetData.usesDenseRows(snapshot?.rows?.size ?: 0, heightDp)
    val showHistory = WidgetData.showsRowHistory(
        rowCount = snapshot?.rows?.size ?: 0,
        hasHistory = snapshot?.rows.orEmpty().any { it.recentStatuses.isNotEmpty() },
        spacious = spacious,
    )
    val maxRows = WidgetData.visibleRowCount(heightDp, showHistory)
    Column(
        modifier = GlanceModifier.fillMaxSize().background(Bg).padding(
            horizontal = 8.dp,
            vertical = if (denseRows) 4.dp else 8.dp,
        ),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                snapshot?.title?.takeUnless { state.locked } ?: context.getString(R.string.app_name),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = OnBg,
                    fontSize = if (denseRows) 13.sp else 14.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.width((widthDp - 72f).coerceAtLeast(60f).dp),
            )
            if (!state.locked) {
                Text(
                    context.getString(R.string.widget_refresh),
                    style = TextStyle(
                        color = Green,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (denseRows) 11.sp else 12.sp,
                    ),
                    modifier = GlanceModifier.padding(start = 8.dp)
                        .clickable(actionRunCallback<RefreshWidgetAction>()),
                )
            }
        }
        if (!denseRows) Spacer(GlanceModifier.height(2.dp))
        if (state.locked) {
            Text(
                context.getString(R.string.widget_locked),
                style = TextStyle(color = Muted, fontSize = 12.sp),
                modifier = GlanceModifier.clickable(openAppIntent(context)),
            )
        } else if (snapshot == null || snapshot.rows.isEmpty()) {
            Text(
                context.getString(R.string.widget_no_data),
                style = TextStyle(color = Muted, fontSize = 12.sp),
                modifier = GlanceModifier.clickable(openAppIntent(context)),
            )
        } else {
            val attention = if (snapshot.urgentCount > 0) {
                context.resources.getQuantityString(
                    R.plurals.widget_urgent,
                    snapshot.urgentCount,
                    snapshot.urgentCount,
                )
            } else {
                context.getString(R.string.widget_no_urgent)
            }
            if (!denseRows) {
                Text(
                    "$attention; ${freshnessLabel(context, snapshot.updatedAt)}",
                    style = TextStyle(
                        color = if (snapshot.urgentCount > 0) Red else Muted,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(2.dp))
            }
            snapshot.rows.take(maxRows).forEach { row ->
                WidgetMonitorRow(
                    context,
                    checkNotNull(state.config),
                    row,
                    widthDp,
                    showHistory && snapshot.rows.size > 1,
                    denseRows,
                )
            }
            if (spacious) {
                if (snapshot.rows.size == 1) {
                    ExpandedSingleMonitor(context, snapshot.rows.single())
                }
            }
        }
    }
}

@Composable
private fun ExpandedSingleMonitor(context: Context, row: WidgetRow) {
    row.uptimePercent?.let { uptime ->
        Spacer(GlanceModifier.height(10.dp))
        Text(
            context.getString(R.string.widget_uptime_24h),
            style = TextStyle(color = Muted, fontSize = 10.sp),
        )
        Text(
            String.format(Locale.US, "%.2f%%", uptime.coerceIn(0.0, 100.0)),
            style = TextStyle(color = statusColor(row.status), fontWeight = FontWeight.Bold, fontSize = 24.sp),
        )
    }
    if (row.recentStatuses.isNotEmpty()) {
        Spacer(GlanceModifier.height(8.dp))
        Text(
            context.getString(R.string.widget_recent_checks),
            style = TextStyle(color = Muted, fontSize = 10.sp),
        )
        Row(modifier = GlanceModifier.padding(top = 4.dp)) {
            row.recentStatuses.forEach { status ->
                Spacer(GlanceModifier.width(9.dp).height(5.dp).background(statusColor(status)))
                Spacer(GlanceModifier.width(2.dp).height(5.dp).background(BarTrack))
            }
        }
    }
}

@Composable
private fun WidgetMonitorRow(
    context: Context,
    config: WidgetConfig,
    row: WidgetRow,
    widthDp: Float,
    showHistory: Boolean,
    dense: Boolean,
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = if (dense) 0.dp else 1.dp)
            .clickable(rowIntent(context, config, row.id)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(GlanceModifier.size(if (dense) 6.dp else 8.dp).background(statusColor(row.status)))
            Spacer(GlanceModifier.width(if (dense) 5.dp else 7.dp))
            Text(
                row.name,
                style = TextStyle(
                    color = OnBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (dense) 10.sp else 12.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.width(
                    (widthDp - if (dense) 100f else 112f).coerceAtLeast(56f).dp,
                ),
            )
            val metric = buildString {
                row.pingMs?.let { append("${it}ms") }
                row.uptimePercent?.let {
                    if (isNotEmpty()) append("  ")
                    append(String.format(Locale.US, "%.1f%%", it.coerceIn(0.0, 100.0)))
                }
            }
            if (metric.isNotEmpty()) {
                Text(metric, style = TextStyle(color = Muted, fontSize = if (dense) 9.sp else 10.sp))
            }
        }
        if (showHistory && row.recentStatuses.isNotEmpty()) {
            Row(modifier = GlanceModifier.padding(start = 15.dp, top = 2.dp)) {
                row.recentStatuses.forEach { status ->
                    Spacer(GlanceModifier.width(9.dp).height(3.dp).background(statusColor(status)))
                    Spacer(GlanceModifier.width(2.dp).height(3.dp).background(BarTrack))
                }
            }
        }
    }
}

private fun statusColor(status: MonitorStatus): ColorProvider = when (status) {
    MonitorStatus.UP -> Green
    MonitorStatus.DOWN -> Red
    MonitorStatus.PENDING, MonitorStatus.MAINTENANCE -> Amber
}

private fun openAppIntent(context: Context) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    },
)

private fun rowIntent(context: Context, config: WidgetConfig, monitorId: Int) = actionStartActivity(
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = (
            if (config.source == WidgetSource.PUBLIC_PAGE) {
                "ursa://status-page/${Uri.encode(config.sourceId)}"
            } else {
                AppDeepLink.monitor(config.sourceId, monitorId)
            }
        ).toUri()
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    },
)

private fun freshnessLabel(context: Context, updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val age = (now - updatedAt).coerceAtLeast(0L)
    val label = DateUtils.getRelativeTimeSpanString(
        updatedAt.coerceAtMost(now),
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    )
    return if (age >= FleetRepository.DEFAULT_STALE_AFTER_MILLIS) {
        context.getString(R.string.widget_stale, label)
    } else {
        context.getString(R.string.widget_updated, label)
    }
}
