package com.starsmash.kids.ui.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starsmash.kids.settings.EffectsIntensity
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.touch.TouchEventType
import kotlin.math.*
import kotlin.random.Random

// ── Effect model ──────────────────────────────────────────────────────────────

private enum class EffectType { STAR_BURST, SPARKLE, RAINBOW_TRAIL, CONFETTI, RIPPLE_WAVE, BUBBLE }

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

// ── Floating target model ─────────────────────────────────────────────────────

private enum class TargetKind { EMOJI, CIRCLE, STAR, SQUARE, TRIANGLE, HEART, DIAMOND }

private data class FloatingTarget(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val color: Color,
    val kind: TargetKind,
    val emoji: String,
    var spin: Float,
    val spinSpeed: Float,
    var dirChangeTimer: Float
)

// Kid-friendly emoji pool
private val EMOJI_POOL = listOf(
    "🚒", "🚚", "🚜", "🦖", "🐶", "🐱", "🐘", "🐵", "🐻", "🧸",
    "🎈", "⭐", "🚀", "🦄", "🦁", "🐙", "🐬", "🏆", "🎂", "🌟",
    "💫", "🎀", "🎠", "🌈"
)

// ── PlayScreen ────────────────────────────────────────────────────────────────

/**
 * Fullscreen child-facing play canvas.
 *
 * Floating targets drift in from screen edges after 5 s. Tapping them bursts
 * them and increments a score counter. Background animates with the current
 * theme. Difficulty ramps over time: targets speed up and their count grows.
 *
 * Back-press handling requires three presses, with a toast notification.
 */
@Composable
fun PlayScreen(
    onExit: () -> Unit,
    viewModel: PlayViewModel = viewModel()
) {
    val playState by viewModel.playState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reloadSettings() }

    val settings = playState.settings

    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val effects = remember { mutableStateListOf<Effect>() }
    val targets = remember { mutableStateListOf<FloatingTarget>() }
    var score by remember { mutableIntStateOf(0) }
    var backPressCount by remember { mutableIntStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    val sessionStart = remember { System.currentTimeMillis() }
    var bgPhase by remember { mutableFloatStateOf(0f) }

    BackHandler {
        backPressCount += 1
        when (backPressCount) {
            1 -> toastMessage = "Press back 2 more times to exit"
            2 -> toastMessage = "Press back 1 more time to exit"
            else -> onExit()
        }
    }

    // Auto-dismiss toast after 2.5 s and reset back-press counter.
    LaunchedEffect(backPressCount, toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2500L)
            toastMessage = null
            backPressCount = 0
        }
    }

    val themeColors = remember(settings.playTheme) { themeColorPalette(settings.playTheme) }
    val currentThemeColors by rememberUpdatedState(themeColors)
    val currentReducedMotion by rememberUpdatedState(settings.reducedMotion)

    val baseScale = remember(settings.effectsIntensity, playState.stimulusLevel, playState.isGuardActive) {
        val intensityMult = when (settings.effectsIntensity) {
            EffectsIntensity.LOW -> 0.6f
            EffectsIntensity.MEDIUM -> 1.0f
            EffectsIntensity.HIGH -> 1.5f
        }
        val adaptiveMult = if (settings.adaptivePlayEnabled) {
            0.6f + playState.stimulusLevel * 0.8f
        } else 1.0f
        val guardMult = if (playState.isGuardActive) 0.6f else 1.0f
        (intensityMult * adaptiveMult * guardMult).coerceIn(0.2f, 2.5f)
    }
    val currentBaseScale by rememberUpdatedState(baseScale)

    val vibrancy = when (settings.effectsIntensity) {
        EffectsIntensity.LOW -> 0.85f
        EffectsIntensity.MEDIUM -> 1.0f
        EffectsIntensity.HIGH -> 1.15f
    }

    val phaseSpeed = when (settings.effectsIntensity) {
        EffectsIntensity.LOW -> 0.004f
        EffectsIntensity.MEDIUM -> 0.007f
        EffectsIntensity.HIGH -> 0.012f
    }

    val fullEmojiMode = settings.fullEmojiMode

    // Animation loop: advances effect ages, moves targets, spawns new ones.
    LaunchedEffect(screenSize) {
        if (screenSize == IntSize.Zero) return@LaunchedEffect
        val width = screenSize.width.toFloat()
        val height = screenSize.height.toFloat()
        val rng = Random

        var lastFrameTime = withFrameMillis { it }
        var nextTargetId = 0L

        while (true) {
            val currentFrameTime = withFrameMillis { it }
            val dt = (currentFrameTime - lastFrameTime).coerceIn(0L, 100L)
            lastFrameTime = currentFrameTime
            val deltaMs = if (dt > 0) dt.toFloat() else 16f

            val elapsedSec = (currentFrameTime - sessionStart) / 1000f
            val difficulty = (0.4f + elapsedSec / 60f).coerceAtMost(1.6f)

            // Advance background phase.
            if (!settings.reducedMotion) {
                bgPhase = (bgPhase + phaseSpeed * (deltaMs / 1000f) * difficulty) % 1f
            }

            // Advance and expire effects.
            effects.removeAll { it.age >= 1f }
            val esize = effects.size
            for (i in 0 until esize) {
                if (i >= effects.size) break
                val e = effects[i]
                effects[i] = e.copy(age = (e.age + deltaMs / e.maxAge).coerceAtMost(1f))
            }

            // Move floating targets.
            val speedMul = 0.05f * difficulty
            val tsize = targets.size
            for (i in 0 until tsize) {
                if (i >= targets.size) break
                val t = targets[i]
                var nx = t.x + t.vx * deltaMs * speedMul
                var ny = t.y + t.vy * deltaMs * speedMul
                t.spin += t.spinSpeed * deltaMs * 0.001f

                // After 60 s, targets occasionally change direction.
                if (elapsedSec > 60f) {
                    t.dirChangeTimer -= deltaMs
                    if (t.dirChangeTimer <= 0f) {
                        val speed = hypot(t.vx.toDouble(), t.vy.toDouble()).toFloat()
                        val newAngle = rng.nextFloat() * 2f * PI.toFloat()
                        t.vx = cos(newAngle) * speed
                        t.vy = sin(newAngle) * speed
                        t.dirChangeTimer = rng.nextFloat() * 4000f + 2000f
                    }
                }

                // Remove target if it fully exits the screen.
                if (nx < -t.radius * 3f || nx > width + t.radius * 3f ||
                    ny < -t.radius * 3f || ny > height + t.radius * 3f
                ) {
                    targets.removeAt(i)
                    // After removal indices shift; the outer loop will handle it
                    // safely because we guard with `if (i >= targets.size) break`.
                    break
                } else {
                    t.x = nx
                    t.y = ny
                }
            }

            // Spawn targets from edges after the first 5 s.
            if (elapsedSec >= 5f) {
                val desiredCount = (3 + (difficulty * 5).toInt()).coerceIn(3, 12)
                if (targets.size < desiredCount) {
                    targets.add(
                        spawnFromEdge(
                            nextTargetId++, width, height,
                            currentThemeColors, difficulty, fullEmojiMode, rng
                        )
                    )
                }
            }

            // Cap total effects.
            while (effects.size > 250) effects.removeAt(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords -> screenSize = coords.size }
            .pointerInput(Unit) {
                val lastPositions = mutableMapOf<Long, Offset>()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val activeCount = changes.count { it.pressed }
                        var anyNewPress = false

                        changes.forEach { change ->
                            val id = change.id.value
                            val pos = change.position
                            val wasPressed = change.previousPressed
                            val nowPressed = change.pressed

                            if (nowPressed && !wasPressed) {
                                anyNewPress = true
                                lastPositions[id] = pos

                                val hitIdx = targets.indexOfLast { t ->
                                    hypot((t.x - pos.x).toDouble(), (t.y - pos.y).toDouble())
                                        .toFloat() <= t.radius + 20f
                                }
                                if (hitIdx >= 0) {
                                    val hit = targets.removeAt(hitIdx)
                                    score++
                                    spawnTargetBurst(
                                        effects, hit,
                                        currentThemeColors, currentBaseScale, currentReducedMotion
                                    )
                                    viewModel.onTargetHit()
                                } else {
                                    spawnTapBurst(
                                        effects, pos.x, pos.y,
                                        currentThemeColors, currentBaseScale, currentReducedMotion
                                    )
                                    val evtType = when {
                                        activeCount >= 4 -> TouchEventType.PalmLikeBurst(pos.x, pos.y)
                                        activeCount >= 3 -> TouchEventType.MultiTouchBurst(pos.x, pos.y, activeCount)
                                        activeCount == 2 -> TouchEventType.TwoFingerTap(pos.x, pos.y, pos.x, pos.y)
                                        else -> TouchEventType.SingleTap(pos.x, pos.y)
                                    }
                                    viewModel.onTouchEvent(evtType, activeCount)
                                }

                            } else if (nowPressed && wasPressed) {
                                val last = lastPositions[id]
                                if (last != null) {
                                    val dist = hypot(
                                        (pos.x - last.x).toDouble(),
                                        (pos.y - last.y).toDouble()
                                    ).toFloat()
                                    if (dist > 18f) {
                                        lastPositions[id] = pos
                                        spawnTrail(
                                            effects, pos.x, pos.y,
                                            currentThemeColors, currentBaseScale
                                        )
                                        viewModel.onTouchEvent(
                                            TouchEventType.SingleDrag(pos.x, pos.y),
                                            activeCount
                                        )
                                        // Opportunistic target hit-check on drag.
                                        val hitIdx = targets.indexOfLast { t ->
                                            hypot((t.x - pos.x).toDouble(), (t.y - pos.y).toDouble())
                                                .toFloat() <= t.radius + 20f
                                        }
                                        if (hitIdx >= 0) {
                                            val hit = targets.removeAt(hitIdx)
                                            score++
                                            spawnTargetBurst(
                                                effects, hit,
                                                currentThemeColors, currentBaseScale, currentReducedMotion
                                            )
                                            viewModel.onTargetHit()
                                        }
                                    }
                                }

                            } else if (!nowPressed && wasPressed) {
                                lastPositions.remove(id)
                            }

                            change.consume()
                        }

                        // Extra ripple when 3+ fingers press simultaneously.
                        if (anyNewPress && activeCount >= 3) {
                            val cx = changes.filter { it.pressed }.map { it.position.x }.average().toFloat()
                            val cy = changes.filter { it.pressed }.map { it.position.y }.average().toFloat()
                            spawnRipple(effects, cx, cy, currentThemeColors, currentBaseScale)
                        }
                    }
                }
            }
    ) {
        // Score display — always on top.
        Text(
            text = "⭐ $score",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp),
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = Offset(2f, 2f),
                    blurRadius = 4f
                ),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            with(ThemeBackground) {
                drawThemeBackground(
                    theme = settings.playTheme,
                    phase = bgPhase,
                    elapsedSec = ((System.currentTimeMillis() - sessionStart) / 1000f),
                    reducedMotion = settings.reducedMotion,
                    vibrancy = vibrancy
                )
            }
            targets.forEach { drawFloatingTarget(it) }
            effects.forEach { drawEffect(it, settings.reducedMotion) }
        }

        // Guard overlay — subtle dimming.
        if (playState.isGuardActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        Modifier.pointerInput(Unit) {} // absorb touches silently
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color.Black.copy(alpha = 0.15f), size = size)
                }
            }
        }

        // Back-press toast.
        val msg = toastMessage
        if (msg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp, start = 32.dp, end = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
            ) {
                Text(
                    text = msg,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(1f, 1f),
                            blurRadius = 6f
                        ),
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}

// ── Target spawning ───────────────────────────────────────────────────────────

/**
 * Spawn a new target from a random screen edge, moving toward the interior.
 */
private fun spawnFromEdge(
    id: Long,
    width: Float,
    height: Float,
    colors: List<Color>,
    difficulty: Float,
    fullEmojiMode: Boolean,
    rng: Random
): FloatingTarget {
    val radius = 32f + rng.nextFloat() * 28f
    val baseSpeed = 0.8f + rng.nextFloat() * 1.4f
    val speed = baseSpeed * difficulty.coerceAtLeast(0.5f)

    // Pick a random edge: 0=left, 1=right, 2=top, 3=bottom.
    val edge = rng.nextInt(4)
    val (startX, startY) = when (edge) {
        0 -> Pair(-radius * 2f, rng.nextFloat() * height)
        1 -> Pair(width + radius * 2f, rng.nextFloat() * height)
        2 -> Pair(rng.nextFloat() * width, -radius * 2f)
        else -> Pair(rng.nextFloat() * width, height + radius * 2f)
    }

    // Inward angle toward the opposite half of the screen, with ±30° jitter.
    val baseAngle = when (edge) {
        0 -> 0f                         // left edge → moving right
        1 -> PI.toFloat()               // right edge → moving left
        2 -> PI.toFloat() / 2f          // top edge → moving down
        else -> -PI.toFloat() / 2f      // bottom edge → moving up
    }
    val jitter = (rng.nextFloat() - 0.5f) * (PI.toFloat() / 3f) // ±30°
    val angle = baseAngle + jitter

    val color = colors[rng.nextInt(colors.size)]
    val emoji = EMOJI_POOL[rng.nextInt(EMOJI_POOL.size)]

    val kind = if (fullEmojiMode) {
        TargetKind.EMOJI
    } else {
        // Mix: roughly half emoji, half geometry.
        val kinds = TargetKind.values()
        kinds[rng.nextInt(kinds.size)]
    }

    return FloatingTarget(
        id = id,
        x = startX,
        y = startY,
        vx = cos(angle) * speed,
        vy = sin(angle) * speed,
        radius = radius,
        color = color,
        kind = kind,
        emoji = emoji,
        spin = rng.nextFloat() * 2f * PI.toFloat(),
        spinSpeed = (rng.nextFloat() - 0.5f) * 2f,
        dirChangeTimer = rng.nextFloat() * 4000f + 2000f
    )
}

// ── Effect spawning ───────────────────────────────────────────────────────────

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
    // Extra star-burst spokes for big hit feedback.
    if (!reducedMotion) {
        repeat(8) { i ->
            val angle = (i.toFloat() / 8f) * 2f * PI.toFloat()
            effects.add(
                Effect(
                    x = hit.x, y = hit.y,
                    age = 0f, maxAge = 800f,
                    color = hit.color,
                    type = EffectType.STAR_BURST,
                    scale = baseScale * 1.5f,
                    velocityX = cos(angle) * 6f,
                    velocityY = sin(angle) * 6f,
                    angle = angle
                )
            )
        }
    }
}

// ── DrawScope extensions ──────────────────────────────────────────────────────

private fun DrawScope.drawFloatingTarget(t: FloatingTarget) {
    val c = t.color
    if (t.kind == TargetKind.EMOJI) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                textSize = t.radius * 2.2f
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(t.emoji, t.x, t.y + t.radius * 0.75f, paint)
        }
        return
    }

    when (t.kind) {
        TargetKind.CIRCLE -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c, c.copy(alpha = 0.4f)),
                    center = Offset(t.x, t.y),
                    radius = t.radius
                ),
                radius = t.radius,
                center = Offset(t.x, t.y)
            )
            // Specular highlight.
            drawCircle(
                color = Color.White.copy(alpha = 0.7f),
                radius = t.radius * 0.25f,
                center = Offset(t.x - t.radius * 0.3f, t.y - t.radius * 0.3f)
            )
        }
        TargetKind.STAR -> {
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
        TargetKind.SQUARE -> {
            val s = t.radius * 1.6f
            drawRect(
                color = c,
                topLeft = Offset(t.x - s / 2f, t.y - s / 2f),
                size = Size(s, s)
            )
        }
        TargetKind.TRIANGLE -> {
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
        TargetKind.HEART -> {
            val r = t.radius
            val cx = t.x
            val cy = t.y
            val path = Path()
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
        TargetKind.DIAMOND -> {
            val r = t.radius
            val path = Path()
            path.moveTo(t.x, t.y - r)
            path.lineTo(t.x + r * 0.8f, t.y)
            path.lineTo(t.x, t.y + r)
            path.lineTo(t.x - r * 0.8f, t.y)
            path.close()
            drawPath(path = path, color = c)
        }
        TargetKind.EMOJI -> { /* handled above */ }
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
            val safeR = r.coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(px, py),
                    radius = safeR
                ),
                radius = safeR,
                center = Offset(px, py)
            )
        }
        EffectType.RAINBOW_TRAIL -> {
            val r = effect.scale * 14f * (1f - progress * 0.5f)
            drawCircle(color = color, radius = r.coerceAtLeast(0.5f), center = Offset(px, py))
        }
        EffectType.CONFETTI -> {
            val sz = effect.scale * 14f * (1f - progress * 0.2f)
            if (!reducedMotion) {
                drawRect(
                    color = color,
                    topLeft = Offset(px - sz / 2f, py - sz / 2f),
                    size = Size(sz, sz)
                )
            } else {
                drawCircle(color = color, radius = (sz / 2f).coerceAtLeast(0.5f), center = Offset(px, py))
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

/**
 * Returns the effect color palette for the given theme.
 * These are used for spawning burst/trail/ripple effects — not the background
 * (which ThemeBackground handles entirely).
 */
private fun themeColorPalette(theme: PlayTheme): List<Color> = when (theme) {
    PlayTheme.SPACE -> listOf(
        Color(0xFF7C4DFF), Color(0xFF40C4FF), Color(0xFFE040FB),
        Color(0xFFFFFF00), Color(0xFF69F0AE), Color(0xFFFF80AB)
    )
    PlayTheme.OCEAN -> listOf(
        Color(0xFF00B0FF), Color(0xFF00E5FF), Color(0xFF80DEEA),
        Color(0xFF1DE9B6), Color(0xFFB2EBF2), Color(0xFF40C4FF)
    )
    PlayTheme.RAINBOW -> listOf(
        Color(0xFFFF5252), Color(0xFFFF6D00), Color(0xFFFFD740),
        Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB)
    )
    PlayTheme.SHAPES -> listOf(
        Color(0xFFFF80AB), Color(0xFF82B1FF), Color(0xFFB9F6CA),
        Color(0xFFFFD180), Color(0xFFEA80FC), Color(0xFF84FFFF)
    )
}
