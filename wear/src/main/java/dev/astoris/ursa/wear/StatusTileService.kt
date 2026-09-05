package dev.astoris.ursa.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.runBlocking

/**
 * Glanceable URSA status tile. Polls a public Kuma status page for up/down counts; tap to open the
 * status-page URL. Shows the last fetch's counts, refreshed on the freshness interval.
 */
class StatusTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val url = WearPrefs.statusUrl(this)
            ?: return Futures.immediateFuture(
                tileOf(centered(messageLayout("Tap to set up"), WearConfigActivity::class.java)),
            )

        // Fetch on a worker thread and complete the future; a tile refresh is
        // infrequent, so a blocking Ktor call here is fine. Upgrade to WorkManager +
        // cache if refresh frequency ever grows.
        val future = SettableFuture.create<TileBuilders.Tile>()
        Thread {
            val snapshot = runBlocking { StatusPoll.fetchSnapshot(url) }
            future.set(tileOf(centered(snapshotLayout(snapshot), WearMainActivity::class.java)))
        }.start()
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun snapshotLayout(snapshot: WearSnapshot?): LayoutElementBuilders.LayoutElement {
        if (snapshot == null) return messageLayout("No data")
        val headlineText = WearDisplay.complicationText(snapshot)
        val headlineColor = when {
            snapshot.down > 0 -> DOWN_RED
            snapshot.pending > 0 || snapshot.maintenance > 0 -> PENDING
            else -> UP_GREEN
        }
        return LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, headlineText)
                    .setTypography(Typography.TYPOGRAPHY_TITLE1)
                    .setColor(ColorBuilders.argb(headlineColor))
                    .build(),
            )
            .addContent(
                Text.Builder(this, "${snapshot.up}/${snapshot.monitors.size} up")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(ColorBuilders.argb(SUBTLE))
                    .build(),
            )
            .build()
    }

    private fun messageLayout(message: String): LayoutElementBuilders.LayoutElement =
        Text.Builder(this, message)
            .setTypography(Typography.TYPOGRAPHY_TITLE2)
            .setColor(ColorBuilders.argb(WHITE))
            .build()

    /** Center content and make the whole tile open its relevant watch screen. */
    private fun centered(
        content: LayoutElementBuilders.LayoutElement,
        activityClass: Class<*>,
    ): LayoutElementBuilders.LayoutElement {
        val openConfig = ModifiersBuilders.Clickable.Builder()
            .setId("configure")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(activityClass.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(openConfig).build())
            .addContent(content)
            .build()
    }

    private fun tileOf(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(REFRESH_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val REFRESH_MS = 5 * 60 * 1000L
        const val UP_GREEN = 0xFF5CDD8B.toInt()
        const val DOWN_RED = 0xFFDC3545.toInt()
        const val PENDING = 0xFFFFC247.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SUBTLE = 0xFFAAAAAA.toInt()
    }
}
