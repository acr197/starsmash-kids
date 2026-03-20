package com.starsmash.kids.touch

import kotlin.math.abs
import kotlin.math.sqrt

// ── Result type ───────────────────────────────────────────────────────────────

/**
 * The classified type of a touch gesture.
 *
 * All subclasses carry the coordinates of the primary touch point(s) so that
 * PlayScreen can spawn visual effects at the right location without having to
 * re-read the raw event.
 */
sealed class TouchEventType {
    /** Single finger that went down and came back up with minimal movement. */
    data class SingleTap(val x: Float, val y: Float) : TouchEventType()

    /** Single finger moving across the screen. */
    data class SingleDrag(val x: Float, val y: Float) : TouchEventType()

    /** Exactly two fingers that went down with little movement. */
    data class TwoFingerTap(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : TouchEventType()

    /** Exactly two fingers moving. */
    data class TwoFingerDrag(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : TouchEventType()

    /** Three or more fingers detected. [count] is the number of active pointers. */
    data class MultiTouchBurst(val x: Float, val y: Float, val count: Int) : TouchEventType()

    /**
     * Four or more contact points covering a large area – likely a palm press.
     *
     * IMPORTANT: Palm detection is a heuristic based on [TouchEvent.touchMajor].
     * The reliability of this detection varies significantly across devices:
     *   - Some devices do not report AXIS_TOUCH_MAJOR meaningfully (always 0 or 1).
     *   - Screen protectors can reduce the reported contact size.
     *   - Stylus use may falsely suppress detection.
     * This is NOT a machine-learning palm rejection algorithm; it is a simple
     * threshold on reported contact area.
     */
    data class PalmLikeBurst(val x: Float, val y: Float) : TouchEventType()

    /**
     * Multiple taps in rapid succession at approximately the same location.
     * Triggered when [TouchClassifier.RAPID_TAP_COUNT_THRESHOLD] taps occur within
     * [TouchClassifier.RAPID_TAP_WINDOW_MS] milliseconds.
     */
    data class RapidTapCluster(val x: Float, val y: Float, val tapCount: Int) : TouchEventType()

    /**
     * A pointer that entered from near the screen edge.
     * Used to distinguish intentional "back swipe" gestures from play input.
     * Note: full back-gesture prevention is not possible on Android 10+.
     */
    data class EdgeEntrySwipe(val x: Float, val y: Float, val fromLeft: Boolean) : TouchEventType()
}

// ── Classifier ────────────────────────────────────────────────────────────────

/**
 * Stateful classifier that maps raw touch frames into high-level [TouchEventType]s.
 *
 * This is pure Kotlin with no Android framework dependencies so it can be
 * thoroughly unit-tested on the JVM.
 *
 * Typical usage (from PlayScreen):
 * ```
 * val classifier = TouchClassifier(screenWidth = width, screenHeight = height)
 * // on each pointer event:
 * val type = classifier.classify(frame)
 * if (type != null) viewModel.onTouchEvent(type)
 * ```
 *
 * Thread safety: NOT thread-safe. Call from a single thread (the composition thread).
 */
class TouchClassifier(
    /** Screen width in pixels, used for edge detection. */
    private val screenWidth: Float,
    /** Screen height in pixels, used for edge detection. */
    private val screenHeight: Float
) {

    // ── Configurable thresholds ────────────────────────────────────────────

    companion object {
        /** Max pixels a finger can travel and still count as a "tap" (not a drag). */
        const val TAP_SLOP_PX = 20f

        /**
         * Minimum average touchMajor (contact diameter in px) across all active pointers
         * to classify as a palm-like burst.
         *
         * Device note: typical fingertip contact is ~40-60 px on a 1080p screen;
         * a palm typically registers 100+ px if the device reports it accurately.
         * This threshold is deliberately conservative.
         */
        const val PALM_TOUCH_MAJOR_THRESHOLD = 80f

        /** Minimum pointer count to qualify for PalmLikeBurst. */
        const val PALM_MIN_POINTER_COUNT = 4

        /** Minimum pointer count to qualify for MultiTouchBurst (but below palm). */
        const val MULTI_TOUCH_BURST_COUNT = 3

        /** Number of taps within [RAPID_TAP_WINDOW_MS] that triggers RapidTapCluster. */
        const val RAPID_TAP_COUNT_THRESHOLD = 4

        /** Time window for rapid tap detection in milliseconds. */
        const val RAPID_TAP_WINDOW_MS = 800L

        /**
         * Distance from screen edge (in pixels) within which a DOWN event is
         * considered an EdgeEntrySwipe candidate.
         */
        const val EDGE_ZONE_PX = 30f
    }

    // ── State ──────────────────────────────────────────────────────────────

    /** Starting position of each currently-active pointer, keyed by pointer ID. */
    private val pointerDownPositions = mutableMapOf<Int, Pair<Float, Float>>()

    /** Timestamps of recent tap completions, for rapid-tap detection. */
    private val recentTapTimes = ArrayDeque<Long>(8)

    /** Position of the most recent tap centroid, for cluster spatial grouping. */
    private var lastTapX: Float = 0f
    private var lastTapY: Float = 0f

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Classify a touch frame and return the appropriate [TouchEventType], or
     * null if the frame does not complete a classifiable gesture (e.g., mid-drag
     * MOVE events that don't yet qualify for anything).
     *
     * The classifier emits events eagerly on MOVE and POINTER_DOWN so that
     * visual effects begin immediately rather than waiting for finger lift.
     */
    fun classify(frame: TouchFrame): TouchEventType? {
        return when (frame.action) {
            TouchAction.DOWN -> handleDown(frame)
            TouchAction.POINTER_DOWN -> handlePointerDown(frame)
            TouchAction.MOVE -> handleMove(frame)
            TouchAction.UP -> handleUp(frame)
            TouchAction.POINTER_UP -> handlePointerUp(frame)
        }
    }

    /** Reset all internal state (e.g., when the composable leaves composition). */
    fun reset() {
        pointerDownPositions.clear()
        recentTapTimes.clear()
    }

    // ── Private handlers ────────────────────────────────────────────────────

    private fun handleDown(frame: TouchFrame): TouchEventType? {
        val pointer = frame.pointers.firstOrNull() ?: return null
        pointerDownPositions[pointer.pointerId] = Pair(pointer.x, pointer.y)

        // Edge entry detection – fires immediately on DOWN so PlayViewModel can
        // decide whether to suppress effects and show the exit hint overlay.
        if (isNearEdge(pointer.x, pointer.y)) {
            return TouchEventType.EdgeEntrySwipe(
                x = pointer.x,
                y = pointer.y,
                fromLeft = pointer.x < screenWidth / 2f
            )
        }
        return null
    }

    private fun handlePointerDown(frame: TouchFrame): TouchEventType? {
        frame.pointers.forEach { p ->
            if (!pointerDownPositions.containsKey(p.pointerId)) {
                pointerDownPositions[p.pointerId] = Pair(p.x, p.y)
            }
        }

        val count = frame.pointerCount
        val centroid = centroidOf(frame.pointers)

        return when {
            isPalmLike(frame) ->
                TouchEventType.PalmLikeBurst(centroid.first, centroid.second)
            count >= MULTI_TOUCH_BURST_COUNT ->
                TouchEventType.MultiTouchBurst(centroid.first, centroid.second, count)
            count == 2 -> {
                val p1 = frame.pointers[0]
                val p2 = frame.pointers[1]
                TouchEventType.TwoFingerTap(p1.x, p1.y, p2.x, p2.y)
            }
            else -> null
        }
    }

    private fun handleMove(frame: TouchFrame): TouchEventType? {
        val count = frame.pointerCount
        val centroid = centroidOf(frame.pointers)

        return when {
            isPalmLike(frame) ->
                TouchEventType.PalmLikeBurst(centroid.first, centroid.second)
            count >= MULTI_TOUCH_BURST_COUNT ->
                TouchEventType.MultiTouchBurst(centroid.first, centroid.second, count)
            count == 2 -> {
                val p1 = frame.pointers[0]
                val p2 = frame.pointers[1]
                // Only emit if meaningful movement on at least one pointer
                if (hasMovedFromDown(p1) || hasMovedFromDown(p2)) {
                    TouchEventType.TwoFingerDrag(p1.x, p1.y, p2.x, p2.y)
                } else {
                    null
                }
            }
            count == 1 -> {
                val p = frame.pointers[0]
                if (hasMovedFromDown(p)) {
                    TouchEventType.SingleDrag(p.x, p.y)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun handleUp(frame: TouchFrame): TouchEventType? {
        // On UP the frame may still contain the lifting pointer or may be empty.
        // We use the stored down positions to determine what happened.
        val liftedPointer = frame.pointers.firstOrNull()
        val result = if (liftedPointer != null && !hasMovedFromDown(liftedPointer)) {
            recordTap(liftedPointer.x, liftedPointer.y, frame.eventTime)
        } else {
            null
        }
        pointerDownPositions.remove(liftedPointer?.pointerId ?: -1)
        if (frame.pointerCount <= 1) pointerDownPositions.clear()
        return result
    }

    private fun handlePointerUp(frame: TouchFrame): TouchEventType? {
        // Individual pointer lifted while others remain – nothing to classify here;
        // the ongoing gesture continues to be classified via MOVE events.
        frame.pointers.forEach { p ->
            pointerDownPositions.remove(p.pointerId)
        }
        return null
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Records a completed tap and returns [TouchEventType.RapidTapCluster] if the
     * rapid-tap threshold is met, otherwise [TouchEventType.SingleTap].
     */
    private fun recordTap(x: Float, y: Float, eventTime: Long): TouchEventType {
        // Prune taps outside the time window
        val cutoff = eventTime - RAPID_TAP_WINDOW_MS
        while (recentTapTimes.isNotEmpty() && recentTapTimes.first() < cutoff) {
            recentTapTimes.removeFirst()
        }
        recentTapTimes.addLast(eventTime)
        lastTapX = x
        lastTapY = y

        return if (recentTapTimes.size >= RAPID_TAP_COUNT_THRESHOLD) {
            TouchEventType.RapidTapCluster(x, y, recentTapTimes.size)
        } else {
            TouchEventType.SingleTap(x, y)
        }
    }

    /** True if a pointer has moved more than [TAP_SLOP_PX] since it went down. */
    private fun hasMovedFromDown(pointer: TouchEvent): Boolean {
        val origin = pointerDownPositions[pointer.pointerId] ?: return false
        val dx = pointer.x - origin.first
        val dy = pointer.y - origin.second
        return sqrt(dx * dx + dy * dy) > TAP_SLOP_PX
    }

    /**
     * Heuristic palm detection.
     *
     * Returns true when:
     *   (a) there are at least [PALM_MIN_POINTER_COUNT] active pointers, AND
     *   (b) the average reported touchMajor exceeds [PALM_TOUCH_MAJOR_THRESHOLD].
     *
     * Caveats:
     *   - Many budget devices do not report AXIS_TOUCH_MAJOR accurately.
     *   - Screen protectors reduce apparent contact area.
     *   - This heuristic will miss palms on devices that clamp touchMajor to 1.
     *   - It may false-positive if the user uses all fingertips simultaneously
     *     with high pressure on a high-end device with accurate touch hardware.
     */
    private fun isPalmLike(frame: TouchFrame): Boolean {
        if (frame.pointerCount < PALM_MIN_POINTER_COUNT) return false
        if (frame.pointers.all { it.touchMajor <= 0f }) return false // Device doesn't report it
        val avgMajor = frame.pointers.map { it.touchMajor }.average().toFloat()
        return avgMajor >= PALM_TOUCH_MAJOR_THRESHOLD
    }

    /** Returns (meanX, meanY) of the given pointer list. */
    private fun centroidOf(pointers: List<TouchEvent>): Pair<Float, Float> {
        if (pointers.isEmpty()) return Pair(0f, 0f)
        val x = pointers.sumOf { it.x.toDouble() }.toFloat() / pointers.size
        val y = pointers.sumOf { it.y.toDouble() }.toFloat() / pointers.size
        return Pair(x, y)
    }

    /** Returns true if the position is within [EDGE_ZONE_PX] of either vertical edge. */
    private fun isNearEdge(x: Float, @Suppress("UNUSED_PARAMETER") y: Float): Boolean {
        return x < EDGE_ZONE_PX || x > screenWidth - EDGE_ZONE_PX
    }
}
