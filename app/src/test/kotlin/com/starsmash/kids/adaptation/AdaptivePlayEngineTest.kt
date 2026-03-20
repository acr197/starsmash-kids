package com.starsmash.kids.adaptation

import com.starsmash.kids.touch.TouchEventType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AdaptivePlayEngine].
 *
 * All tests run on the JVM – no Android framework required.
 * Uses an injected clock (simple long counter) for deterministic time control.
 */
class AdaptivePlayEngineTest {

    private lateinit var engine: AdaptivePlayEngine

    @Before
    fun setUp() {
        engine = AdaptivePlayEngine()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun singleTap(x: Float = 540f, y: Float = 1200f) =
        TouchEventType.SingleTap(x, y)

    private fun burstEvent() =
        TouchEventType.MultiTouchBurst(540f, 1200f, 4)

    /**
     * Feed [count] events spaced [intervalMs] apart, starting at [startTime].
     */
    private fun feedEvents(
        count: Int,
        intervalMs: Long = 100L,
        startTime: Long = 1000L,
        eventFactory: () -> TouchEventType = { singleTap() },
        pointerCount: Int = 1
    ) {
        for (i in 0 until count) {
            engine.recordEvent(eventFactory(), pointerCount, startTime + i * intervalMs)
        }
    }

    // ── Zero events ───────────────────────────────────────────────────────────

    @Test
    fun noEvents_stimulusLevelIsZero() {
        assertEquals(0f, engine.stimulusLevel, 0.01f)
    }

    @Test
    fun noEvents_currentLevel_isZero() {
        assertEquals(0f, engine.currentLevel(), 0.01f)
    }

    // ── Calm input ────────────────────────────────────────────────────────────

    @Test
    fun fewSlowTaps_stimulusLevelStaysLow() {
        // 3 taps spread over 9 seconds (~0.33 taps/sec, well below maxTapsPerSec=5)
        feedEvents(count = 3, intervalMs = 3000L)
        assertTrue(
            "Slow taps should keep stimulusLevel low, got ${engine.stimulusLevel}",
            engine.stimulusLevel < 0.4f
        )
    }

    // ── Energetic input ───────────────────────────────────────────────────────

    @Test
    fun manyRapidTaps_stimulusLevelIncreases() {
        // 50 taps within 5 seconds = 10 taps/sec (above maxTapsPerSec=5)
        feedEvents(count = 50, intervalMs = 100L)
        assertTrue(
            "Many rapid taps should raise stimulusLevel, got ${engine.stimulusLevel}",
            engine.stimulusLevel > 0.3f
        )
    }

    @Test
    fun veryRapidBurstEvents_stimulusLevelHigher() {
        feedEvents(count = 40, intervalMs = 80L, eventFactory = { burstEvent() }, pointerCount = 4)
        assertTrue(
            "Burst events should raise stimulusLevel significantly, got ${engine.stimulusLevel}",
            engine.stimulusLevel > 0.5f
        )
    }

    @Test
    fun highPointerCount_raisesLevelMoreThanSingleTouch() {
        val singleTouchEngine = AdaptivePlayEngine()
        val multiTouchEngine = AdaptivePlayEngine()

        feedEventsInto(singleTouchEngine, count = 20, intervalMs = 100L, pointerCount = 1)
        feedEventsInto(multiTouchEngine, count = 20, intervalMs = 100L, pointerCount = 4)

        assertTrue(
            "Multi-touch should produce higher stimulus level than single touch. " +
                    "single=${singleTouchEngine.stimulusLevel}, multi=${multiTouchEngine.stimulusLevel}",
            multiTouchEngine.stimulusLevel >= singleTouchEngine.stimulusLevel
        )
    }

    // ── Disabled engine ───────────────────────────────────────────────────────

    @Test
    fun engineDisabled_alwaysReturnsNeutralLevel() {
        engine.enabled = false

        // Feed many rapid events
        feedEvents(count = 50, intervalMs = 50L)

        assertEquals(
            "Disabled engine should return DISABLED_LEVEL regardless of events",
            AdaptivePlayEngine.DISABLED_LEVEL,
            engine.currentLevel(),
            0.001f
        )
    }

    @Test
    fun engineDisabled_stimulusLevelIsNeutral() {
        engine.enabled = false
        engine.recordEvent(singleTap(), 1, 1000L)
        assertEquals(
            AdaptivePlayEngine.DISABLED_LEVEL,
            engine.stimulusLevel,
            0.001f
        )
    }

    @Test
    fun reEnablingEngine_resumesTracking() {
        engine.enabled = false
        feedEvents(count = 10, intervalMs = 100L)
        assertEquals(AdaptivePlayEngine.DISABLED_LEVEL, engine.currentLevel(), 0.001f)

        engine.enabled = true
        feedEvents(count = 30, intervalMs = 80L, startTime = 2000L)
        // Should now be tracking – level should not be exactly DISABLED_LEVEL
        assertNotEquals(AdaptivePlayEngine.DISABLED_LEVEL, engine.currentLevel(), 0.01f)
    }

    // ── Event window pruning ──────────────────────────────────────────────────

    @Test
    fun eventsOutsideWindow_doNotInflateLevel() {
        val config = AdaptivePlayEngine.Config(windowMs = 5000L)
        val windowedEngine = AdaptivePlayEngine(config)

        // Feed a burst 20 seconds ago
        feedEventsInto(windowedEngine, count = 50, intervalMs = 100L, startTime = 0L)
        val oldLevel = windowedEngine.stimulusLevel

        // Feed a single calm tap "now" (well outside the window of old events)
        windowedEngine.recordEvent(singleTap(), 1, 25_000L)
        val newLevel = windowedEngine.stimulusLevel

        // After pruning, level should be lower than the burst peak
        assertTrue(
            "Level after window prune ($newLevel) should be lower than burst peak ($oldLevel)",
            newLevel < oldLevel
        )
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsLevelToZero() {
        feedEvents(count = 30, intervalMs = 100L)
        assertTrue(engine.stimulusLevel > 0.1f) // sanity check

        engine.reset()
        assertEquals(0f, engine.stimulusLevel, 0.001f)
    }

    @Test
    fun reset_clearsEventHistory() {
        feedEvents(count = 30, intervalMs = 100L)
        engine.reset()
        // After reset, a single calm tap should produce very low level
        engine.recordEvent(singleTap(), 1, 50_000L)
        assertTrue(
            "After reset, single tap should produce low level, got ${engine.stimulusLevel}",
            engine.stimulusLevel < 0.3f
        )
    }

    // ── Level bounds ──────────────────────────────────────────────────────────

    @Test
    fun stimulusLevel_neverExceedsOne() {
        // Flood with very rapid events
        feedEvents(count = 200, intervalMs = 10L)
        assertTrue(
            "stimulusLevel should never exceed 1.0, got ${engine.stimulusLevel}",
            engine.stimulusLevel <= 1.0f
        )
    }

    @Test
    fun stimulusLevel_neverBelowZero() {
        assertEquals(0f, engine.stimulusLevel, 0.001f)
        assertTrue(engine.stimulusLevel >= 0f)
    }

    // ── Smoothing ─────────────────────────────────────────────────────────────

    @Test
    fun emaSmoothing_preventsAbruptJumps() {
        // With alpha=0.3, a single burst event should not instantly push to 1.0
        engine.recordEvent(burstEvent(), 5, 1000L)
        assertTrue(
            "Single burst should not instantly max out level (EMA smoothing), got ${engine.stimulusLevel}",
            engine.stimulusLevel < 0.8f
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun feedEventsInto(
        target: AdaptivePlayEngine,
        count: Int,
        intervalMs: Long = 100L,
        startTime: Long = 1000L,
        pointerCount: Int = 1
    ) {
        for (i in 0 until count) {
            target.recordEvent(singleTap(), pointerCount, startTime + i * intervalMs)
        }
    }
}
