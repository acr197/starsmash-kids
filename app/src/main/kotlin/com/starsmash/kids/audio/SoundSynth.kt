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
        BREEZE
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
        val file = File(cacheDir, "ssk_music_${style.name.lowercase()}.wav")
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
     * ARCADE: upbeat pentatonic arpeggio in C major over a soft bass pulse.
     * Inspired by the cheerful Super Mario / Kirby overworld themes -
     * bouncy, happy, never harsh. ~11 s loop.
     */
    private fun renderMusicLoopArcade(): ShortArray {
        val sr = MUSIC_SAMPLE_RATE
        // Pentatonic C major: C D E G A, across two octaves.
        val arp = floatArrayOf(
            261.63f, 329.63f, 392.00f, 440.00f,
            523.25f, 440.00f, 392.00f, 329.63f
        )
        val bassNotes = floatArrayOf(130.81f, 196.00f) // C3, G3
        val stepSec = 0.35f                              // ~170 BPM-ish
        val totalSteps = arp.size * 4                   // 32 steps = ~11.2s
        val totalSamples = (sr * stepSec * totalSteps).toInt()
        val buf = FloatArray(totalSamples)

        val twoPi = (2f * PI).toFloat()
        for (step in 0 until totalSteps) {
            val note = arp[step % arp.size]
            val startSample = (step * stepSec * sr).toInt()
            val noteSamples = (stepSec * sr * 0.9f).toInt()
            for (i in 0 until noteSamples) {
                val t = i.toFloat() / sr
                val env = exp(-3.2f * t) * min(t / 0.02f, 1f)
                val s = sin(twoPi * note * t) * 0.28f +
                    sin(twoPi * note * 2f * t) * 0.06f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }
            // Bass pulse every two steps.
            if (step % 2 == 0) {
                val bass = bassNotes[(step / 2) % bassNotes.size]
                val bassLen = (stepSec * sr * 1.9f).toInt()
                for (i in 0 until bassLen) {
                    val t = i.toFloat() / sr
                    val env = exp(-1.4f * t) * min(t / 0.02f, 1f)
                    val s = sin(twoPi * bass * t) * 0.22f
                    val idx = startSample + i
                    if (idx < totalSamples) buf[idx] += s * env
                }
            }
        }
        return normaliseAndQuantise(buf, totalSamples)
    }

    /**
     * ADVENTURE: slower melodic loop in A minor with a longer held lead note
     * and a walking bass. Inspired by the exploration themes of early
     * Zelda / Kirby's Dream Land. ~12 s loop.
     */
    private fun renderMusicLoopAdventure(): ShortArray {
        val sr = MUSIC_SAMPLE_RATE
        // A minor melody (A4 C5 E5 D5 C5 E5 D5 B4)
        val melody = floatArrayOf(
            440.00f, 523.25f, 659.25f, 587.33f,
            523.25f, 659.25f, 587.33f, 493.88f
        )
        val bass = floatArrayOf(110.00f, 146.83f, 164.81f, 146.83f) // A2 D3 E3 D3
        val stepSec = 0.42f
        val totalSteps = melody.size * 4
        val totalSamples = (sr * stepSec * totalSteps).toInt()
        val buf = FloatArray(totalSamples)
        val twoPi = (2f * PI).toFloat()

        for (step in 0 until totalSteps) {
            val note = melody[step % melody.size]
            val startSample = (step * stepSec * sr).toInt()
            val noteLen = (stepSec * sr * 0.85f).toInt()
            for (i in 0 until noteLen) {
                val t = i.toFloat() / sr
                // Slower decay for a sustained feel.
                val env = exp(-1.8f * t) * min(t / 0.025f, 1f)
                val s = sin(twoPi * note * t) * 0.26f +
                    sin(twoPi * note * 2f * t) * 0.05f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }
            val bassNote = bass[(step / 2) % bass.size]
            val bassLen = (stepSec * sr * 1.8f).toInt()
            for (i in 0 until bassLen) {
                val t = i.toFloat() / sr
                val env = exp(-1.0f * t) * min(t / 0.03f, 1f)
                val s = sin(twoPi * bassNote * t) * 0.20f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }
        }
        return normaliseAndQuantise(buf, totalSamples)
    }

    /**
     * BUBBLE_POP: gentle G major ostinato over a soft pad, with every other
     * note an octave up - feels like a Kirby / Animal Crossing cosy tune.
     * ~11 s loop.
     */
    private fun renderMusicLoopBubblePop(): ShortArray {
        val sr = MUSIC_SAMPLE_RATE
        // G major pentatonic: G A B D E, with octave jumps
        val lead = floatArrayOf(
            392.00f, 783.99f, 440.00f, 493.88f,
            587.33f, 659.25f, 783.99f, 587.33f
        )
        val pad = floatArrayOf(196.00f, 246.94f, 293.66f) // G3 B3 D4
        val stepSec = 0.32f
        val totalSteps = lead.size * 4
        val totalSamples = (sr * stepSec * totalSteps).toInt()
        val buf = FloatArray(totalSamples)
        val twoPi = (2f * PI).toFloat()

        for (step in 0 until totalSteps) {
            val note = lead[step % lead.size]
            val startSample = (step * stepSec * sr).toInt()
            val noteLen = (stepSec * sr * 0.9f).toInt()
            for (i in 0 until noteLen) {
                val t = i.toFloat() / sr
                val env = exp(-4.5f * t) * min(t / 0.01f, 1f)
                // Triangle-ish by stacking odd harmonics
                val s = sin(twoPi * note * t) * 0.28f +
                    sin(twoPi * note * 3f * t) * 0.04f
                val idx = startSample + i
                if (idx < totalSamples) buf[idx] += s * env
            }
        }
        // Slow sustained pad underneath, cycling every 4 steps.
        val padStepSamples = (stepSec * 4f * sr).toInt()
        for (seg in 0 until (totalSamples / padStepSamples + 1)) {
            val padNote = pad[seg % pad.size]
            val padStart = seg * padStepSamples
            val padLen = (padStepSamples * 1.2f).toInt()
            for (i in 0 until padLen) {
                val t = i.toFloat() / sr
                val env = min(t / 0.4f, 1f) * (1f - min(t / (padLen.toFloat() / sr), 1f) * 0.4f)
                val s = sin(twoPi * padNote * t) * 0.12f
                val idx = padStart + i
                if (idx in 0 until totalSamples) buf[idx] += s * env
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
