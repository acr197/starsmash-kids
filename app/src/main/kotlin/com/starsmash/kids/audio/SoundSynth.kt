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
 * Sample rate is 44100 Hz (16-bit mono).
 *
 * Note: background music generation (ARCADE, ADVENTURE, BUBBLE_POP) was
 * removed. Music now plays from Ogg Vorbis assets in res/raw via AudioEngine.
 */
object SoundSynth {

    const val SFX_SAMPLE_RATE = 44100

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
