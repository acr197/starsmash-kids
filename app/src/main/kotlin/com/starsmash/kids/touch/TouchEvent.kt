package com.starsmash.kids.touch

/**
 * Possible Android pointer actions, mapped from MotionEvent constants.
 * Having our own enum makes TouchClassifier and its tests free of Android deps.
 */
enum class TouchAction {
    DOWN,           // First pointer touches screen (ACTION_DOWN)
    MOVE,           // Pointer moves (ACTION_MOVE)
    UP,             // Last pointer lifts (ACTION_UP)
    POINTER_DOWN,   // Additional pointer touches screen (ACTION_POINTER_DOWN)
    POINTER_UP      // Non-last pointer lifts (ACTION_POINTER_UP)
}

/**
 * A platform-independent snapshot of a single pointer's state at one instant.
 *
 * All fields come directly from [android.view.MotionEvent] but are copied out
 * into this plain data class so that [TouchClassifier] (and its unit tests) have
 * zero Android framework dependencies.
 *
 * @param pointerId     Stable pointer identifier (from MotionEvent.getPointerId).
 * @param x             X position in pixels relative to the composable's top-left.
 * @param y             Y position in pixels relative to the composable's top-left.
 * @param pressure      Normalised pressure in [0.0, 1.0]. May be 0 on devices that
 *                      don't report pressure.
 * @param touchMajor    Diameter of the contact area in pixels (from MotionEvent
 *                      AXIS_TOUCH_MAJOR). Used for palm detection. Note: this is a
 *                      device-dependent heuristic – not all devices report meaningful
 *                      values for this field.
 * @param eventTime     [android.os.SystemClock.uptimeMillis] at the time of the event.
 * @param action        Interpreted pointer action for this specific pointer.
 */
data class TouchEvent(
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val touchMajor: Float,
    val eventTime: Long,
    val action: TouchAction
)

/**
 * A multi-pointer snapshot: the complete set of active pointers for one gesture frame.
 *
 * @param pointers  All currently active pointers at this moment.
 * @param eventTime Timestamp shared across all pointers in this frame.
 * @param action    The action that triggered this snapshot.
 */
data class TouchFrame(
    val pointers: List<TouchEvent>,
    val eventTime: Long,
    val action: TouchAction
) {
    val pointerCount: Int get() = pointers.size
}
