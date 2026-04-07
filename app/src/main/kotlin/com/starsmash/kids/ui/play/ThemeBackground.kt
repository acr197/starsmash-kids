package com.starsmash.kids.ui.play

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.starsmash.kids.settings.PlayTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ThemeBackground
 * ===============
 * Draws an animated, themed backdrop behind the play canvas. Each theme has
 * a distinct look:
 *
 *   SPACE   – deep indigo base with a field of twinkling stars and a slow
 *             nebula gradient that drifts across the screen.
 *   OCEAN   – layered blue gradient with slow rising bubbles and a gentle
 *             horizontal "wave" tint sweep.
 *   RAINBOW – white base overlaid with large, slowly shifting rainbow
 *             bands – never harsh, always pastel-ish.
 *   SHAPES  – cream base with large soft pastel gradient blobs that drift
 *             slowly and gently hue-shift.
 *
 * All themes phase-shift their palette over time based on the [phase]
 * parameter (0..1, wraps). The caller (PlayScreen) computes phase from
 * elapsed session time, session difficulty, and the effectsIntensity setting.
 *
 * Reduced-motion flag is honoured: when set, no motion is applied – the
 * background renders as a static, calmer version.
 */
object ThemeBackground {

    // Deterministic star positions so the sky is stable across frames.
    private val stars: List<StarSeed> = List(80) {
        StarSeed(
            rx = Random.nextFloat(),
            ry = Random.nextFloat(),
            radius = 0.6f + Random.nextFloat() * 1.8f,
            twinklePhase = Random.nextFloat() * 2f * PI.toFloat(),
            twinkleSpeed = 0.4f + Random.nextFloat() * 1.4f
        )
    }

    private val bubbles: List<BubbleSeed> = List(18) {
        BubbleSeed(
            rx = Random.nextFloat(),
            startRy = Random.nextFloat(),
            radius = 6f + Random.nextFloat() * 14f,
            speed = 0.05f + Random.nextFloat() * 0.15f
        )
    }

    fun DrawScope.drawThemeBackground(
        theme: PlayTheme,
        phase: Float,            // 0..1 (wraps)
        elapsedSec: Float,       // raw session seconds (for motion)
        reducedMotion: Boolean,
        vibrancy: Float          // 0.85 (LOW) – 1.15 (HIGH)
    ) {
        val w = size.width
        val h = size.height
        val t = if (reducedMotion) 0f else elapsedSec

        when (theme) {
            PlayTheme.SPACE -> drawSpace(w, h, t, phase, reducedMotion, vibrancy)
            PlayTheme.OCEAN -> drawOcean(w, h, t, phase, reducedMotion, vibrancy)
            PlayTheme.RAINBOW -> drawRainbow(w, h, t, phase, reducedMotion, vibrancy)
            PlayTheme.SHAPES -> drawShapes(w, h, t, phase, reducedMotion, vibrancy)
        }
    }

    // ── SPACE ──────────────────────────────────────────────────────────────

    private fun DrawScope.drawSpace(
        w: Float, h: Float, t: Float, phase: Float, reducedMotion: Boolean, vibrancy: Float
    ) {
        // Deep indigo base with a slow hue-rotating nebula.
        val baseTop = hsvBlend(0.68f, 0.72f, 0.10f, phase, 0.05f).scale(vibrancy)
        val baseBottom = hsvBlend(0.74f, 0.70f, 0.06f, phase, 0.04f).scale(vibrancy)
        drawRect(
            brush = Brush.verticalGradient(listOf(baseTop, baseBottom)),
            size = Size(w, h)
        )

        // Nebula – two large soft radial gradients that drift.
        val cx1 = w * (0.3f + 0.1f * sin(t * 0.05f))
        val cy1 = h * (0.35f + 0.08f * cos(t * 0.04f))
        val nebula1 = hsvBlend(0.78f, 0.65f, 0.55f, phase, 0.15f).copy(alpha = 0.30f).scale(vibrancy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(nebula1, Color.Transparent),
                center = Offset(cx1, cy1),
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = Offset(cx1, cy1)
        )

        val cx2 = w * (0.75f + 0.1f * cos(t * 0.04f))
        val cy2 = h * (0.7f + 0.08f * sin(t * 0.03f))
        val nebula2 = hsvBlend(0.92f, 0.55f, 0.55f, phase, 0.2f).copy(alpha = 0.25f).scale(vibrancy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(nebula2, Color.Transparent),
                center = Offset(cx2, cy2),
                radius = w * 0.7f
            ),
            radius = w * 0.7f,
            center = Offset(cx2, cy2)
        )

        // Stars – deterministic positions, twinkling alpha.
        for (s in stars) {
            val twinkle =
                if (reducedMotion) 1f
                else 0.5f + 0.5f * sin(t * s.twinkleSpeed + s.twinklePhase)
            val color = Color.White.copy(alpha = 0.4f + 0.6f * twinkle)
            drawCircle(
                color = color,
                radius = s.radius,
                center = Offset(s.rx * w, s.ry * h)
            )
        }
    }

    // ── OCEAN ──────────────────────────────────────────────────────────────

    private fun DrawScope.drawOcean(
        w: Float, h: Float, t: Float, phase: Float, reducedMotion: Boolean, vibrancy: Float
    ) {
        val top = hsvBlend(0.55f, 0.85f, 0.25f, phase, 0.04f).scale(vibrancy)
        val mid = hsvBlend(0.53f, 0.70f, 0.45f, phase, 0.05f).scale(vibrancy)
        val bot = hsvBlend(0.50f, 0.55f, 0.65f, phase, 0.05f).scale(vibrancy)
        drawRect(
            brush = Brush.verticalGradient(
                0f to top,
                0.55f to mid,
                1f to bot
            ),
            size = Size(w, h)
        )

        // Soft horizontal "shine" band that slides vertically.
        val bandY = (0.25f + 0.5f * (0.5f + 0.5f * sin(t * 0.08f))) * h
        val band = Color.White.copy(alpha = 0.08f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to band,
                1f to Color.Transparent,
                startY = bandY - 160f,
                endY = bandY + 160f
            ),
            topLeft = Offset(0f, bandY - 160f),
            size = Size(w, 320f)
        )

        // Rising bubbles – looped vertically.
        for (b in bubbles) {
            val ry = ((b.startRy - t * b.speed) % 1f + 1f) % 1f
            val x = b.rx * w
            val y = ry * h
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = b.radius,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = b.radius * 0.35f,
                center = Offset(x - b.radius * 0.3f, y - b.radius * 0.3f)
            )
        }
    }

    // ── RAINBOW ────────────────────────────────────────────────────────────

    private fun DrawScope.drawRainbow(
        w: Float, h: Float, t: Float, phase: Float, reducedMotion: Boolean, vibrancy: Float
    ) {
        // Soft pastel white base.
        drawRect(color = Color(0xFFFFFDF7), size = Size(w, h))

        // Diagonal rainbow bands that slowly slide.
        val offset = if (reducedMotion) 0f else (t * 20f) % (w + h)
        val baseStops = listOf(
            Color(0xFFFFC1C1),
            Color(0xFFFFE0B0),
            Color(0xFFFFF6A8),
            Color(0xFFBEEBB4),
            Color(0xFFB3DBFF),
            Color(0xFFDAB6FF)
        )
        val diag = (w + h)
        // Rotate hue-phase so the bands slowly cycle colors.
        val shifted = baseStops.map { c ->
            hueRotate(c, phase * 360f * 0.25f).scale(vibrancy * 1.05f).copy(alpha = 0.55f)
        }
        drawRect(
            brush = Brush.linearGradient(
                colors = shifted,
                start = Offset(-offset, -offset),
                end = Offset(diag - offset, diag - offset)
            ),
            size = Size(w, h)
        )
    }

    // ── SHAPES ─────────────────────────────────────────────────────────────

    private fun DrawScope.drawShapes(
        w: Float, h: Float, t: Float, phase: Float, reducedMotion: Boolean, vibrancy: Float
    ) {
        // Cream base.
        drawRect(color = Color(0xFFFFF6ED), size = Size(w, h))

        // Four large pastel blobs that drift and hue-rotate.
        val blobColors = listOf(
            Color(0xFFFFC1D8),
            Color(0xFFC4F1D1),
            Color(0xFFC6E0FF),
            Color(0xFFFFE2A8)
        )
        val positions = listOf(
            Offset(w * (0.25f + 0.1f * sin(t * 0.05f)), h * (0.3f + 0.1f * cos(t * 0.04f))),
            Offset(w * (0.75f + 0.1f * cos(t * 0.06f)), h * (0.35f + 0.1f * sin(t * 0.05f))),
            Offset(w * (0.3f + 0.1f * cos(t * 0.04f)), h * (0.72f + 0.1f * sin(t * 0.06f))),
            Offset(w * (0.78f + 0.1f * sin(t * 0.05f)), h * (0.78f + 0.1f * cos(t * 0.04f)))
        )
        for (i in blobColors.indices) {
            val c = hueRotate(blobColors[i], phase * 360f * 0.5f).scale(vibrancy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.65f), Color.Transparent),
                    center = positions[i],
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = positions[i]
            )
        }
    }

    // ── Color helpers ──────────────────────────────────────────────────────

    private fun hsvBlend(
        h: Float, s: Float, v: Float, phase: Float, amount: Float
    ): Color {
        // Small hue wobble driven by phase.
        val hh = ((h + amount * (phase - 0.5f) * 2f) + 1f) % 1f
        return hsvToColor(hh, s, v)
    }

    /** Simple HSV → RGB conversion (h in [0,1)). */
    private fun hsvToColor(h: Float, s: Float, v: Float): Color {
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

    private fun Color.scale(factor: Float): Color {
        val f = factor.coerceIn(0.6f, 1.4f)
        return Color(
            (red * f).coerceIn(0f, 1f),
            (green * f).coerceIn(0f, 1f),
            (blue * f).coerceIn(0f, 1f),
            alpha
        )
    }

    /** Rotate a color's hue by [degrees] (0–360). */
    private fun hueRotate(color: Color, degrees: Float): Color {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val v = max
        val d = max - min
        val s = if (max == 0f) 0f else d / max
        var h = when {
            d == 0f -> 0f
            max == r -> (g - b) / d + (if (g < b) 6f else 0f)
            max == g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        h = (h + degrees / 360f) % 1f
        if (h < 0f) h += 1f
        return hsvToColor(h, s, v).copy(alpha = color.alpha)
    }

    // ── Deterministic seeds ────────────────────────────────────────────────

    private data class StarSeed(
        val rx: Float,
        val ry: Float,
        val radius: Float,
        val twinklePhase: Float,
        val twinkleSpeed: Float
    )

    private data class BubbleSeed(
        val rx: Float,
        val startRy: Float,
        val radius: Float,
        val speed: Float
    )
}
