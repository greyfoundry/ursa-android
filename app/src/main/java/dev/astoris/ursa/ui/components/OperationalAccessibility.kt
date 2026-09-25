package dev.astoris.ursa.ui.components

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * False when the platform animator duration scale is disabled. Expensive or
 * decorative motion should use this; essential state changes remain immediate.
 */
@Composable
fun systemMotionEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/**
 * Announces a meaningful operational state change without moving focus. Callers
 * should pass stable summaries, not high-frequency heartbeat or timer text.
 */
fun Modifier.operationalAnnouncement(
    message: String?,
    assertive: Boolean = false,
): Modifier = if (message.isNullOrBlank()) {
    this
} else {
    semantics(mergeDescendants = true) {
        liveRegion = if (assertive) LiveRegionMode.Assertive else LiveRegionMode.Polite
        stateDescription = message
    }
}
