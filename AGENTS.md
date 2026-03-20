# AGENTS.md — Instructions for AI Agents Working on StarSmash Kids

This file contains guidance for AI agents (Claude, GPT, Gemini, etc.) tasked with
modifying, extending, or reviewing this codebase.

---

## Project Summary

StarSmash Kids is a privacy-first, offline-only sensory-play Android app for young
children. It uses Kotlin, Jetpack Compose, Material 3, and Gradle Kotlin DSL.
There is no backend, no analytics, no ads, and no user accounts.

---

## Code Style

- **Kotlin idioms:** Use data classes, sealed classes, extension functions, and
  `when` expressions where they clarify intent. Avoid Java-style verbosity.
- **Coroutines:** Prefer `StateFlow` and `viewModelScope` for async state. Avoid
  `LiveData` (we are Compose-only).
- **Compose:** Hoist state up to ViewModels. Keep Composables stateless where
  possible. Use `collectAsStateWithLifecycle` (not `collectAsState`) for lifecycle safety.
- **Naming:** Follow Kotlin conventions. ViewModel state flows are named
  `_privateFlow` (private) and `publicFlow` (public read-only).
- **Comments:** Write comments explaining *why*, not *what*. Platform limitations
  and heuristic assumptions must always be documented in code comments.

---

## Testing Expectations

- **Business logic is pure Kotlin:** `TouchClassifier`, `AdaptivePlayEngine`, and
  `OverstimulationGuard` must remain free of Android imports so they can be
  tested with plain JUnit4 on the JVM (`./gradlew test`).
- **New logic goes in pure-Kotlin classes first.** If you need Android APIs,
  extract the Android-specific part into a thin adapter/wrapper.
- **Test coverage:** Any new feature in the above classes must include JUnit4 tests
  in the corresponding `*Test.kt` file.
- **Do not add Mockito, Robolectric, or heavy test frameworks** unless strictly
  necessary and explicitly requested. Keep the test dependency footprint minimal.

---

## Privacy-First Design — PRESERVE THIS

The following are non-negotiable constraints. Do not modify them without explicit
instruction from the project owner:

1. **No INTERNET permission.** Do not add `android.permission.INTERNET` to the manifest.
2. **No analytics SDKs.** Do not add Firebase, Crashlytics, Sentry, or any telemetry.
3. **No advertising SDKs.** Do not add AdMob, MoPub, or any ad framework.
4. **No user authentication.** Do not add login, accounts, or cloud sync.
5. **No sensitive permissions.** Do not add CAMERA, RECORD_AUDIO, READ_CONTACTS,
   ACCESS_FINE_LOCATION, READ_EXTERNAL_STORAGE, or similar.
6. **Settings stay local.** SharedPreferences only. Do not sync to cloud.

If a feature requires any of the above, raise it as a question rather than
implementing it silently.

---

## Honest AI Feature Documentation

Both "AI features" (Adaptive Play and Overstimulation Guard) are rule-based
heuristics. When writing code or documentation:

- **Always** describe them as "rule-based threshold logic" or "heuristic engine".
- **Never** describe them as "AI", "machine learning", "neural network", or "model"
  unless a genuine ML implementation is added with explicit approval.
- **Always** document in code comments what the feature can and cannot do.
- **Never** imply clinical or medical benefit.

---

## Child Screen Constraints

The Play screen (`PlayScreen.kt`) must remain:

- **Fullscreen** — no visible buttons, labels, or UI chrome.
- **Touch-only** — no keyboard interaction, no text input.
- **Non-navigable by children** — the back-press guard (two presses within 2s) must
  be preserved. Do not make it easier to exit.
- **Performance-conscious** — effects are rendered in a Compose Canvas; keep the
  effect list size bounded (cap at `MAX_EFFECTS = 200`).

Do not add advertisements, banners, mascots that link to external URLs, or any
content that could expose a child to inappropriate material.

---

## Recruiter-Presentable Code Quality

This project is intended to be reviewed by software engineering recruiters and
hiring managers. Therefore:

- **All public classes and functions must have KDoc comments** explaining their
  purpose, key parameters, and any non-obvious design decisions.
- **Platform limitations must be documented honestly** (e.g., palm detection
  accuracy, fullscreen limitation, ToneGenerator restrictions).
- **No commented-out dead code** in committed files.
- **No TODO comments** without a corresponding issue or PR.
- **Consistent formatting** — use `./gradlew ktlintFormat` if ktlint is added, or
  follow Android Studio default formatting.

---

## Dependency Hygiene

- All dependencies are managed in `gradle/libs.versions.toml`.
- Do not add dependencies directly to `app/build.gradle.kts`; use the version
  catalog alias pattern.
- Keep the dependency count minimal. Prefer AndroidX and Compose BOM-managed
  libraries over third-party alternatives.
- Check for known CVEs before adding new libraries.

---

## File Ownership Summary

| File / Package | Owner | Notes |
|---|---|---|
| `touch/` | Core game logic | Keep Android-free |
| `adaptation/` | Core game logic | Keep Android-free |
| `audio/AudioEngine.kt` | Platform layer | Android `ToneGenerator` |
| `util/ImmersiveModeHelper.kt` | Platform layer | Android `WindowInsetsController` |
| `ui/play/PlayScreen.kt` | Compose UI | Child-facing, keep chrome-free |
| `ui/home/HomeScreen.kt` | Compose UI | Parent-facing, Material 3 |
| `settings/AppSettings.kt` | Data layer | SharedPreferences schema |
| `SECURITY.md` | Documentation | Keep accurate |

---

## Running the Project

```bash
# Unit tests (JVM only, no device needed)
./gradlew test

# Debug build
./gradlew assembleDebug

# Install on device/emulator
./gradlew installDebug
```

The project requires:
- JDK 17+
- Android SDK with Build Tools 34
- `gradle-wrapper.jar` (not committed; run `gradle wrapper` or download manually)
