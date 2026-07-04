package dev.astoris.ursa.ui.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Hosts [MonitorWidget]. Exported as required by the AppWidget framework. */
class MonitorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonitorWidget()
}
