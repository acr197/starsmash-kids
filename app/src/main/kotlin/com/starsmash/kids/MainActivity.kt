package com.starsmash.kids

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.starsmash.kids.ui.home.HomeScreen
import com.starsmash.kids.ui.play.PlayScreen
import com.starsmash.kids.ui.theme.StarSmashKidsTheme
import com.starsmash.kids.util.ImmersiveModeHelper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Single activity for the entire StarSmash Kids app.
 *
 * Architecture: Single Activity + Compose NavHost.
 * Two destinations: "home" (parent setup screen) and "play" (child fullscreen canvas).
 *
 * Immersive fullscreen is applied here and re-applied on resume and window focus
 * change, because Android can revert immersive mode when the user interacts with
 * system bars or when the app returns from background.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the previous launch crashed, surface the stack trace before doing
        // anything else. This is the only way to debug a sideloaded APK on a
        // device with no adb/logcat access.
        val previousCrash = readAndConsumePreviousCrash()
        if (previousCrash != null) {
            showCrashScreen(
                title = "StarSmash Kids crashed last launch",
                body = previousCrash
            )
            return
        }

        try {
            ImmersiveModeHelper.applyImmersiveMode(this)
        } catch (t: Throwable) {
            // Immersive mode is non-critical; failing here should not block startup.
        }

        try {
            setContent {
                StarSmashKidsTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        StarSmashNavHost()
                    }
                }
            }
        } catch (t: Throwable) {
            // Initial Compose composition failed. Show the stack trace on screen
            // instead of crashing back to the launcher.
            showCrashScreen(
                title = "StarSmash Kids failed to start",
                body = stackTraceString(t)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply immersive mode on resume; Android can clear it when returning from
        // background, notifications, or permission dialogs.
        try {
            ImmersiveModeHelper.applyImmersiveMode(this)
        } catch (_: Throwable) {
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply when window regains focus (e.g., after dismissing a dialog).
            try {
                ImmersiveModeHelper.applyImmersiveMode(this)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Toggle the FLAG_KEEP_SCREEN_ON window flag.
     * Called by PlayViewModel when the user changes the "Keep Screen Awake" setting.
     */
    fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ── Crash diagnostic helpers ────────────────────────────────────────────

    private fun readAndConsumePreviousCrash(): String? {
        return try {
            val file = File(cacheDir, StarSmashApp.LAST_CRASH_FILE)
            if (!file.exists()) return null
            val text = file.readText()
            file.delete()
            text
        } catch (_: Throwable) {
            null
        }
    }

    private fun stackTraceString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    /**
     * Plain Android (non-Compose) crash screen. Used for both "we crashed last
     * time" and "the initial Compose composition just threw" cases. Plain View
     * APIs are used here so this code path can never depend on the same Compose
     * libraries that may have caused the crash in the first place.
     */
    private fun showCrashScreen(title: String, body: String) {
        val pad = (16 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFFFFFBF0.toInt())
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(0xFF1A1A2E.toInt())
            setPadding(0, 0, 0, pad)
        }
        container.addView(titleView)

        val helpView = TextView(this).apply {
            text = "Please screenshot the text below and send it to the developer " +
                "so the crash can be fixed."
            textSize = 14f
            setTextColor(0xFF4A4A6A.toInt())
            setPadding(0, 0, 0, pad)
        }
        container.addView(helpView)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val bodyView = TextView(this).apply {
            text = body
            textSize = 12f
            setTextColor(0xFF1A1A2E.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(bodyView)
        container.addView(scrollView)

        val dismissButton = Button(this).apply {
            text = "Try again"
            setOnClickListener { recreate() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad }
        }
        container.addView(dismissButton)

        // Make sure system bars are visible so the user can read the trace.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        setContentView(container)
    }
}

@Composable
fun StarSmashNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onStartPlaying = { navController.navigate("play") }
            )
        }
        composable("play") {
            PlayScreen(
                onExit = { navController.popBackStack() }
            )
        }
    }
}
