package com.starsmash.kids.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starsmash.kids.audio.AudioEngineHolder
import com.starsmash.kids.settings.AppSettings
import com.starsmash.kids.settings.EffectsIntensity
import com.starsmash.kids.settings.HighScoreEntry
import com.starsmash.kids.settings.HighScoreStore
import com.starsmash.kids.settings.MusicTrack
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.settings.SmashCategory
import com.starsmash.kids.settings.SoundMode
import com.starsmash.kids.settings.StartingDifficulty
import com.starsmash.kids.settings.TrailLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the parent-facing Home/Settings screen.
 *
 * Holds all settings state plus the high score list as StateFlows. Each
 * toggle or selector on the Home screen calls one of the typed update
 * functions, which creates a new [AppSettings] snapshot and persists it
 * to SharedPreferences.
 *
 * Also owns the background-music "menu playback" hookup: on init it kicks
 * the shared [AudioEngineHolder] engine to start, and whenever music
 * settings change it reflects the change immediately so the music in the
 * menu changes in real time.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _settings = MutableStateFlow(AppSettings.load(application))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _highScores = MutableStateFlow(HighScoreStore.load(application))
    val highScores: StateFlow<List<HighScoreEntry>> = _highScores.asStateFlow()

    private val audioEngine = AudioEngineHolder.get(application.applicationContext)

    init {
        // Reflect the current settings into the shared audio engine, then
        // kick the engine to start (on a background dispatcher because the
        // first launch synthesises WAVs into the cache dir). The engine is
        // idempotent so calling start() repeatedly is a no-op.
        val current = _settings.value
        audioEngine.soundEnabled = current.soundEnabled
        audioEngine.setSoundMode(current.soundMode)
        audioEngine.setMusicTrack(current.musicTrack)
        audioEngine.setMusicVolume(current.musicVolume)
        audioEngine.setSfxVolume(current.sfxVolume)
        audioEngine.setMusicSpeed(1.0f)
        viewModelScope.launch(Dispatchers.IO) {
            try { audioEngine.start() } catch (_: Throwable) {}
        }
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    fun setSoundEnabled(enabled: Boolean) = update { it.copy(soundEnabled = enabled) }
    fun setSoundMode(mode: SoundMode) = update { it.copy(soundMode = mode) }
    fun setTrailSoundEnabled(enabled: Boolean) = update { it.copy(trailSoundEnabled = enabled) }
    fun setMusicTrack(track: MusicTrack) = update { it.copy(musicTrack = track) }
    fun setMusicVolume(volume: Float) = update { it.copy(musicVolume = volume) }
    fun setSfxVolume(volume: Float) = update { it.copy(sfxVolume = volume) }

    /**
     * Cycle to the next music track. Order: TRACK_01 → ... → TRACK_05 → NONE → TRACK_01.
     * Called by the track selector button on the home screen.
     */
    fun cycleMusicTrack() {
        val current = _settings.value.musicTrack
        val next = when (current) {
            MusicTrack.TRACK_01 -> MusicTrack.TRACK_02
            MusicTrack.TRACK_02 -> MusicTrack.TRACK_03
            MusicTrack.TRACK_03 -> MusicTrack.TRACK_04
            MusicTrack.TRACK_04 -> MusicTrack.TRACK_05
            MusicTrack.TRACK_05 -> MusicTrack.NONE
            MusicTrack.NONE -> MusicTrack.TRACK_01
        }
        setMusicTrack(next)
    }
    fun setEffectsIntensity(intensity: EffectsIntensity) = update { it.copy(effectsIntensity = intensity) }
    fun setTrailLength(length: TrailLength) = update { it.copy(trailLength = length) }
    fun setPlayTheme(theme: PlayTheme) = update { it.copy(playTheme = theme) }
    fun setStartingDifficulty(diff: StartingDifficulty) = update { it.copy(startingDifficulty = diff) }

    /**
     * Toggle a single [SmashCategory] on or off. We deliberately allow the
     * empty set (the PlayScreen silently falls back to EMOJI in that case).
     */
    fun toggleSmashCategory(category: SmashCategory) = update {
        val next = if (category in it.smashCategories) {
            it.smashCategories - category
        } else {
            it.smashCategories + category
        }
        it.copy(smashCategories = next)
    }

    fun setKeepScreenAwake(enabled: Boolean) = update { it.copy(keepScreenAwake = enabled) }
    fun setReducedMotion(enabled: Boolean) = update { it.copy(reducedMotion = enabled) }
    fun setIdleDemo(enabled: Boolean) = update { it.copy(idleDemo = enabled) }
    fun setAdaptivePlayEnabled(enabled: Boolean) = update { it.copy(adaptivePlayEnabled = enabled) }
    fun setOverstimulationGuardEnabled(enabled: Boolean) =
        update { it.copy(overstimulationGuardEnabled = enabled) }
    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    /** Re-read the high score list from disk (call when returning from play). */
    fun reloadHighScores() {
        _highScores.value = HighScoreStore.load(getApplication())
    }

    /** Wipe the entire high score leaderboard. */
    fun clearHighScores() {
        HighScoreStore.clear(getApplication())
        _highScores.value = emptyList()
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun update(transform: (AppSettings) -> AppSettings) {
        val prev = _settings.value
        val newSettings = transform(prev)
        _settings.value = newSettings
        // Propagate relevant changes into the shared audio engine immediately
        // so the menu music updates in real time.
        if (prev.soundEnabled != newSettings.soundEnabled) {
            audioEngine.soundEnabled = newSettings.soundEnabled
            audioEngine.refreshSoundEnabled()
        }
        if (prev.soundMode != newSettings.soundMode) {
            audioEngine.setSoundMode(newSettings.soundMode)
        }
        if (prev.musicTrack != newSettings.musicTrack) {
            audioEngine.setMusicTrack(newSettings.musicTrack)
        }
        if (prev.musicVolume != newSettings.musicVolume) {
            audioEngine.setMusicVolume(newSettings.musicVolume)
        }
        if (prev.sfxVolume != newSettings.sfxVolume) {
            audioEngine.setSfxVolume(newSettings.sfxVolume)
        }
        viewModelScope.launch {
            AppSettings.save(getApplication(), newSettings)
        }
    }
}
