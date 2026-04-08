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
 * Draws a smooth, animated, themed backdrop behind the play canvas.
 *
 *   SPACE   - deep indigo gradient with two large nebulas slowly drifting
 *             across the screen and hundreds of stars twinkling on a slow
 *             continuous loop.
 *   OCEAN   - layered blue gradient with rising bubbles and a slow vertical
 *             shine band.
 *   RAINBOW - smooth full-spectrum rainbow gradient that continuously
 *             rotates its hue, with wide soft colour bands that segue into
 *             each other (no hard lines).
 *
 * All animation is driven by a single, monotonic [timeSec] argument. The
 * caller passes the same elapsed seconds value used for gameplay timing,
 * so the background and gameplay are perfectly in sync.
 *
 * The [animationSpeed] argument is a multiplier set by the user's
 * Effects Intensity choice. Higher values rotate / drift the background
 * faster, but never to a jarring degree (max 1.6x normal).
 *
 * Reduced-motion flag is honoured: when set, no motion is applied - the
 * background renders as a static, calmer version.
 */
object ThemeBackground {

    // Deterministic star positions so the sky is stable across frames.
    private val stars: List<StarSeed> = List(120) {
        StarSeed(
            rx = Random.nextFloat(),
            ry = Random.nextFloat(),
            radius = 0.6f + Random.nextFloat() * 1.8f,
            twinklePhase = Random.nextFloat() * 2f * PI.toFloat(),
            twinkleSpeed = 0.25f + Random.nextFloat() * 0.6f
        )
    }

    private val bubbles: List<BubbleSeed> = List(22) {
        BubbleSeed(
            rx = Random.nextFloat(),
            startRy = Random.nextFloat(),
            radius = 6f + Random.nextFloat() * 14f,
            speed = 0.04f + Random.nextFloat() * 0.10f
        )
    }

    fun DrawScope.drawThemeBackground(
        theme: PlayTheme,
        timeSec: Float,
        reducedMotion: Boolean,
        vibrancy: Float,            // 0.85 (LOW) - 1.15 (HIGH)
        animationSpeed: Float       // 0.7 (LOW) - 1.6 (HIGH)
    ) {
        val w = size.width
        val h = size.height
        val t = if (reducedMotion) 0f else timeSec * animationSpeed

        when (theme) {
            PlayTheme.SPACE -> drawSpace(w, h, t, reducedMotion, vibrancy)
            PlayTheme.OCEAN -> drawOcean(w, h, t, reducedMotion, vibrancy)
            PlayTheme.RAINBOW -> drawRainbow(w, h, t, reducedMotion, vibrancy)
        }
    }

    // -- SPACE -----------------------------------------------------------------

    private fun DrawScope.drawSpace(
        w: Float, h: Float, t: Float, reducedMotion: Boolean, vibrancy: Float
    ) {
        // Deep indigo base. Constant - no per-frame palette changes.
        val baseTop = Color(0xFF0A0820).scale(vibrancy)
        val baseBottom = Color(0xFF120A2A).scale(vibrancy)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(baseTop, baseBottom),
                startY = 0f,
                endY = h
            ),
            size = Size(w, h)
        )

        // Three nebulas moving along large orbital paths so it never feels
        // static. Much more travel than before (0.35w amplitude) so the eye
        // can actually see them drifting.
        val driftA = 0.09f
        val cx1 = w * (0.5f + 0.35f * sin(t * driftA))
        val cy1 = h * (0.5f + 0.22f * cos(t * driftA * 0.7f))
        val nebulaHueA = (0.78f + 0.04f * sin(t * 0.02f)) % 1f
        val nebula1 = hsvToColor(nebulaHueA, 0.60f, 0.6f).copy(alpha = 0.32f).scale(vibrancy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(nebula1, Color.Transparent),
                center = Offset(cx1, cy1),
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = Offset(cx1, cy1)
        )

        val cx2 = w * (0.5f + 0.38f * cos(t * driftA * 0.6f + 1.5f))
        val cy2 = h * (0.5f + 0.28f * sin(t * driftA * 0.85f + 1.5f))
        val nebulaHueB = (0.92f + 0.04f * cos(t * 0.025f) + 1f) % 1f
        val nebula2 = hsvToColor(nebulaHueB, 0.60f, 0.55f).copy(alpha = 0.28f).scale(vibrancy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(nebula2, Color.Transparent),
                center = Offset(cx2, cy2),
                radius = w * 0.80f
            ),
            radius = w * 0.80f,
            center = Offset(cx2, cy2)
        )

        val cx3 = w * (0.5f + 0.30f * sin(t * driftA * 0.5f + 3.0f))
        val cy3 = h * (0.5f + 0.30f * cos(t * driftA * 0.9f + 3.0f))
        val nebulaHueC = (0.62f + 0.05f * sin(t * 0.018f) + 1f) % 1f
        val nebula3 = hsvToColor(nebulaHueC, 0.55f, 0.50f).copy(alpha = 0.22f).scale(vibrancy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(nebula3, Color.Transparent),
                center = Offset(cx3, cy3),
                radius = w * 0.70f
            ),
            radius = w * 0.70f,
            center = Offset(cx3, cy3)
        )

        // Stars - positions drift slowly around the center so the whole
        // starfield appears to rotate. Period ~ 2 minutes at 1x speed.
        val rotation = if (reducedMotion) 0f else t * 0.015f
        val cosR = cos(rotation)
        val sinR = sin(rotation)
        val cw = w * 0.5f
        val ch = h * 0.5f
        for (s in stars) {
            // Translate star position relative to center, rotate, translate back.
            val sx = s.rx * w - cw
            val sy = s.ry * h - ch
            val rx = cw + sx * cosR - sy * sinR
            val ry = ch + sx * sinR + sy * cosR
            val twinkle =
                if (reducedMotion) 1f
                else 0.5f + 0.5f * sin(t * s.twinkleSpeed + s.twinklePhase)
            val color = Color.White.copy(alpha = 0.35f + 0.65f * twinkle)
            drawCircle(
                color = color,
                radius = s.radius,
                center = Offset(rx, ry)
            )
        }
    }

    // -- OCEAN -----------------------------------------------------------------

    private fun DrawScope.drawOcean(
        w: Float, h: Float, t: Float, reducedMotion: Boolean, vibrancy: Float
    ) {
        val top = Color(0xFF0A4D8C).scale(vibrancy)
        val mid = Color(0xFF1A82CC).scale(vibrancy)
        val bot = Color(0xFF49B5E5).scale(vibrancy)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to top,
                    0.55f to mid,
                    1f to bot
                ),
                startY = 0f,
                endY = h
            ),
            size = Size(w, h)
        )

        // Soft horizontal "shine" band that slowly drifts vertically.
        val bandY = (0.25f + 0.5f * (0.5f + 0.5f * sin(t * 0.06f))) * h
        val band = Color.White.copy(alpha = 0.10f)
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to band,
                    1f to Color.Transparent
                ),
                startY = bandY - 160f,
                endY = bandY + 160f
            ),
            topLeft = Offset(0f, bandY - 160f),
            size = Size(w, 320f)
        )

        // Rising bubbles - looped vertically.
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

    // -- RAINBOW ---------------------------------------------------------------

    private fun DrawScope.drawRainbow(
        w: Float, h: Float, t: Float, reducedMotion: Boolean, vibrancy: Float
    ) {
        // Hue offset advances continuously - the rainbow flows.
        val hueShift = if (reducedMotion) 0f else (t * 0.04f) % 1f

        // Build a wide rainbow with many soft stops so the colours segue
        // smoothly with no hard lines.
        val stopCount = 12
        val stops = Array(stopCount) { i ->
            val frac = i / (stopCount - 1f)
            val hue = (frac + hueShift) % 1f
            // Pastel: lower saturation, higher value.
            val c = hsvToColor(hue, 0.55f, 1f).scale(vibrancy)
            frac to c
        }

        // Diagonal gradient (top-left to bottom-right) for a more dynamic feel.
        val diag = w + h
        drawRect(
            brush = Brush.linearGradient(
                colorStops = stops,
                start = Offset(0f, 0f),
                end = Offset(diag, diag)
            ),
            size = Size(w, h)
        )

        // Subtle white wash to soften the saturation overall.
        drawRect(
            color = Color.White.copy(alpha = 0.10f),
            size = Size(w, h)
        )
    }

    // -- Color helpers ---------------------------------------------------------

    /** Simple HSV -> RGB conversion (h in [0,1)). */
    private fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val hh = ((h % 1f) + 1f) % 1f
        val i = (hh * 6f).toInt()
        val f = hh * 6f - i
        val p = v * (1f - s)
        val q = v * (1f - f * s)
        val tt = v * (1f - (1f - f) * s)
        val (r, g, b) = when (i % 6) {
            0 -> Triple(v, tt, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, tt)
            3 -> Triple(p, q, v)
            4 -> Triple(tt, p, v)
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

    // -- Deterministic seeds ---------------------------------------------------

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
