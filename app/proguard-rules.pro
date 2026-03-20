# StarSmash Kids ProGuard / R8 rules
# ====================================
# This file is used only for release builds (minifyEnabled = true in release config).
# Debug builds skip ProGuard entirely.

# ── Kotlin ────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }

# ── Jetpack Compose ───────────────────────────────────────────────────────────
# Compose is largely compatible with R8 out of the box, but keep the runtime
# and key internal classes to avoid stripping.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── AndroidX ──────────────────────────────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# ── App-specific: keep data classes used with StateFlow ───────────────────────
-keep class com.starsmash.kids.settings.AppSettings { *; }
-keep class com.starsmash.kids.ui.play.PlayState { *; }
-keep enum com.starsmash.kids.settings.** { *; }

# ── Keep touch + adaptation classes (used via reflection in tests) ─────────────
-keep class com.starsmash.kids.touch.** { *; }
-keep class com.starsmash.kids.adaptation.** { *; }

# ── Remove logging from release builds ────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
