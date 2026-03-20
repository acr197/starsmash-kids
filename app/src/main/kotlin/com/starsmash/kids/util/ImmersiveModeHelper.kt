package com.starsmash.kids.util

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * Helper for applying and maintaining immersive fullscreen mode.
 *
 * ── ANDROID PLATFORM LIMITATIONS ────────────────────────────────────────────
 *
 * Android does NOT provide a way for an app to permanently hide system bars or
 * prevent the user from accessing them.  This is an intentional platform safety
 * decision by Google.
 *
 * What we CAN do:
 *   - Request "sticky immersive" mode, where the system bars are hidden by default
 *     and reappear only briefly when the user swipes from an edge.
 *   - Re-apply immersive mode every time our window regains focus (onWindowFocusChanged)
 *     and every time the activity resumes (onResume), minimising the window in which
 *     bars are visible.
 *
 * What we CANNOT do:
 *   - Prevent a determined child from swiping up or pressing the home/back button.
 *   - Prevent Android's gesture navigation from receiving edge swipes.
 *   - Block the notification shade or power/volume buttons.
 *
 * For a fully locked-down kiosk experience, use Android's official Screen Pinning
 * feature (Settings → Security → Screen Pinning / App Pinning).  Screen Pinning
 * requires a parent PIN to unpin and does prevent navigation.  The HomeScreen
 * explains this to parents via an informational card.
 *
 * API strategy:
 *   - Android 11+ (API 30+): [WindowInsetsController] API.
 *   - Android 8–10 (API 26–29): Deprecated [View.SYSTEM_UI_FLAG_*] flags.
 *   Both paths achieve the same "sticky immersive" behaviour.
 */
object ImmersiveModeHelper {

    /**
     * Apply sticky immersive fullscreen to the given [activity].
     * Safe to call multiple times (idempotent).
     */
    fun applyImmersiveMode(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ path
            activity.window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // API 26–29 path (deprecated flags, still functional)
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }
}
