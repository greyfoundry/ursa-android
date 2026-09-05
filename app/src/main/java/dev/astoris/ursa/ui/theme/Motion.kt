package dev.astoris.ursa.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Small, shared motion vocabulary. Compose applies the system duration scale. */
object UrsaMotion {
    const val Fast = 120
    const val Standard = 180
    const val Emphasis = 240

    fun <T> fast() = tween<T>(durationMillis = Fast)
    fun <T> standard() = tween<T>(durationMillis = Standard)

    fun <T> settle() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
