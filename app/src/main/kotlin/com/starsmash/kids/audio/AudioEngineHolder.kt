package com.starsmash.kids.audio

import android.content.Context

/**
 * Application-scoped singleton for the [AudioEngine] so that the home
 * (menu) and play screens share the same SoundPool and MediaPlayer
 * instance. Without this, navigating between screens would tear down and
 * re-initialise audio - the music would cut out on every navigation.
 *
 * The engine is created lazily on first [get]. Callers must pass the
 * application context to avoid Activity leaks.
 */
object AudioEngineHolder {
    @Volatile
    private var instance: AudioEngine? = null

    fun get(context: Context): AudioEngine {
        val existing = instance
        if (existing != null) return existing
        synchronized(this) {
            val again = instance
            if (again != null) return again
            val fresh = AudioEngine(context.applicationContext)
            instance = fresh
            return fresh
        }
    }
}
