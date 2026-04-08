package com.starsmash.kids.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starsmash.kids.settings.EffectsIntensity
import com.starsmash.kids.settings.HighScoreEntry
import com.starsmash.kids.settings.MusicTrack
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.settings.SmashCategory
import com.starsmash.kids.settings.SoundMode
import com.starsmash.kids.settings.StartingDifficulty
import com.starsmash.kids.settings.TrailLength
import kotlin.math.cos
import kotlin.math.sin

/**
 * Parent-facing setup / menu screen.
 *
 * The top of the screen is an animated, colorful header with a large
 * "Play" button, followed by grouped Settings cards in a scrollable column.
 * Cards expand visually when relevant. High scores are at the bottom.
 *
 * When the screen first appears we reload high scores so any new entry
 * saved from the PlayScreen dialog is reflected immediately.
 */
@Composable
fun HomeScreen(
    onStartPlaying: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val highScores by viewModel.highScores.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reloadHighScores() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { AnimatedHeader(onStartPlaying = onStartPlaying) }

        // ── What to smash ───────────────────────────────────────────────────
        item {
            SettingsCard(title = "What would you like to smash?") {
                SmashCategoryGrid(
                    selected = settings.smashCategories,
                    onToggle = viewModel::toggleSmashCategory
                )
            }
        }

        // ── Sound Settings ──────────────────────────────────────────────────
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
                        label = "Trail sounds",
                        info = "Soft chirps while dragging a finger across the screen. Turn off for silent trails.",
                        checked = settings.trailSoundEnabled,
                        onCheckedChange = viewModel::setTrailSoundEnabled
                    )
                    Spacer(Modifier.height(8.dp))
                    LabeledChoice(
                        label = "Sound Mode",
                        info = "Playful: arcade pops and coin dings. Calm: hushed taps and soft drops.",
                        options = listOf("Calm", "Playful"),
                        selected = if (settings.soundMode == SoundMode.CALM) "Calm" else "Playful",
                        onSelected = { choice ->
                            viewModel.setSoundMode(if (choice == "Calm") SoundMode.CALM else SoundMode.PLAYFUL)
                        }
                    )
                    LabeledChoice(
                        label = "Music",
                        info = "Pick a looping background track. Changes apply instantly, even here in the menu.",
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

        // ── Visuals ─────────────────────────────────────────────────────────
        item {
            SettingsCard(title = "Visuals") {
                LabeledChoice(
                    label = "Effects Intensity",
                    info = "How big, fast, and vibrant bursts look. Also affects background drift speed.",
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
                    info = "Animated backdrop behind play. Space drifts and rotates, Ocean has bubbles, Rainbow flows through colors.",
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
                Spacer(Modifier.height(4.dp))
                SettingsToggleRow(
                    label = "Reduced motion",
                    info = "Freezes the animated background and heavily shortens drag trails.",
                    checked = settings.reducedMotion,
                    onCheckedChange = viewModel::setReducedMotion
                )
            }
        }

        // ── Gameplay ────────────────────────────────────────────────────────
        item {
            SettingsCard(title = "Gameplay") {
                LabeledChoice(
                    label = "Starting speed",
                    info = "How busy the screen is when the game begins. Gentle waits longer and starts slower.",
                    options = listOf("Gentle", "Medium", "Fast"),
                    selected = when (settings.startingDifficulty) {
                        StartingDifficulty.GENTLE -> "Gentle"
                        StartingDifficulty.MEDIUM -> "Medium"
                        StartingDifficulty.FAST -> "Fast"
                    },
                    onSelected = { choice ->
                        viewModel.setStartingDifficulty(
                            when (choice) {
                                "Medium" -> StartingDifficulty.MEDIUM
                                "Fast" -> StartingDifficulty.FAST
                                else -> StartingDifficulty.GENTLE
                            }
                        )
                    }
                )
            }
        }

        // ── Interaction ─────────────────────────────────────────────────────
        item {
            SettingsCard(title = "Interaction") {
                SettingsToggleRow(
                    label = "Haptics",
                    info = "Gentle vibration on taps and hits.",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled
                )
                SettingsToggleRow(
                    label = "Idle demo",
                    info = "Shows a brief auto-play demo when the app has been idle for a while.",
                    checked = settings.idleDemo,
                    onCheckedChange = viewModel::setIdleDemo
                )
                SettingsToggleRow(
                    label = "Keep screen awake",
                    info = "Prevents the screen from dimming or sleeping while play is active.",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = viewModel::setKeepScreenAwake
                )
            }
        }

        // ── Adaptive Features (no info icons; plain toggles) ────────────────
        item {
            SettingsCard(title = "Adaptive") {
                PlainToggleRow(
                    label = "Adaptive Play",
                    checked = settings.adaptivePlayEnabled,
                    onCheckedChange = viewModel::setAdaptivePlayEnabled
                )
                PlainToggleRow(
                    label = "Overstimulation Guard",
                    checked = settings.overstimulationGuardEnabled,
                    onCheckedChange = viewModel::setOverstimulationGuardEnabled
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "When active, a tiny indicator appears at the top of the play screen so a parent can tell.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── High Scores ─────────────────────────────────────────────────────
        item {
            HighScoresCard(
                entries = highScores,
                onClear = viewModel::clearHighScores
            )
        }

        // ── Screen Pinning info ─────────────────────────────────────────────
        item { ScreenPinningCard() }

        // ── Support link ────────────────────────────────────────────────────
        item { SupportCard() }
    }
}

// ── Animated header with big Play button ────────────────────────────────────

@Composable
private fun AnimatedHeader(onStartPlaying: () -> Unit) {
    // Slow hue rotation for the header gradient.
    val transition = rememberInfiniteTransition(label = "header")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "headerPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(26.dp))
    ) {
        // Animated multi-stop gradient background.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stops = arrayOf(
                0.0f to rotatedHue(0.58f, phase),
                0.35f to rotatedHue(0.78f, phase),
                0.7f to rotatedHue(0.92f, phase),
                1.0f to rotatedHue(0.12f, phase)
            )
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = stops,
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )
            // A few drifting white circles for depth.
            val t = phase * 6.28f
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = w * 0.28f,
                center = Offset(w * (0.3f + 0.15f * cos(t)), h * (0.4f + 0.15f * sin(t)))
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = w * 0.32f,
                center = Offset(w * (0.75f + 0.12f * sin(t * 0.8f)), h * (0.7f + 0.1f * cos(t * 0.8f)))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "StarSmash Kids",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A touch-play world for little hands",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            // Bouncing Play button.
            val buttonScale by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounce"
            )
            Button(
                onClick = onStartPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale },
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Start Smashing! 🚀",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Build an hsv colour shifted by [phase] (0..1).
private fun rotatedHue(baseHue: Float, phase: Float): Color {
    val h = ((baseHue + phase) % 1f + 1f) % 1f
    return hsv(h, 0.55f, 1f)
}

private fun hsv(h: Float, s: Float, v: Float): Color {
    val i = (h * 6f).toInt()
    val f = h * 6f - i
    val p = v * (1f - s)
    val q = v * (1f - f * s)
    val t = v * (1f - (1f - f) * s)
    val (r, g, b) = when (i % 6) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
}

// ── Smash category chip grid ────────────────────────────────────────────────

@Composable
private fun SmashCategoryGrid(
    selected: Set<SmashCategory>,
    onToggle: (SmashCategory) -> Unit
) {
    val rows = listOf(
        listOf(
            SmashCategory.EMOJI to "⭐ Emoji",
            SmashCategory.ANIMALS to "🐶 Animals",
            SmashCategory.DINOSAURS to "🦖 Dinos"
        ),
        listOf(
            SmashCategory.TRUCKS to "🚒 Trucks",
            SmashCategory.TOYS to "🧸 Toys",
            SmashCategory.FOOD to "🍎 Food"
        ),
        listOf(
            SmashCategory.SPACE to "🚀 Space",
            SmashCategory.SHAPES to "⬢ Shapes"
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (cat, label) ->
                    SmashCategoryChip(
                        label = label,
                        selected = cat in selected,
                        onClick = { onToggle(cat) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if the row has fewer than 3 items.
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SmashCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "chipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chipFg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        contentColor = fg,
        tonalElevation = if (selected) 4.dp else 0.dp,
        modifier = modifier.heightIn(min = 56.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                softWrap = true,
                maxLines = 2
            )
        }
    }
}

// ── Reusable card component ─────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            content()
        }
    }
}

// ── Info icon with press-and-hold popup ─────────────────────────────────────

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
                .size(22.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            showPopup = event.changes.any { it.pressed }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
            tint = MaterialTheme.colorScheme.primary
        )
        if (showPopup) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 64, y = 64),
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

// ── Row components ──────────────────────────────────────────────────────────

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
                style = MaterialTheme.typography.bodyLarge,
                softWrap = true
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Plain toggle row with no info icon - used for Adaptive settings. */
@Composable
private fun PlainToggleRow(
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
            modifier = Modifier.weight(1f),
            softWrap = true
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Label + press-and-hold info icon + a row of tappable choice buttons.
 * Choices wrap text softly so long labels don't get truncated.
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
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp)
        ) {
            InfoIcon(info = info, label = label)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                ChoiceButton(
                    label = option,
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * A labelled pill button used by [LabeledChoice]. Unlike FilterChip, this
 * allows the text to wrap across two lines so long labels like
 * "Adventure" don't get cut off on narrow screens.
 */
@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "choiceBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "choiceFg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                softWrap = true,
                maxLines = 2
            )
        }
    }
}

// ── High scores card ────────────────────────────────────────────────────────

@Composable
private fun HighScoresCard(
    entries: List<HighScoreEntry>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏆 High Scores",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (entries.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear", fontSize = 13.sp)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            if (entries.isEmpty()) {
                Text(
                    text = "No scores yet. Play a round to add one!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    when (index) {
                                        0 -> Color(0xFFFFD166)
                                        1 -> Color(0xFFB0BEC5)
                                        2 -> Color(0xFFD7A26B)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = entry.formattedDate(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "⭐ ${entry.score}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ── Screen Pinning info card ────────────────────────────────────────────────

@Composable
private fun ScreenPinningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "About accidental exits",
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

// ── Support link card ───────────────────────────────────────────────────────

@Composable
private fun SupportCard() {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri("https://buymeacoffee.com/AndrewRy") },
        shape = RoundedCornerShape(18.dp),
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
