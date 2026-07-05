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
 * Glanceable URSA status tile (FOSS: androidx tiles/protolayout only, no Google Play
 * Services). Polls a public Kuma status page for up/down counts; tap to configure the
 * status-page URL. Shows the last fetch's counts, refreshed on the freshness interval.
 */
class StatusTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val url = WearPrefs.statusUrl(this)
            ?: return Futures.immediateFuture(tileOf(centered(messageLayout("Tap to set up"))))

        // ponytail: fetch on a worker thread and complete the future; a tile refresh is
        // infrequent, so a blocking Ktor call here is fine. Upgrade to WorkManager +
        // cache if refresh frequency ever grows.
        val future = SettableFuture.create<TileBuilders.Tile>()
        Thread {
            val counts = runBlocking { StatusPoll.fetch(url) }
            future.set(tileOf(centered(countsLayout(counts))))
        }.start()
        return future
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private fun countsLayout(counts: StatusPoll.Counts?): LayoutElementBuilders.LayoutElement {
        if (counts == null) return messageLayout("No data")
        val headlineText = if (counts.down == 0) "All clear" else "${counts.down} down"
        val headlineColor = if (counts.down == 0) UP_GREEN else DOWN_RED
        return LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, headlineText)
                    .setTypography(Typography.TYPOGRAPHY_TITLE1)
                    .setColor(ColorBuilders.argb(headlineColor))
                    .build(),
            )
            .addContent(
                Text.Builder(this, "${counts.up}/${counts.total} up")
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

    /** Center content and make the whole tile tap-to-configure. */
    private fun centered(content: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement {
        val openConfig = ModifiersBuilders.Clickable.Builder()
            .setId("configure")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WearConfigActivity::class.java.name)
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
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SUBTLE = 0xFFAAAAAA.toInt()
    }
}
