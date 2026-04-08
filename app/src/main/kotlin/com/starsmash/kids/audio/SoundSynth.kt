package com.starsmash.kids.audio

import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * SoundSynth
 * ==========
 * Procedural PCM sample generator. Produces short 16-bit mono WAV clips that
 * [AudioEngine] loads into a [android.media.SoundPool] at startup.
 *
 * Why synthesize instead of shipping audio assets?
 *   1. Zero additional asset weight / licensing concerns.
 *   2. Every sound is deterministic and easy to tweak in code.
 *   3. No network / download required.
 *
 * All effects are simple additive/noise synthesis – the goal is "pleasant and
 * playful for a toddler", not realism. Nothing here is intense, sharp, or
 * startling.
 *
 * Sample rate is 44100 Hz for effects (short, high-quality) and 22050 Hz for
 * the background music loop (longer, lower memory footprint).
 */
object SoundSynth {

    const val SFX_SAMPLE_RATE = 44100
    const val MUSIC_SAMPLE_RATE = 22050

    // ── Public entry point ─────────────────────────────────────────────────

    /** Identifier for a single cached SFX clip. */
    enum class Clip {
        // Shared / always-on
        COIN_DING,            // Target hit (Mario-coin flavour)
        CHIME_SOFT,           // Target hit (calm)

        // Playful family (used when soundMode = PLAYFUL)
        POP_HIGH,
        POP_MID,
        POP_LOW,
        BOING,
        SOFT_BOOM,
        SPARKLE_UP,

        // Calm family (used when soundMode = CALM)
        WATER_DROP,
        WOOD_TAP,
        GLASS_PING,
        BREEZE,

        // Very soft whispered taps for CALM-mode drags (rapid-fire).
        HUSH_LOW,
        HUSH_MID
    }

    /**
     * Generate the full SFX bank as WAV files into [cacheDir]. Returns a map
     * from [Clip] to the written [File]. Files are written only if missing so
     * repeated launches are cheap.
     *
     * WAV format: 16-bit PCM mono, 44100 Hz.
     */
    fun generateSfxBank(cacheDir: File): Map<Clip, File> {
        cacheDir.mkdirs()
        val out = mutableMapOf<Clip, File>()
        for (clip in Clip.values()) {
            val file = File(cacheDir, "ssk_${clip.name.lowercase()}.wav")
            if (!file.exists() || file.length() < 128) {
                val samples = renderClip(clip)
                writeWav(samples, SFX_SAMPLE_RATE, file)
            }
            out[clip] = file
        }
        return out
    }

    /** Identifier for a generated background music loop style. */
    enum class MusicStyle { ARCADE, ADVENTURE, BUBBLE_POP }

    /**
     * Generate a background music loop in the given style. Each style is a
     * ~12 s loop designed to seam smoothly. Files are cached in [cacheDir].
     */
    fun generateMusicLoop(cacheDir: File, style: MusicStyle = MusicStyle.ARCADE): File {
        cacheDir.mkdirs()
        // Bump v suffix whenever the waveform generator changes so old caches
        // don't get reused with new code.
        val file = File(cacheDir, "ssk_music_${style.name.lowercase()}_v2.wav")
        if (!file.exists() || file.length() < 1024) {
            val samples = when (style) {
                MusicStyle.ARCADE -> renderMusicLoopArcade()
                MusicStyle.ADVENTURE -> renderMusicLoopAdventure()
                MusicStyle.BUBBLE_POP -> renderMusicLoopBubblePop()
            }
            writeWav(samples, MUSIC_SAMPLE_RATE, file)
        }
        return file
    }

    // ── Clip rendering ─────────────────────────────────────────────────────

    private fun renderClip(clip: Clip): ShortArray = when (clip) {
        Clip.COIN_DING -> coinDing()
        Clip.CHIME_SOFT -> chimeSoft()
        Clip.POP_HIGH -> pop(880f, 0.15f)
        Clip.POP_MID -> pop(660f, 0.17f)
        Clip.POP_LOW -> pop(440f, 0.19f)
        Clip.BOING -> boing()
        Clip.SOFT_BOOM -> softBoom()
        Clip.SPARKLE_UP -> sparkleUp()
        Clip.WATER_DROP -> waterDrop()
        Clip.WOOD_TAP -> woodTap()
        Clip.GLASS_PING -> glassPing()
        Clip.BREEZE -> breeze()
        Clip.HUSH_LOW -> hush(165f)
        Clip.HUSH_MID -> hush(220f)
    }

    /**
     * Very quiet, heavily-filtered, short "hush" tick - a low sine blip
     * wrapped in a fast decaying envelope. Intended for CALM-mode drag
     * trails where we want subtle breath-like feedback, not repeated taps.
     * ~90 ms.
     */
    private fun hush(freq: Float): ShortArray {
        val dur = 0.09f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val twoPi = (2f * PI).toFloat()
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val attack = min(t / 0.008f, 1f)
            val decay = exp(-24f * t)
            val env = attack * decay
            // Very low amp on purpose - this is meant to be barely there.
            val s = sin(twoPi * freq * t) * 0.20f
            buf[i] = toPcm(s * env)
        }
        return buf
    }

    // ── Individual sound designs ───────────────────────────────────────────
    //
    // All generators return mono 16-bit PCM at [SFX_SAMPLE_RATE].
    // They aim for ~100–300 ms total length; longer than that would start
    // crowding rapid tap play.

    /**
     * Arcade coin / jackpot: the classic two-note Mario coin (E5 -> B5) with
     * a bright triangle-wave harmonic on top and a tiny shimmer tail for
     * that "you just won" casino feel. Total length ~260 ms.
     */
    private fun coinDing(): ShortArray {
        val dur = 0.26f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val twoPi = (2f * PI).toFloat()
        val e5 = 659.25f
        val b5 = 987.77f
        val split = (n * 0.25f).toInt() // pitch jump at 25%
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val freq = if (i < split) e5 else b5
            // Local time since note start (for per-note envelope).
            val tLocal = if (i < split) t else (i - split).toFloat() / SFX_SAMPLE_RATE
            val attack = min(tLocal / 0.003f, 1f)
            val decay = exp(-6f * tLocal)
            val env = attack * decay
            // Sine + half-amplitude octave harmonic => brighter "arcade" tone.
            val s = sin(twoPi * freq * t) * 0.55f +
                sin(twoPi * freq * 2f * t) * 0.20f +
                sin(twoPi * freq * 3f * t) * 0.08f
            buf[i] = toPcm(s * env * 0.55f)
        }
        return buf
    }

    /**
     * Calm low "chime": sine around 290 Hz with a soft fifth harmonic above.
     * Much lower than the previous C5/C6 version so it never feels shrill.
     * ~280 ms.
     */
    private fun chimeSoft(): ShortArray {
        val dur = 0.28f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val f1 = 293.66f // D4
        val f2 = 440f    // A4 (a soft fifth above)
        val twoPi = (2f * PI).toFloat()
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val env = exp(-5f * t)
            val s = 0.55f * sin(twoPi * f1 * t) + 0.18f * sin(twoPi * f2 * t)
            buf[i] = toPcm(s * env * 0.45f)
        }
        return applyFadeIn(buf, 0.004f)
    }

    /**
     * Generic "pop" – a short sine burst with a pitch drop, ~120 ms.
     * Used for playful taps at three different base pitches.
     */
    private fun pop(baseFreq: Float, durSec: Float): ShortArray {
        val n = (SFX_SAMPLE_RATE * durSec).toInt()
        val buf = ShortArray(n)
        val twoPi = (2f * PI).toFloat()
        var phase = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            // Pitch drops ~25% over the duration.
            val freq = baseFreq * (1f - 0.25f * (t / durSec))
            phase += twoPi * freq / SFX_SAMPLE_RATE
            // Attack (4ms) then exponential decay.
            val attack = min(t / 0.004f, 1f)
            val decay = exp(-10f * t)
            val env = attack * decay
            buf[i] = toPcm(sin(phase) * env * 0.5f)
        }
        return buf
    }

    /**
     * Comedic "boing": pitch wobble from ~200 Hz up to ~450 Hz and back.
     * ~200 ms.
     */
    private fun boing(): ShortArray {
        val dur = 0.22f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val twoPi = (2f * PI).toFloat()
        var phase = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val wobble = 200f + 250f * sin((t / dur) * PI.toFloat())
            phase += twoPi * wobble / SFX_SAMPLE_RATE
            val env = exp(-5f * t) * min(t / 0.005f, 1f)
            buf[i] = toPcm(sin(phase) * env * 0.55f)
        }
        return buf
    }

    /**
     * "Soft boom" – filtered white noise pulse with a low rumble underneath.
     * Short (~220 ms) so it doesn't feel scary. Used for multi-finger bursts.
     */
    private fun softBoom(): ShortArray {
        val dur = 0.24f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val rng = Random(12345)
        val twoPi = (2f * PI).toFloat()
        // Simple 1-pole low-pass on noise.
        var prev = 0f
        val alpha = 0.15f
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val noise = (rng.nextFloat() * 2f - 1f)
            prev = prev + alpha * (noise - prev)
            val rumble = 0.55f * sin(twoPi * 80f * t) + 0.25f * sin(twoPi * 140f * t)
            val env = exp(-8f * t) * min(t / 0.01f, 1f)
            buf[i] = toPcm((prev * 0.45f + rumble * 0.35f) * env * 0.6f)
        }
        return buf
    }

    /**
     * Upward sparkle arpeggio – four short sine taps walking up a major arp.
     * Used for ripples / big multi-finger hits. Still gentle.
     */
    private fun sparkleUp(): ShortArray {
        val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
        val parts = notes.map { sinePluck(it, 0.055f, amp = 0.5f) }
        return parts.reduce { acc, s -> concatenate(acc, s) }
    }

    /**
     * Water drop – descending pitch ping, ~220 ms. Very light.
     * Used as the primary CALM tap sound.
     */
    private fun waterDrop(): ShortArray {
        val dur = 0.22f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val twoPi = (2f * PI).toFloat()
        var phase = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            // Frequency sweeps from 600 Hz down to 220 Hz - softer than before.
            val freq = 600f - 380f * (t / dur).pow(0.5f)
            phase += twoPi * freq / SFX_SAMPLE_RATE
            val attack = min(t / 0.006f, 1f)
            val decay = exp(-6f * t)
            buf[i] = toPcm(sin(phase) * attack * decay * 0.5f)
        }
        return buf
    }

    /**
     * Wood tap – brief noise burst with a strong low-pass, 90 ms. Sounds like
     * a soft knock on a hollow wood block.
     */
    private fun woodTap(): ShortArray {
        val dur = 0.09f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val rng = Random(98765)
        var prev = 0f
        val alpha = 0.12f
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val noise = (rng.nextFloat() * 2f - 1f)
            prev += alpha * (noise - prev)
            val env = exp(-30f * t)
            buf[i] = toPcm(prev * env * 0.7f)
        }
        return buf
    }

    /**
     * Low wooden knock: short attack, deep thud at ~230 Hz. Used for calm-mode
     * target hits. Much lower than the previous 880 Hz "glass ping" which felt
     * shrill and ringing. ~220 ms.
     */
    private fun glassPing(): ShortArray {
        val dur = 0.22f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val twoPi = (2f * PI).toFloat()
        val f1 = 230f
        val f2 = 345f
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val attack = min(t / 0.004f, 1f)
            val decay = exp(-7f * t)
            val env = attack * decay
            val s = 0.65f * sin(twoPi * f1 * t) + 0.20f * sin(twoPi * f2 * t)
            buf[i] = toPcm(s * env * 0.45f)
        }
        return buf
    }

    /**
     * Breeze – a very soft filtered-noise swell, ~400 ms. Used for calm
     * multi-finger / palm events so they don't startle.
     */
    private fun breeze(): ShortArray {
        val dur = 0.45f
        val n = (SFX_SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        val rng = Random(55555)
        var prev1 = 0f
        var prev2 = 0f
        val a = 0.06f
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val raw = rng.nextFloat() * 2f - 1f
            prev1 += a * (raw - prev1)
            prev2 += a * (prev1 - prev2)
            // Bell-shaped envelope: swells up, then down.
            val x = t / dur
            val env = 4f * x * (1f - x)
            buf[i] = toPcm(prev2 * env * 0.55f)
        }
        return buf
    }

    // ── Music loop ─────────────────────────────────────────────────────────

    /**
     * ARCADE: upbeat I-vi-IV-V progression in C major with a bouncy melody,
     * walking bass, and soft chord pad. Written to feel like classic NES /
     * SNES overworld themes - cheerful, polyphonic, and loops cleanly.
     * ~12 s loop @ ~130 BPM.
     */
    private fun renderMusicLoopArcade(): ShortArray {
        val sr = MUSIC_SAMPLE_RATE
        val stepSec = 0.23f // ~130 BPM sixteenth-ish
        // 4 bars, 16 steps per bar = 64 steps ~= 14.7 s (we'll trim to 48 steps).
        val totalSteps = 48
        val totalSamples = (sr * stepSec * totalSteps).toInt()
        val buf = FloatArray(totalSamples)

        // Chord progression: C - Am - F - G, 12 steps per chord.
        // Each chord is 3 notes. Triads in root position.
        val chordC = floatArrayOf(261.63f, 329.63f, 392.00f)       // C E G
        val chordAm = floatArrayOf(220.00f, 261.63f, 329.63f)      // A C E
        val chordF = floatArrayOf(174.61f, 220.00f, 261.63f)       // F A C
        val chordG = floatArrayOf(196.00f, 246.94f, 293.66f)       // G B D
        val chordProg = arrayOf(chordC, chordAm, chordF, chordG)
        val bassProg = floatArrayOf(130.81f, 110.00f, 87.31f, 98.00f) // C2 A2 F2 G2

        // Bouncy melody: a memorable 12-step motif repeated per chord, shifted
        // so it follows each chord's root tone. (Intervals 0, 4, 7, 4, 12, 7, 4, 0, 2, 4, 7, 4).
        val motifIntervals = intArrayOf(0, 4, 7, 4, 12, 7, 4, 0, 2, 4, 7, 4)
        // Each step = quarter note (stepSec). Motif = 12 steps per chord.

        for (step in 0 until totalSteps) {
            val chordIdx = (step / 12) % chordProg.size
            val chord = chordProg[chordIdx]
            val bassNote = bassProg[chordIdx]
            val startSample = (step * stepSec * sr).toInt()
            val noteLen = (stepSec * sr * 0.95f).toInt()

            // --- Chord pad (softer sustain, sums all three notes) ---
            // Play the chord only on the first step of each chord region so
            // the pad holds through the chord.
            if (step % 12 == 0) {
                val padLen = (stepSec * sr * 12f).toInt()
                for (i in 0 until padLen) {
                    val t = i.toFloat() / sr
                    val attack = min(t / 0.08f, 1f)
                    val release = 1f - (t / (padLen.toFloat() / sr)).coerceIn(0f, 1f) * 0.3f
                    val env = attack * release
                    // Sum three sines, slightly detuned for chorus warmth.
                    var s = 0f
                    for (n in chord) {
                        s += sin(2f * PI.toFloat() * n * t) * 0.09f
                        s += sin(2f * PI.toFloat() * n * 1.003f * t) * 0.06f
                    }
                    val idx = startSample + i
                    if (idx < totalSamples) buf[idx] += s * env
                }
            }

            // --- Lead melody: bouncy motif ---
            val stepInChord = step % 12
            val interval = motifIntervals[stepInChord]
            val leadFreq = chord[0] * 2f * 2f.pow(interval / 12f)
            for (i in 0 until noteLen) {
                val t = i.toFloat() / sr
                val attack = min(t / 0.008f, 1f)
                val decay = exp(-4.5f * t)
                val env = attack * decay
                // Square-ish via 3 odd harmonics for that chiptune brightness.
                val s = sin(2f * PI.toFloat() * leadFreq * t) * 0.22f +
                    sin(2f * PI.toFloat() * leadFreq * 3f * t) * 0.06f +
                    sin(2f * PI.toFloat() * leadFreq * 5f * t) * 0.02f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }

            // --- Walking bass: root note on every step ---
            // Alternate root and fifth for a walking feel.
            val walkingFreq = if (step % 2 == 0) bassNote else bassNote * 1.5f
            val bassLen = (stepSec * sr * 0.9f).toInt()
            for (i in 0 until bassLen) {
                val t = i.toFloat() / sr
                val attack = min(t / 0.01f, 1f)
                val decay = exp(-2.5f * t)
                val env = attack * decay
                val s = sin(2f * PI.toFloat() * walkingFreq * t) * 0.30f +
                    sin(2f * PI.toFloat() * walkingFreq * 2f * t) * 0.05f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }

            // --- Counter-melody plink on off-beats (every 3rd step) ---
            if (step % 3 == 1) {
                val plinkFreq = chord[2] * 2f
                val plinkLen = (stepSec * sr * 0.5f).toInt()
                for (i in 0 until plinkLen) {
                    val t = i.toFloat() / sr
                    val env = exp(-10f * t) * min(t / 0.003f, 1f)
                    val s = sin(2f * PI.toFloat() * plinkFreq * t) * 0.10f
                    val idx = startSample + i
                    if (idx < totalSamples) buf[idx] += s * env
                }
            }
        }
        return normaliseAndQuantise(buf, totalSamples)
    }

    /**
     * ADVENTURE: major-key chord progression (I-V-vi-IV) in D major with
     * a bright singable melody, third harmony line above, and walking bass.
     * Medium-tempo, uplifting, Banjo-Kazooie / Kirby vibe. ~12 s loop.
     */
    private fun renderMusicLoopAdventure(): ShortArray {
        val sr = MUSIC_SAMPLE_RATE
        val stepSec = 0.25f // 120 BPM
        val totalSteps = 48
        val totalSamples = (sr * stepSec * totalSteps).toInt()
        val buf = FloatArray(totalSamples)

        // D major I-V-vi-IV: D - A - Bm - G
        val chordD = floatArrayOf(293.66f, 369.99f, 440.00f)       // D F# A
        val chordA = floatArrayOf(220.00f, 277.18f, 329.63f)       // A C# E
        val chordBm = floatArrayOf(246.94f, 293.66f, 369.99f)      // B D F#
        val chordG = floatArrayOf(196.00f, 246.94f, 293.66f)       // G B D
        val chordProg = arrayOf(chordD, chordA, chordBm, chordG)
        val bassProg = floatArrayOf(73.42f, 110.00f, 123.47f, 98.00f) // D2 A2 B2 G2

        // Singable 12-step melody (intervals over chord root in semitones).
        // Think of it as "da-da da-dum, da-da da-dum, soaring up".
        val motifIntervals = intArrayOf(12, 12, 16, 14, 12, 16, 19, 16, 14, 12, 14, 12)

        for (step in 0 until totalSteps) {
            val chordIdx = (step / 12) % chordProg.size
            val chord = chordProg[chordIdx]
            val bassNote = bassProg[chordIdx]
            val startSample = (step * stepSec * sr).toInt()
            val noteLen = (stepSec * sr * 0.95f).toInt()

            // --- Chord pad held through the whole chord region ---
            if (step % 12 == 0) {
                val padLen = (stepSec * sr * 12f).toInt()
                for (i in 0 until padLen) {
                    val t = i.toFloat() / sr
                    val attack = min(t / 0.15f, 1f)
                    val release = 1f - (t / (padLen.toFloat() / sr)).coerceIn(0f, 1f) * 0.25f
                    val env = attack * release
                    var s = 0f
                    for (n in chord) {
                        s += sin(2f * PI.toFloat() * n * t) * 0.10f
                        s += sin(2f * PI.toFloat() * n * 1.005f * t) * 0.06f
                    }
                    val idx = startSample + i
                    if (idx < totalSamples) buf[idx] += s * env
                }
            }

            // --- Lead melody ---
            val stepInChord = step % 12
            val interval = motifIntervals[stepInChord]
            val leadFreq = chord[0] * 2f.pow(interval / 12f)
            for (i in 0 until noteLen) {
                val t = i.toFloat() / sr
                val attack = min(t / 0.015f, 1f)
                val decay = exp(-2.2f * t)
                val env = attack * decay
                val s = sin(2f * PI.toFloat() * leadFreq * t) * 0.24f +
                    sin(2f * PI.toFloat() * leadFreq * 2f * t) * 0.06f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }

            // --- Harmony line: a third (4 semitones) above the lead ---
            val harmonyFreq = leadFreq * 2f.pow(4f / 12f)
            for (i in 0 until noteLen) {
                val t = i.toFloat() / sr
                val attack = min(t / 0.02f, 1f)
                val decay = exp(-2.5f * t)
                val env = attack * decay
                val s = sin(2f * PI.toFloat() * harmonyFreq * t) * 0.12f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }

            // --- Walking bass: root on 1/3, fifth on 2/4 ---
            val walkingFreq = if (step % 2 == 0) bassNote else bassNote * 1.5f
            val bassLen = (stepSec * sr * 0.9f).toInt()
            for (i in 0 until bassLen) {
                val t = i.toFloat() / sr
                val attack = min(t / 0.01f, 1f)
                val decay = exp(-2.0f * t)
                val env = attack * decay
                val s = sin(2f * PI.toFloat() * walkingFreq * t) * 0.32f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }
        }
        return normaliseAndQuantise(buf, totalSamples)
    }

    /**
     * BUBBLE_POP: fast happy I-V-vi-IV in G major with a bouncy sixteenth-note
     * arpeggio, sparkly plinks on off-beats, chord pad, and walking bass.
     * Dense and poppy - Bloons / Kirby vibe. ~12 s loop @ ~140 BPM.
     */
    private fun renderMusicLoopBubblePop(): ShortArray {
        val sr = MUSIC_SAMPLE_RATE
        val stepSec = 0.21f // ~140 BPM sixteenths
        val totalSteps = 56
        val totalSamples = (sr * stepSec * totalSteps).toInt()
        val buf = FloatArray(totalSamples)

        // G major I-V-vi-IV: G - D - Em - C
        val chordG = floatArrayOf(196.00f, 246.94f, 293.66f)       // G B D
        val chordD = floatArrayOf(146.83f, 185.00f, 220.00f)       // D F# A
        val chordEm = floatArrayOf(164.81f, 196.00f, 246.94f)      // E G B
        val chordC = floatArrayOf(130.81f, 164.81f, 196.00f)       // C E G
        val chordProg = arrayOf(chordG, chordD, chordEm, chordC)
        val bassProg = floatArrayOf(98.00f, 73.42f, 82.41f, 65.41f) // G2 D2 E2 C2

        // 14 steps per chord. Arpeggio walks up and bounces back.
        // Pattern in chord-tone indices: 0,1,2,1,0,2,1,2 then repeats with twist.
        val arpPattern = intArrayOf(0, 1, 2, 1, 3, 2, 1, 0, 2, 1, 3, 2, 1, 0)

        for (step in 0 until totalSteps) {
            val chordIdx = (step / 14) % chordProg.size
            val chord = chordProg[chordIdx]
            val bassNote = bassProg[chordIdx]
            val startSample = (step * stepSec * sr).toInt()
            val noteLen = (stepSec * sr * 0.9f).toInt()

            // Chord pad.
            if (step % 14 == 0) {
                val padLen = (stepSec * sr * 14f).toInt()
                for (i in 0 until padLen) {
                    val t = i.toFloat() / sr
                    val attack = min(t / 0.06f, 1f)
                    val release = 1f - (t / (padLen.toFloat() / sr)).coerceIn(0f, 1f) * 0.3f
                    val env = attack * release
                    var s = 0f
                    for (n in chord) {
                        s += sin(2f * PI.toFloat() * n * 2f * t) * 0.08f
                    }
                    val idx = startSample + i
                    if (idx < totalSamples) buf[idx] += s * env
                }
            }

            // Arpeggio lead.
            val stepInChord = step % 14
            val tone = arpPattern[stepInChord]
            // tone 0,1,2 = chord tones; 3 = octave of root
            val arpFreq = when (tone) {
                3 -> chord[0] * 4f
                else -> chord[tone] * 2f
            }
            for (i in 0 until noteLen) {
                val t = i.toFloat() / sr
                val attack = min(t / 0.006f, 1f)
                val decay = exp(-6.5f * t)
                val env = attack * decay
                val s = sin(2f * PI.toFloat() * arpFreq * t) * 0.22f +
                    sin(2f * PI.toFloat() * arpFreq * 2f * t) * 0.05f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }

            // Walking bass: root - fifth alternation, syncopated.
            val walkingFreq = when (step % 4) {
                0 -> bassNote
                1 -> bassNote * 1.5f
                2 -> bassNote
                else -> bassNote * 2f
            }
            val bassLen = (stepSec * sr * 0.85f).toInt()
            for (i in 0 until bassLen) {
                val t = i.toFloat() / sr
                val attack = min(t / 0.008f, 1f)
                val decay = exp(-3f * t)
                val env = attack * decay
                val s = sin(2f * PI.toFloat() * walkingFreq * t) * 0.28f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }

            // Sparkle plink on step 7 of each chord.
            if (step % 14 == 7) {
                val plinkFreq = chord[2] * 4f
                val plinkLen = (stepSec * sr * 1.2f).toInt()
                for (i in 0 until plinkLen) {
                    val t = i.toFloat() / sr
                    val env = exp(-8f * t) * min(t / 0.003f, 1f)
                    val s = sin(2f * PI.toFloat() * plinkFreq * t) * 0.08f
                    val idx = startSample + i
                    if (idx < totalSamples) buf[idx] += s * env
                }
            }
        }
        return normaliseAndQuantise(buf, totalSamples)
    }

    /** Normalise a float buffer to 0.85 peak and quantise to 16-bit signed. */
    private fun normaliseAndQuantise(buf: FloatArray, totalSamples: Int): ShortArray {
        val out = ShortArray(totalSamples)
        var peak = 0f
        for (v in buf) if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
        val norm = if (peak > 0f) 0.85f / peak else 1f
        for (i in 0 until totalSamples) {
            val v = (buf[i] * norm * 0.6f).coerceIn(-1f, 1f)
            out[i] = (v * 32767f).toInt().toShort()
        }
        return out
    }

    // ── Helper building blocks ─────────────────────────────────────────────

    /**
     * Short sine "pluck": sine wave with a fast exponential decay and 4ms
     * attack. Produces a clean, soft tone.
     */
    private fun sinePluck(
        freq: Float,
        durSec: Float,
        amp: Float = 0.5f,
        decayRate: Float = 10f
    ): ShortArray {
        val n = (SFX_SAMPLE_RATE * durSec).toInt()
        val buf = ShortArray(n)
        val twoPi = (2f * PI).toFloat()
        for (i in 0 until n) {
            val t = i.toFloat() / SFX_SAMPLE_RATE
            val attack = min(t / 0.004f, 1f)
            val decay = exp(-decayRate * t)
            val env = attack * decay
            buf[i] = toPcm(sin(twoPi * freq * t) * amp * env)
        }
        return buf
    }

    /** Convert a normalised sample in [-1, 1] to signed 16-bit PCM. */
    private fun toPcm(v: Float): Short {
        val clamped = v.coerceIn(-1f, 1f)
        return (clamped * 32767f).toInt().toShort()
    }

    /** Append [b] to [a] into a new array. */
    private fun concatenate(a: ShortArray, b: ShortArray): ShortArray {
        val out = ShortArray(a.size + b.size)
        System.arraycopy(a, 0, out, 0, a.size)
        System.arraycopy(b, 0, out, a.size, b.size)
        return out
    }

    /** Apply a short linear fade-in to avoid click artifacts. */
    private fun applyFadeIn(buf: ShortArray, fadeSec: Float): ShortArray {
        val fadeSamples = min((SFX_SAMPLE_RATE * fadeSec).toInt(), buf.size)
        for (i in 0 until fadeSamples) {
            val gain = i.toFloat() / fadeSamples
            buf[i] = (buf[i] * gain).toInt().toShort()
        }
        return buf
    }

    // ── WAV writer ─────────────────────────────────────────────────────────

    /**
     * Minimal RIFF/WAV writer: 16-bit PCM, mono, specified [sampleRate].
     * This produces a file that Android [android.media.SoundPool] can load
     * without any codec work.
     */
    private fun writeWav(samples: ShortArray, sampleRate: Int, file: File) {
        val byteCount = samples.size * 2
        val header = ByteArray(44)
        fun putInt(pos: Int, value: Int) {
            header[pos] = (value and 0xff).toByte()
            header[pos + 1] = ((value shr 8) and 0xff).toByte()
            header[pos + 2] = ((value shr 16) and 0xff).toByte()
            header[pos + 3] = ((value shr 24) and 0xff).toByte()
        }
        fun putShort(pos: Int, value: Int) {
            header[pos] = (value and 0xff).toByte()
            header[pos + 1] = ((value shr 8) and 0xff).toByte()
        }

        // "RIFF"
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        putInt(4, 36 + byteCount)
        // "WAVE"
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        // "fmt "
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        putInt(16, 16)                  // fmt chunk size
        putShort(20, 1)                 // audio format = PCM
        putShort(22, 1)                 // channels = 1
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2)      // byte rate: sr * channels * bytesPerSample
        putShort(32, 2)                 // block align
        putShort(34, 16)                // bits per sample
        // "data"
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        putInt(40, byteCount)

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val data = ByteArray(byteCount)
            for (i in samples.indices) {
                val s = samples[i].toInt()
                data[i * 2] = (s and 0xff).toByte()
                data[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
            }
            fos.write(data)
        }
    }
}
