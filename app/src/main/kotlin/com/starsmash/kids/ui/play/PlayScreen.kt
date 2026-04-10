package com.starsmash.kids.ui.play

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.starsmash.kids.MainActivity
import com.starsmash.kids.settings.EffectsIntensity
import com.starsmash.kids.settings.HighScoreStore
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.settings.SmashCategory
import com.starsmash.kids.settings.StartingDifficulty
import com.starsmash.kids.settings.TrailLength
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
    var dirChangeTimer: Float,
    // Sinusoidal wobble parameters (targets spawned at 50-99 stars)
    val wobbleAmplitude: Float = 0f,
    val wobbleFrequency: Float = 0f,
    var wobblePhase: Float = 0f,
    // Bezier curve parameters (targets spawned at 100+ stars)
    val bezierP0x: Float = 0f,
    val bezierP0y: Float = 0f,
    val bezierP1x: Float = 0f,
    val bezierP1y: Float = 0f,
    val bezierP2x: Float = 0f,
    val bezierP2y: Float = 0f,
    val bezierP3x: Float = 0f,
    val bezierP3y: Float = 0f,
    var bezierT: Float = 0f,
    var bezierDuration: Float = 0f
)

// Categorised emoji pools. The child's selected set of [SmashCategory] is
// unioned at runtime into a single pool to sample from.
private val CATEGORY_EMOJI: Map<SmashCategory, List<String>> = mapOf(
    SmashCategory.EMOJI to listOf("⭐", "🌟", "💫", "🎈", "🎀", "🎠", "🌈", "🏆", "🎂", "🎁"),
    SmashCategory.DINOSAURS to listOf("🦖", "🦕", "🐊", "🦎"),
    SmashCategory.TRUCKS to listOf("🚒", "🚚", "🚜", "🚛", "🚗", "🚕", "🏎️", "🚓", "🚌"),
    SmashCategory.ANIMALS to listOf("🐶", "🐱", "🐘", "🐵", "🐻", "🦁", "🐙", "🐬", "🦄", "🐰", "🐼", "🦊", "🐸"),
    SmashCategory.TOYS to listOf("🧸", "🪀", "🪁", "🎮", "🪅", "🎨", "🎲", "🧩"),
    SmashCategory.FOOD to listOf("🍎", "🍌", "🍓", "🍕", "🍩", "🍪", "🍦", "🧁", "🍭"),
    SmashCategory.SPACE to listOf("🚀", "🛸", "🪐", "🌙", "☄️", "👾", "🌠"),
    // SHAPES is handled via the geometric TargetKind path, not emoji.
    SmashCategory.SHAPES to emptyList()
)

/** Build the active emoji pool for a settings snapshot. Falls back to EMOJI
 *  if the user unselected everything that produces emoji targets. */
private fun buildEmojiPool(categories: Set<SmashCategory>): List<String> {
    val pool = categories.flatMap { CATEGORY_EMOJI[it] ?: emptyList() }
    return if (pool.isEmpty()) CATEGORY_EMOJI[SmashCategory.EMOJI]!! else pool
}

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

    // Pause music when the app goes to background / screen locks, resume on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.audioEngine.pauseMusic()
                Lifecycle.Event.ON_RESUME -> viewModel.audioEngine.resumeMusic()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settings = playState.settings

    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val effects = remember { mutableStateListOf<Effect>() }
    val targets = remember { mutableStateListOf<FloatingTarget>() }
    var score by remember { mutableIntStateOf(0) }
    var backPressCount by remember { mutableIntStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    // Elapsed seconds since play started, updated once per frame from withFrameMillis.
    // Used as the single source of truth for both gameplay and background timing.
    var elapsedSecState by remember { mutableFloatStateOf(0f) }
    // Exit dialog: shown on third back press, lets the user save a high score.
    var showExitDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Toddler lock: suppress volume keys and start screen pinning during gameplay.
    val activity = context as? MainActivity
    DisposableEffect(activity) {
        activity?.isInPlayMode = true
        activity?.startScreenPinning()
        onDispose {
            activity?.isInPlayMode = false
            activity?.stopScreenPinning()
        }
    }

    BackHandler {
        backPressCount += 1
        when (backPressCount) {
            1 -> toastMessage = "Press back 2 more times to exit"
            2 -> toastMessage = "Press back 1 more time to exit"
            else -> {
                // Open the exit dialog instead of calling onExit() directly.
                showExitDialog = true
            }
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

    // Background animation speed multiplier. Low = slow drift, High = noticeably
    // faster but still smooth.
    val animationSpeed = when (settings.effectsIntensity) {
        EffectsIntensity.LOW -> 0.7f
        EffectsIntensity.MEDIUM -> 1.0f
        EffectsIntensity.HIGH -> 1.6f
    }

    // Trail length multiplier - how long drag trails persist on screen.
    // When reducedMotion is on, every trail is further cut to 25%.
    val trailLifeMulBase = when (settings.trailLength) {
        TrailLength.SHORT -> 0.5f
        TrailLength.MEDIUM -> 1.0f
        TrailLength.LONG -> 2.2f
    }
    val trailLifeMul = trailLifeMulBase * if (settings.reducedMotion) 0.25f else 1f
    val currentTrailLifeMul by rememberUpdatedState(trailLifeMul)

    // Intensity also scales burst travel distance and lifetime.
    val effectLifeMul = when (settings.effectsIntensity) {
        EffectsIntensity.LOW -> 0.75f
        EffectsIntensity.MEDIUM -> 1.0f
        EffectsIntensity.HIGH -> 1.4f
    }
    val currentEffectLifeMul by rememberUpdatedState(effectLifeMul)

    val trailSoundEnabled = settings.trailSoundEnabled

    // Build the current emoji pool and geometric-shape flag from the user's
    // smash category selection. Recomputed whenever the set changes.
    val smashCategories = settings.smashCategories
    val emojiPool = remember(smashCategories) { buildEmojiPool(smashCategories) }
    val currentEmojiPool by rememberUpdatedState(emojiPool)
    val shapesAllowed = SmashCategory.SHAPES in smashCategories
    val currentShapesAllowed by rememberUpdatedState(shapesAllowed)

    // Starting difficulty affects how soon targets appear and the initial
    // difficulty floor. GENTLE keeps the original 5s wait; FAST drops to 1s
    // and starts difficulty already partway up the ramp.
    val startDelaySec = when (settings.startingDifficulty) {
        StartingDifficulty.GENTLE -> 5f
        StartingDifficulty.MEDIUM -> 2.5f
        StartingDifficulty.FAST -> 1f
    }
    val difficultyStart = when (settings.startingDifficulty) {
        StartingDifficulty.GENTLE -> 0.4f
        StartingDifficulty.MEDIUM -> 0.75f
        StartingDifficulty.FAST -> 1.0f
    }

    // Animation loop: advances effect ages, moves targets, spawns new ones.
    // CRITICAL: elapsed time must be relative to a frame-time baseline captured
    // inside the loop. Mixing System.currentTimeMillis() (wallclock) with
    // withFrameMillis() (uptime since boot) produces nonsense and breaks the
    // >= 5s target-spawn gate.
    LaunchedEffect(screenSize) {
        if (screenSize == IntSize.Zero) return@LaunchedEffect
        val width = screenSize.width.toFloat()
        val height = screenSize.height.toFloat()
        val rng = Random

        val firstFrameTime = withFrameMillis { it }
        var lastFrameTime = firstFrameTime
        var nextTargetId = 0L
        var lastMusicSpeed = 1.0f

        while (true) {
            val currentFrameTime = withFrameMillis { it }
            val dt = (currentFrameTime - lastFrameTime).coerceIn(0L, 100L)
            lastFrameTime = currentFrameTime
            val deltaMs = if (dt > 0) dt.toFloat() else 16f

            val elapsedSec = (currentFrameTime - firstFrameTime) / 1000f
            elapsedSecState = elapsedSec

            // Speed multiplier driven by star count (score).
            // 0-49 stars = 1x, 50-99 = 2x, 100-149 = 3x, 150+ = 4x.
            val speedMultiplier = (score / 50 + 1).coerceAtMost(4)

            // Music playback rate scales linearly from 1.0x at 0 stars
            // to 2.0x at 150 stars, capped at 2.0x.
            val targetMusicSpeed = (1.0f + (score / 150.0f)).coerceAtMost(2.0f)
            if (kotlin.math.abs(targetMusicSpeed - lastMusicSpeed) > 0.01f) {
                viewModel.setMusicSpeed(targetMusicSpeed)
                lastMusicSpeed = targetMusicSpeed
            }

            // Advance and expire effects.
            effects.removeAll { it.age >= 1f }
            val esize = effects.size
            for (i in 0 until esize) {
                if (i >= effects.size) break
                val e = effects[i]
                effects[i] = e.copy(age = (e.age + deltaMs / e.maxAge).coerceAtMost(1f))
            }

            // Move floating targets. Speed driven by star-count multiplier.
            val speedMul = 0.05f * speedMultiplier
            val tsize = targets.size
            for (i in 0 until tsize) {
                if (i >= targets.size) break
                val t = targets[i]
                t.spin += t.spinSpeed * deltaMs * 0.001f

                if (t.bezierDuration > 0f) {
                    // ── Bezier curve movement (spawned at 100+ stars) ──
                    t.bezierT += (deltaMs / t.bezierDuration) * speedMultiplier
                    if (t.bezierT >= 1f) {
                        targets.removeAt(i)
                        break
                    }
                    val bt = t.bezierT
                    val u = 1f - bt
                    t.x = u*u*u*t.bezierP0x + 3*u*u*bt*t.bezierP1x + 3*u*bt*bt*t.bezierP2x + bt*bt*bt*t.bezierP3x
                    t.y = u*u*u*t.bezierP0y + 3*u*u*bt*t.bezierP1y + 3*u*bt*bt*t.bezierP2y + bt*bt*bt*t.bezierP3y
                } else {
                    // ── Straight-line or wobble movement ──
                    var nx = t.x + t.vx * deltaMs * speedMul
                    var ny = t.y + t.vy * deltaMs * speedMul

                    // Sinusoidal wobble perpendicular to travel (spawned at 50-99 stars)
                    if (t.wobbleAmplitude > 0f) {
                        val spd = hypot(t.vx.toDouble(), t.vy.toDouble()).toFloat()
                        if (spd > 0.001f) {
                            val perpX = -t.vy / spd
                            val perpY = t.vx / spd
                            val prevPhase = t.wobblePhase
                            t.wobblePhase += deltaMs * 0.001f * t.wobbleFrequency
                            val deltaWobble = t.wobbleAmplitude * (sin(t.wobblePhase) - sin(prevPhase))
                            nx += perpX * deltaWobble
                            ny += perpY * deltaWobble
                        }
                    }

                    // Occasional direction changes for straight-line targets only
                    if (t.wobbleAmplitude == 0f && elapsedSec > 60f) {
                        t.dirChangeTimer -= deltaMs
                        if (t.dirChangeTimer <= 0f) {
                            val spd = hypot(t.vx.toDouble(), t.vy.toDouble()).toFloat()
                            val newAngle = rng.nextFloat() * 2f * PI.toFloat()
                            t.vx = cos(newAngle) * spd
                            t.vy = sin(newAngle) * spd
                            t.dirChangeTimer = rng.nextFloat() * 4000f + 2000f
                        }
                    }

                    // Remove if fully off-screen.
                    if (nx < -t.radius * 3f || nx > width + t.radius * 3f ||
                        ny < -t.radius * 3f || ny > height + t.radius * 3f
                    ) {
                        targets.removeAt(i)
                        break
                    } else {
                        t.x = nx
                        t.y = ny
                    }
                }
            }

            // Shape-on-shape collision: only when SHAPES category is selected
            // AND star count is 150+. Shapes bounce off each other elastically.
            // No collision for animals, trucks, toys, or any non-shape category.
            if (currentShapesAllowed && score >= 150) {
                for (i in 0 until targets.size) {
                    val a = targets[i]
                    if (a.kind == TargetKind.EMOJI) continue
                    for (j in i + 1 until targets.size) {
                        val b = targets[j]
                        if (b.kind == TargetKind.EMOJI) continue
                        val cdx = b.x - a.x
                        val cdy = b.y - a.y
                        val dist = hypot(cdx.toDouble(), cdy.toDouble()).toFloat()
                        val minDist = a.radius + b.radius
                        if (dist < minDist && dist > 0.001f) {
                            val cnx = cdx / dist
                            val cny = cdy / dist
                            // If on bezier, convert to straight-line with tangent velocity
                            if (a.bezierDuration > 0f) {
                                val bt = a.bezierT; val au = 1f - bt
                                val tx = 3*au*au*(a.bezierP1x-a.bezierP0x) + 6*au*bt*(a.bezierP2x-a.bezierP1x) + 3*bt*bt*(a.bezierP3x-a.bezierP2x)
                                val ty = 3*au*au*(a.bezierP1y-a.bezierP0y) + 6*au*bt*(a.bezierP2y-a.bezierP1y) + 3*bt*bt*(a.bezierP3y-a.bezierP2y)
                                val tLen = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(0.001f)
                                a.vx = tx / tLen * 1.5f; a.vy = ty / tLen * 1.5f
                                a.bezierDuration = 0f
                            }
                            if (b.bezierDuration > 0f) {
                                val bt = b.bezierT; val bu = 1f - bt
                                val tx = 3*bu*bu*(b.bezierP1x-b.bezierP0x) + 6*bu*bt*(b.bezierP2x-b.bezierP1x) + 3*bt*bt*(b.bezierP3x-b.bezierP2x)
                                val ty = 3*bu*bu*(b.bezierP1y-b.bezierP0y) + 6*bu*bt*(b.bezierP2y-b.bezierP1y) + 3*bt*bt*(b.bezierP3y-b.bezierP2y)
                                val tLen = hypot(tx.toDouble(), ty.toDouble()).toFloat().coerceAtLeast(0.001f)
                                b.vx = tx / tLen * 1.5f; b.vy = ty / tLen * 1.5f
                                b.bezierDuration = 0f
                            }
                            // Elastic collision response along collision normal
                            val relVelDotN = (b.vx - a.vx) * cnx + (b.vy - a.vy) * cny
                            if (relVelDotN < 0f) {
                                a.vx -= relVelDotN * cnx
                                a.vy -= relVelDotN * cny
                                b.vx += relVelDotN * cnx
                                b.vy += relVelDotN * cny
                            }
                            // Separate overlapping shapes
                            val overlap = minDist - dist
                            a.x -= cnx * overlap * 0.5f
                            a.y -= cny * overlap * 0.5f
                            b.x += cnx * overlap * 0.5f
                            b.y += cny * overlap * 0.5f
                        }
                    }
                }
            }

            // Spawn targets from edges after the configured start delay.
            if (elapsedSec >= startDelaySec) {
                val desiredCount = (3 + speedMultiplier * 3).coerceIn(3, 15)
                if (targets.size < desiredCount) {
                    targets.add(
                        spawnFromEdge(
                            nextTargetId++, width, height,
                            currentThemeColors,
                            currentEmojiPool, currentShapesAllowed,
                            score, rng
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

                                // Widen hit radius during multi-touch since
                                // finger precision drops with 2+ fingers.
                                val hitSlop = if (activeCount >= 2) 48f else 20f
                                val hitIdx = targets.indexOfLast { t ->
                                    hypot((t.x - pos.x).toDouble(), (t.y - pos.y).toDouble())
                                        .toFloat() <= t.radius + hitSlop
                                }
                                if (hitIdx >= 0) {
                                    val hit = targets.removeAt(hitIdx)
                                    score++
                                    spawnTargetBurst(
                                        effects, hit,
                                        currentThemeColors, currentBaseScale,
                                        currentReducedMotion, currentEffectLifeMul
                                    )
                                    viewModel.onTargetHit()
                                } else {
                                    spawnTapBurst(
                                        effects, pos.x, pos.y,
                                        currentThemeColors, currentBaseScale,
                                        currentReducedMotion, currentEffectLifeMul
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
                                            currentThemeColors, currentBaseScale,
                                            currentTrailLifeMul
                                        )
                                        // Only play drag sounds if trail sound is enabled.
                                        if (trailSoundEnabled) {
                                            viewModel.onTouchEvent(
                                                TouchEventType.SingleDrag(pos.x, pos.y),
                                                activeCount
                                            )
                                        }
                                        // Opportunistic target hit-check on drag.
                                        val dragHitSlop = if (activeCount >= 2) 48f else 20f
                                        val hitIdx = targets.indexOfLast { t ->
                                            hypot((t.x - pos.x).toDouble(), (t.y - pos.y).toDouble())
                                                .toFloat() <= t.radius + dragHitSlop
                                        }
                                        if (hitIdx >= 0) {
                                            val hit = targets.removeAt(hitIdx)
                                            score++
                                            spawnTargetBurst(
                                                effects, hit,
                                                currentThemeColors, currentBaseScale,
                                                currentReducedMotion, currentEffectLifeMul
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
                            spawnRipple(effects, cx, cy, currentThemeColors, currentBaseScale, currentEffectLifeMul)
                        }
                    }
                }
            }
    ) {
        // Canvas renders FIRST so the score Text draws on top of it.
        Canvas(modifier = Modifier.fillMaxSize()) {
            with(ThemeBackground) {
                drawThemeBackground(
                    theme = settings.playTheme,
                    timeSec = elapsedSecState,
                    reducedMotion = settings.reducedMotion,
                    vibrancy = vibrancy,
                    animationSpeed = animationSpeed
                )
            }
            targets.forEach { drawFloatingTarget(it) }
            effects.forEach { drawEffect(it, settings.reducedMotion) }
        }

        // Score display — rendered AFTER the canvas so it's always visible on top.
        Text(
            text = "⭐ $score",
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 36.dp),
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.7f),
                    offset = Offset(2f, 2f),
                    blurRadius = 6f
                ),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        )

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
            // Subtle banner telling the parent (not the kid) that the guard
            // has kicked in. Fades in at the top-right, out of the way of
            // gameplay.
            Text(
                text = "● calming things down",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp),
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(1f, 1f),
                        blurRadius = 3f
                    ),
                    fontSize = 13.sp
                )
            )
        } else if (settings.adaptivePlayEnabled && playState.stimulusLevel > 0.8f) {
            // Adaptive engine is dialling things UP - equally subtle banner.
            Text(
                text = "● adapting",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp),
                style = TextStyle(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(1f, 1f),
                        blurRadius = 3f
                    ),
                    fontSize = 13.sp
                )
            )
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

    if (showExitDialog) {
        ExitScoreDialog(
            score = score,
            isHighScore = remember(score) { HighScoreStore.isHighScore(context, score) },
            onSave = { name ->
                HighScoreStore.save(context, name, score)
                showExitDialog = false
                onExit()
            },
            onSkip = {
                showExitDialog = false
                onExit()
            }
        )
    }
}

// ── Exit / high score dialog ──────────────────────────────────────────────────

/**
 * Post-game dialog. If the score is a top-10 high score the user is offered a
 * text field to enter their name (up to 20 characters); otherwise the dialog
 * just shows the final score and offers to go back to the menu.
 */
@Composable
private fun ExitScoreDialog(
    score: Int,
    isHighScore: Boolean,
    onSave: (String) -> Unit,
    onSkip: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onSkip,
        title = {
            androidx.compose.material3.Text(
                text = if (isHighScore) "New High Score!" else "Great game!",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    text = "You smashed ⭐ $score targets.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
                if (isHighScore) {
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.height(12.dp)
                    )
                    androidx.compose.material3.Text(
                        text = "Enter a name to save to the leaderboard:",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.height(8.dp)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = {
                            if (it.length <= HighScoreStore.MAX_NAME_LENGTH) name = it
                        },
                        singleLine = true,
                        placeholder = { androidx.compose.material3.Text("Your name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (isHighScore) {
                androidx.compose.material3.TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onSave(name.trim()) }
                ) {
                    androidx.compose.material3.Text("Save")
                }
            } else {
                androidx.compose.material3.TextButton(onClick = onSkip) {
                    androidx.compose.material3.Text("Back to menu")
                }
            }
        },
        dismissButton = if (isHighScore) {
            {
                androidx.compose.material3.TextButton(onClick = onSkip) {
                    androidx.compose.material3.Text("Skip")
                }
            }
        } else null
    )
}

// ── Target spawning ───────────────────────────────────────────────────────────

/**
 * Spawn a new target from a random screen edge, moving toward the interior.
 * The [emojiPool] is sampled from when an emoji-typed target is picked, and
 * [shapesAllowed] controls whether non-emoji geometric targets can also appear.
 */
private fun spawnFromEdge(
    id: Long,
    width: Float,
    height: Float,
    colors: List<Color>,
    emojiPool: List<String>,
    shapesAllowed: Boolean,
    starCount: Int,
    rng: Random
): FloatingTarget {
    val radius = 32f + rng.nextFloat() * 28f
    val baseSpeed = 0.8f + rng.nextFloat() * 1.4f
    val speed = baseSpeed // speedMultiplier is applied per-frame in the animation loop

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
    val emoji = emojiPool[rng.nextInt(emojiPool.size)]

    // Kind selection:
    //  - If the user enabled SHAPES, roughly 50/50 mix with emoji.
    //  - Otherwise always emoji.
    val kind = if (shapesAllowed && rng.nextFloat() < 0.5f) {
        val geometric = arrayOf(
            TargetKind.CIRCLE, TargetKind.STAR, TargetKind.SQUARE,
            TargetKind.TRIANGLE, TargetKind.HEART, TargetKind.DIAMOND
        )
        geometric[rng.nextInt(geometric.size)]
    } else {
        TargetKind.EMOJI
    }

    val vx = cos(angle) * speed
    val vy = sin(angle) * speed
    val spinStart = rng.nextFloat() * 2f * PI.toFloat()
    val spinSpd = (rng.nextFloat() - 0.5f) * 2f
    val dirTimer = rng.nextFloat() * 4000f + 2000f

    // Movement pattern based on current star count at spawn time.
    return if (starCount >= 100) {
        // Bezier curve: smooth arc from entry edge across the screen.
        val endEdge = (edge + 1 + rng.nextInt(2)) % 4
        val (endX, endY) = when (endEdge) {
            0 -> Pair(-radius * 2f, rng.nextFloat() * height)
            1 -> Pair(width + radius * 2f, rng.nextFloat() * height)
            2 -> Pair(rng.nextFloat() * width, -radius * 2f)
            else -> Pair(rng.nextFloat() * width, height + radius * 2f)
        }
        val cp1x = width * (0.15f + rng.nextFloat() * 0.7f)
        val cp1y = height * (0.15f + rng.nextFloat() * 0.7f)
        val cp2x = width * (0.15f + rng.nextFloat() * 0.7f)
        val cp2y = height * (0.15f + rng.nextFloat() * 0.7f)
        val duration = 4000f + rng.nextFloat() * 4000f
        FloatingTarget(
            id = id, x = startX, y = startY, vx = vx, vy = vy,
            radius = radius, color = color, kind = kind, emoji = emoji,
            spin = spinStart, spinSpeed = spinSpd, dirChangeTimer = dirTimer,
            bezierP0x = startX, bezierP0y = startY,
            bezierP1x = cp1x, bezierP1y = cp1y,
            bezierP2x = cp2x, bezierP2y = cp2y,
            bezierP3x = endX, bezierP3y = endY,
            bezierT = 0f, bezierDuration = duration
        )
    } else if (starCount >= 50) {
        // Sinusoidal wobble added to straight-line path.
        FloatingTarget(
            id = id, x = startX, y = startY, vx = vx, vy = vy,
            radius = radius, color = color, kind = kind, emoji = emoji,
            spin = spinStart, spinSpeed = spinSpd, dirChangeTimer = dirTimer,
            wobbleAmplitude = 30f + rng.nextFloat() * 40f,
            wobbleFrequency = 4f + rng.nextFloat() * 6f,
            wobblePhase = rng.nextFloat() * 2f * PI.toFloat()
        )
    } else {
        // Straight-line movement (0-49 stars).
        FloatingTarget(
            id = id, x = startX, y = startY, vx = vx, vy = vy,
            radius = radius, color = color, kind = kind, emoji = emoji,
            spin = spinStart, spinSpeed = spinSpd, dirChangeTimer = dirTimer
        )
    }
}

// ── Effect spawning ───────────────────────────────────────────────────────────

private fun spawnTapBurst(
    effects: MutableList<Effect>,
    x: Float,
    y: Float,
    colors: List<Color>,
    baseScale: Float,
    reducedMotion: Boolean,
    lifeMul: Float
) {
    val rng = Random
    val maxAge = (if (reducedMotion) 600f else 900f) * lifeMul
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
    baseScale: Float,
    trailLifeMul: Float
) {
    val rng = Random
    effects.add(
        Effect(
            x = x, y = y,
            age = 0f, maxAge = 500f * trailLifeMul,
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
    baseScale: Float,
    lifeMul: Float
) {
    val rng = Random
    effects.add(
        Effect(
            x = x, y = y,
            age = 0f, maxAge = 1200f * lifeMul,
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
                age = 0f, maxAge = 1000f * lifeMul,
                color = colors[rng.nextInt(colors.size)],
                type = EffectType.CONFETTI,
                scale = baseScale,
                velocityX = cos(angle) * speed * lifeMul,
                velocityY = sin(angle) * speed * lifeMul,
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
    reducedMotion: Boolean,
    lifeMul: Float
) {
    val rng = Random
    val count = if (reducedMotion) 12 else 22
    repeat(count) {
        val angle = rng.nextFloat() * 2f * PI.toFloat()
        val speed = (rng.nextFloat() * 9f + 3f) * lifeMul
        effects.add(
            Effect(
                x = hit.x, y = hit.y,
                age = 0f, maxAge = 1100f * lifeMul,
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
            age = 0f, maxAge = 900f * lifeMul,
            color = hit.color,
            type = EffectType.SPARKLE,
            scale = baseScale * 2f
        )
    )
    effects.add(
        Effect(
            x = hit.x, y = hit.y,
            age = 0f, maxAge = 1000f * lifeMul,
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
                    age = 0f, maxAge = 800f * lifeMul,
                    color = hit.color,
                    type = EffectType.STAR_BURST,
                    scale = baseScale * 1.5f,
                    velocityX = cos(angle) * 6f * lifeMul,
                    velocityY = sin(angle) * 6f * lifeMul,
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
}
