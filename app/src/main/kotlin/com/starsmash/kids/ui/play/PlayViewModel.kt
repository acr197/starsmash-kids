package com.starsmash.kids.ui.play

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.starsmash.kids.adaptation.AdaptivePlayEngine
import com.starsmash.kids.adaptation.OverstimulationGuard
import com.starsmash.kids.audio.AudioEngine
import com.starsmash.kids.settings.AppSettings
import com.starsmash.kids.touch.TouchEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State snapshot exposed to [PlayScreen].
 *
 * @param stimulusLevel     0.0 = very calm, 1.0 = very energetic. Drives effect scale/density.
 * @param isGuardActive     True when OverstimulationGuard has softened the experience.
 * @param settings          Current app settings (theme, sound, etc.).
 */
data class PlayState(
    val stimulusLevel: Float = 0.3f,
    val isGuardActive: Boolean = false,
    val settings: AppSettings = AppSettings()
)

/**
 * ViewModel for the child-facing play screen.
 *
 * Responsibilities:
 *   - Hold and expose [PlayState] as a [StateFlow].
 *   - Relay classified [TouchEventType]s to [AdaptivePlayEngine] and [OverstimulationGuard].
 *   - Drive [AudioEngine] in response to touch events.
 *   - Keep the adaptive/guard systems in sync with the current settings.
 */
class PlayViewModel(application: Application) : AndroidViewModel(application) {

    private val _playState = MutableStateFlow(PlayState())
    val playState: StateFlow<PlayState> = _playState.asStateFlow()

    private val adaptiveEngine = AdaptivePlayEngine()
    private val overstimGuard = OverstimulationGuard()
    val audioEngine = AudioEngine()

    init {
        val settings = AppSettings.load(application)
        _playState.value = PlayState(settings = settings)

        adaptiveEngine.enabled = settings.adaptivePlayEnabled
        overstimGuard.enabled = settings.overstimulationGuardEnabled
        audioEngine.soundEnabled = settings.soundEnabled
        audioEngine.setSoundMode(settings.soundMode)

        audioEngine.start()
    }

    /**
     * Called by PlayScreen on each classified touch event.
     *
     * Updates the adaptive engine, overstimulation guard, and triggers audio.
     * All downstream state changes are reflected in the next [playState] emission.
     */
    fun onTouchEvent(eventType: TouchEventType, pointerCount: Int) {
        val now = SystemClock.uptimeMillis()

        // Adaptive engine update
        val settings = _playState.value.settings
        if (settings.adaptivePlayEnabled) {
            adaptiveEngine.recordEvent(eventType, pointerCount, now)
        }

        val newLevel = adaptiveEngine.currentLevel()

        // Overstimulation guard update
        val guardActive = if (settings.overstimulationGuardEnabled) {
            overstimGuard.update(newLevel, now)
        } else {
            false
        }

        audioEngine.setGuardActive(guardActive)

        // Emit updated state
        _playState.value = _playState.value.copy(
            stimulusLevel = newLevel,
            isGuardActive = guardActive
        )

        // Play audio (non-blocking via AudioEngine's own thread)
        if (settings.soundEnabled) {
            audioEngine.play(eventType)
        }
    }

    /**
     * Reload settings from SharedPreferences (called when Play screen becomes visible).
     * Syncs engine enable/disable flags with the latest settings.
     */
    fun reloadSettings() {
        viewModelScope.launch {
            val settings = AppSettings.load(getApplication())
            adaptiveEngine.enabled = settings.adaptivePlayEnabled
            overstimGuard.enabled = settings.overstimulationGuardEnabled
            audioEngine.soundEnabled = settings.soundEnabled
            audioEngine.setSoundMode(settings.soundMode)
            _playState.value = _playState.value.copy(settings = settings)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stop()
        adaptiveEngine.reset()
        overstimGuard.reset()
    }
}
