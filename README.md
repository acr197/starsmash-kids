# StarSmash Kids

**Fullscreen touch-play app for toddlers. No ads, no data, no nonsense.**

---

## Overview

Kids tap, drag, and smash the screen to trigger colorful visuals and playful sounds.  
No goals, no UI clutter, fully offline.

---

## AI Features

**Adaptive Play**  
Adjusts visuals and sound in real time based on interaction intensity.

**Overstimulation Guard**  
Automatically softens effects if play becomes too chaotic.

> Both are simple, rule-based systems. No ML, no tracking, on-device only.

---

## Highlights

- Multi-touch interactions (tap, drag, 2+ fingers, palm-like heuristic)  
- Fullscreen, zero chrome for kids  
- Themes, sound modes, and intensity controls  
- Haptics, reduced motion, keep-awake  
- Privacy-first: no internet, no analytics, no accounts  

---

## Tech

- Kotlin + Jetpack Compose  
- Pure Kotlin core logic (testable, no Android deps)  
- Lightweight audio + real-time touch classification  

---

## Download the APK (no build needed)

Every push to this repo automatically builds a phone-installable debug APK
via GitHub Actions. To grab it:

1. Go to the **Actions** tab on GitHub.
2. Click the most recent successful **Build APK** workflow run.
3. Scroll to the **Artifacts** section and download `StarSmashKids-debug-apk`.
4. Unzip it on your phone (or download on desktop and transfer) — you'll get
   `StarSmashKids-debug.apk`.
5. Open the APK with any APK installer app to install it. You may need to
   allow "install from unknown sources" the first time.

For a stable, shareable download, push a tag like `v1.0.0` and the workflow
will additionally attach the APK to a GitHub **Release**.

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Build locally

```bash
./gradlew assembleDebug
