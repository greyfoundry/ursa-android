package dev.astoris.ursa.wear

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Glanceable URSA status tile (FOSS: androidx tiles/protolayout only, no Google Play
 * Services). W1 renders a placeholder; a later phase polls a public Kuma status page
 * (over the phone-routed network) and shows live up/down counts.
 */
class StatusTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val root = Text.Builder(this, "URSA")
            .setTypography(Typography.TYPOGRAPHY_TITLE2)
            .build()
        val timeline = TimelineBuilders.Timeline.fromLayoutElement(root)
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(timeline)
                .build(),
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
        )

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
