package com.starsmash.kids.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary palette ──────────────────────────────────────────────────────────
// Warm, vibrant but not harsh – designed to be inviting for parents and
// enthusiastically colorful for children without being visually aggressive.

/** Star yellow – main accent, used on primary buttons and highlights. */
val StarYellow = Color(0xFFFFD700)
val StarYellowDark = Color(0xFFC8A900)
val StarYellowContainer = Color(0xFFFFF3B0)

/** Soft coral – secondary accent, warm and energetic. */
val SoftCoral = Color(0xFFFF6B6B)
val SoftCoralDark = Color(0xFFCC4A4A)
val SoftCoralContainer = Color(0xFFFFD6D6)

/** Sky blue – tertiary accent, calming counterpoint. */
val SkyBlue = Color(0xFF4FC3F7)
val SkyBlueDark = Color(0xFF0288D1)
val SkyBlueContainer = Color(0xFFB3E5FC)

/** Warm background – off-white with a hint of warmth, easier on young eyes. */
val WarmBackground = Color(0xFFFFFBF0)
val WarmSurface = Color(0xFFFFF8E1)
val WarmSurfaceVariant = Color(0xFFFFECB3)

// ── Neutral tones ─────────────────────────────────────────────────────────────
val NeutralDark = Color(0xFF1A1A2E)
val NeutralMedium = Color(0xFF4A4A6A)
val NeutralLight = Color(0xFFF5F5FA)

// ── Play-screen theme palettes ────────────────────────────────────────────────
// These are used by PlayScreen to apply different visual themes.

object SpaceTheme {
    val background = Color(0xFF0A0A1E)
    val primary = Color(0xFF9C27B0)
    val secondary = Color(0xFF7C4DFF)
    val accent = Color(0xFFFFD700)
    val trail = Color(0xFF3F51B5)
}

object OceanTheme {
    val background = Color(0xFF001B3A)
    val primary = Color(0xFF0288D1)
    val secondary = Color(0xFF00BCD4)
    val accent = Color(0xFF80CBC4)
    val trail = Color(0xFF26C6DA)
}

object RainbowTheme {
    val background = Color(0xFFFFFFFF)
    val colors = listOf(
        Color(0xFFFF0000), // red
        Color(0xFFFF7700), // orange
        Color(0xFFFFFF00), // yellow
        Color(0xFF00CC00), // green
        Color(0xFF0000FF), // blue
        Color(0xFF8B00FF)  // violet
    )
}

object ShapesTheme {
    val background = Color(0xFFF8F4FF)
    val colors = listOf(
        Color(0xFFFF8A80), // pastel red
        Color(0xFFFFD180), // pastel orange
        Color(0xFFCCFF90), // pastel green
        Color(0xFF80D8FF), // pastel blue
        Color(0xFFEA80FC), // pastel purple
        Color(0xFFFF80AB)  // pastel pink
    )
}
