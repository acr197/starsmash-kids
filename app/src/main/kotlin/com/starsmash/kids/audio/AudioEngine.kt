package com.starsmash.kids.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import com.starsmash.kids.R
import com.starsmash.kids.settings.MusicTrack
import com.starsmash.kids.settings.SoundMode
import com.starsmash.kids.touch.TouchEventType
import java.io.File

/**
 * AudioEngine
 * ===========
 *
 * Plays soft, playful sound effects in response to touch events, and an
 * optional background music loop loaded from Ogg Vorbis assets in res/raw.
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
 *   - SFX samples are generated in code by [SoundSynth] and cached as mono
 *     16-bit WAV files in the app cacheDir.
 *   - Playback goes through [android.media.SoundPool] which handles
 *     polyphony (up to [MAX_STREAMS] concurrent streams) natively.
 *   - Background music uses a single [MediaPlayer] with
 *     [MediaPlayer.setLooping] = true. One player instance persists for
 *     the lifetime of the current track, only recreated on explicit
 *     track changes via [setMusicTrack].
 *   - All init happens off the main thread (caller's responsibility – the
 *     ViewModel calls [start] from a background coroutine).
 *
 * Music asset mapping (old procedural tracks removed):
 *   track_01.ogg = "Going Up"
 *   track_02.ogg = "Heavier"
 *   track_03.ogg = "Ocean Bubbles"
 *   track_04.ogg = "Old School Arcade"
 *   track_05.ogg = "Trendy"
 *
 * Previously used procedural music files (removed):
 *   ssk_music_arcade_v2.wav, ssk_music_adventure_v2.wav, ssk_music_bubble_pop_v2.wav
 */
class AudioEngine(private val context: Context) {

    companion object {
        /** Maximum simultaneous SFX streams SoundPool will mix. */
        const val MAX_STREAMS = 6

        /** Minimum ms between successive plays of the SAME clip. */
        const val COOLDOWN_MS = 55L

        /** Default SFX volume (0–1 SoundPool scale). */
        const val NORMAL_VOLUME = 0.9f

        /** SFX guard ratio: guard volume = user SFX volume × this. */
        private const val GUARD_SFX_RATIO = 0.5f // GUARD_VOLUME / NORMAL_VOLUME

        /** Default background music volume. */
        const val MUSIC_VOLUME = 0.22f

        /** Music guard ratio: guard volume = user music volume × this. */
        private const val GUARD_MUSIC_RATIO = 0.545f // ≈ MUSIC_VOLUME_GUARD / MUSIC_VOLUME
    }

    // ── State ─────────────────────────────────────────────────────────────

    private var soundPool: SoundPool? = null
    private val clipIds = mutableMapOf<SoundSynth.Clip, Int>()
    private val loadedClips = mutableSetOf<SoundSynth.Clip>()
    private val lastPlayTime = mutableMapOf<SoundSynth.Clip, Long>()

    // Single looping MediaPlayer: persists for the current track's lifetime.
    private var currentPlayer: MediaPlayer? = null
    private var musicTrack: MusicTrack = MusicTrack.TRACK_01
    private var currentMusicSpeed: Float = 1.0f

    private var soundMode: SoundMode = SoundMode.PLAYFUL
    private var isGuardActive: Boolean = false

    // User-settable volumes (0.0–1.0). The computed properties below apply
    // the guard ratio on top. Defaults match the original hardcoded constants.
    // @JvmField suppresses Kotlin setter generation so these don't clash with
    // the explicit setMusicVolume() / setSfxVolume() functions below.
    @JvmField var musicVolume: Float = MUSIC_VOLUME
    @JvmField var sfxVolume: Float = NORMAL_VOLUME

    // Effective volumes accounting for the overstimulation guard.
    private val activeMusicVolume: Float
        get() = if (isGuardActive) (musicVolume * GUARD_MUSIC_RATIO).coerceIn(0f, 1f) else musicVolume
    private val activeSfxVolume: Float
        get() = if (isGuardActive) (sfxVolume * GUARD_SFX_RATIO).coerceIn(0f, 1f) else sfxVolume

    var soundEnabled: Boolean = true
    private var started: Boolean = false

    // Audio focus management – ensures music stops when the app loses focus
    // (e.g. user switches apps, screen locks, another app plays audio).
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    private var pausedByFocusLoss: Boolean = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    try {
                        val mp = currentPlayer ?: return@OnAudioFocusChangeListener
                        if (soundEnabled && musicTrack != MusicTrack.NONE && !mp.isPlaying) {
                            mp.start()
                        }
                    } catch (_: Throwable) {}
                }
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                pausedByFocusLoss = true
                try {
                    val mp = currentPlayer ?: return@OnAudioFocusChangeListener
                    if (mp.isPlaying) mp.pause()
                } catch (_: Throwable) {}
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // CAN_DUCK: keep music playing at current volume. Don't
                // pause or clear hasAudioFocus – SoundPool SFX can trigger
                // duck events on some devices, and pausing/clearing focus
                // here permanently stops music.
            }
        }
    }

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

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        requestAudioFocus()

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

        // Start background music for the current track.
        startMusicForCurrentTrack()
    }

    /** Resolve a [MusicTrack] to its res/raw resource ID, or null for NONE. */
    private fun trackResId(track: MusicTrack): Int? = when (track) {
        MusicTrack.TRACK_01 -> R.raw.track_01
        MusicTrack.TRACK_02 -> R.raw.track_02
        MusicTrack.TRACK_03 -> R.raw.track_03
        MusicTrack.TRACK_04 -> R.raw.track_04
        MusicTrack.TRACK_05 -> R.raw.track_05
        MusicTrack.NONE -> null
    }

    private val musicAttributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    /**
     * Create and synchronously prepare a [MediaPlayer] for the given raw
     * resource. Returns null if anything goes wrong.
     */
    private fun createPreparedPlayer(resId: Int): MediaPlayer? {
        return try {
            val mp = MediaPlayer.create(context, resId, musicAttributes, 0)
                ?: return null
            mp.isLooping = true
            mp.setVolume(activeMusicVolume, activeMusicVolume)
            applySpeed(mp, currentMusicSpeed)
            mp
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Start music playback for [musicTrack] using a single [MediaPlayer]
     * with [isLooping] enabled. The player persists until an explicit
     * track change or [stop].
     */
    private fun startMusicForCurrentTrack() {
        releaseAllPlayers()

        val resId = trackResId(musicTrack) ?: return

        try {
            val mp = createPreparedPlayer(resId) ?: return
            currentPlayer = mp
            if (soundEnabled && musicTrack != MusicTrack.NONE) {
                mp.start()
            }
        } catch (_: Throwable) {
            releaseAllPlayers()
        }
    }

    private fun releaseAllPlayers() {
        try { currentPlayer?.stop() } catch (_: Throwable) {}
        try { currentPlayer?.release() } catch (_: Throwable) {}
        currentPlayer = null
    }

    fun stop() {
        started = false
        abandonAudioFocus()
        releaseAllPlayers()

        try { soundPool?.release() } catch (_: Throwable) {}
        soundPool = null
        clipIds.clear()
        loadedClips.clear()
    }

    /**
     * Pause music playback. Called when the app goes to background or the
     * screen is locked so the music doesn't continue playing.
     */
    fun pauseMusic() {
        try {
            val mp = currentPlayer ?: return
            if (mp.isPlaying) mp.pause()
        } catch (_: Throwable) {}
        abandonAudioFocus()
    }

    /**
     * Resume music playback after a [pauseMusic] call, provided sound is
     * still enabled and a music track is selected.
     */
    fun resumeMusic() {
        requestAudioFocus()
        if (!hasAudioFocus) return
        try {
            val mp = currentPlayer ?: return
            if (soundEnabled && musicTrack != MusicTrack.NONE && !mp.isPlaying) {
                mp.start()
            }
        } catch (_: Throwable) {}
    }

    // ── Configuration ─────────────────────────────────────────────────────

    fun setSoundMode(mode: SoundMode) {
        soundMode = mode
    }

    /**
     * Change the active music track. If the new track is NONE the music
     * stops. Otherwise the existing player is released and a fresh
     * one starts on the chosen track's loop.
     */
    fun setMusicTrack(track: MusicTrack) {
        if (musicTrack == track) return
        musicTrack = track
        if (!started) return
        startMusicForCurrentTrack()
    }

    /**
     * Adjust the playback rate of the background music. Used to speed up
     * the tempo as gameplay progresses.
     *
     * Rate scales linearly: 1.0x at 0 stars → 2.0x at 150 stars, capped at 2.0x.
     */
    fun setMusicSpeed(speed: Float) {
        val clamped = speed.coerceIn(1.0f, 2.0f)
        currentMusicSpeed = clamped
        applySpeed(currentPlayer, clamped)
    }

    private fun applySpeed(mp: MediaPlayer?, speed: Float) {
        if (mp == null) return
        try {
            val params = mp.playbackParams.setSpeed(speed)
            mp.playbackParams = params
        } catch (_: Throwable) {
            // Older Android / unsupported codec - silently ignore.
        }
    }

    /**
     * Set the background music volume (0.0–1.0). Applied immediately to any
     * playing MediaPlayer instances.
     */
    fun setMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0f, 1f)
        applyMusicVolume()
    }

    /**
     * Set the SFX volume (0.0–1.0). Applied at the next clip playback since
     * SoundPool reads the volume at play() time.
     */
    fun setSfxVolume(volume: Float) {
        sfxVolume = volume.coerceIn(0f, 1f)
    }

    /** Push the current [activeMusicVolume] to the live MediaPlayer. */
    private fun applyMusicVolume() {
        val vol = activeMusicVolume
        try {
            currentPlayer?.setVolume(vol, vol)
        } catch (_: Throwable) {}
    }

    fun setGuardActive(active: Boolean) {
        if (isGuardActive == active) return
        isGuardActive = active
        applyMusicVolume()
    }

    /**
     * React to the `soundEnabled` master switch changing. Pauses/resumes
     * the background music and mutes all SFX playback.
     */
    fun refreshSoundEnabled() {
        val mp = currentPlayer ?: return
        try {
            if (soundEnabled && musicTrack != MusicTrack.NONE) {
                if (!mp.isPlaying) mp.start()
            } else {
                if (mp.isPlaying) mp.pause()
            }
        } catch (_: Throwable) {}
    }

    // ── Audio Focus ───────────────────────────────────────────────────────

    /**
     * Request audio focus so the system knows we're actively playing audio.
     * Other apps that respect audio focus will duck or pause when we hold it.
     */
    fun requestAudioFocus() {
        if (hasAudioFocus) return
        val am = audioManager ?: return
        // Abandon any stale request before building a new one.
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()
        audioFocusRequest = request
        hasAudioFocus = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Release audio focus when the app goes to background or audio stops.
     */
    fun abandonAudioFocus() {
        val am = audioManager ?: return
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        hasAudioFocus = false
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
            is TouchEventType.SingleTap -> {
                val bank = if (playful) PLAYFUL_TAPS else CALM_TAPS
                nonRepeating(bank)
            }
            is TouchEventType.SingleDrag -> {
                // Drags fire rapidly, so CALM drags use the ultra-quiet
                // HUSH bank (barely-there whispered ticks) instead of the
                // full calm-tap bank. Playful stays energetic.
                val bank = if (playful) PLAYFUL_TAPS else CALM_DRAGS
                nonRepeating(bank)
            }
            is TouchEventType.TwoFingerTap,
            is TouchEventType.TwoFingerDrag -> {
                if (playful) SoundSynth.Clip.BOING else SoundSynth.Clip.WOOD_TAP
            }
            is TouchEventType.MultiTouchBurst -> {
                if (playful) SoundSynth.Clip.SPARKLE_UP else SoundSynth.Clip.CHIME_SOFT
            }
            is TouchEventType.PalmLikeBurst -> {
                if (playful) SoundSynth.Clip.SOFT_BOOM else SoundSynth.Clip.BREEZE
            }
            is TouchEventType.RapidTapCluster -> {
                if (playful) SoundSynth.Clip.COIN_DING else SoundSynth.Clip.CHIME_SOFT
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
            pool.play(id, activeSfxVolume, activeSfxVolume, 1, 0, 1f)
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

    // CALM-mode drag sounds: ultra-quiet hushed ticks only. Drags fire rapidly,
    // so these need to be barely audible per-event to feel soothing.
    private val CALM_DRAGS = listOf(
        SoundSynth.Clip.HUSH_LOW,
        SoundSynth.Clip.HUSH_MID
    )
}
