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

## Build

```bash
./gradlew assembleDebug
