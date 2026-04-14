# StarSmash Kids
Android app for kids that uses AI to dynamically adjust gameplay and prevent overstimulation in real time.

## What It Does
Lets kids smash freely on screen while an AI layer monitors session behavior and modifies the game state to keep engagement healthy. Adjusts difficulty, pacing, and stimulus intensity based on play patterns without interrupting the experience.

## Stack
Kotlin, Android SDK, Anthropic API

## How It Works
- AI controls respond to in-session signals to dial gameplay intensity up or down without requiring manual parent intervention
- Game parameters (speed, density, sound, and visual load) are tunable at runtime so the AI can modify the experience mid-session
- Music and sound effects run on independent volume controls, persisted across menu open/close within the session
- Audio managed through a single tracked MediaPlayer instance so only one track plays at a time and selections don't stack
- Built for a single real user -- design decisions reflect actual observed behavior, not hypothetical UX

## Setup
Clone the repo and open in Android Studio. Build and deploy to a physical device or emulator running Android 8.0+. No backend required -- API calls are made directly from the app.

## Relevance
Demonstrates applied AI integration in a mobile context outside the typical enterprise use case. The overstimulation detection angle is a concrete example of using model output to drive real-time UX decisions.
