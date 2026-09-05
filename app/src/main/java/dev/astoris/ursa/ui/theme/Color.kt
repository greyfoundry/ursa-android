package dev.astoris.ursa.ui.theme

import androidx.compose.ui.graphics.Color

// Palette taken from Uptime Kuma (src/assets/vars.scss) so URSA feels familiar to
// anyone who already uses Kuma. Only icons and imagery differ.

// Brand + status colors (shared across light and dark, as in Kuma / Bootstrap).
val KumaGreen = Color(0xFF5CDD8B)   // $primary / status Up
val KumaGreenHi = Color(0xFF7CE8A4) // $highlight
val KumaRed = Color(0xFFDC3545)     // $danger / status Down
val KumaOrange = Color(0xFFF8A306)  // $warning / status Pending
val KumaBlue = Color(0xFF1747F5)    // $maintenance
val KumaOnBrand = Color(0xFF020B05) // dark text on the green ($dark-font-color2)
val White = Color(0xFFFFFFFF)

// Quiet neutral surfaces keep status colours meaningful instead of making every
// screen compete with live operational state.
val LightBackground = Color(0xFFF5F8F6)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE7EDE9)
val LightOnSurface = Color(0xFF17211C)
val LightOnSurfaceVariant = Color(0xFF5D6B63)
val LightOutline = Color(0xFFD4DDD7)
val LightContainerLow = Color(0xFFF0F4F1)
val LightContainer = Color(0xFFEAF0EC)
val LightContainerHigh = Color(0xFFE2EAE5)
val LightErrorContainer = Color(0xFFFFDAD8)
val LightOnErrorContainer = Color(0xFF410006)

// Green-tinted charcoal avoids a flat blue-grey "old Android" dark theme while
// remaining neutral enough for long overnight monitoring sessions.
val DarkBackground = Color(0xFF09100D)
val DarkSurface = Color(0xFF111915)
val DarkSurfaceVariant = Color(0xFF1A2720)
val DarkOnSurface = Color(0xFFE5EDE8)
val DarkOnSurfaceVariant = Color(0xFF9BAAA1)
val DarkOutline = Color(0xFF2A3930)
val DarkContainerLow = Color(0xFF0E1612)
val DarkContainer = Color(0xFF141E19)
val DarkContainerHigh = Color(0xFF1A2720)
val DarkErrorContainer = Color(0xFF5E1119)
val DarkOnErrorContainer = Color(0xFFFFDAD8)
