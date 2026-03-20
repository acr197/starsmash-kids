package com.starsmash.kids.adaptation

import com.starsmash.kids.touch.TouchEventType

/**
 * Adaptive Play Engine
 * ====================
 * Tracks the child's touch activity over a rolling time window and outputs a
 * [stimulusLevel] float in [0.0, 1.0] representing how energetically the child
 * is currently playing.
 *
 * IMPORTANT – What this is and is NOT:
 *   - This is a HEURISTIC BEHAVIORAL ENGINE based on simple rule-based thresholds.
 *   - It is NOT a machine learning model, neural network, or AI classifier.
 *   - It uses no personal data, biometrics, or network communication.
 *   - The output drives only local visual and audio scaling – it has no external effect.
 *
 * Design goals:
 *   - Pure Kotlin, zero Android framework dependencies → fully unit-testable on JVM.
 *   - Configurable thresholds grouped in [Config] so adjustments don't require
 *     hunting through logic.
 *   - Exponential moving average smoothing to avoid abrupt visual jumps.
 *   - Can be disabled at runtime; returns [DISABLED_LEVEL] when off.
 */
class AdaptivePlayEngine(
    private val config: Config = Config()
) {

    // ── Configuration ────────────────────────────────────────────────────────

    /**
     * All tunable thresholds for the adaptive engine.
     *
     * @param windowMs          Rolling event window in milliseconds (default 10 s).
     * @param maxTapsPerSec     Tap rate that maps to stimulusLevel = 1.0 (default 5 taps/s).
     * @param maxAvgPointers    Avg pointer count that maps to full contribution (default 4).
     * @param burstWeight       Weight applied to burst events in the score calculation.
     * @param emaAlpha          EMA smoothing factor in (0, 1). Higher = more reactive.
     *                          Lower = smoother but slower to respond. (default 0.3).
     */
    data class Config(
        val windowMs: Long = 10_000L,
        val maxTapsPerSec: Float = 5f,
        val maxAvgPointers: Float = 4f,
        val burstWeight: Float = 1.5f,
        val emaAlpha: Float = 0.3f
    )

    companion object {
        /** Returned by [stimulusLevel] when the engine is disabled. */
        const val DISABLED_LEVEL = 0.5f
    }

    // ── State ────────────────────────────────────────────────────────────────

    /** Whether adaptive play is currently active. */
    var enabled: Boolean = true

    /** Smoothed output level, updated on each [recordEvent] call. */
    var stimulusLevel: Float = 0f
        private set

    /**
     * Ring-buffer of recent events stored as (timestamp, pointerCount, isBurst).
     * We keep only events within [config.windowMs] of the latest event.
     */
    private data class EventRecord(
        val time: Long,
        val pointerCount: Int,
        val isBurst: Boolean
    )

    private val eventHistory = ArrayDeque<EventRecord>(64)

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Record a classified touch event and update [stimulusLevel].
     *
     * @param event     The classified touch type (determines burst weight).
     * @param pointerCount  Active pointer count at the time of the event.
     * @param eventTime Monotonic timestamp in ms (use SystemClock.uptimeMillis on device,
     *                  or an injected clock in tests).
     */
    fun recordEvent(
        event: TouchEventType,
        pointerCount: Int,
        eventTime: Long
    ) {
        if (!enabled) {
            stimulusLevel = DISABLED_LEVEL
            return
        }

        val isBurst = event is TouchEventType.MultiTouchBurst
                || event is TouchEventType.PalmLikeBurst
                || event is TouchEventType.RapidTapCluster

        eventHistory.addLast(EventRecord(eventTime, pointerCount, isBurst))
        pruneOldEvents(eventTime)

        val raw = computeRawScore(eventTime)
        // Exponential moving average smoothing:
        //   new_level = alpha * raw + (1 - alpha) * old_level
        stimulusLevel = (config.emaAlpha * raw + (1f - config.emaAlpha) * stimulusLevel)
            .coerceIn(0f, 1f)
    }

    /**
     * Query the current stimulus level without recording a new event.
     * Returns [DISABLED_LEVEL] if the engine is disabled.
     */
    fun currentLevel(): Float = if (enabled) stimulusLevel else DISABLED_LEVEL

    /** Discard all history and reset level to 0. */
    fun reset() {
        eventHistory.clear()
        stimulusLevel = 0f
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Remove events older than the rolling window. */
    private fun pruneOldEvents(now: Long) {
        val cutoff = now - config.windowMs
        while (eventHistory.isNotEmpty() && eventHistory.first().time < cutoff) {
            eventHistory.removeFirst()
        }
    }

    /**
     * Compute a raw [0.0, 1.0] score from the current event window.
     *
     * Score contributions (each normalised, then averaged):
     *   1. Tap rate (events per second) relative to [Config.maxTapsPerSec].
     *   2. Average pointer count relative to [Config.maxAvgPointers].
     *   3. Burst density: fraction of events that are burst-type, weighted by
     *      [Config.burstWeight].
     *
     * This is intentionally simple arithmetic – no ML, no black box.
     */
    private fun computeRawScore(now: Long): Float {
        val n = eventHistory.size
        if (n == 0) return 0f

        val windowSec = config.windowMs / 1000f

        // 1. Tap rate contribution
        val tapRate = n / windowSec
        val tapContrib = (tapRate / config.maxTapsPerSec).coerceIn(0f, 1f)

        // 2. Average pointer count contribution
        val avgPointers = eventHistory.sumOf { it.pointerCount.toDouble() }.toFloat() / n
        val pointerContrib = (avgPointers / config.maxAvgPointers).coerceIn(0f, 1f)

        // 3. Burst density contribution
        val burstCount = eventHistory.count { it.isBurst }
        val burstFraction = burstCount.toFloat() / n
        val burstContrib = (burstFraction * config.burstWeight).coerceIn(0f, 1f)

        return (tapContrib + pointerContrib + burstContrib) / 3f
    }
}
