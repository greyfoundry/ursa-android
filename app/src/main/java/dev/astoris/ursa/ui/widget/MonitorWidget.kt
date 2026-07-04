package dev.astoris.ursa.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.astoris.ursa.MainActivity
import dev.astoris.ursa.core.storage.ConnectionStore
import dev.astoris.ursa.core.storage.MonitorCacheStore
import dev.astoris.ursa.data.model.MonitorStatus
import kotlinx.coroutines.flow.first

/** At-a-glance up/down counts, computed from the last-known cached monitor list. */
data class WidgetSummary(val up: Int, val down: Int, val total: Int)

/**
 * Home-screen widget showing how many monitors are up or down. Widgets run in the
 * host's process with no live socket, so it reads the encrypted last-known snapshot
 * for the active server. Tapping opens the app.
 */
class MonitorWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = loadSummary(context)
        provideContent { WidgetContent(summary) }
    }

    private suspend fun loadSummary(context: Context): WidgetSummary {
        val activeUrl = ConnectionStore(context).activeUrl.first()
        val monitors = activeUrl?.let { MonitorCacheStore(context).load(it)?.monitors }.orEmpty()
        return WidgetSummary(
            up = monitors.count { it.status == MonitorStatus.UP },
            down = monitors.count { it.status == MonitorStatus.DOWN },
            total = monitors.size,
        )
    }
}

private val Green = ColorProvider(Color(0xFF2E7D32))
private val Red = ColorProvider(Color(0xFFC62828))
private val OnBg = ColorProvider(Color(0xFF1B1B1B))
private val Bg = ColorProvider(Color(0xFFFAFAFA))

@Composable
private fun WidgetContent(summary: WidgetSummary) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Bg)
            .padding(12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Text("URSA", style = TextStyle(fontWeight = FontWeight.Bold, color = OnBg))
        Spacer(GlanceModifier.height(6.dp))
        if (summary.total == 0) {
            Text("No data yet", style = TextStyle(color = OnBg))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${summary.up} up", style = TextStyle(color = Green, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(10.dp))
                Text("${summary.down} down", style = TextStyle(color = Red, fontWeight = FontWeight.Bold))
            }
            Text("${summary.total} monitors", style = TextStyle(color = OnBg))
        }
    }
}
