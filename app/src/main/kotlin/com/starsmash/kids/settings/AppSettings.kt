package com.starsmash.kids.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Enumeration of available play themes.
 * Each corresponds to a distinct color palette in Color.kt.
 */
enum class PlayTheme { SPACE, OCEAN, RAINBOW, SHAPES }

/**
 * Enumeration of sound modes.
 * CALM: slower, pentatonic notes with gentle character.
 * PLAYFUL: faster, brighter tones with more energy.
 */
enum class SoundMode { CALM, PLAYFUL }

/**
 * Enumeration of visual effects intensity.
 */
enum class EffectsIntensity { LOW, MEDIUM, HIGH }

/**
 * Immutable snapshot of all user-facing settings.
 *
 * All fields have safe defaults that produce a pleasant out-of-the-box experience.
 * Settings are persisted to SharedPreferences via [AppSettings.save] and
 * restored via [AppSettings.load].
 */
data class AppSettings(
    val soundEnabled: Boolean = true,
    val soundMode: SoundMode = SoundMode.PLAYFUL,
    val effectsIntensity: EffectsIntensity = EffectsIntensity.MEDIUM,
    val playTheme: PlayTheme = PlayTheme.SPACE,
    val keepScreenAwake: Boolean = true,
    val reducedMotion: Boolean = false,
    val fullEmojiMode: Boolean = true,
    val idleDemo: Boolean = false,
    val adaptivePlayEnabled: Boolean = true,
    val overstimulationGuardEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
) {
    companion object {
        private const val PREFS_NAME = "starsmash_settings"

        // SharedPreferences keys
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_SOUND_MODE = "sound_mode"
        private const val KEY_EFFECTS_INTENSITY = "effects_intensity"
        private const val KEY_PLAY_THEME = "play_theme"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_REDUCED_MOTION = "reduced_motion"
        private const val KEY_FULL_EMOJI_MODE = "full_emoji_mode"
        private const val KEY_IDLE_DEMO = "idle_demo"
        private const val KEY_ADAPTIVE_PLAY = "adaptive_play_enabled"
        private const val KEY_OVERSTIM_GUARD = "overstimulation_guard_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"

        /** Restore settings from SharedPreferences. Returns defaults if no prefs exist. */
        fun load(context: Context): AppSettings {
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AppSettings(
                soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
                soundMode = SoundMode.valueOf(
                    prefs.getString(KEY_SOUND_MODE, SoundMode.PLAYFUL.name)!!
                ),
                effectsIntensity = EffectsIntensity.valueOf(
                    prefs.getString(KEY_EFFECTS_INTENSITY, EffectsIntensity.MEDIUM.name)!!
                ),
                playTheme = PlayTheme.valueOf(
                    prefs.getString(KEY_PLAY_THEME, PlayTheme.SPACE.name)!!
                ),
                keepScreenAwake = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true),
                reducedMotion = prefs.getBoolean(KEY_REDUCED_MOTION, false),
                fullEmojiMode = prefs.getBoolean(KEY_FULL_EMOJI_MODE, true),
                idleDemo = prefs.getBoolean(KEY_IDLE_DEMO, false),
                adaptivePlayEnabled = prefs.getBoolean(KEY_ADAPTIVE_PLAY, true),
                overstimulationGuardEnabled = prefs.getBoolean(KEY_OVERSTIM_GUARD, true),
                hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true)
            )
        }

        /** Persist the given settings snapshot to SharedPreferences. */
        fun save(context: Context, settings: AppSettings) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled)
                .putString(KEY_SOUND_MODE, settings.soundMode.name)
                .putString(KEY_EFFECTS_INTENSITY, settings.effectsIntensity.name)
                .putString(KEY_PLAY_THEME, settings.playTheme.name)
                .putBoolean(KEY_KEEP_SCREEN_AWAKE, settings.keepScreenAwake)
                .putBoolean(KEY_REDUCED_MOTION, settings.reducedMotion)
                .putBoolean(KEY_FULL_EMOJI_MODE, settings.fullEmojiMode)
                .putBoolean(KEY_IDLE_DEMO, settings.idleDemo)
                .putBoolean(KEY_ADAPTIVE_PLAY, settings.adaptivePlayEnabled)
                .putBoolean(KEY_OVERSTIM_GUARD, settings.overstimulationGuardEnabled)
                .putBoolean(KEY_HAPTICS, settings.hapticsEnabled)
                .apply()
        }
    }
}
