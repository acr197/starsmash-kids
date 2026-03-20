package com.starsmash.kids.ui.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.touch.TouchAction
import com.starsmash.kids.touch.TouchClassifier
import com.starsmash.kids.touch.TouchEvent
import com.starsmash.kids.touch.TouchEventType
import com.starsmash.kids.touch.TouchFrame
import com.starsmash.kids.ui.theme.OceanTheme
import com.starsmash.kids.ui.theme.RainbowTheme
import com.starsmash.kids.ui.theme.ShapesTheme
import com.starsmash.kids.ui.theme.SpaceTheme
import kotlinx.coroutines.withFrameMillis
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Effect model ─────────────────────────────────────────────────────────────

private enum class EffectType {
    STAR_BURST, SPARKLE, RAINBOW_TRAIL, CONFETTI, RIPPLE_WAVE,
    BUBBLE, SHAPE, ARC_BRIDGE
}

private data class Effect(
    val x: Float,
    val y: Float,
    var age: Float,         // 0.0 = just born, 1.0 = fully dead
    val maxAge: Float,      // ms before the effect is removed
    val color: Color,
    val type: EffectType,
    val scale: Float,       // size multiplier
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val angle: Float = 0f,  // radians, for directional effects
    val x2: Float = 0f,     // secondary coordinate (TwoFinger, arc end)
    val y2: Float = 0f
)

// ── PlayScreen ────────────────────────────────────────────────────────────────

/**
 * Fullscreen child-facing play canvas.
 *
 * Design principles:
 *   - NO visible UI chrome (no buttons, no text, no nav bars).
 *   - Touch → immediate visual + audio feedback.
 *   - All effects live in a flat mutable list updated each animation frame.
 *   - Back gesture handling: requires TWO back presses within 2 seconds.
 *     Comment: Android 10+ does not permit an app to fully prevent back gestures;
 *     we use BackHandler to require a second press as a usability speedbump.
 *     Full prevention is not possible without using Android's Screen Pinning feature.
 */
@Composable
fun PlayScreen(
    onExit: () -> Unit,
    viewModel: PlayViewModel = viewModel()
) {
    val playState by viewModel.playState.collectAsStateWithLifecycle()
    val settings = playState.settings

    // Reload settings in case parent changed them after this VM was created
    LaunchedEffect(Unit) { viewModel.reloadSettings() }

    // Screen size tracked for TouchClassifier initialization
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    // TouchClassifier – recreated if screen size changes
    val classifier = remember(screenSize) {
        if (screenSize == IntSize.Zero) null
        else TouchClassifier(screenSize.width.toFloat(), screenSize.height.toFloat())
    }

    // Mutable list of live effects – mutated every frame
    val effects = remember { mutableStateListOf<Effect>() }

    // Back-press exit guard
    var backPressCount by remember { mutableIntStateOf(0) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    var showExitHint by remember { mutableStateOf(false) }

    // IMPORTANT: Android 10+ (gesture navigation) does not allow apps to fully
    // intercept back gestures. BackHandler catches the system back button / back
    // gesture event in Compose, but edge swipes that trigger Android's built-in
    // gesture navigation happen BEFORE Compose sees them and cannot be intercepted.
    // This double-press mechanism is a usability hint, not full prevention.
    BackHandler {
        val now = System.currentTimeMillis()
        if (backPressCount > 0 && now - lastBackPressTime < 2000L) {
            // Second back press within 2 seconds – exit
            onExit()
        } else {
            backPressCount = 1
            lastBackPressTime = now
            showExitHint = true
        }
    }

    // Hide exit hint after 2 seconds
    LaunchedEffect(showExitHint) {
        if (showExitHint) {
            kotlinx.coroutines.delay(2000L)
            showExitHint = false
            backPressCount = 0
        }
    }

    // Theme colors
    val backgroundColor = remember(settings.playTheme) { themeBackground(settings.playTheme) }
    val themeColors = remember(settings.playTheme) { themeColorPalette(settings.playTheme) }

    // Effect base scale driven by settings intensity and adaptive play
    val baseScale = remember(settings.effectsIntensity, playState.stimulusLevel, playState.isGuardActive) {
        val intensityMult = when (settings.effectsIntensity) {
            com.starsmash.kids.settings.EffectsIntensity.LOW -> 0.6f
            com.starsmash.kids.settings.EffectsIntensity.MEDIUM -> 1.0f
            com.starsmash.kids.settings.EffectsIntensity.HIGH -> 1.5f
        }
        val adaptiveMult = if (settings.adaptivePlayEnabled) {
            0.6f + playState.stimulusLevel * 0.8f
        } else 1.0f
        val guardMult = if (playState.isGuardActive) 0.6f else 1.0f
        (intensityMult * adaptiveMult * guardMult).coerceIn(0.2f, 2.5f)
    }

    // Animation loop: update effect ages each frame, trigger recompose
    var frameTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val dt = withFrameMillis { it } - frameTime
            frameTime = withFrameMillis { it }

            // Advance effect ages (dt in ms, maxAge in ms)
            val deltaFraction = if (dt > 0) dt.toFloat() else 16f
            effects.removeAll { effect ->
                effect.age >= 1f
            }
            for (i in effects.indices) {
                val e = effects[i]
                effects[i] = e.copy(age = (e.age + deltaFraction / e.maxAge).coerceAtMost(1f))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onGloballyPositioned { coords ->
                screenSize = coords.size
            }
            .pointerInput(Unit) {
                // Multi-touch tracking using awaitPointerEventScope
                // All pointer events flow through here, building TouchFrames for the classifier
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val c = classifier ?: continue

                        // Build a list of TouchEvent from the PointerInputChange list
                        val action = when (event.type) {
                            PointerEventType.Press -> TouchAction.DOWN
                            PointerEventType.Move -> TouchAction.MOVE
                            PointerEventType.Release -> TouchAction.UP
                            else -> TouchAction.MOVE
                        }

                        val pointerEvents = event.changes.map { change ->
                            TouchEvent(
                                pointerId = change.id.value.toInt(),
                                x = change.position.x,
                                y = change.position.y,
                                pressure = change.pressure,
                                touchMajor = 0f, // Not available via Compose pointer API
                                eventTime = change.uptimeMillis,
                                action = if (!change.pressed && change.previousPressed) TouchAction.UP else action
                            )
                        }

                        val frame = TouchFrame(
                            pointers = pointerEvents,
                            eventTime = event.changes.firstOrNull()?.uptimeMillis ?: 0L,
                            action = action
                        )

                        val touchType = c.classify(frame) ?: continue

                        // Spawn effects
                        spawnEffects(
                            type = touchType,
                            effects = effects,
                            colors = themeColors,
                            baseScale = baseScale,
                            reducedMotion = settings.reducedMotion,
                            emojiMode = settings.fullEmojiMode,
                            theme = settings.playTheme
                        )

                        // Notify ViewModel (audio + adaptive engine)
                        viewModel.onTouchEvent(touchType, frame.pointerCount)

                        // Consume all changes to prevent gesture conflicts
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        // Canvas renders all live effects
        Canvas(modifier = Modifier.fillMaxSize()) {
            effects.forEach { effect ->
                drawEffect(effect, settings.reducedMotion)
            }
        }

        // Overstimulation guard overlay – subtle dimming
        if (playState.isGuardActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }

        // Exit hint overlay – shown after first back press
        if (showExitHint) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Press back again to exit",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

// ── Effect spawning ───────────────────────────────────────────────────────────

private fun spawnEffects(
    type: TouchEventType,
    effects: MutableList<Effect>,
    colors: List<Color>,
    baseScale: Float,
    reducedMotion: Boolean,
    emojiMode: Boolean,
    theme: PlayTheme
) {
    val maxAge = if (reducedMotion) 600f else 900f
    val count = if (reducedMotion) 4 else 8
    val rng = Random

    fun randomColor() = colors[rng.nextInt(colors.size)]

    when (type) {
        is TouchEventType.SingleTap -> {
            repeat(count) { i ->
                val angle = (i.toFloat() / count) * 2f * PI.toFloat()
                effects.add(
                    Effect(
                        x = type.x, y = type.y,
                        age = 0f, maxAge = maxAge,
                        color = randomColor(),
                        type = EffectType.STAR_BURST,
                        scale = baseScale,
                        velocityX = cos(angle) * 4f,
                        velocityY = sin(angle) * 4f,
                        angle = angle
                    )
                )
            }
            // Central sparkle
            effects.add(
                Effect(
                    x = type.x, y = type.y,
                    age = 0f, maxAge = maxAge * 0.7f,
                    color = randomColor(),
                    type = EffectType.SPARKLE,
                    scale = baseScale * 1.2f
                )
            )
        }

        is TouchEventType.SingleDrag -> {
            effects.add(
                Effect(
                    x = type.x, y = type.y,
                    age = 0f, maxAge = if (reducedMotion) 300f else 500f,
                    color = randomColor(),
                    type = EffectType.RAINBOW_TRAIL,
                    scale = baseScale
                )
            )
        }

        is TouchEventType.TwoFingerTap -> {
            // Mirrored bursts from both touch points + connecting arc
            listOf(Pair(type.x1, type.y1), Pair(type.x2, type.y2)).forEach { (bx, by) ->
                repeat(count / 2) { i ->
                    val angle = (i.toFloat() / (count / 2)) * 2f * PI.toFloat()
                    effects.add(
                        Effect(
                            x = bx, y = by,
                            age = 0f, maxAge = maxAge,
                            color = randomColor(),
                            type = EffectType.STAR_BURST,
                            scale = baseScale,
                            velocityX = cos(angle) * 3f,
                            velocityY = sin(angle) * 3f
                        )
                    )
                }
            }
            // Arc bridge between two points
            effects.add(
                Effect(
                    x = type.x1, y = type.y1,
                    age = 0f, maxAge = maxAge * 1.2f,
                    color = randomColor(),
                    type = EffectType.ARC_BRIDGE,
                    scale = baseScale,
                    x2 = type.x2, y2 = type.y2
                )
            )
        }

        is TouchEventType.TwoFingerDrag -> {
            effects.add(
                Effect(
                    x = type.x1, y = type.y1,
                    age = 0f, maxAge = 400f,
                    color = randomColor(),
                    type = EffectType.RAINBOW_TRAIL,
                    scale = baseScale * 0.8f
                )
            )
            effects.add(
                Effect(
                    x = type.x2, y = type.y2,
                    age = 0f, maxAge = 400f,
                    color = randomColor(),
                    type = EffectType.RAINBOW_TRAIL,
                    scale = baseScale * 0.8f
                )
            )
        }

        is TouchEventType.MultiTouchBurst -> {
            // Confetti explosion + multi-star spray
            val burstCount = if (reducedMotion) 10 else 20
            repeat(burstCount) {
                val angle = rng.nextFloat() * 2f * PI.toFloat()
                val speed = rng.nextFloat() * 8f + 2f
                effects.add(
                    Effect(
                        x = type.x, y = type.y,
                        age = 0f, maxAge = maxAge * 1.2f,
                        color = randomColor(),
                        type = EffectType.CONFETTI,
                        scale = baseScale,
                        velocityX = cos(angle) * speed,
                        velocityY = sin(angle) * speed,
                        angle = rng.nextFloat() * 2f * PI.toFloat()
                    )
                )
            }
        }

        is TouchEventType.PalmLikeBurst -> {
            // Full-screen ripple wave + large confetti
            effects.add(
                Effect(
                    x = type.x, y = type.y,
                    age = 0f, maxAge = maxAge * 1.5f,
                    color = randomColor(),
                    type = EffectType.RIPPLE_WAVE,
                    scale = baseScale * 2f
                )
            )
            val confettiCount = if (reducedMotion) 15 else 35
            repeat(confettiCount) {
                val angle = rng.nextFloat() * 2f * PI.toFloat()
                val speed = rng.nextFloat() * 12f + 3f
                effects.add(
                    Effect(
                        x = type.x, y = type.y,
                        age = 0f, maxAge = maxAge * 1.3f,
                        color = randomColor(),
                        type = EffectType.CONFETTI,
                        scale = baseScale * 1.4f,
                        velocityX = cos(angle) * speed,
                        velocityY = sin(angle) * speed,
                        angle = rng.nextFloat() * 2f * PI.toFloat()
                    )
                )
            }
        }

        is TouchEventType.RapidTapCluster -> {
            // Bouncing energetic stars
            val starCount = if (reducedMotion) 6 else (type.tapCount * 2).coerceAtMost(16)
            repeat(starCount) {
                val angle = rng.nextFloat() * 2f * PI.toFloat()
                val speed = rng.nextFloat() * 10f + 4f
                effects.add(
                    Effect(
                        x = type.x, y = type.y,
                        age = 0f, maxAge = maxAge,
                        color = randomColor(),
                        type = EffectType.STAR_BURST,
                        scale = baseScale * 1.3f,
                        velocityX = cos(angle) * speed,
                        velocityY = sin(angle) * speed,
                        angle = angle
                    )
                )
            }
        }

        is TouchEventType.EdgeEntrySwipe -> {
            // Subtle bubble puff – less intense since this may be an accidental edge swipe
            repeat(4) {
                val angle = rng.nextFloat() * 2f * PI.toFloat()
                effects.add(
                    Effect(
                        x = type.x, y = type.y,
                        age = 0f, maxAge = 400f,
                        color = randomColor().copy(alpha = 0.6f),
                        type = EffectType.BUBBLE,
                        scale = baseScale * 0.5f,
                        velocityX = cos(angle) * 2f,
                        velocityY = sin(angle) * 2f
                    )
                )
            }
        }
    }

    // Cap total effects for performance
    val maxEffects = 200
    while (effects.size > maxEffects) {
        effects.removeAt(0)
    }
}

// ── Draw functions ────────────────────────────────────────────────────────────

private fun DrawScope.drawEffect(effect: Effect, reducedMotion: Boolean) {
    val alpha = (1f - effect.age).coerceIn(0f, 1f)
    if (alpha <= 0f) return

    // Positional update: age-based movement (effects move along velocity vector)
    val progress = effect.age
    val px = effect.x + effect.velocityX * progress * effect.maxAge * 0.06f
    val py = effect.y + effect.velocityY * progress * effect.maxAge * 0.06f

    val color = effect.color.copy(alpha = alpha)

    when (effect.type) {
        EffectType.STAR_BURST -> {
            val radius = effect.scale * 20f * (1f + progress * 1.5f)
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(px, py)
            )
            // Star points
            if (!reducedMotion) {
                val pointRadius = radius * 0.4f
                for (i in 0 until 5) {
                    val a = (i * 2f * PI / 5f + effect.angle).toFloat()
                    val sx = px + cos(a) * radius
                    val sy = py + sin(a) * radius
                    drawCircle(
                        color = color,
                        radius = pointRadius,
                        center = Offset(sx, sy)
                    )
                }
            }
        }

        EffectType.SPARKLE -> {
            val r = effect.scale * 30f * (1f - progress * 0.3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(px, py),
                    radius = r
                ),
                radius = r,
                center = Offset(px, py)
            )
        }

        EffectType.RAINBOW_TRAIL -> {
            val r = effect.scale * 12f * (1f - progress * 0.5f)
            drawCircle(color = color, radius = r, center = Offset(px, py))
        }

        EffectType.CONFETTI -> {
            val size = effect.scale * 14f * (1f - progress * 0.2f)
            if (!reducedMotion) {
                // Rotate confetti square
                val rot = effect.angle + progress * 4f
                drawRect(
                    color = color,
                    topLeft = Offset(px - size / 2f, py - size / 2f),
                    size = Size(size, size)
                )
            } else {
                drawCircle(color = color, radius = size / 2f, center = Offset(px, py))
            }
        }

        EffectType.RIPPLE_WAVE -> {
            val r = effect.scale * 80f * (0.3f + progress * 1.5f)
            val strokeWidth = effect.scale * 6f * (1f - progress)
            if (strokeWidth > 0f) {
                drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(effect.x, effect.y),
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        EffectType.BUBBLE -> {
            val r = effect.scale * 18f * (0.5f + progress * 0.8f)
            drawCircle(
                color = color,
                radius = r,
                center = Offset(px, py),
                style = Stroke(width = 3f)
            )
        }

        EffectType.SHAPE -> {
            val r = effect.scale * 20f
            drawCircle(color = color, radius = r, center = Offset(px, py))
        }

        EffectType.ARC_BRIDGE -> {
            // Quadratic bezier arc between (x,y) and (x2,y2)
            val midX = (effect.x + effect.x2) / 2f
            val midY = (effect.y + effect.y2) / 2f - 120f * effect.scale
            val path = Path().apply {
                moveTo(effect.x, effect.y)
                quadraticTo(midX, midY, effect.x2, effect.y2)
            }
            val strokeW = effect.scale * 5f * (1f - progress * 0.7f)
            if (strokeW > 0f) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                )
            }
        }
    }
}

// ── Theme helpers ─────────────────────────────────────────────────────────────

private fun themeBackground(theme: PlayTheme): Color = when (theme) {
    PlayTheme.SPACE -> SpaceTheme.background
    PlayTheme.OCEAN -> OceanTheme.background
    PlayTheme.RAINBOW -> RainbowTheme.background
    PlayTheme.SHAPES -> ShapesTheme.background
}

private fun themeColorPalette(theme: PlayTheme): List<Color> = when (theme) {
    PlayTheme.SPACE -> listOf(
        SpaceTheme.primary, SpaceTheme.secondary, SpaceTheme.accent,
        Color(0xFFE040FB), Color(0xFF40C4FF), Color(0xFFFFFF00)
    )
    PlayTheme.OCEAN -> listOf(
        OceanTheme.primary, OceanTheme.secondary, OceanTheme.accent,
        Color(0xFF00E5FF), Color(0xFF80DEEA), Color(0xFFB2EBF2)
    )
    PlayTheme.RAINBOW -> RainbowTheme.colors
    PlayTheme.SHAPES -> ShapesTheme.colors
}
