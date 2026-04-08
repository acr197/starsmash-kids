package com.starsmash.kids.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starsmash.kids.settings.AppSettings
import com.starsmash.kids.settings.EffectsIntensity
import com.starsmash.kids.settings.MusicTrack
import com.starsmash.kids.settings.PlayTheme
import com.starsmash.kids.settings.SoundMode
import com.starsmash.kids.settings.TrailLength
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the parent-facing Home/Settings screen.
 *
 * Holds all settings state as a [StateFlow<AppSettings>]. Each toggle or selector
 * on the Home screen calls one of the typed update functions, which creates a new
 * [AppSettings] snapshot and persists it to SharedPreferences.
 *
 * Using [AndroidViewModel] gives access to [Application] context for SharedPreferences
 * without leaking an Activity context.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _settings = MutableStateFlow(AppSettings.load(application))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // ── Setters ──────────────────────────────────────────────────────────────
    // Each setter updates the in-memory state and persists to SharedPreferences
    // on the viewModelScope (off the main thread via the default dispatcher).

    fun setSoundEnabled(enabled: Boolean) = update { it.copy(soundEnabled = enabled) }
    fun setSoundMode(mode: SoundMode) = update { it.copy(soundMode = mode) }
    fun setTrailSoundEnabled(enabled: Boolean) = update { it.copy(trailSoundEnabled = enabled) }
    fun setMusicTrack(track: MusicTrack) = update { it.copy(musicTrack = track) }
    fun setEffectsIntensity(intensity: EffectsIntensity) = update { it.copy(effectsIntensity = intensity) }
    fun setTrailLength(length: TrailLength) = update { it.copy(trailLength = length) }
    fun setPlayTheme(theme: PlayTheme) = update { it.copy(playTheme = theme) }
    fun setKeepScreenAwake(enabled: Boolean) = update { it.copy(keepScreenAwake = enabled) }
    fun setReducedMotion(enabled: Boolean) = update { it.copy(reducedMotion = enabled) }
    fun setFullEmojiMode(enabled: Boolean) = update { it.copy(fullEmojiMode = enabled) }
    fun setIdleDemo(enabled: Boolean) = update { it.copy(idleDemo = enabled) }
    fun setAdaptivePlayEnabled(enabled: Boolean) = update { it.copy(adaptivePlayEnabled = enabled) }
    fun setOverstimulationGuardEnabled(enabled: Boolean) =
        update { it.copy(overstimulationGuardEnabled = enabled) }
    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun update(transform: (AppSettings) -> AppSettings) {
        val newSettings = transform(_settings.value)
        _settings.value = newSettings
        viewModelScope.launch {
            AppSettings.save(getApplication(), newSettings)
        }
    }
}
