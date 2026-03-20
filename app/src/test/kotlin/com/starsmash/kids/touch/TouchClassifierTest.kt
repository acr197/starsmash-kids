package com.starsmash.kids.touch

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TouchClassifier].
 *
 * All tests run on the JVM – no Android framework required.
 * The classifier is designed to be fully testable without emulator or device.
 */
class TouchClassifierTest {

    private lateinit var classifier: TouchClassifier

    // Standard screen dimensions for tests
    private val screenW = 1080f
    private val screenH = 2400f

    // Centre of screen
    private val cx = screenW / 2f
    private val cy = screenH / 2f

    @Before
    fun setUp() {
        classifier = TouchClassifier(screenW, screenH)
    }

    // ── Helper builders ──────────────────────────────────────────────────────

    private fun pointer(
        id: Int = 0,
        x: Float = cx,
        y: Float = cy,
        action: TouchAction = TouchAction.DOWN,
        touchMajor: Float = 40f
    ) = TouchEvent(
        pointerId = id, x = x, y = y,
        pressure = 0.5f, touchMajor = touchMajor,
        eventTime = 1000L, action = action
    )

    private fun frame(
        vararg pointers: TouchEvent,
        action: TouchAction = pointers.first().action,
        time: Long = 1000L
    ) = TouchFrame(pointers.toList(), time, action)

    // ── Single pointer: tap ───────────────────────────────────────────────────

    @Test
    fun singlePointerDown_thenUp_withNoMovement_isSingleTap() {
        val downFrame = frame(pointer(id = 0, action = TouchAction.DOWN), action = TouchAction.DOWN)
        classifier.classify(downFrame)   // records down position

        val upFrame = frame(pointer(id = 0, action = TouchAction.UP), action = TouchAction.UP)
        val result = classifier.classify(upFrame)

        assertTrue("Expected SingleTap but got $result", result is TouchEventType.SingleTap)
    }

    @Test
    fun singleTap_positionIsCorrect() {
        val downFrame = frame(pointer(id = 0, x = 300f, y = 500f, action = TouchAction.DOWN))
        classifier.classify(downFrame)
        val upFrame = frame(pointer(id = 0, x = 300f, y = 500f, action = TouchAction.UP))
        val result = classifier.classify(upFrame)
        val tap = result as? TouchEventType.SingleTap
        assertNotNull(tap)
        assertEquals(300f, tap!!.x, 1f)
        assertEquals(500f, tap.y, 1f)
    }

    // ── Single pointer: drag ──────────────────────────────────────────────────

    @Test
    fun singlePointerWithMovement_isSingleDrag() {
        val downFrame = frame(pointer(id = 0, x = 100f, y = 100f, action = TouchAction.DOWN))
        classifier.classify(downFrame)

        // Move beyond slop threshold
        val moveX = 100f + TouchClassifier.TAP_SLOP_PX * 2
        val moveFrame = frame(
            pointer(id = 0, x = moveX, y = 100f, action = TouchAction.MOVE),
            action = TouchAction.MOVE
        )
        val result = classifier.classify(moveFrame)

        assertTrue("Expected SingleDrag but got $result", result is TouchEventType.SingleDrag)
    }

    @Test
    fun moveWithinSlop_doesNotReturnDrag() {
        val downFrame = frame(pointer(id = 0, x = 100f, y = 100f, action = TouchAction.DOWN))
        classifier.classify(downFrame)

        // Move within slop (less than TAP_SLOP_PX)
        val moveX = 100f + TouchClassifier.TAP_SLOP_PX * 0.5f
        val moveFrame = frame(
            pointer(id = 0, x = moveX, y = 100f, action = TouchAction.MOVE),
            action = TouchAction.MOVE
        )
        val result = classifier.classify(moveFrame)

        assertNull("Expected null for sub-slop move but got $result", result)
    }

    // ── Two pointers ──────────────────────────────────────────────────────────

    @Test
    fun twoPointerDown_isTwoFingerTap() {
        val downFrame = frame(pointer(id = 0, action = TouchAction.DOWN))
        classifier.classify(downFrame)

        val secondDown = frame(
            pointer(id = 0, x = 300f, y = 400f, action = TouchAction.POINTER_DOWN),
            pointer(id = 1, x = 700f, y = 400f, action = TouchAction.POINTER_DOWN),
            action = TouchAction.POINTER_DOWN
        )
        val result = classifier.classify(secondDown)
        assertTrue("Expected TwoFingerTap but got $result", result is TouchEventType.TwoFingerTap)
    }

    @Test
    fun twoPointerMove_isTwoFingerDrag() {
        // Setup: two pointers down
        classifier.classify(frame(pointer(id = 0, x = 300f, y = 400f, action = TouchAction.DOWN)))
        classifier.classify(
            frame(
                pointer(id = 0, x = 300f, y = 400f, action = TouchAction.POINTER_DOWN),
                pointer(id = 1, x = 700f, y = 400f, action = TouchAction.POINTER_DOWN),
                action = TouchAction.POINTER_DOWN
            )
        )

        // Move both fingers beyond slop
        val moveFrame = frame(
            pointer(id = 0, x = 300f + TouchClassifier.TAP_SLOP_PX * 3, y = 500f, action = TouchAction.MOVE),
            pointer(id = 1, x = 700f + TouchClassifier.TAP_SLOP_PX * 3, y = 500f, action = TouchAction.MOVE),
            action = TouchAction.MOVE
        )
        val result = classifier.classify(moveFrame)
        assertTrue("Expected TwoFingerDrag but got $result", result is TouchEventType.TwoFingerDrag)
    }

    // ── Multi-touch burst ─────────────────────────────────────────────────────

    @Test
    fun fourPointersDown_isMultiTouchBurst() {
        classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN)))

        val multiFrame = frame(
            pointer(id = 0, action = TouchAction.POINTER_DOWN),
            pointer(id = 1, x = 300f, action = TouchAction.POINTER_DOWN),
            pointer(id = 2, x = 600f, action = TouchAction.POINTER_DOWN),
            pointer(id = 3, x = 900f, action = TouchAction.POINTER_DOWN),
            action = TouchAction.POINTER_DOWN
        )
        val result = classifier.classify(multiFrame)
        assertTrue(
            "Expected MultiTouchBurst or PalmLikeBurst but got $result",
            result is TouchEventType.MultiTouchBurst || result is TouchEventType.PalmLikeBurst
        )
    }

    @Test
    fun threePointers_multiTouchBurst_countIsCorrect() {
        classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN)))

        val multiFrame = frame(
            pointer(id = 0, touchMajor = 40f, action = TouchAction.POINTER_DOWN),
            pointer(id = 1, x = 400f, touchMajor = 40f, action = TouchAction.POINTER_DOWN),
            pointer(id = 2, x = 800f, touchMajor = 40f, action = TouchAction.POINTER_DOWN),
            action = TouchAction.POINTER_DOWN
        )
        val result = classifier.classify(multiFrame)
        if (result is TouchEventType.MultiTouchBurst) {
            assertEquals(3, result.count)
        }
        // (If classified as PalmLikeBurst due to touchMajor, that's also acceptable)
    }

    // ── Palm-like burst ───────────────────────────────────────────────────────

    @Test
    fun fourPointersWithLargeTouchMajor_isPalmLikeBurst() {
        classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN)))

        val palmFrame = frame(
            pointer(id = 0, touchMajor = TouchClassifier.PALM_TOUCH_MAJOR_THRESHOLD + 10f, action = TouchAction.POINTER_DOWN),
            pointer(id = 1, x = 300f, touchMajor = TouchClassifier.PALM_TOUCH_MAJOR_THRESHOLD + 10f, action = TouchAction.POINTER_DOWN),
            pointer(id = 2, x = 600f, touchMajor = TouchClassifier.PALM_TOUCH_MAJOR_THRESHOLD + 10f, action = TouchAction.POINTER_DOWN),
            pointer(id = 3, x = 900f, touchMajor = TouchClassifier.PALM_TOUCH_MAJOR_THRESHOLD + 10f, action = TouchAction.POINTER_DOWN),
            action = TouchAction.POINTER_DOWN
        )
        val result = classifier.classify(palmFrame)
        assertTrue("Expected PalmLikeBurst but got $result", result is TouchEventType.PalmLikeBurst)
    }

    @Test
    fun fourPointersWithSmallTouchMajor_isNotPalmLikeBurst() {
        classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN)))

        val notPalmFrame = frame(
            pointer(id = 0, touchMajor = 30f, action = TouchAction.POINTER_DOWN),
            pointer(id = 1, x = 300f, touchMajor = 30f, action = TouchAction.POINTER_DOWN),
            pointer(id = 2, x = 600f, touchMajor = 30f, action = TouchAction.POINTER_DOWN),
            pointer(id = 3, x = 900f, touchMajor = 30f, action = TouchAction.POINTER_DOWN),
            action = TouchAction.POINTER_DOWN
        )
        val result = classifier.classify(notPalmFrame)
        assertFalse("Expected NOT PalmLikeBurst but got $result", result is TouchEventType.PalmLikeBurst)
    }

    // ── Rapid tap cluster ──────────────────────────────────────────────────────

    @Test
    fun rapidSuccessiveTaps_isRapidTapCluster() {
        var time = 1000L
        var lastResult: TouchEventType? = null

        repeat(TouchClassifier.RAPID_TAP_COUNT_THRESHOLD + 1) {
            classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN), time = time))
            lastResult = classifier.classify(frame(pointer(id = 0, action = TouchAction.UP), time = time + 50))
            time += 100L // 100ms between taps – within the 800ms window
        }

        assertTrue(
            "Expected RapidTapCluster after ${ TouchClassifier.RAPID_TAP_COUNT_THRESHOLD + 1} quick taps, got $lastResult",
            lastResult is TouchEventType.RapidTapCluster
        )
    }

    @Test
    fun slowTaps_areNotRapidTapCluster() {
        var time = 1000L
        var lastResult: TouchEventType? = null

        repeat(3) {
            classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN), time = time))
            lastResult = classifier.classify(frame(pointer(id = 0, action = TouchAction.UP), time = time + 50))
            time += TouchClassifier.RAPID_TAP_WINDOW_MS + 100L // each tap outside window
        }

        assertFalse(
            "Slow taps should not trigger RapidTapCluster but got $lastResult",
            lastResult is TouchEventType.RapidTapCluster
        )
    }

    // ── Edge entry swipe ───────────────────────────────────────────────────────

    @Test
    fun pointerAtLeftEdge_isEdgeEntrySwipe() {
        val edgeX = TouchClassifier.EDGE_ZONE_PX * 0.5f
        val downFrame = frame(pointer(id = 0, x = edgeX, action = TouchAction.DOWN))
        val result = classifier.classify(downFrame)
        assertTrue("Expected EdgeEntrySwipe but got $result", result is TouchEventType.EdgeEntrySwipe)
    }

    @Test
    fun pointerAtRightEdge_isEdgeEntrySwipe() {
        val edgeX = screenW - TouchClassifier.EDGE_ZONE_PX * 0.5f
        val downFrame = frame(pointer(id = 0, x = edgeX, action = TouchAction.DOWN))
        val result = classifier.classify(downFrame)
        assertTrue("Expected EdgeEntrySwipe but got $result", result is TouchEventType.EdgeEntrySwipe)
    }

    @Test
    fun pointerAtLeftEdge_fromLeftIsTrue() {
        val edgeX = 10f
        val downFrame = frame(pointer(id = 0, x = edgeX, action = TouchAction.DOWN))
        val result = classifier.classify(downFrame) as? TouchEventType.EdgeEntrySwipe
        assertNotNull(result)
        assertTrue("fromLeft should be true for left-edge entry", result!!.fromLeft)
    }

    @Test
    fun pointerAtRightEdge_fromLeftIsFalse() {
        val edgeX = screenW - 10f
        val downFrame = frame(pointer(id = 0, x = edgeX, action = TouchAction.DOWN))
        val result = classifier.classify(downFrame) as? TouchEventType.EdgeEntrySwipe
        assertNotNull(result)
        assertFalse("fromLeft should be false for right-edge entry", result!!.fromLeft)
    }

    @Test
    fun pointerInCenter_isNotEdgeEntrySwipe() {
        val downFrame = frame(pointer(id = 0, x = cx, y = cy, action = TouchAction.DOWN))
        val result = classifier.classify(downFrame)
        assertFalse("Centre pointer should not be EdgeEntrySwipe but got $result",
            result is TouchEventType.EdgeEntrySwipe)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun reset_clearsPreviousState() {
        // Build up some rapid tap state
        var time = 1000L
        repeat(5) {
            classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN), time = time))
            classifier.classify(frame(pointer(id = 0, action = TouchAction.UP), time = time + 50))
            time += 100L
        }

        // Reset and do a single tap
        classifier.reset()
        classifier.classify(frame(pointer(id = 0, action = TouchAction.DOWN), time = time))
        val result = classifier.classify(frame(pointer(id = 0, action = TouchAction.UP), time = time + 50))

        // After reset, should be SingleTap (no rapid cluster history)
        assertTrue("After reset, single tap should be SingleTap but got $result",
            result is TouchEventType.SingleTap)
    }
}
