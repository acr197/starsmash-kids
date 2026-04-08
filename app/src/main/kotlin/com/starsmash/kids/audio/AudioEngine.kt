package com.starsmash.kids.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.starsmash.kids.settings.MusicTrack
import com.starsmash.kids.settings.SoundMode
import com.starsmash.kids.touch.TouchEventType
import java.io.File

/**
 * AudioEngine
 * ===========
 *
 * Plays soft, playful sound effects in response to touch events, and an
 * optional procedurally-generated background music loop.
 *
 * Sound design philosophy:
 *   - NO dial tones, beeps, or anything that sounds like a phone.
 *   - Short (<300 ms) sample-based pops, chimes and soft thuds.
 *   - Two explicit moods:
 *       PLAYFUL → bouncy pops, coin dings, small sparkles
 *       CALM    → water drops, wood taps, glass pings, soft breeze swells
 *   - Each tap randomises across a small per-mood bank so the same sound
 *     never plays twice in a row.
 *
 * Implementation:
 *   - Samples are generated in code by [SoundSynth] and cached as mono
 *     16-bit WAV files in the app cacheDir.
 *   - Playback goes through [android.media.SoundPool] which handles
 *     polyphony (up to [MAX_STREAMS] concurrent streams) natively.
 *   - Background music uses [android.media.MediaPlayer] with looping,
 *     because SoundPool's max clip size is too restrictive for a ~12 s loop.
 *   - All init happens off the main thread (caller's responsibility – the
 *     ViewModel calls [start] from a background coroutine).
 */
class AudioEngine(private val context: Context) {

    companion object {
        /** Maximum simultaneous SFX streams SoundPool will mix. */
        const val MAX_STREAMS = 6

        /** Minimum ms between successive plays of the SAME clip. */
        const val COOLDOWN_MS = 55L

        /** SFX volume multiplier at normal level (0–1 SoundPool scale). */
        const val NORMAL_VOLUME = 0.9f

        /** SFX volume when the overstimulation guard is active. */
        const val GUARD_VOLUME = 0.45f

        /** Background music volume. */
        const val MUSIC_VOLUME = 0.22f
        const val MUSIC_VOLUME_GUARD = 0.12f
    }

    // ── State ─────────────────────────────────────────────────────────────

    private var soundPool: SoundPool? = null
    private val clipIds = mutableMapOf<SoundSynth.Clip, Int>()
    private val loadedClips = mutableSetOf<SoundSynth.Clip>()
    private val lastPlayTime = mutableMapOf<SoundSynth.Clip, Long>()

    private var musicPlayer: MediaPlayer? = null
    private var musicTrack: MusicTrack = MusicTrack.ARCADE

    private var soundMode: SoundMode = SoundMode.PLAYFUL
    private var isGuardActive: Boolean = false
    private var activeVolume: Float = NORMAL_VOLUME
    private var activeMusicVolume: Float = MUSIC_VOLUME

    var soundEnabled: Boolean = true
    private var started: Boolean = false

    // Remember the last clip used in each category so we don't repeat.
    private var lastTapClip: SoundSynth.Clip? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /**
     * Initialise SoundPool, synthesize (if not cached) all SFX, and start
     * loading them. Safe to call multiple times; subsequent calls are no-ops.
     *
     * This does real file I/O – call from a background thread.
     */
    fun start() {
        if (started) return
        started = true

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        soundPool = pool

        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                val clip = clipIds.entries.firstOrNull { it.value == sampleId }?.key
                if (clip != null) loadedClips.add(clip)
            }
        }

        // Generate SFX bank into cacheDir and load into the pool.
        try {
            val sfxDir = File(context.cacheDir, "ssk_sfx")
            val files = SoundSynth.generateSfxBank(sfxDir)
            files.forEach { (clip, file) ->
                val id = pool.load(file.absolutePath, 1)
                clipIds[clip] = id
            }
        } catch (t: Throwable) {
            // Audio is non-critical. If synthesis / file I/O fails we fall
            // back to silence rather than crashing the app.
        }

        // Start background music for the current track (async - safe to fail).
        startMusicForCurrentTrack()
    }

    private fun startMusicForCurrentTrack() {
        // Tear down any existing player first.
        try { musicPlayer?.stop() } catch (_: Throwable) {}
        try { musicPlayer?.release() } catch (_: Throwable) {}
        musicPlayer = null

        if (musicTrack == MusicTrack.NONE) return

        val style = when (musicTrack) {
            MusicTrack.ARCADE -> SoundSynth.MusicStyle.ARCADE
            MusicTrack.ADVENTURE -> SoundSynth.MusicStyle.ADVENTURE
            MusicTrack.BUBBLE_POP -> SoundSynth.MusicStyle.BUBBLE_POP
            MusicTrack.NONE -> return
        }
        try {
            val musicFile = SoundSynth.generateMusicLoop(
                File(context.cacheDir, "ssk_music"), style
            )
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(musicFile.absolutePath)
            mp.isLooping = true
            mp.setVolume(activeMusicVolume, activeMusicVolume)
            mp.setOnPreparedListener {
                if (soundEnabled && musicTrack != MusicTrack.NONE) {
                    try { it.start() } catch (_: Throwable) {}
                }
            }
            mp.prepareAsync()
            musicPlayer = mp
        } catch (_: Throwable) {
            musicPlayer = null
        }
    }

    fun stop() {
        started = false
        try { musicPlayer?.stop() } catch (_: Throwable) {}
        try { musicPlayer?.release() } catch (_: Throwable) {}
        musicPlayer = null

        try { soundPool?.release() } catch (_: Throwable) {}
        soundPool = null
        clipIds.clear()
        loadedClips.clear()
    }

    // ── Configuration ─────────────────────────────────────────────────────

    fun setSoundMode(mode: SoundMode) {
        soundMode = mode
    }

    /**
     * Change the active music track. If the new track is NONE the music
     * stops. Otherwise the existing player is torn down and a fresh player
     * starts on the chosen track's loop.
     */
    fun setMusicTrack(track: MusicTrack) {
        if (musicTrack == track) return
        musicTrack = track
        if (!started) return
        startMusicForCurrentTrack()
    }

    fun setGuardActive(active: Boolean) {
        if (isGuardActive == active) return
        isGuardActive = active
        activeVolume = if (active) GUARD_VOLUME else NORMAL_VOLUME
        activeMusicVolume = if (active) MUSIC_VOLUME_GUARD else MUSIC_VOLUME
        try {
            musicPlayer?.setVolume(activeMusicVolume, activeMusicVolume)
        } catch (_: Throwable) {}
    }

    /**
     * React to the `soundEnabled` master switch changing. Pauses/resumes
     * the background music and mutes all SFX playback.
     */
    fun refreshSoundEnabled() {
        val mp = musicPlayer ?: return
        try {
            if (soundEnabled && musicTrack != MusicTrack.NONE) {
                if (!mp.isPlaying) mp.start()
            } else {
                if (mp.isPlaying) mp.pause()
            }
        } catch (_: Throwable) {}
    }

    // ── Playback ──────────────────────────────────────────────────────────

    /**
     * Play an appropriate sound for [eventType]. Picks from a mood-specific
     * bank and alternates to avoid immediate repeats.
     */
    fun play(eventType: TouchEventType) {
        if (!soundEnabled) return
        val clip = chooseClip(eventType) ?: return
        playClip(clip)
    }

    /** Fire the coin-ding when a floating target is successfully hit. */
    fun playTargetHit() {
        if (!soundEnabled) return
        val clip = when (soundMode) {
            SoundMode.PLAYFUL -> SoundSynth.Clip.COIN_DING
            SoundMode.CALM -> SoundSynth.Clip.GLASS_PING
        }
        playClip(clip)
    }

    private fun chooseClip(eventType: TouchEventType): SoundSynth.Clip? {
        val playful = soundMode == SoundMode.PLAYFUL
        return when (eventType) {
            is TouchEventType.SingleTap,
            is TouchEventType.SingleDrag -> {
                val bank = if (playful) PLAYFUL_TAPS else CALM_TAPS
                nonRepeating(bank)
            }
            is TouchEventType.TwoFingerTap,
            is TouchEventType.TwoFingerDrag -> {
                if (playful) SoundSynth.Clip.BOING else SoundSynth.Clip.WATER_DROP
            }
            is TouchEventType.MultiTouchBurst -> {
                if (playful) SoundSynth.Clip.SPARKLE_UP else SoundSynth.Clip.CHIME_SOFT
            }
            is TouchEventType.PalmLikeBurst -> {
                if (playful) SoundSynth.Clip.SOFT_BOOM else SoundSynth.Clip.BREEZE
            }
            is TouchEventType.RapidTapCluster -> {
                if (playful) SoundSynth.Clip.COIN_DING else SoundSynth.Clip.GLASS_PING
            }
            is TouchEventType.EdgeEntrySwipe -> null // silent – don't reward edge gestures
        }
    }

    private fun nonRepeating(bank: List<SoundSynth.Clip>): SoundSynth.Clip {
        val candidates = if (bank.size > 1 && lastTapClip != null) {
            bank.filter { it != lastTapClip }
        } else bank
        val pick = candidates[(Math.random() * candidates.size).toInt()
            .coerceIn(0, candidates.size - 1)]
        lastTapClip = pick
        return pick
    }

    private fun playClip(clip: SoundSynth.Clip) {
        val pool = soundPool ?: return
        val id = clipIds[clip] ?: return
        if (clip !in loadedClips) return // still loading – drop silently

        // Cooldown so we don't machine-gun the exact same sample.
        val now = System.currentTimeMillis()
        val last = lastPlayTime[clip] ?: 0L
        if (now - last < COOLDOWN_MS) return
        lastPlayTime[clip] = now

        try {
            pool.play(id, activeVolume, activeVolume, 1, 0, 1f)
        } catch (_: Throwable) {
            // Ignore – audio is non-critical.
        }
    }

    // ── Sound banks ───────────────────────────────────────────────────────

    private val PLAYFUL_TAPS = listOf(
        SoundSynth.Clip.POP_HIGH,
        SoundSynth.Clip.POP_MID,
        SoundSynth.Clip.POP_LOW,
        SoundSynth.Clip.BOING
    )

    private val CALM_TAPS = listOf(
        SoundSynth.Clip.WATER_DROP,
        SoundSynth.Clip.WOOD_TAP,
        SoundSynth.Clip.GLASS_PING
    )
}
