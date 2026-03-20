package com.starsmash.kids.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material 3 color scheme for the parent-facing screens (Home screen, dialogs).
 *
 * The child-facing Play screen uses its own draw-scope colors and does NOT
 * use MaterialTheme directly – it uses the selected play theme palette from Color.kt.
 */
private val LightColorScheme = lightColorScheme(
    primary = StarYellow,
    onPrimary = NeutralDark,
    primaryContainer = StarYellowContainer,
    onPrimaryContainer = NeutralDark,

    secondary = SoftCoral,
    onSecondary = NeutralDark,
    secondaryContainer = SoftCoralContainer,
    onSecondaryContainer = NeutralDark,

    tertiary = SkyBlue,
    onTertiary = NeutralDark,
    tertiaryContainer = SkyBlueContainer,
    onTertiaryContainer = NeutralDark,

    background = WarmBackground,
    onBackground = NeutralDark,

    surface = WarmSurface,
    onSurface = NeutralDark,

    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = NeutralMedium,

    outline = NeutralMedium
)

private val DarkColorScheme = darkColorScheme(
    primary = StarYellow,
    onPrimary = NeutralDark,
    primaryContainer = StarYellowDark,
    onPrimaryContainer = NeutralLight,

    secondary = SoftCoral,
    onSecondary = NeutralDark,
    secondaryContainer = SoftCoralDark,
    onSecondaryContainer = NeutralLight,

    tertiary = SkyBlue,
    onTertiary = NeutralDark,
    tertiaryContainer = SkyBlueDark,
    onTertiaryContainer = NeutralLight,

    background = NeutralDark,
    onBackground = NeutralLight,

    surface = NeutralDark,
    onSurface = NeutralLight
)

/**
 * The root Material 3 theme for StarSmash Kids.
 *
 * Applied to the entire composition tree via MainActivity.
 * Play screen overrides colors locally using theme palette objects.
 */
@Composable
fun StarSmashKidsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StarSmashTypography,
        content = content
    )
}
