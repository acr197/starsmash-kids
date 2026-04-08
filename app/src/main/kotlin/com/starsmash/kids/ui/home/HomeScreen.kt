package com.starsmash.kids.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starsmash.kids.settings.EffectsIntensity
import com.starsmash.kids.settings.MusicTrack
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.settings.SoundMode
import com.starsmash.kids.settings.TrailLength

/**
 * Parent-facing setup screen.
 *
 * Laid out as a [LazyColumn] so the settings list is fully scrollable even on
 * small devices. Settings are grouped into [Card] sections.
 *
 * This is the ONLY screen that uses Material 3 components and theming. The play
 * screen is intentionally chrome-free.
 */
@Composable
fun HomeScreen(
    onStartPlaying: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⭐ StarSmash Kids",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A touch-play world for little hands",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onStartPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Start Smashing! 🚀",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Sound Settings ───────────────────────────────────────────────────
        item {
            SettingsCard(title = "Sound") {
                SettingsToggleRow(
                    label = "Sound",
                    info = "Master switch for all sound effects and music.",
                    checked = settings.soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled
                )
                if (settings.soundEnabled) {
                    SettingsToggleRow(
                        label = "Trail Sounds",
                        info = "Little chirps and blips while dragging a finger across the screen. Turn off for silent trails.",
                        checked = settings.trailSoundEnabled,
                        onCheckedChange = viewModel::setTrailSoundEnabled
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledChoice(
                        label = "Sound Mode",
                        info = "Playful: bouncy pops and arcade coin dings. Calm: soft wood taps and gentle drops.",
                        options = listOf("Calm", "Playful"),
                        selected = if (settings.soundMode == SoundMode.CALM) "Calm" else "Playful",
                        onSelected = { choice ->
                            viewModel.setSoundMode(if (choice == "Calm") SoundMode.CALM else SoundMode.PLAYFUL)
                        }
                    )
                    LabeledChoice(
                        label = "Music",
                        info = "Choose a looping background music track, or turn it off.",
                        options = listOf("Off", "Arcade", "Adventure", "Bubbly"),
                        selected = when (settings.musicTrack) {
                            MusicTrack.NONE -> "Off"
                            MusicTrack.ARCADE -> "Arcade"
                            MusicTrack.ADVENTURE -> "Adventure"
                            MusicTrack.BUBBLE_POP -> "Bubbly"
                        },
                        onSelected = { choice ->
                            viewModel.setMusicTrack(
                                when (choice) {
                                    "Arcade" -> MusicTrack.ARCADE
                                    "Adventure" -> MusicTrack.ADVENTURE
                                    "Bubbly" -> MusicTrack.BUBBLE_POP
                                    else -> MusicTrack.NONE
                                }
                            )
                        }
                    )
                }
            }
        }

        // ── Effects Settings ─────────────────────────────────────────────────
        item {
            SettingsCard(title = "Visual Effects") {
                LabeledChoice(
                    label = "Effects Intensity",
                    info = "How big, fast, and vibrant taps and bursts look. Also changes how quickly backgrounds drift.",
                    options = listOf("Low", "Medium", "High"),
                    selected = when (settings.effectsIntensity) {
                        EffectsIntensity.LOW -> "Low"
                        EffectsIntensity.MEDIUM -> "Medium"
                        EffectsIntensity.HIGH -> "High"
                    },
                    onSelected = { choice ->
                        viewModel.setEffectsIntensity(
                            when (choice) {
                                "Low" -> EffectsIntensity.LOW
                                "High" -> EffectsIntensity.HIGH
                                else -> EffectsIntensity.MEDIUM
                            }
                        )
                    }
                )
                LabeledChoice(
                    label = "Trail Length",
                    info = "How long drag trails stay on screen before fading away.",
                    options = listOf("Short", "Medium", "Long"),
                    selected = when (settings.trailLength) {
                        TrailLength.SHORT -> "Short"
                        TrailLength.MEDIUM -> "Medium"
                        TrailLength.LONG -> "Long"
                    },
                    onSelected = { choice ->
                        viewModel.setTrailLength(
                            when (choice) {
                                "Short" -> TrailLength.SHORT
                                "Long" -> TrailLength.LONG
                                else -> TrailLength.MEDIUM
                            }
                        )
                    }
                )
                LabeledChoice(
                    label = "Theme",
                    info = "The animated background behind play. Space has drifting nebulas, Ocean has rising bubbles, Rainbow flows through colors.",
                    options = listOf("Space", "Ocean", "Rainbow"),
                    selected = when (settings.playTheme) {
                        PlayTheme.SPACE -> "Space"
                        PlayTheme.OCEAN -> "Ocean"
                        PlayTheme.RAINBOW -> "Rainbow"
                    },
                    onSelected = { choice ->
                        viewModel.setPlayTheme(
                            when (choice) {
                                "Ocean" -> PlayTheme.OCEAN
                                "Rainbow" -> PlayTheme.RAINBOW
                                else -> PlayTheme.SPACE
                            }
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                SettingsToggleRow(
                    label = "Reduced Motion",
                    info = "Freezes the animated background and cuts particle count for kids sensitive to motion.",
                    checked = settings.reducedMotion,
                    onCheckedChange = viewModel::setReducedMotion
                )
                SettingsToggleRow(
                    label = "Full Emoji Mode",
                    info = "Spawns only emoji targets (animals, trucks, dinosaurs). Turn off to mix in simple shapes too.",
                    checked = settings.fullEmojiMode,
                    onCheckedChange = viewModel::setFullEmojiMode
                )
            }
        }

        // ── Interaction Settings ─────────────────────────────────────────────
        item {
            SettingsCard(title = "Interaction") {
                SettingsToggleRow(
                    label = "Haptics (Vibration)",
                    info = "Gentle vibration on taps and hits.",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled
                )
                SettingsToggleRow(
                    label = "Idle Demo",
                    info = "Shows a brief auto-play demo when the app has been idle for a while.",
                    checked = settings.idleDemo,
                    onCheckedChange = viewModel::setIdleDemo
                )
                SettingsToggleRow(
                    label = "Keep Screen Awake",
                    info = "Prevents the screen from dimming or sleeping while play is active.",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = viewModel::setKeepScreenAwake
                )
            }
        }

        // ── AI / Adaptive Features ───────────────────────────────────────────
        item {
            SettingsCard(title = "Adaptive Features") {
                SettingsToggleRow(
                    label = "Adaptive Play",
                    info = "Quietly adjusts visuals and sounds based on how energetic the child's touches are. Rule-based, no data collected.",
                    checked = settings.adaptivePlayEnabled,
                    onCheckedChange = viewModel::setAdaptivePlayEnabled
                )
                SettingsToggleRow(
                    label = "Overstimulation Guard",
                    info = "If play gets very intense, gently softens effects and volume. Rule-based, no data collected.",
                    checked = settings.overstimulationGuardEnabled,
                    onCheckedChange = viewModel::setOverstimulationGuardEnabled
                )
            }
        }

        // ── Screen Pinning Info Card ─────────────────────────────────────────
        item {
            ScreenPinningCard()
        }

        // ── Support Link ─────────────────────────────────────────────────────
        item {
            SupportCard()
        }
    }
}

// ── Reusable card component ──────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            content()
        }
    }
}

// ── Info icon with press-and-hold popup ─────────────────────────────────────

/**
 * Small circled "i" rendered to the LEFT of a setting's label. Press and hold
 * to show an explanatory popup offset down-and-right so the finger doesn't
 * cover it. Release to dismiss.
 */
@Composable
private fun InfoIcon(
    info: String,
    label: String
) {
    var showPopup by remember { mutableStateOf(false) }
    Box {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "About $label - press and hold",
            modifier = Modifier
                .size(20.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val anyPressed = event.changes.any { it.pressed }
                            showPopup = anyPressed
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
            tint = MaterialTheme.colorScheme.primary
        )
        if (showPopup) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 56, y = 56),
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = 260.dp)
                ) {
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

// ── Row components ───────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    label: String,
    info: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            InfoIcon(info = info, label = label)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Label + press-and-hold info icon + a horizontal row of FilterChip options.
 * Used for Effects Intensity, Trail Length, Theme, Sound Mode, Music.
 */
@Composable
private fun LabeledChoice(
    label: String,
    info: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 4.dp)
        ) {
            InfoIcon(info = info, label = label)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ── Screen Pinning info card ──────────────────────────────────────────────────

@Composable
private fun ScreenPinningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "About Accidental Exits",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "StarSmash Kids runs in fullscreen immersive mode, which hides the navigation bar. " +
                        "However, Android does not allow apps to fully prevent children from exiting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "For a fully locked session, use Android's built-in Screen Pinning:\n" +
                        "Settings → Security → Screen Pinning (or App Pinning).\n" +
                        "A parent PIN is required to unpin.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// ── Support link card ─────────────────────────────────────────────────────────

@Composable
private fun SupportCard() {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri("https://buymeacoffee.com/AndrewRy") },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "☕  Support this project",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
