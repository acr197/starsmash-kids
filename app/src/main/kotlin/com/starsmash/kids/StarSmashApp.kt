package com.starsmash.kids

import android.app.Application

/**
 * Application subclass for StarSmash Kids.
 *
 * Currently minimal — exists as the correct hook point for any future
 * app-wide initialization (e.g., a future dependency injection graph).
 * Keeping it here also makes the architecture explicit and recruiter-readable.
 */
class StarSmashApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // No analytics, no crash reporters, no ad SDKs.
        // Just a clean application start.
    }
}
