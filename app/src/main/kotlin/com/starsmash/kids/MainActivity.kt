package com.starsmash.kids

import android.os.Bundle
import android.view.WindowManager
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

        // Keep screen on flag is managed dynamically by PlayViewModel via FLAG_KEEP_SCREEN_ON,
        // but we set the window reference here so ImmersiveModeHelper can use it.
        ImmersiveModeHelper.applyImmersiveMode(this)

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
    }

    override fun onResume() {
        super.onResume()
        // Re-apply immersive mode on resume; Android can clear it when returning from
        // background, notifications, or permission dialogs.
        ImmersiveModeHelper.applyImmersiveMode(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Re-apply when window regains focus (e.g., after dismissing a dialog).
            ImmersiveModeHelper.applyImmersiveMode(this)
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
