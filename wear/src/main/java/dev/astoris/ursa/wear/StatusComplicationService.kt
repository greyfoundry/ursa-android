package dev.astoris.ursa.wear

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class StatusComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = dataFor(
        type,
        WearSnapshot(
            title = "URSA",
            monitors = listOf(
                WearMonitor(1, "Example", null, WearMonitorStatus.UP, 42, 0.999, emptyList()),
            ),
        ),
    )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val url = WearPrefs.statusUrl(this) ?: return NoDataComplicationData()
        val snapshot = StatusPoll.fetchSnapshot(url) ?: return NoDataComplicationData()
        return dataFor(request.complicationType, snapshot)
    }

    private fun dataFor(type: ComplicationType, snapshot: WearSnapshot): ComplicationData {
        val short = WearDisplay.complicationText(snapshot)
        val description = PlainComplicationText.Builder("URSA status: ${WearDisplay.fleetSummary(snapshot)}").build()
        val tapAction = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(short).build(),
                contentDescription = description,
            ).setTapAction(tapAction).build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(WearDisplay.fleetSummary(snapshot)).build(),
                contentDescription = description,
            ).setTitle(PlainComplicationText.Builder(snapshot.title).build())
                .setTapAction(tapAction)
                .build()
            else -> NoDataComplicationData()
        }
    }
}

object WearSurfaceUpdates {
    fun request(context: Context) {
        TileService.getUpdater(context).requestUpdate(StatusTileService::class.java)
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, StatusComplicationService::class.java),
        ).requestUpdateAll()
    }
}
