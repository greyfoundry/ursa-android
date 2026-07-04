package dev.astoris.ursa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = KumaGreen,
    onPrimary = KumaOnBrand,
    primaryContainer = KumaGreenHi,
    onPrimaryContainer = KumaOnBrand,
    secondary = KumaGreen,
    onSecondary = KumaOnBrand,
    tertiary = KumaBlue,
    onTertiary = White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = White,
    surfaceContainerLow = LightContainerLow,
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainerHigh,
    surfaceContainerHighest = LightContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = KumaRed,
    onError = White,
)

private val DarkColors = darkColorScheme(
    primary = KumaGreen,
    onPrimary = KumaOnBrand,
    primaryContainer = KumaGreen,
    onPrimaryContainer = KumaOnBrand,
    secondary = KumaGreen,
    onSecondary = KumaOnBrand,
    tertiary = KumaBlue,
    onTertiary = White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = KumaRed,
    onError = White,
)

// Kuma uses very rounded corners (cards ~0.75-1rem, pills 50rem).
private val UrsaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** URSA theme: Kuma's colors, following the system light/dark setting. */
@Composable
fun UrsaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = UrsaShapes,
    ) {
        // Paint the themed background and set the default content color for every
        // screen (some screens are not wrapped in a Scaffold/Surface of their own).
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
