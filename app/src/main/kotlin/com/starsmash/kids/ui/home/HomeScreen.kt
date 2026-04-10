package com.starsmash.kids.ui.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import kotlin.math.roundToInt
import kotlin.math.sin

// ── Private dark palette for the settings screen ─────────────────────────────
// Inspired by Alto's Odyssey / Monument Valley: deep navy backdrop, cards that
// float above it, a single muted violet accent replacing the harsh yellow.
private val ScreenBg   = Color(0xFF0D0D1A)   // very dark navy
private val CardBg     = Color(0xFF141428)   // card surface, slightly lighter
private val CardRaised = Color(0xFF1B1B33)   // elevated element inside a card
private val ChipBg     = Color(0xFF1F1F3A)   // unselected option chip
private val Accent     = Color(0xFF9D8DF1)   // soft indigo-violet, main accent
private val AccentDim  = Color(0x339D8DF1)   // 20 % opacity violet (selected tint)
private val TextHigh   = Color(0xFFECECFF)   // primary text on dark
private val TextLow    = Color(0xFF8888AA)   // muted / secondary text
private val Divider    = Color(0xFF252545)   // subtle row divider

/**
 * Parent-facing setup / menu screen.
 *
 * High scores are reloaded every time the screen becomes RESUMED so a score
 * saved in the ExitScoreDialog (on the Play screen back-stack) is visible
 * immediately when the user returns here.
 */
@Composable
fun HomeScreen(
    onStartPlaying: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val highScores by viewModel.highScores.collectAsStateWithLifecycle()

    // Reload high scores on every RESUME (initial appearance *and* returning
    // from PlayScreen). LaunchedEffect(Unit) would only fire on first
    // composition; the Home NavBackStackEntry stays composed while Play is on
    // top, so we use the lifecycle observer to catch the STARTED→RESUMED
    // transition when popping the Play destination.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.reloadHighScores()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { AnimatedHeader(onStartPlaying = onStartPlaying) }

        // ── What to smash ───────────────────────────────────────────────────
        item {
            MenuCard(title = "WHAT TO SMASH") {
                SmashCategoryGrid(
                    selected = settings.smashCategories,
                    onToggle = viewModel::toggleSmashCategory
                )
            }
        }

        // ── Sound ───────────────────────────────────────────────────────────
        item {
            MenuCard(title = "SOUND") {
                MenuToggleRow(
                    label = "Sound",
                    info = "Master switch for all sound effects and music.",
                    checked = settings.soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled
                )
                if (settings.soundEnabled) {
                    RowDivider()
                    MenuToggleRow(
                        label = "Trail sounds",
                        info = "Soft chirps while dragging a finger across the screen. Turn off for silent trails.",
                        checked = settings.trailSoundEnabled,
                        onCheckedChange = viewModel::setTrailSoundEnabled
                    )
                    RowDivider()
                    CollapsibleChoiceRow(
                        label = "Sound Mode",
                        info = "Playful: arcade pops and coin dings. Calm: hushed taps and soft drops.",
                        options = listOf("Calm", "Playful"),
                        selected = if (settings.soundMode == SoundMode.CALM) "Calm" else "Playful",
                        onSelected = { choice ->
                            viewModel.setSoundMode(
                                if (choice == "Calm") SoundMode.CALM else SoundMode.PLAYFUL
                            )
                        }
                    )
                    RowDivider()
                    val trackLabel = when (settings.musicTrack) {
                        MusicTrack.NONE     -> "Off"
                        MusicTrack.TRACK_01 -> "Going Up"
                        MusicTrack.TRACK_02 -> "Heavier"
                        MusicTrack.TRACK_03 -> "Ocean Bubbles"
                        MusicTrack.TRACK_04 -> "Old School Arcade"
                        MusicTrack.TRACK_05 -> "Trendy"
                    }
                    CollapsibleChoiceRow(
                        label = "Music",
                        info = "Background music track. Changes apply instantly.",
                        options = listOf("Off", "Going Up", "Heavier", "Ocean Bubbles", "Old School Arcade", "Trendy"),
                        selected = trackLabel,
                        onSelected = { choice ->
                            viewModel.setMusicTrack(
                                when (choice) {
                                    "Off"              -> MusicTrack.NONE
                                    "Going Up"         -> MusicTrack.TRACK_01
                                    "Heavier"          -> MusicTrack.TRACK_02
                                    "Ocean Bubbles"    -> MusicTrack.TRACK_03
                                    "Old School Arcade"-> MusicTrack.TRACK_04
                                    else               -> MusicTrack.TRACK_05
                                }
                            )
                        }
                    )
                    RowDivider()
                    VolumeSliderRow(
                        label = "Music Volume",
                        info = "Controls how loud the background music is. Does not affect sound effects.",
                        value = settings.musicVolume,
                        onValueChange = viewModel::setMusicVolume
                    )
                    VolumeSliderRow(
                        label = "Effects Volume",
                        info = "Controls how loud taps, pops, and reward sounds are. Does not affect music.",
                        value = settings.sfxVolume,
                        onValueChange = viewModel::setSfxVolume
                    )
                }
            }
        }

        // ── Visuals ─────────────────────────────────────────────────────────
        item {
            MenuCard(title = "VISUALS") {
                CollapsibleChoiceRow(
                    label = "Effects Intensity",
                    info = "How big, fast, and vibrant bursts look. Also affects background drift speed.",
                    options = listOf("Low", "Medium", "High"),
                    selected = when (settings.effectsIntensity) {
                        EffectsIntensity.LOW    -> "Low"
                        EffectsIntensity.MEDIUM -> "Medium"
                        EffectsIntensity.HIGH   -> "High"
                    },
                    onSelected = { choice ->
                        viewModel.setEffectsIntensity(
                            when (choice) {
                                "Low"  -> EffectsIntensity.LOW
                                "High" -> EffectsIntensity.HIGH
                                else   -> EffectsIntensity.MEDIUM
                            }
                        )
                    }
                )
                RowDivider()
                CollapsibleChoiceRow(
                    label = "Trail Length",
                    info = "How long drag trails stay on screen before fading away.",
                    options = listOf("Short", "Medium", "Long"),
                    selected = when (settings.trailLength) {
                        TrailLength.SHORT  -> "Short"
                        TrailLength.MEDIUM -> "Medium"
                        TrailLength.LONG   -> "Long"
                    },
                    onSelected = { choice ->
                        viewModel.setTrailLength(
                            when (choice) {
                                "Short" -> TrailLength.SHORT
                                "Long"  -> TrailLength.LONG
                                else    -> TrailLength.MEDIUM
                            }
                        )
                    }
                )
                RowDivider()
                CollapsibleChoiceRow(
                    label = "Theme",
                    info = "Animated backdrop behind play. Space drifts, Ocean has bubbles, Rainbow flows through colors.",
                    options = listOf("Space", "Ocean", "Rainbow"),
                    selected = when (settings.playTheme) {
                        PlayTheme.SPACE   -> "Space"
                        PlayTheme.OCEAN   -> "Ocean"
                        PlayTheme.RAINBOW -> "Rainbow"
                    },
                    onSelected = { choice ->
                        viewModel.setPlayTheme(
                            when (choice) {
                                "Ocean"   -> PlayTheme.OCEAN
                                "Rainbow" -> PlayTheme.RAINBOW
                                else      -> PlayTheme.SPACE
                            }
                        )
                    }
                )
                RowDivider()
                MenuToggleRow(
                    label = "Reduced motion",
                    info = "Freezes the animated background and heavily shortens drag trails.",
                    checked = settings.reducedMotion,
                    onCheckedChange = viewModel::setReducedMotion
                )
            }
        }

        // ── Gameplay ────────────────────────────────────────────────────────
        item {
            MenuCard(title = "GAMEPLAY") {
                CollapsibleChoiceRow(
                    label = "Starting speed",
                    info = "How busy the screen is when the game begins. Gentle waits longer and starts slower.",
                    options = listOf("Gentle", "Medium", "Fast"),
                    selected = when (settings.startingDifficulty) {
                        StartingDifficulty.GENTLE -> "Gentle"
                        StartingDifficulty.MEDIUM -> "Medium"
                        StartingDifficulty.FAST   -> "Fast"
                    },
                    onSelected = { choice ->
                        viewModel.setStartingDifficulty(
                            when (choice) {
                                "Medium" -> StartingDifficulty.MEDIUM
                                "Fast"   -> StartingDifficulty.FAST
                                else     -> StartingDifficulty.GENTLE
                            }
                        )
                    }
                )
            }
        }

        // ── Interaction ─────────────────────────────────────────────────────
        item {
            MenuCard(title = "INTERACTION") {
                MenuToggleRow(
                    label = "Haptics",
                    info = "Gentle vibration on taps and hits.",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled
                )
                RowDivider()
                MenuToggleRow(
                    label = "Idle demo",
                    info = "Shows a brief auto-play demo when the app has been idle for a while.",
                    checked = settings.idleDemo,
                    onCheckedChange = viewModel::setIdleDemo
                )
                RowDivider()
                MenuToggleRow(
                    label = "Keep screen awake",
                    info = "Prevents the screen from dimming or sleeping while play is active.",
                    checked = settings.keepScreenAwake,
                    onCheckedChange = viewModel::setKeepScreenAwake
                )
            }
        }

        // ── Adaptive ────────────────────────────────────────────────────────
        item {
            MenuCard(title = "ADAPTIVE") {
                MenuToggleRow(
                    label = "Adaptive Play",
                    info = "Automatically adjusts target speed and density based on how actively your child is playing.",
                    checked = settings.adaptivePlayEnabled,
                    onCheckedChange = viewModel::setAdaptivePlayEnabled
                )
                RowDivider()
                MenuToggleRow(
                    label = "Overstimulation Guard",
                    info = "Softens effects and slows targets during very intense play. A tiny indicator appears at the top of the screen so a parent can tell.",
                    checked = settings.overstimulationGuardEnabled,
                    onCheckedChange = viewModel::setOverstimulationGuardEnabled
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

// ── Animated header ──────────────────────────────────────────────────────────

@Composable
private fun AnimatedHeader(onStartPlaying: () -> Unit) {
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
            .clip(RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val stops = arrayOf(
                0.0f  to rotatedHue(0.58f, phase),
                0.35f to rotatedHue(0.78f, phase),
                0.7f  to rotatedHue(0.92f, phase),
                1.0f  to rotatedHue(0.12f, phase)
            )
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = stops,
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )
            val t = phase * 6.28f
            drawCircle(Color.White.copy(alpha = 0.12f), w * 0.28f,
                Offset(w * (0.3f + 0.15f * cos(t)), h * (0.4f + 0.15f * sin(t))))
            drawCircle(Color.White.copy(alpha = 0.08f), w * 0.32f,
                Offset(w * (0.75f + 0.12f * sin(t * 0.8f)), h * (0.7f + 0.1f * cos(t * 0.8f))))
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("StarSmash Kids", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("A touch-play world for little hands", fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            }
            val buttonScale by transition.animateFloat(
                initialValue = 1.0f, targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    tween(1200, easing = LinearEasing), RepeatMode.Reverse
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
                    contentColor = Accent
                )
            ) {
                Text("Start Smashing! 🚀", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── HSV colour helper ────────────────────────────────────────────────────────

private fun rotatedHue(baseHue: Float, phase: Float): Color {
    val h = ((baseHue + phase) % 1f + 1f) % 1f
    return hsv(h, 0.55f, 1f)
}

private fun hsv(h: Float, s: Float, v: Float): Color {
    val i = (h * 6f).toInt()
    val f = h * 6f - i
    val p = v * (1f - s); val q = v * (1f - f * s); val t = v * (1f - (1f - f) * s)
    val (r, g, b) = when (i % 6) {
        0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t)
        3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q)
    }
    return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), 1f)
}

// ── Smash category grid ──────────────────────────────────────────────────────

@Composable
private fun SmashCategoryGrid(
    selected: Set<SmashCategory>,
    onToggle: (SmashCategory) -> Unit
) {
    val rows = listOf(
        listOf(SmashCategory.EMOJI to "⭐ Emoji", SmashCategory.ANIMALS to "🐶 Animals", SmashCategory.DINOSAURS to "🦖 Dinos"),
        listOf(SmashCategory.TRUCKS to "🚒 Trucks", SmashCategory.TOYS to "🧸 Toys", SmashCategory.FOOD to "🍎 Food"),
        listOf(SmashCategory.SPACE to "🚀 Space", SmashCategory.SHAPES to "⬢ Shapes")
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
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
        targetValue = if (selected) AccentDim else ChipBg, label = "chipBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Accent else TextLow, label = "chipFg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                softWrap = true,
                maxLines = 2
            )
        }
    }
}

// ── Card shell ───────────────────────────────────────────────────────────────

@Composable
private fun MenuCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Accent,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = Divider, thickness = 0.5.dp)
}

// ── Toggle row ───────────────────────────────────────────────────────────────
//
// The label supports a deliberate ~500 ms hold to reveal the description text
// inline (sticky – stays open until the user taps the label again to close it).
// detectTapGestures self-cancels if the pointer moves beyond touchSlop, so a
// scroll gesture is never mistaken for a hold and the parent LazyColumn is
// never blocked. The Switch is a separate touch target and is unaffected.

@Composable
private fun MenuToggleRow(
    label: String,
    info: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextHigh,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(info) {
                        detectTapGestures(
                            onLongPress = { showInfo = !showInfo },
                            onTap = { if (showInfo) showInfo = false }
                        )
                    }
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = TextLow,
                    uncheckedTrackColor = ChipBg,
                    uncheckedBorderColor = Divider
                )
            )
        }
        if (showInfo) {
            Text(
                text = info,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = TextLow,
                modifier = Modifier.padding(top = 4.dp, end = 56.dp)
            )
        }
    }
}

// ── Collapsible choice row ───────────────────────────────────────────────────
//
// Shows "Label  [Current ▾]" on a single line.
// Tapping the value chip opens an inline dropdown listing all options.
// A deliberate ~500 ms hold on the label reveals the description (sticky);
// a subsequent tap on the label dismisses it.

@Composable
private fun CollapsibleChoiceRow(
    label: String,
    info: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextHigh,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(info) {
                        detectTapGestures(
                            onLongPress = { showInfo = !showInfo },
                            onTap = { if (showInfo) showInfo = false }
                        )
                    }
            )
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(8.dp),
                color = if (expanded) AccentDim else ChipBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = selected,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        if (showInfo) {
            Text(
                text = info,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = TextLow,
                modifier = Modifier.padding(top = 4.dp, end = 120.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardRaised)
            ) {
                options.forEachIndexed { idx, option ->
                    val isSelected = option == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(option)
                                expanded = false
                            }
                            .background(if (isSelected) AccentDim else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            fontSize = 14.sp,
                            color = if (isSelected) Accent else TextHigh,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (idx < options.lastIndex) {
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ── Volume slider row ────────────────────────────────────────────────────────

@Composable
private fun VolumeSliderRow(
    label: String,
    info: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextHigh,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(info) {
                        detectTapGestures(
                            onLongPress = { showInfo = !showInfo },
                            onTap = { if (showInfo) showInfo = false }
                        )
                    }
            )
            Text(
                text = "${(value * 100).roundToInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Accent
            )
        }
        if (showInfo) {
            Text(
                text = info,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = TextLow,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = ChipBg
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── High scores card ─────────────────────────────────────────────────────────

@Composable
private fun HighScoresCard(
    entries: List<HighScoreEntry>,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardBg,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HIGH SCORES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Accent
                )
                if (entries.isNotEmpty()) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextLow)
                    ) {
                        Text("Clear", fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
            if (entries.isEmpty()) {
                Text(
                    text = "No scores yet. Play a round to add one!",
                    fontSize = 14.sp,
                    color = TextLow
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    when (index) {
                                        0    -> Color(0xFFFFD166)
                                        1    -> Color(0xFFB0BEC5)
                                        2    -> Color(0xFFD7A26B)
                                        else -> ChipBg
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextHigh)
                            Text(entry.formattedDate(), fontSize = 11.sp, color = TextLow)
                        }
                        Text("⭐ ${entry.score}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    if (index < entries.lastIndex) {
                        HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ── Screen pinning card ──────────────────────────────────────────────────────

@Composable
private fun ScreenPinningCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0B1524),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "About accidental exits",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4FC3F7)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "StarSmash Kids runs in fullscreen immersive mode, which hides the navigation bar. " +
                    "However, Android does not allow apps to fully prevent children from exiting.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = TextLow
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "For a fully locked session, use Android's built-in Screen Pinning:\n" +
                    "Settings → Security → Screen Pinning (or App Pinning).\n" +
                    "A parent PIN is required to unpin.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                color = Color(0xFFB0C8E8)
            )
        }
    }
}

// ── Support link card ────────────────────────────────────────────────────────

@Composable
private fun SupportCard() {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri("https://buymeacoffee.com/AndrewRy") },
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1A1430),
        shadowElevation = 4.dp
    ) {
        Text(
            text = "☕  Support this project",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE0D0FF),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}
