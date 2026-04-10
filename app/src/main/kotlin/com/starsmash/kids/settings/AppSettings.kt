package com.starsmash.kids.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Enumeration of available play themes.
 *
 * Each theme is rendered by [com.starsmash.kids.ui.play.ThemeBackground].
 * Note: SHAPES was removed because it overlapped visually with RAINBOW.
 */
enum class PlayTheme { SPACE, OCEAN, RAINBOW }

/**
 * Enumeration of sound modes.
 * CALM:    soft thumps and water-drop pings, no high-pitched chimes.
 * PLAYFUL: bouncy pops, coin dings, casino-style sparkles.
 */
enum class SoundMode { CALM, PLAYFUL }

/**
 * Enumeration of visual effects intensity. Affects:
 *   - Burst particle count
 *   - Trail life (longer trails on HIGH)
 *   - Star/burst max travel distance
 *   - Background animation speed
 *   - Background vibrancy
 */
enum class EffectsIntensity { LOW, MEDIUM, HIGH }

/**
 * Length of finger-drag trails. Independent of effects intensity so the user
 * can pick "long trails" with low overall effects, or vice versa.
 */
enum class TrailLength { SHORT, MEDIUM, LONG }

/**
 * Background music track choice. NONE disables music entirely.
 *
 * Tracks are Ogg Vorbis assets in res/raw, mastered for seamless looping:
 *   track_01 = "Going Up"
 *   track_02 = "Heavier"
 *   track_03 = "Ocean Bubbles"
 *   track_04 = "Old School Arcade"
 *   track_05 = "Trendy"
 *
 * Previous procedurally-generated tracks (removed):
 *   ARCADE, ADVENTURE, BUBBLE_POP
 */
enum class MusicTrack { NONE, TRACK_01, TRACK_02, TRACK_03, TRACK_04, TRACK_05 }

/**
 * How fast the game should start. Later difficulty still ramps over time,
 * but this picks the starting point so impatient kids don't have to wait
 * through a slow build-up.
 */
enum class StartingDifficulty { GENTLE, MEDIUM, FAST }

/**
 * Which kinds of objects the child can smash. User picks any combination;
 * if they pick none, we fall back to EMOJI so the game is never empty.
 */
enum class SmashCategory { SHAPES, EMOJI, DINOSAURS, TRUCKS, ANIMALS, TOYS, FOOD, SPACE }

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
    val trailSoundEnabled: Boolean = true,
    val musicTrack: MusicTrack = MusicTrack.TRACK_01,
    val effectsIntensity: EffectsIntensity = EffectsIntensity.MEDIUM,
    val trailLength: TrailLength = TrailLength.MEDIUM,
    val playTheme: PlayTheme = PlayTheme.SPACE,
    val startingDifficulty: StartingDifficulty = StartingDifficulty.GENTLE,
    val smashCategories: Set<SmashCategory> = setOf(
        SmashCategory.EMOJI, SmashCategory.DINOSAURS, SmashCategory.TRUCKS,
        SmashCategory.ANIMALS, SmashCategory.TOYS
    ),
    val keepScreenAwake: Boolean = true,
    val reducedMotion: Boolean = false,
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
        private const val KEY_TRAIL_SOUND = "trail_sound_enabled"
        private const val KEY_MUSIC_TRACK = "music_track"
        private const val KEY_EFFECTS_INTENSITY = "effects_intensity"
        private const val KEY_TRAIL_LENGTH = "trail_length"
        private const val KEY_PLAY_THEME = "play_theme"
        private const val KEY_STARTING_DIFFICULTY = "starting_difficulty"
        private const val KEY_SMASH_CATEGORIES = "smash_categories_csv"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_REDUCED_MOTION = "reduced_motion"
        private const val KEY_IDLE_DEMO = "idle_demo"
        private const val KEY_ADAPTIVE_PLAY = "adaptive_play_enabled"
        private const val KEY_OVERSTIM_GUARD = "overstimulation_guard_enabled"
        private const val KEY_HAPTICS = "haptics_enabled"

        /** Restore settings from SharedPreferences. Returns defaults if no prefs exist. */
        fun load(context: Context): AppSettings {
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Theme value may be a stale "SHAPES" from older builds; map to RAINBOW.
            val themeName = prefs.getString(KEY_PLAY_THEME, PlayTheme.SPACE.name) ?: PlayTheme.SPACE.name
            val theme = runCatching { PlayTheme.valueOf(themeName) }
                .getOrDefault(PlayTheme.RAINBOW)
            val categoriesCsv = prefs.getString(KEY_SMASH_CATEGORIES, null)
            val categories: Set<SmashCategory> = if (categoriesCsv.isNullOrBlank()) {
                setOf(
                    SmashCategory.EMOJI, SmashCategory.DINOSAURS, SmashCategory.TRUCKS,
                    SmashCategory.ANIMALS, SmashCategory.TOYS
                )
            } else {
                categoriesCsv.split(',')
                    .mapNotNull { name ->
                        runCatching { SmashCategory.valueOf(name.trim()) }.getOrNull()
                    }
                    .toSet()
            }
            return AppSettings(
                soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
                soundMode = SoundMode.valueOf(
                    prefs.getString(KEY_SOUND_MODE, SoundMode.PLAYFUL.name)!!
                ),
                trailSoundEnabled = prefs.getBoolean(KEY_TRAIL_SOUND, true),
                musicTrack = runCatching {
                    MusicTrack.valueOf(prefs.getString(KEY_MUSIC_TRACK, MusicTrack.TRACK_01.name)!!)
                }.getOrDefault(MusicTrack.TRACK_01),
                effectsIntensity = EffectsIntensity.valueOf(
                    prefs.getString(KEY_EFFECTS_INTENSITY, EffectsIntensity.MEDIUM.name)!!
                ),
                trailLength = runCatching {
                    TrailLength.valueOf(prefs.getString(KEY_TRAIL_LENGTH, TrailLength.MEDIUM.name)!!)
                }.getOrDefault(TrailLength.MEDIUM),
                playTheme = theme,
                startingDifficulty = runCatching {
                    StartingDifficulty.valueOf(
                        prefs.getString(KEY_STARTING_DIFFICULTY, StartingDifficulty.GENTLE.name)!!
                    )
                }.getOrDefault(StartingDifficulty.GENTLE),
                smashCategories = categories,
                keepScreenAwake = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true),
                reducedMotion = prefs.getBoolean(KEY_REDUCED_MOTION, false),
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
                .putBoolean(KEY_TRAIL_SOUND, settings.trailSoundEnabled)
                .putString(KEY_MUSIC_TRACK, settings.musicTrack.name)
                .putString(KEY_EFFECTS_INTENSITY, settings.effectsIntensity.name)
                .putString(KEY_TRAIL_LENGTH, settings.trailLength.name)
                .putString(KEY_PLAY_THEME, settings.playTheme.name)
                .putString(KEY_STARTING_DIFFICULTY, settings.startingDifficulty.name)
                .putString(
                    KEY_SMASH_CATEGORIES,
                    settings.smashCategories.joinToString(",") { it.name }
                )
                .putBoolean(KEY_KEEP_SCREEN_AWAKE, settings.keepScreenAwake)
                .putBoolean(KEY_REDUCED_MOTION, settings.reducedMotion)
                .putBoolean(KEY_IDLE_DEMO, settings.idleDemo)
                .putBoolean(KEY_ADAPTIVE_PLAY, settings.adaptivePlayEnabled)
                .putBoolean(KEY_OVERSTIM_GUARD, settings.overstimulationGuardEnabled)
                .putBoolean(KEY_HAPTICS, settings.hapticsEnabled)
                .apply()
        }
    }
}
