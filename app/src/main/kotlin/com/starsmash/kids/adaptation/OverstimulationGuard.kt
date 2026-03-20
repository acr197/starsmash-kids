package com.starsmash.kids.adaptation

/**
 * Overstimulation Guard
 * =====================
 * Monitors the [AdaptivePlayEngine.stimulusLevel] over time and activates a
 * "guard" mode when sustained high-intensity play is detected.
 *
 * When active, the guard signals PlayViewModel to:
 *   - Reduce visual effect scale by ~40%
 *   - Instruct AudioEngine to lower output volume
 *
 * After a period of calmer play the guard releases automatically.
 *
 * IMPORTANT – What this is and is NOT:
 *   - This is RULE-BASED THRESHOLD LOGIC, not machine learning or AI.
 *   - The thresholds are configurable constants; there is no training or model.
 *   - It does not collect or transmit any data about the child.
 *   - Its sole effect is local: softening on-screen and audio feedback.
 *
 * Design: Pure Kotlin, zero Android dependencies → fully unit-testable on JVM.
 */
class OverstimulationGuard(
    private val config: Config = Config()
) {

    // ── Configuration ─────────────────────────────────────────────────────

    /**
     * @param triggerThreshold      stimulusLevel above which we start the timer (default 0.85).
     * @param triggerDurationMs     Milliseconds stimulusLevel must stay above [triggerThreshold]
     *                              before the guard activates (default 5 000 ms = 5 s).
     * @param releaseThreshold      stimulusLevel below which we start the release timer (default 0.5).
     * @param releaseDurationMs     Milliseconds stimulusLevel must stay below [releaseThreshold]
     *                              before the guard deactivates (default 8 000 ms = 8 s).
     */
    data class Config(
        val triggerThreshold: Float = 0.85f,
        val triggerDurationMs: Long = 5_000L,
        val releaseThreshold: Float = 0.5f,
        val releaseDurationMs: Long = 8_000L
    )

    // ── State ─────────────────────────────────────────────────────────────

    /** Whether overstimulation guard is currently suppressing effects. */
    var guardActive: Boolean = false
        private set

    /** Whether the guard feature is enabled at all. */
    var enabled: Boolean = true

    /** Timestamp (ms) when we first observed stimulusLevel > triggerThreshold. */
    private var highStartTime: Long? = null

    /** Timestamp (ms) when we first observed stimulusLevel < releaseThreshold after guard activated. */
    private var calmStartTime: Long? = null

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Feed the current stimulus level and current time; returns whether the guard
     * is now active (the caller should read [guardActive] for the canonical value).
     *
     * Call this at whatever cadence stimulusLevel updates (typically every animation
     * frame or every recorded touch event – whichever is more convenient).
     *
     * @param level     Current output of [AdaptivePlayEngine.stimulusLevel].
     * @param nowMs     Monotonic timestamp in ms (SystemClock.uptimeMillis on device,
     *                  injected clock in tests).
     * @return          [guardActive] after this update.
     */
    fun update(level: Float, nowMs: Long): Boolean {
        if (!enabled) {
            guardActive = false
            highStartTime = null
            calmStartTime = null
            return false
        }

        if (!guardActive) {
            // ── Looking for trigger condition ─────────────────────────────
            if (level >= config.triggerThreshold) {
                if (highStartTime == null) {
                    highStartTime = nowMs
                } else if (nowMs - highStartTime!! >= config.triggerDurationMs) {
                    // Sustained high intensity for long enough – activate guard
                    guardActive = true
                    calmStartTime = null
                    highStartTime = null
                }
            } else {
                // Level dropped below threshold – reset timer
                highStartTime = null
            }
        } else {
            // ── Guard active – looking for release condition ───────────────
            if (level < config.releaseThreshold) {
                if (calmStartTime == null) {
                    calmStartTime = nowMs
                } else if (nowMs - calmStartTime!! >= config.releaseDurationMs) {
                    // Sustained calm play – release the guard
                    guardActive = false
                    calmStartTime = null
                    highStartTime = null
                }
            } else {
                // Still elevated – reset calm timer
                calmStartTime = null
            }
        }

        return guardActive
    }

    /** Reset all state (e.g., when starting a new session). */
    fun reset() {
        guardActive = false
        highStartTime = null
        calmStartTime = null
    }
}
