package com.starsmash.kids.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starsmash.kids.settings.EffectsIntensity
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.settings.SoundMode

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

    // Info dialog state
    var showAdaptivePlayInfo by remember { mutableStateOf(false) }
    var showOverstimInfo by remember { mutableStateOf(false) }

    if (showAdaptivePlayInfo) {
        InfoDialog(
            title = "Adaptive Play",
            message = "Adaptive Play adjusts visuals and sounds in real time based on how your child is interacting. " +
                    "It uses simple rule-based logic to detect activity level — not machine learning or personal data collection.",
            onDismiss = { showAdaptivePlayInfo = false }
        )
    }

    if (showOverstimInfo) {
        InfoDialog(
            title = "Overstimulation Guard",
            message = "Overstimulation Guard automatically softens the experience when play becomes too intense. " +
                    "If your child plays very energetically for more than 5 seconds, effects and volume are gently reduced. " +
                    "This is rule-based logic — no data is collected.",
            onDismiss = { showOverstimInfo = false }
        )
    }

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
                    checked = settings.soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled
                )
                if (settings.soundEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sound Mode",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                    RadioGroupRow(
                        options = listOf("Calm", "Playful"),
                        selected = if (settings.soundMode == SoundMode.CALM) "Calm" else "Playful",
                        onSelected = { choice ->
                            viewModel.setSoundMode(if (choice == "Calm") SoundMode.CALM else SoundMode.PLAYFUL)
                        }
                    )
                }
            }
        }

        // ── Effects Settings ─────────────────────────────────────────────────
        item {
            SettingsCard(title = "Visual Effects") {
                Text(
                    text = "Effects Intensity",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                RadioGroupRow(
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                RadioGroupRow(
                    options = listOf("Space", "Ocean", "Rainbow", "Shapes"),
                    selected = when (settings.playTheme) {
                        PlayTheme.SPACE -> "Space"
                        PlayTheme.OCEAN -> "Ocean"
                        PlayTheme.RAINBOW -> "Rainbow"
                        PlayTheme.SHAPES -> "Shapes"
                    },
                    onSelected = { choice ->
                        viewModel.setPlayTheme(
                            when (choice) {
                                "Ocean" -> PlayTheme.OCEAN
                                "Rainbow" -> PlayTheme.RAINBOW
                                "Shapes" -> PlayTheme.SHAPES
                                else -> PlayTheme.SPACE
                            }
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
                SettingsToggleRow(
                    label = "Reduced Motion",
                    checked = settings.reducedMotion,
                    onCheckedChange = viewModel::setReducedMotion
                )
                SettingsToggleRow(
                    label = "Full Emoji Mode",
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
                    checked = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled
                )
                SettingsToggleRow(
                    label = "Idle Demo",
                    checked = settings.idleDemo,
                    onCheckedChange = viewModel::setIdleDemo
                )
                SettingsToggleRow(
                    label = "Keep Screen Awake",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = viewModel::setKeepScreenAwake
                )
            }
        }

        // ── AI / Adaptive Features ───────────────────────────────────────────
        item {
            SettingsCard(title = "Adaptive Features") {
                SettingsToggleRowWithInfo(
                    label = "Adaptive Play",
                    checked = settings.adaptivePlayEnabled,
                    onCheckedChange = viewModel::setAdaptivePlayEnabled,
                    onInfoClick = { showAdaptivePlayInfo = true }
                )
                SettingsToggleRowWithInfo(
                    label = "Overstimulation Guard",
                    checked = settings.overstimulationGuardEnabled,
                    onCheckedChange = viewModel::setOverstimulationGuardEnabled,
                    onInfoClick = { showOverstimInfo = true }
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

// ── Row components ───────────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsToggleRowWithInfo(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "About $label",
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onInfoClick() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioGroupRow(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
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

// ── Info dialog ───────────────────────────────────────────────────────────────

@Composable
private fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
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
