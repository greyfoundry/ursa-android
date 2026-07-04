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

// Light theme (Kuma light: white surfaces, Bootstrap dark text).
val LightBackground = Color(0xFFF7F8FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDEFF2)
val LightOnSurface = Color(0xFF212529)
val LightOnSurfaceVariant = Color(0xFF6C757D)
val LightOutline = Color(0xFFDDE1E6)
val LightContainerLow = Color(0xFFF2F4F6)
val LightContainer = Color(0xFFEDEFF2)
val LightContainerHigh = Color(0xFFE6E9ED)

// Dark theme (Kuma dark vars: $dark-bg, $dark-header-bg, $dark-border-color, ...).
val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkSurfaceVariant = Color(0xFF1D2634)
val DarkOnSurface = Color(0xFFB1B8C0)
val DarkOnSurfaceVariant = Color(0xFF8B95A1)
val DarkOutline = Color(0xFF1D2634)
val DarkContainerLow = Color(0xFF12161D)
val DarkContainer = Color(0xFF161B22)
val DarkContainerHigh = Color(0xFF1B2029)
