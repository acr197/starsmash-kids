# StarSmash Kids

**A touch-play world for little hands.**

---

## Overview

StarSmash Kids is a fullscreen, sensory-play Android app designed for young children (ages 0–5). Children tap, swipe, and smash the screen with their hands and fingers; the app responds with colourful visual effects and playful sounds. There are no goals, no failure states, and no screens except the play canvas.

The app is entirely offline, has no ads, no analytics, no login, and collects zero data.

---

## Recruiter Summary

This project demonstrates a production-quality Android app built with Jetpack Compose, Material 3, Kotlin, and Gradle Kotlin DSL in a single-activity architecture. It features a custom multi-touch Canvas renderer, a behavioural adaptive engine, and a settings-driven state management layer — all with full unit test coverage of the pure-Kotlin business logic. The codebase is intentionally honest about platform limits and heuristic vs. ML distinctions.

---

## AI Features

Two "smart" features are implemented. Both are honest rule-based heuristics — not machine learning:

### Adaptive Play

**What it does:** Tracks the child's tap rate, pointer count, and burst frequency over a rolling 10-second window. Outputs a `stimulusLevel` float (0.0–1.0) that scales visual effect size and audio volume up or down in real time.

**What it is NOT:** It is not a machine learning model, neural network, or classifier. It is arithmetic on a sliding event buffer with exponential moving average smoothing. No data leaves the device. No model is trained or updated.

### Overstimulation Guard

**What it does:** If `stimulusLevel` exceeds 0.85 for more than 5 consecutive seconds, the guard activates — effects scale down by ~40% and audio volume drops. After 8 seconds of calmer activity (level < 0.5), the guard releases automatically.

**What it is NOT:** It is not AI, not ML, and not a medical device. It is a simple timer-and-threshold rule. It has no clinical basis; it is a thoughtful UX decision.

Both features can be toggled independently by parents from the Home screen.

---

## Feature List

- Fullscreen immersive play canvas — no visible UI chrome for children
- Multi-touch support: single tap, drag, two-finger, palm, rapid cluster
- Seven distinct visual effect types: star bursts, sparkles, rainbow trails, confetti, ripple waves, bubbles, arc bridges
- Four play themes: Space, Ocean, Rainbow, Shapes — each with its own colour palette
- Three effects intensity levels: Low, Medium, High
- Two sound modes: Calm (pentatonic-ish, slow) and Playful (bright, fast)
- Adaptive Play — real-time activity level tracking
- Overstimulation Guard — automatic intensity reduction
- Reduced Motion mode — simpler, slower effects for sensitive children
- Full Emoji Mode — decorative emoji on effects
- Haptic feedback (vibration) on touch events
- Keep Screen Awake option — prevents screen lock during play
- Idle Demo mode — (scaffold for future idle animation)
- Portrait orientation lock — consistent touch target geometry
- No ads, no analytics, no internet, no login

---

## Screenshots

*Build the debug APK and run on a device or emulator to see the app in action.*

*(Placeholder — screenshots to be added after first device run)*

---

## Architecture

```
MainActivity (single activity)
└── StarSmashNavHost (NavHost)
    ├── HomeScreen (LazyColumn, parent-facing)
    │   └── HomeViewModel (AppSettings StateFlow → SharedPreferences)
    └── PlayScreen (fullscreen Canvas, child-facing)
        └── PlayViewModel
            ├── TouchClassifier (pure Kotlin, platform-free)
            ├── AdaptivePlayEngine (pure Kotlin, platform-free)
            ├── OverstimulationGuard (pure Kotlin, platform-free)
            └── AudioEngine (ToneGenerator, background HandlerThread)
```

**Pattern:** Single Activity + Compose Navigation. Two routes: `"home"` and `"play"`.

**State:** `AppSettings` is a Kotlin data class persisted to `SharedPreferences`. `PlayState` is a `StateFlow<>` updated every touch event.

**Threading:** UI runs on the main thread. Audio dispatches to a dedicated `HandlerThread`. All business logic (classifier, adaptive engine, guard) is synchronous pure Kotlin.

---

## Touch Model

Touches from the Compose `pointerInput` API are assembled into `TouchFrame` objects (a snapshot of all active pointers at one instant) and passed to `TouchClassifier`. The classifier is stateful — it tracks pointer-down positions and recent tap timestamps — and emits a `TouchEventType` sealed class variant:

| Type | Trigger |
|---|---|
| `SingleTap` | 1 pointer, minimal movement |
| `SingleDrag` | 1 pointer moving > slop threshold |
| `TwoFingerTap` | 2 pointers, minimal movement |
| `TwoFingerDrag` | 2 pointers moving |
| `MultiTouchBurst(count)` | 3+ pointers |
| `PalmLikeBurst` | 4+ pointers with large contact area (device-dependent) |
| `RapidTapCluster` | 4+ taps within 800ms |
| `EdgeEntrySwipe` | Pointer starts within 30px of screen edge |

Palm detection is an acknowledged heuristic: `touchMajor` (contact diameter) is not reliably reported by all Android hardware. The code comments are explicit about this limitation.

---

## Parent Setup Flow

1. Launch app → **Home Screen** appears (parent-facing, Material 3 theme).
2. Configure sound, theme, intensity, and feature toggles.
3. Tap **"Start Smashing!"** → navigates to fullscreen play canvas.
4. Child plays; parent can return by pressing back twice within 2 seconds.
5. For a fully locked session, enable Android Screen Pinning (see in-app card).

---

## Fullscreen Limitation — Honest Explanation

Android **does not permit apps to permanently prevent system navigation**. This is a deliberate platform safety decision by Google.

What the app does:
- Requests "sticky immersive" mode (system bars hidden, reappear transiently on edge swipe).
- Re-applies immersive mode on every `onResume` and `onWindowFocusChanged` call.
- Requires two back-presses within 2 seconds to exit the play screen.

What the app cannot do:
- Prevent a child from swiping up or pressing the Home button.
- Block Android gesture navigation edge swipes (Android 10+).
- Prevent the notification shade from appearing.

**For truly locked kiosk use:** enable Android's built-in **Screen Pinning** (Settings → Security → Screen Pinning / App Pinning). A parent PIN is required to unpin.

---

## Privacy and Security

- **No internet permission.** `INTERNET` is intentionally absent from the manifest.
- **No data collected.** No analytics, no crash reporters, no telemetry.
- **No user accounts.** No login, no sign-up, no cloud sync.
- **Local settings only.** SharedPreferences contain only benign toggle states.
- **No microphone, camera, location, or storage permissions.**
- **Minimum permissions:** `VIBRATE` (optional haptics) and `WAKE_LOCK` (screen awake during play).

See `SECURITY.md` for the full security posture.

---

## Local Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later, **or** command-line tools with JDK 17+
- Android SDK with Build Tools 34

### Build

```bash
# Clone the repo
git clone <repo-url>
cd starsmash-kids

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Install on connected device / emulator
./gradlew installDebug
```

> **Note:** `gradle-wrapper.jar` is not committed to this repository (it is a binary). Run `gradle wrapper` once locally or download it from the Gradle website. The wrapper is configured to use **Gradle 8.4** and **AGP 8.2.2**.

---

## Debug APK Location

After `./gradlew assembleDebug`:

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Release Signing

To build a signed release APK:

1. Generate a keystore: `keytool -genkey -v -keystore starsmash.jks -alias starsmash -keyalg RSA -keysize 2048 -validity 10000`
2. Add signing config to `app/build.gradle.kts` under `signingConfigs { release { ... } }`.
3. Store keystore credentials in environment variables or `local.properties` — **never commit them to git**.
4. Run `./gradlew assembleRelease`.

---

## Why This Project Is Interesting

- **Real multi-touch classification in pure Kotlin** — no ML libraries, fully unit-testable.
- **Heuristic adaptive engine** — honestly documented, predictable, debuggable.
- **Zero-dependency business logic** — `TouchClassifier`, `AdaptivePlayEngine`, and `OverstimulationGuard` have no Android imports and are covered by plain JUnit4 tests.
- **Privacy-first** — the manifest tells the full story in one glance.
- **Honest engineering** — every limitation (palm detection accuracy, fullscreen limitations, heuristic vs. ML) is documented in code and README.

---

## AI Collaboration Workflow

This project was developed with AI assistance (Claude). The AI generated code was reviewed for:

- Correctness of Android API usage
- Honesty in comments about heuristic vs. ML approaches
- Privacy implications of any new code
- Test coverage completeness

The adaptive and guard features are intentionally labelled "rule-based" throughout the codebase to avoid overstating their sophistication.

---

## Product and UX Decisions

| Decision | Rationale |
|---|---|
| Portrait-only lock | Rotation disrupts the animation loop and exposes nav bars |
| No goals or failure states | Developmentally appropriate for ages 0–5 |
| Two back-presses to exit | Reduces accidental exits without preventing intentional ones |
| ToneGenerator over AudioTrack | Simpler, no audio assets, OS-native latency |
| Material 3 on Home only | Parent-facing screen benefits from familiar UI; play screen must be chrome-free |
| SharedPreferences over DataStore | Synchronous read at startup avoids async loading UI; settings are small |

---

## Platform Constraints and Honest Tradeoffs

| Constraint | Impact |
|---|---|
| `touchMajor` not reliable on all devices | Palm detection is a best-effort heuristic; may miss or false-positive on some hardware |
| Android gesture nav (API 30+) intercepts edge swipes before Compose | Full back-gesture prevention is impossible without Screen Pinning |
| `ToneGenerator` tone palette is limited | Tones feel "telephony-like" rather than musical; AudioTrack PCM synthesis would sound better but requires more complexity |
| No Compose `touchMajor` API | The Compose pointer API doesn't expose `AXIS_TOUCH_MAJOR`; would require interop with raw `MotionEvent` for better palm detection |

---

## Support

If this project is useful or delights a child, consider buying the developer a coffee:

[buymeacoffee.com/AndrewRy](https://buymeacoffee.com/AndrewRy)
