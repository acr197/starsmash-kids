package com.starsmash.kids.ui.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.touch.TouchEventType
import com.starsmash.kids.ui.theme.OceanTheme
import com.starsmash.kids.ui.theme.RainbowTheme
import com.starsmash.kids.ui.theme.ShapesTheme
import com.starsmash.kids.ui.theme.SpaceTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// ── Effect model ─────────────────────────────────────────────────────────────

private enum class EffectType {
    STAR_BURST, SPARKLE, RAINBOW_TRAIL, CONFETTI, RIPPLE_WAVE, BUBBLE
}

private data class Effect(
    val x: Float,
    val y: Float,
    var age: Float,
    val maxAge: Float,
    val color: Color,
    val type: EffectType,
    val scale: Float,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val angle: Float = 0f
)

// ── Floating target model ────────────────────────────────────────────────────

private enum class TargetShape { STAR, CIRCLE, SQUARE, TRIANGLE, HEART, DIAMOND }

private data class FloatingTarget(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    val shape: TargetShape,
    var spin: Float,
    val spinSpeed: Float
)

// ── PlayScreen ────────────────────────────────────────────────────────────────

/**
 * Fullscreen child-facing play canvas.
 *
 * Any touch (tap, drag, multi-finger, palm) spawns visual effects and plays a
 * sound. Floating shapes drift across the screen; tapping one produces a
 * larger reactive burst. Session difficulty (target speed and count) ramps up
 * the longer the session lasts.
 *
 * Back-press handling requires three presses. The first and second presses
 * show a small semi-transparent toast at the top which auto-dismisses.
 */
@Composable
fun PlayScreen(
    onExit: () -> Unit,
    viewModel: PlayViewModel = viewModel()
) {
    val playState by viewModel.playState.collectAsStateWithLifecycle()
    val settings = playState.settings

    LaunchedEffect(Unit) { viewModel.reloadSettings() }

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    // Effects list + floating targets list. Both mutated from the animation
    // loop and the pointerInput handler; SnapshotStateLists are thread-safe
    // enough for the single-threaded Compose UI model.
    val effects = remember { mutableStateListOf<Effect>() }
    val targets = remember { mutableStateListOf<FloatingTarget>() }

    // Session start time drives difficulty escalation.
    val sessionStart = remember { System.currentTimeMillis() }

    // Back-press state: need 3 presses to exit. Toast shown between presses.
    var backPressCount by remember { mutableIntStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    BackHandler {
        backPressCount += 1
        when (backPressCount) {
            1 -> toastMessage = "Press back 2 more times to exit"
            2 -> toastMessage = "Press back 1 more time to exit"
            else -> onExit()
        }
    }

    // Auto-dismiss toast and reset counter after inactivity.
    LaunchedEffect(backPressCount, toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500L)
            toastMessage = null
            backPressCount = 0
        }
    }

    val backgroundColor = remember(settings.playTheme) { themeBackground(settings.playTheme) }
    val themeColors = remember(settings.playTheme) { themeColorPalette(settings.playTheme) }

    // rememberUpdatedState so the long-lived pointerInput lambda always reads
    // the latest values without being torn down and re-launched.
    val currentThemeColors by rememberUpdatedState(themeColors)
    val currentReducedMotion by rememberUpdatedState(settings.reducedMotion)

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
    val currentBaseScale by rememberUpdatedState(baseScale)

    // Animation loop: advance effect ages, move floating targets, spawn new
    // ones as needed. Difficulty scales with elapsed session time.
    LaunchedEffect(screenSize) {
        if (screenSize == IntSize.Zero) return@LaunchedEffect
        val width = screenSize.width.toFloat()
        val height = screenSize.height.toFloat()
        val rng = Random

        var lastFrameTime = withFrameMillis { it }
        var nextTargetId = 0L

        // Seed initial targets so the screen isn't empty on entry.
        repeat(4) {
            targets.add(spawnTarget(nextTargetId++, width, height, currentThemeColors, 1f, rng))
        }

        while (true) {
            val currentFrameTime = withFrameMillis { it }
            val dt = (currentFrameTime - lastFrameTime).coerceIn(0L, 100L)
            lastFrameTime = currentFrameTime
            val deltaMs = if (dt > 0) dt.toFloat() else 16f

            // Difficulty curve: 0 at 0s, 1 around 60s, clamped to ~1.6.
            val elapsedSec = (currentFrameTime - sessionStart) / 1000f
            val difficulty = (0.4f + elapsedSec / 60f).coerceAtMost(1.6f)

            // Advance effect ages.
            effects.removeAll { it.age >= 1f }
            val esize = effects.size
            for (i in 0 until esize) {
                if (i >= effects.size) break
                val e = effects[i]
                effects[i] = e.copy(age = (e.age + deltaMs / e.maxAge).coerceAtMost(1f))
            }

            // Move floating targets, wrap around screen edges.
            val speedMul = 0.06f * difficulty
            val tsize = targets.size
            for (i in 0 until tsize) {
                if (i >= targets.size) break
                val t = targets[i]
                var nx = t.x + t.vx * deltaMs * speedMul
                var ny = t.y + t.vy * deltaMs * speedMul
                val margin = t.radius + 20f
                if (nx < -margin) nx = width + margin
                if (nx > width + margin) nx = -margin
                if (ny < -margin) ny = height + margin
                if (ny > height + margin) ny = -margin
                t.x = nx
                t.y = ny
                t.spin += t.spinSpeed * deltaMs * 0.001f
            }

            // Maintain a target count that grows with difficulty.
            val desiredCount = (4 + (difficulty * 6).toInt()).coerceIn(4, 14)
            while (targets.size < desiredCount) {
                targets.add(spawnTarget(nextTargetId++, width, height, currentThemeColors, difficulty, rng))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onGloballyPositioned { coords -> screenSize = coords.size }
            .pointerInput(Unit) {
                // Per-pointer last-known position for drag-trail spawning.
                val lastPositions = mutableMapOf<Long, Offset>()

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val activeCount = changes.count { it.pressed }

                        // Determine which pointers just went down, which are
                        // moving, and which just lifted. Spawn appropriate
                        // effects and audio events for each.
                        var anyNewPress = false
                        changes.forEach { change ->
                            val id = change.id.value
                            val pos = change.position
                            val wasPressed = change.previousPressed
                            val nowPressed = change.pressed

                            if (nowPressed && !wasPressed) {
                                // New touch down.
                                anyNewPress = true
                                lastPositions[id] = pos

                                // Check if it landed on a floating target.
                                val hitIdx = targets.indexOfLast { t ->
                                    hypot((t.x - pos.x).toDouble(), (t.y - pos.y).toDouble())
                                        .toFloat() <= t.radius + 18f
                                }
                                if (hitIdx >= 0) {
                                    val hit = targets.removeAt(hitIdx)
                                    spawnTargetBurst(effects, hit, currentThemeColors, currentBaseScale, currentReducedMotion)
                                    viewModel.onTouchEvent(
                                        TouchEventType.RapidTapCluster(pos.x, pos.y, 2),
                                        activeCount
                                    )
                                } else {
                                    spawnTapBurst(effects, pos.x, pos.y, currentThemeColors, currentBaseScale, currentReducedMotion)
                                    val evtType = when {
                                        activeCount >= 4 -> TouchEventType.PalmLikeBurst(pos.x, pos.y)
                                        activeCount >= 3 -> TouchEventType.MultiTouchBurst(pos.x, pos.y, activeCount)
                                        activeCount == 2 -> TouchEventType.TwoFingerTap(pos.x, pos.y, pos.x, pos.y)
                                        else -> TouchEventType.SingleTap(pos.x, pos.y)
                                    }
                                    viewModel.onTouchEvent(evtType, activeCount)
                                }
                            } else if (nowPressed && wasPressed) {
                                // Movement – spawn a trail if we moved enough.
                                val last = lastPositions[id]
                                if (last != null) {
                                    val dist = hypot(
                                        (pos.x - last.x).toDouble(),
                                        (pos.y - last.y).toDouble()
                                    ).toFloat()
                                    if (dist > 18f) {
                                        lastPositions[id] = pos
                                        spawnTrail(effects, pos.x, pos.y, currentThemeColors, currentBaseScale)
                                        viewModel.onTouchEvent(
                                            TouchEventType.SingleDrag(pos.x, pos.y),
                                            activeCount
                                        )
                                        // Opportunistic target hit-check on drag.
                                        val hitIdx = targets.indexOfLast { t ->
                                            hypot((t.x - pos.x).toDouble(), (t.y - pos.y).toDouble())
                                                .toFloat() <= t.radius + 12f
                                        }
                                        if (hitIdx >= 0) {
                                            val hit = targets.removeAt(hitIdx)
                                            spawnTargetBurst(
                                                effects, hit, currentThemeColors, currentBaseScale,
                                                currentReducedMotion
                                            )
                                        }
                                    }
                                }
                            } else if (!nowPressed && wasPressed) {
                                lastPositions.remove(id)
                            }

                            change.consume()
                        }

                        // Extra sparkle when several fingers press simultaneously.
                        if (anyNewPress && activeCount >= 3) {
                            val cx = changes.filter { it.pressed }.map { it.position.x }.average().toFloat()
                            val cy = changes.filter { it.pressed }.map { it.position.y }.average().toFloat()
                            spawnRipple(effects, cx, cy, currentThemeColors, currentBaseScale)
                        }

                        // Cap total effects.
                        while (effects.size > 250) effects.removeAt(0)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw floating targets first so effects sit on top.
            targets.forEach { drawTarget(it) }
            effects.forEach { drawEffect(it, settings.reducedMotion) }
        }

        // Guard overlay (subtle dimming).
        if (playState.isGuardActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }

        // Top toast for back-press warning. Non-blocking: doesn't cover play area.
        val msg = toastMessage
        if (msg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp, start = 32.dp, end = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Target spawning and hit effects ──────────────────────────────────────────

private fun spawnTarget(
    id: Long,
    width: Float,
    height: Float,
    colors: List<Color>,
    difficulty: Float,
    rng: Random
): FloatingTarget {
    val radius = 32f + rng.nextFloat() * 28f
    val baseSpeed = 0.8f + rng.nextFloat() * 1.6f
    val speed = baseSpeed * (0.8f + difficulty * 0.8f)
    val angle = rng.nextFloat() * 2f * PI.toFloat()
    return FloatingTarget(
        id = id,
        x = rng.nextFloat() * width,
        y = rng.nextFloat() * height,
        vx = cos(angle) * speed,
        vy = sin(angle) * speed,
        radius = radius,
        color = colors[rng.nextInt(colors.size)],
        shape = TargetShape.values()[rng.nextInt(TargetShape.values().size)],
        spin = rng.nextFloat() * 2f * PI.toFloat(),
        spinSpeed = (rng.nextFloat() - 0.5f) * 2f
    )
}

private fun spawnTapBurst(
    effects: MutableList<Effect>,
    x: Float,
    y: Float,
    colors: List<Color>,
    baseScale: Float,
    reducedMotion: Boolean
) {
    val rng = Random
    val maxAge = if (reducedMotion) 600f else 900f
    val count = if (reducedMotion) 5 else 10
    repeat(count) { i ->
        val angle = (i.toFloat() / count) * 2f * PI.toFloat()
        effects.add(
            Effect(
                x = x, y = y,
                age = 0f, maxAge = maxAge,
                color = colors[rng.nextInt(colors.size)],
                type = EffectType.STAR_BURST,
                scale = baseScale,
                velocityX = cos(angle) * 4f,
                velocityY = sin(angle) * 4f,
                angle = angle
            )
        )
    }
    effects.add(
        Effect(
            x = x, y = y,
            age = 0f, maxAge = maxAge * 0.7f,
            color = colors[rng.nextInt(colors.size)],
            type = EffectType.SPARKLE,
            scale = baseScale * 1.4f
        )
    )
}

private fun spawnTrail(
    effects: MutableList<Effect>,
    x: Float,
    y: Float,
    colors: List<Color>,
    baseScale: Float
) {
    val rng = Random
    effects.add(
        Effect(
            x = x, y = y,
            age = 0f, maxAge = 500f,
            color = colors[rng.nextInt(colors.size)],
            type = EffectType.RAINBOW_TRAIL,
            scale = baseScale
        )
    )
}

private fun spawnRipple(
    effects: MutableList<Effect>,
    x: Float,
    y: Float,
    colors: List<Color>,
    baseScale: Float
) {
    val rng = Random
    effects.add(
        Effect(
            x = x, y = y,
            age = 0f, maxAge = 1200f,
            color = colors[rng.nextInt(colors.size)],
            type = EffectType.RIPPLE_WAVE,
            scale = baseScale * 2.2f
        )
    )
    repeat(14) {
        val angle = rng.nextFloat() * 2f * PI.toFloat()
        val speed = rng.nextFloat() * 10f + 3f
        effects.add(
            Effect(
                x = x, y = y,
                age = 0f, maxAge = 1000f,
                color = colors[rng.nextInt(colors.size)],
                type = EffectType.CONFETTI,
                scale = baseScale,
                velocityX = cos(angle) * speed,
                velocityY = sin(angle) * speed,
                angle = rng.nextFloat() * 2f * PI.toFloat()
            )
        )
    }
}

private fun spawnTargetBurst(
    effects: MutableList<Effect>,
    hit: FloatingTarget,
    colors: List<Color>,
    baseScale: Float,
    reducedMotion: Boolean
) {
    val rng = Random
    val count = if (reducedMotion) 12 else 22
    repeat(count) {
        val angle = rng.nextFloat() * 2f * PI.toFloat()
        val speed = rng.nextFloat() * 9f + 3f
        effects.add(
            Effect(
                x = hit.x, y = hit.y,
                age = 0f, maxAge = 1100f,
                color = if (rng.nextBoolean()) hit.color else colors[rng.nextInt(colors.size)],
                type = EffectType.CONFETTI,
                scale = baseScale * 1.2f,
                velocityX = cos(angle) * speed,
                velocityY = sin(angle) * speed,
                angle = rng.nextFloat() * 2f * PI.toFloat()
            )
        )
    }
    effects.add(
        Effect(
            x = hit.x, y = hit.y,
            age = 0f, maxAge = 900f,
            color = hit.color,
            type = EffectType.SPARKLE,
            scale = baseScale * 2f
        )
    )
    effects.add(
        Effect(
            x = hit.x, y = hit.y,
            age = 0f, maxAge = 1000f,
            color = hit.color,
            type = EffectType.RIPPLE_WAVE,
            scale = baseScale * 1.5f
        )
    )
}

// ── Draw ─────────────────────────────────────────────────────────────────────

private fun DrawScope.drawTarget(t: FloatingTarget) {
    val c = t.color
    when (t.shape) {
        TargetShape.CIRCLE -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c, c.copy(alpha = 0.4f)),
                    center = Offset(t.x, t.y),
                    radius = t.radius
                ),
                radius = t.radius,
                center = Offset(t.x, t.y)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = t.radius * 0.25f,
                center = Offset(t.x - t.radius * 0.3f, t.y - t.radius * 0.3f)
            )
        }
        TargetShape.STAR -> {
            val points = 5
            val outer = t.radius
            val inner = t.radius * 0.45f
            val path = Path()
            for (i in 0 until points * 2) {
                val r = if (i % 2 == 0) outer else inner
                val a = t.spin + i * PI.toFloat() / points - PI.toFloat() / 2f
                val px = t.x + cos(a) * r
                val py = t.y + sin(a) * r
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path = path, color = c)
        }
        TargetShape.SQUARE -> {
            // Axis-aligned rounded square; spin ignored for simplicity.
            val s = t.radius * 1.6f
            drawRect(
                color = c,
                topLeft = Offset(t.x - s / 2f, t.y - s / 2f),
                size = Size(s, s)
            )
        }
        TargetShape.TRIANGLE -> {
            val r = t.radius
            val path = Path()
            for (i in 0 until 3) {
                val a = t.spin + i * 2f * PI.toFloat() / 3f - PI.toFloat() / 2f
                val px = t.x + cos(a) * r
                val py = t.y + sin(a) * r
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path = path, color = c)
        }
        TargetShape.HEART -> {
            val r = t.radius
            val path = Path()
            // Simple heart built from two circles and a triangle approximation.
            val cx = t.x
            val cy = t.y
            path.moveTo(cx, cy + r * 0.8f)
            path.cubicTo(
                cx - r * 1.4f, cy + r * 0.1f,
                cx - r * 0.6f, cy - r * 0.9f,
                cx, cy - r * 0.2f
            )
            path.cubicTo(
                cx + r * 0.6f, cy - r * 0.9f,
                cx + r * 1.4f, cy + r * 0.1f,
                cx, cy + r * 0.8f
            )
            path.close()
            drawPath(path = path, color = c)
        }
        TargetShape.DIAMOND -> {
            val r = t.radius
            val path = Path()
            path.moveTo(t.x, t.y - r)
            path.lineTo(t.x + r * 0.8f, t.y)
            path.lineTo(t.x, t.y + r)
            path.lineTo(t.x - r * 0.8f, t.y)
            path.close()
            drawPath(path = path, color = c)
        }
    }
}

private fun DrawScope.drawEffect(effect: Effect, reducedMotion: Boolean) {
    val alpha = (1f - effect.age).coerceIn(0f, 1f)
    if (alpha <= 0f) return

    val progress = effect.age
    val px = effect.x + effect.velocityX * progress * effect.maxAge * 0.06f
    val py = effect.y + effect.velocityY * progress * effect.maxAge * 0.06f

    val color = effect.color.copy(alpha = alpha)

    when (effect.type) {
        EffectType.STAR_BURST -> {
            val radius = effect.scale * 18f * (1f + progress * 1.5f)
            drawCircle(color = color, radius = radius, center = Offset(px, py))
            if (!reducedMotion) {
                val pointRadius = radius * 0.4f
                for (i in 0 until 5) {
                    val a = (i * 2f * PI / 5f + effect.angle).toFloat()
                    drawCircle(
                        color = color,
                        radius = pointRadius,
                        center = Offset(px + cos(a) * radius, py + sin(a) * radius)
                    )
                }
            }
        }
        EffectType.SPARKLE -> {
            val r = effect.scale * 34f * (1f - progress * 0.3f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(px, py),
                    radius = max(r, 1f)
                ),
                radius = max(r, 1f),
                center = Offset(px, py)
            )
        }
        EffectType.RAINBOW_TRAIL -> {
            val r = effect.scale * 14f * (1f - progress * 0.5f)
            drawCircle(color = color, radius = r, center = Offset(px, py))
        }
        EffectType.CONFETTI -> {
            val size = effect.scale * 14f * (1f - progress * 0.2f)
            if (!reducedMotion) {
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
            val r = effect.scale * 90f * (0.2f + progress * 1.6f)
            val strokeWidth = effect.scale * 7f * (1f - progress)
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

