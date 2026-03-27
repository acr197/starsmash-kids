package com.starsmash.kids.audio

import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import com.starsmash.kids.settings.SoundMode
import com.starsmash.kids.touch.TouchEventType

/**
 * AudioEngine
 * ===========
 * Plays simple tones in response to touch events using [ToneGenerator].
 *
 * Implementation choice: [ToneGenerator] (android.media) is used rather than
 * [android.media.AudioTrack] PCM synthesis for the following reasons:
 *   1. Zero asset files needed – tones are synthesized by the OS.
 *   2. Low-latency playback (ToneGenerator runs in a native audio HAL thread).
 *   3. Polyphony is handled implicitly by issuing short overlapping tones.
 *   4. No audio file distribution or licensing concerns.
 *
 * Limitations:
 *   - [ToneGenerator] offers a fixed set of DTMF/supervisory tones, not arbitrary
 *     musical notes. We map touch types to the closest-feeling tones.
 *   - Exact timbre and pitch vary across Android versions and device manufacturers.
 *   - Volume control is coarse (0–100 int).
 *
 * Threading: All playback calls are dispatched to a dedicated [HandlerThread] to
 * avoid blocking the main thread and to enforce the cooldown without sleep().
 */
class AudioEngine {

    // ── Constants ─────────────────────────────────────────────────────────

    companion object {
        /** Maximum simultaneous tones (ToneGenerator handles only one at a time; we cap calls). */
        const val MAX_POLYPHONY = 4

        /** Minimum ms between successive plays of the SAME tone type. */
        const val COOLDOWN_MS = 80L

        // Tone durations (ms) per mode
        const val CALM_DURATION_MS = 200
        const val PLAYFUL_DURATION_MS = 100

        // ToneGenerator volume: 0–100
        const val NORMAL_VOLUME = 80
        const val GUARD_VOLUME = 40
    }

    // ── Touch-type to tone mapping ─────────────────────────────────────────
    //
    // ToneGenerator tones chosen for their rough musical feel:
    //   DTMF 0-9 cover various pitches reminiscent of a simple melody.
    //   Supervisory tones (CALL_WAITING, DIAL, etc.) provide accent sounds.
    //
    // Calm mode uses longer durations and lower tones for a gentler feel.
    // Playful mode uses shorter, higher tones for an energetic feel.

    private data class ToneMapping(
        val calmTone: Int,
        val playfulTone: Int
    )

    private val toneMappings = mapOf(
        TouchEventType.SingleTap::class to ToneMapping(
            calmTone = ToneGenerator.TONE_DTMF_1,
            playfulTone = ToneGenerator.TONE_DTMF_5
        ),
        TouchEventType.SingleDrag::class to ToneMapping(
            calmTone = ToneGenerator.TONE_DTMF_2,
            playfulTone = ToneGenerator.TONE_DTMF_6
        ),
        TouchEventType.TwoFingerTap::class to ToneMapping(
            calmTone = ToneGenerator.TONE_DTMF_3,
            playfulTone = ToneGenerator.TONE_DTMF_7
        ),
        TouchEventType.TwoFingerDrag::class to ToneMapping(
            calmTone = ToneGenerator.TONE_DTMF_4,
            playfulTone = ToneGenerator.TONE_DTMF_8
        ),
        TouchEventType.MultiTouchBurst::class to ToneMapping(
            calmTone = ToneGenerator.TONE_DTMF_9,
            playfulTone = ToneGenerator.TONE_DTMF_A
        ),
        TouchEventType.PalmLikeBurst::class to ToneMapping(
            calmTone = ToneGenerator.TONE_PROP_BEEP2,
            playfulTone = ToneGenerator.TONE_PROP_BEEP
        ),
        TouchEventType.RapidTapCluster::class to ToneMapping(
            calmTone = ToneGenerator.TONE_DTMF_B,
            playfulTone = ToneGenerator.TONE_DTMF_C
        ),
        TouchEventType.EdgeEntrySwipe::class to ToneMapping(
            calmTone = ToneGenerator.TONE_PROP_ACK,
            playfulTone = ToneGenerator.TONE_PROP_ACK
        )
    )

    // ── State ─────────────────────────────────────────────────────────────

    private var toneGenerator: ToneGenerator? = null
    private var soundMode: SoundMode = SoundMode.PLAYFUL
    var soundEnabled: Boolean = true
    private var isGuardActive: Boolean = false
    private var activeVolume: Int = NORMAL_VOLUME

    /** Track last play time per tone type to enforce cooldown. */
    private val lastPlayTime = mutableMapOf<Int, Long>()

    /** Count of tones currently "in flight" for polyphony capping. */
    private var activeCount = 0

    private var audioThread: HandlerThread? = null
    private var audioHandler: Handler? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────

    fun start() {
        val thread = HandlerThread("StarSmashAudio").also { it.start() }
        audioThread = thread
        val handler = Handler(thread.looper)
        audioHandler = handler
        handler.post {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, activeVolume)
            } catch (e: RuntimeException) {
                // ToneGenerator can throw if audio focus is unavailable.
                // Silently degrade – the app works without sound.
                toneGenerator = null
            }
        }
    }

    fun stop() {
        audioHandler?.post {
            toneGenerator?.release()
            toneGenerator = null
        }
        audioThread?.quitSafely()
        audioThread = null
        audioHandler = null
    }

    // ── Configuration ─────────────────────────────────────────────────────

    fun setSoundMode(mode: SoundMode) {
        soundMode = mode
    }

    fun setGuardActive(active: Boolean) {
        if (isGuardActive == active) return
        isGuardActive = active
        activeVolume = if (active) GUARD_VOLUME else NORMAL_VOLUME
        audioHandler?.post {
            toneGenerator?.release()
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, activeVolume)
            } catch (e: RuntimeException) {
                toneGenerator = null
            }
        }
    }

    // ── Playback ──────────────────────────────────────────────────────────

    /**
     * Play the tone associated with [eventType].
     * Enforces: soundEnabled, polyphony cap, per-type cooldown.
     * Safe to call from any thread (dispatches to [audioHandler]).
     */
    fun play(eventType: TouchEventType) {
        if (!soundEnabled) return

        val mapping = toneMappings[eventType::class] ?: return
        val toneId = if (soundMode == SoundMode.CALM) mapping.calmTone else mapping.playfulTone
        val duration = if (soundMode == SoundMode.CALM) CALM_DURATION_MS else PLAYFUL_DURATION_MS

        val handler = audioHandler ?: return
        handler.post {
            val gen = toneGenerator ?: return@post

            // Polyphony cap
            if (activeCount >= MAX_POLYPHONY) return@post

            // Cooldown enforcement
            val now = System.currentTimeMillis()
            val last = lastPlayTime[toneId] ?: 0L
            if (now - last < COOLDOWN_MS) return@post

            lastPlayTime[toneId] = now
            activeCount++
            try {
                gen.startTone(toneId, duration)
            } catch (e: Exception) {
                // Silently ignore – audio is non-critical
            }
            handler.postDelayed({ activeCount = (activeCount - 1).coerceAtLeast(0) }, duration.toLong())
        }
    }
}
