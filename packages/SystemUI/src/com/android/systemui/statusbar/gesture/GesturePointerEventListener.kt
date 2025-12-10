/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.statusbar.gesture

import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.hardware.display.DisplayManagerGlobal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.DisplayCutout
import android.view.DisplayInfo
import android.view.GestureDetector
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import android.view.MotionEvent.AXIS_GESTURE_SWIPE_FINGER_COUNT
import android.view.MotionEvent.CLASSIFICATION_MULTI_FINGER_SWIPE
import android.widget.OverScroller
import com.android.internal.R
import com.android.systemui.CoreStartable
import com.android.window.flags.Flags
import java.io.PrintWriter
import javax.inject.Inject

/**
 * Watches for gesture events that may trigger system bar related events and notify the registered
 * callbacks. Add callback to this listener by calling {@link setCallbacks}.
 */
class GesturePointerEventListener
@Inject
constructor(context: Context, gestureDetector: GesturePointerEventDetector) : CoreStartable {
    private val context: Context
    private val handler = Handler(Looper.getMainLooper())
    private var gestureDetector: GesturePointerEventDetector
    private var flingGestureDetector: GestureDetector? = null
    private var displayCutoutTouchableRegionSize = 0

    // The thresholds for each edge of the display
    private val swipeStartThreshold = Rect()
    private var swipeDistanceThreshold = 0
    private var callbacks: Callbacks? = null
    private val downPointerId = IntArray(MAX_TRACKED_POINTERS)
    private val downX = FloatArray(MAX_TRACKED_POINTERS)
    private val downY = FloatArray(MAX_TRACKED_POINTERS)
    private val downTime = LongArray(MAX_TRACKED_POINTERS)
    private var screenHeight = 0
    private var screenWidth = 0
    private var downPointers = 0
    private var swipeFireable = false
    private var debugFireable = false
    private var mouseHoveringAtLeft = false
    private var mouseHoveringAtTop = false
    private var mouseHoveringAtRight = false
    private var mouseHoveringAtBottom = false
    private var lastFlingTime: Long = 0

    init {
        this@GesturePointerEventListener.context = checkNull("context", context)
        this@GesturePointerEventListener.gestureDetector =
            checkNull("gesture detector", gestureDetector)
        onConfigurationChanged()
    }

    override fun start() {
        if (!Flags.enableTransientGestureInSystemUi()) {
            return
        }
        gestureDetector.addOnGestureDetectedCallback(TAG) { ev -> onInputEvent(ev) }
        gestureDetector.startGestureListening()

        flingGestureDetector = object : GestureDetector(context, FlingGestureDetector(), handler) {}
    }

    fun onDisplayInfoChanged(info: DisplayInfo) {
        screenWidth = info.logicalWidth
        screenHeight = info.logicalHeight
        onConfigurationChanged()
    }

    fun onConfigurationChanged() {
        if (!Flags.enableTransientGestureInSystemUi()) {
            return
        }
        val r = context.resources
        val startThreshold = r.getDimensionPixelSize(R.dimen.system_gestures_start_threshold)
        swipeStartThreshold[startThreshold, startThreshold, startThreshold] = startThreshold
        swipeDistanceThreshold = r.getDimensionPixelSize(R.dimen.system_gestures_distance_threshold)
        val display = DisplayManagerGlobal.getInstance().getRealDisplay(context.displayId)
        val displayCutout = display?.cutout
        if (displayCutout != null) {
            // Expand swipe start threshold such that we can catch touches that just start beyond
            // the notch area
            displayCutoutTouchableRegionSize =
                r.getDimensionPixelSize(R.dimen.display_cutout_touchable_region_size)
            val bounds = displayCutout.boundingRectsAll
            if (bounds[DisplayCutout.BOUNDS_POSITION_LEFT] != null) {
                swipeStartThreshold.left =
                    Math.max(
                        swipeStartThreshold.left,
                        bounds[DisplayCutout.BOUNDS_POSITION_LEFT]!!.width() +
                            displayCutoutTouchableRegionSize,
                    )
            }
            if (bounds[DisplayCutout.BOUNDS_POSITION_TOP] != null) {
                swipeStartThreshold.top =
                    Math.max(
                        swipeStartThreshold.top,
                        bounds[DisplayCutout.BOUNDS_POSITION_TOP]!!.height() +
                            displayCutoutTouchableRegionSize,
                    )
            }
            if (bounds[DisplayCutout.BOUNDS_POSITION_RIGHT] != null) {
                swipeStartThreshold.right =
                    Math.max(
                        swipeStartThreshold.right,
                        bounds[DisplayCutout.BOUNDS_POSITION_RIGHT]!!.width() +
                            displayCutoutTouchableRegionSize,
                    )
            }
            if (bounds[DisplayCutout.BOUNDS_POSITION_BOTTOM] != null) {
                swipeStartThreshold.bottom =
                    Math.max(
                        swipeStartThreshold.bottom,
                        bounds[DisplayCutout.BOUNDS_POSITION_BOTTOM]!!.height() +
                            displayCutoutTouchableRegionSize,
                    )
            }
        }
        if (DEBUG)
            Log.d(
                TAG,
                "swipeStartThreshold=$swipeStartThreshold" +
                    " swipeDistanceThreshold=$swipeDistanceThreshold",
            )
    }

    fun onInputEvent(ev: InputEvent) {
        if (ev !is MotionEvent) {
            return
        }
        if (DEBUG) Log.d(TAG, "Received motion event $ev")
        if (ev.isTouchEvent) {
            flingGestureDetector?.onTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeFireable = true
                debugFireable = true
                downPointers = 0
                captureDown(ev, 0)
                if (mouseHoveringAtLeft) {
                    mouseHoveringAtLeft = false
                    callbacks?.onMouseLeaveFromLeft()
                }
                if (mouseHoveringAtTop) {
                    mouseHoveringAtTop = false
                    callbacks?.onMouseLeaveFromTop()
                }
                if (mouseHoveringAtRight) {
                    mouseHoveringAtRight = false
                    callbacks?.onMouseLeaveFromRight()
                }
                if (mouseHoveringAtBottom) {
                    mouseHoveringAtBottom = false
                    callbacks?.onMouseLeaveFromBottom()
                }
                callbacks?.onDown()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                captureDown(ev, ev.actionIndex)
                if (debugFireable) {
                    debugFireable = ev.pointerCount < 5
                    if (!debugFireable) {
                        if (DEBUG) Log.d(TAG, "Firing debug")
                        callbacks?.onDebug()
                    }
                }
            }
            MotionEvent.ACTION_MOVE ->
                if (swipeFireable) {
                    val trackpadSwipe = detectTrackpadThreeFingerSwipe(ev)
                    swipeFireable = trackpadSwipe == TRACKPAD_SWIPE_NONE
                    if (!swipeFireable) {
                        if (trackpadSwipe == TRACKPAD_SWIPE_FROM_TOP) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromTop from trackpad")
                            callbacks?.onSwipeFromTop()
                        } else if (trackpadSwipe == TRACKPAD_SWIPE_FROM_BOTTOM) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromBottom from trackpad")
                            callbacks?.onSwipeFromBottom()
                        } else if (trackpadSwipe == TRACKPAD_SWIPE_FROM_RIGHT) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromRight from trackpad")
                            callbacks?.onSwipeFromRight()
                        } else if (trackpadSwipe == TRACKPAD_SWIPE_FROM_LEFT) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromLeft from trackpad")
                            callbacks?.onSwipeFromLeft()
                        }
                    } else {
                        val swipe = detectSwipe(ev)
                        swipeFireable = swipe == SWIPE_NONE
                        if (swipe == SWIPE_FROM_TOP) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromTop")
                            callbacks?.onSwipeFromTop()
                        } else if (swipe == SWIPE_FROM_BOTTOM) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromBottom")
                            callbacks?.onSwipeFromBottom()
                        } else if (swipe == SWIPE_FROM_RIGHT) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromRight")
                            callbacks?.onSwipeFromRight()
                        } else if (swipe == SWIPE_FROM_LEFT) {
                            if (DEBUG) Log.d(TAG, "Firing onSwipeFromLeft")
                            callbacks?.onSwipeFromLeft()
                        }
                    }
                }
            MotionEvent.ACTION_HOVER_MOVE ->
                if (ev.isFromSource(InputDevice.SOURCE_MOUSE)) {
                    val eventX = ev.x
                    val eventY = ev.y
                    if (!mouseHoveringAtLeft && eventX == 0f) {
                        callbacks?.onMouseHoverAtLeft()
                        mouseHoveringAtLeft = true
                    } else if (mouseHoveringAtLeft && eventX > 0) {
                        callbacks?.onMouseLeaveFromLeft()
                        mouseHoveringAtLeft = false
                    }
                    if (!mouseHoveringAtTop && eventY == 0f) {
                        callbacks?.onMouseHoverAtTop()
                        mouseHoveringAtTop = true
                    } else if (mouseHoveringAtTop && eventY > 0) {
                        callbacks?.onMouseLeaveFromTop()
                        mouseHoveringAtTop = false
                    }
                    if (!mouseHoveringAtRight && eventX >= screenWidth - 1) {
                        callbacks?.onMouseHoverAtRight()
                        mouseHoveringAtRight = true
                    } else if (mouseHoveringAtRight && eventX < screenWidth - 1) {
                        callbacks?.onMouseLeaveFromRight()
                        mouseHoveringAtRight = false
                    }
                    if (!mouseHoveringAtBottom && eventY >= screenHeight - 1) {
                        callbacks?.onMouseHoverAtBottom()
                        mouseHoveringAtBottom = true
                    } else if (mouseHoveringAtBottom && eventY < screenHeight - 1) {
                        callbacks?.onMouseLeaveFromBottom()
                        mouseHoveringAtBottom = false
                    }
                }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                swipeFireable = false
                debugFireable = false
                callbacks?.onUpOrCancel()
            }
            else -> if (DEBUG) Log.d(TAG, "Ignoring $ev")
        }
    }

    fun setCallbacks(callbacks: Callbacks) {
        this@GesturePointerEventListener.callbacks = callbacks
    }

    private fun captureDown(event: MotionEvent, pointerIndex: Int) {
        val pointerId = event.getPointerId(pointerIndex)
        val i = findIndex(pointerId)
        if (DEBUG) Log.d(TAG, "pointer $pointerId down pointerIndex=$pointerIndex trackingIndex=$i")
        if (i != UNTRACKED_POINTER) {
            downX[i] = event.getX(pointerIndex)
            downY[i] = event.getY(pointerIndex)
            downTime[i] = event.eventTime
            if (DEBUG) Log.d(TAG, "pointer " + pointerId + " down x=" + downX[i] + " y=" + downY[i])
        }
    }

    protected fun currentGestureStartedInRegion(r: Region): Boolean {
        return r.contains(downX[0].toInt(), downY[0].toInt())
    }

    private fun findIndex(pointerId: Int): Int {
        for (i in 0 until downPointers) {
            if (downPointerId[i] == pointerId) {
                return i
            }
        }
        if (downPointers == MAX_TRACKED_POINTERS || pointerId == MotionEvent.INVALID_POINTER_ID) {
            return UNTRACKED_POINTER
        }
        downPointerId[downPointers++] = pointerId
        return downPointers - 1
    }

    private fun detectTrackpadThreeFingerSwipe(move: MotionEvent): Int {
        if (!isTrackpadThreeFingerSwipe(move)) {
            return TRACKPAD_SWIPE_NONE
        }
        val dx = move.x - downX[0]
        val dy = move.y - downY[0]
        if (Math.abs(dx) < Math.abs(dy)) {
            if (Math.abs(dy) > swipeDistanceThreshold) {
                return if (dy > 0) TRACKPAD_SWIPE_FROM_TOP else TRACKPAD_SWIPE_FROM_BOTTOM
            }
        } else {
            if (Math.abs(dx) > swipeDistanceThreshold) {
                return if (dx > 0) TRACKPAD_SWIPE_FROM_LEFT else TRACKPAD_SWIPE_FROM_RIGHT
            }
        }
        return TRACKPAD_SWIPE_NONE
    }

    private fun isTrackpadThreeFingerSwipe(event: MotionEvent): Boolean {
        return (event.classification == CLASSIFICATION_MULTI_FINGER_SWIPE &&
            event.getAxisValue(AXIS_GESTURE_SWIPE_FINGER_COUNT) == 3f)
    }

    private fun detectSwipe(move: MotionEvent): Int {
        val historySize = move.historySize
        val pointerCount = move.pointerCount
        for (p in 0 until pointerCount) {
            val pointerId = move.getPointerId(p)
            val i = findIndex(pointerId)
            if (i != UNTRACKED_POINTER) {
                for (h in 0 until historySize) {
                    val time = move.getHistoricalEventTime(h)
                    val x = move.getHistoricalX(p, h)
                    val y = move.getHistoricalY(p, h)
                    val swipe = detectSwipe(i, time, x, y)
                    if (swipe != SWIPE_NONE) {
                        return swipe
                    }
                }
                val swipe = detectSwipe(i, move.eventTime, move.getX(p), move.getY(p))
                if (swipe != SWIPE_NONE) {
                    return swipe
                }
            }
        }
        return SWIPE_NONE
    }

    private fun detectSwipe(i: Int, time: Long, x: Float, y: Float): Int {
        val fromX = downX[i]
        val fromY = downY[i]
        val elapsed = time - downTime[i]
        if (DEBUG)
            Log.d(
                TAG,
                "pointer " +
                    downPointerId[i] +
                    " moved (" +
                    fromX +
                    "->" +
                    x +
                    "," +
                    fromY +
                    "->" +
                    y +
                    ") in " +
                    elapsed,
            )
        if (
            fromY <= swipeStartThreshold.top &&
                y > fromY + swipeDistanceThreshold &&
                elapsed < SWIPE_TIMEOUT_MS
        ) {
            return SWIPE_FROM_TOP
        }
        if (
            fromY >= screenHeight - swipeStartThreshold.bottom &&
                y < fromY - swipeDistanceThreshold &&
                elapsed < SWIPE_TIMEOUT_MS
        ) {
            return SWIPE_FROM_BOTTOM
        }
        if (
            fromX >= screenWidth - swipeStartThreshold.right &&
                x < fromX - swipeDistanceThreshold &&
                elapsed < SWIPE_TIMEOUT_MS
        ) {
            return SWIPE_FROM_RIGHT
        }
        return if (
            fromX <= swipeStartThreshold.left &&
                x > fromX + swipeDistanceThreshold &&
                elapsed < SWIPE_TIMEOUT_MS
        ) {
            SWIPE_FROM_LEFT
        } else SWIPE_NONE
    }

    fun dump(pw: PrintWriter, prefix: String) {
        val inner = "$prefix  "
        pw.println(prefix + TAG + ":")
        pw.print(inner)
        pw.print("displayCutoutTouchableRegionSize=")
        pw.println(displayCutoutTouchableRegionSize)
        pw.print(inner)
        pw.print("swipeStartThreshold=")
        pw.println(swipeStartThreshold)
        pw.print(inner)
        pw.print("swipeDistanceThreshold=")
        pw.println(swipeDistanceThreshold)
    }

    private inner class FlingGestureDetector internal constructor() :
        GestureDetector.SimpleOnGestureListener() {
        private val overscroller: OverScroller = OverScroller(context)

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!overscroller.isFinished) {
                overscroller.forceFinished(true)
            }
            return true
        }

        override fun onFling(
            down: MotionEvent?,
            up: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            overscroller.computeScrollOffset()
            val now = SystemClock.uptimeMillis()
            if (lastFlingTime != 0L && now > lastFlingTime + MAX_FLING_TIME_MILLIS) {
                overscroller.forceFinished(true)
            }
            overscroller.fling(
                0,
                0,
                velocityX.toInt(),
                velocityY.toInt(),
                Int.MIN_VALUE,
                Int.MAX_VALUE,
                Int.MIN_VALUE,
                Int.MAX_VALUE,
            )
            var duration = overscroller.duration
            if (duration > MAX_FLING_TIME_MILLIS) {
                duration = MAX_FLING_TIME_MILLIS
            }
            lastFlingTime = now
            callbacks?.onFling(duration)
            return true
        }
    }

    interface Callbacks {
        fun onSwipeFromTop()

        fun onSwipeFromBottom()

        fun onSwipeFromRight()

        fun onSwipeFromLeft()

        fun onFling(durationMs: Int)

        fun onDown()

        fun onUpOrCancel()

        fun onMouseHoverAtLeft()

        fun onMouseHoverAtTop()

        fun onMouseHoverAtRight()

        fun onMouseHoverAtBottom()

        fun onMouseLeaveFromLeft()

        fun onMouseLeaveFromTop()

        fun onMouseLeaveFromRight()

        fun onMouseLeaveFromBottom()

        fun onDebug()
    }

    companion object {
        private const val TAG = "GesturePointerEventHandler"
        private const val DEBUG = false
        private const val SWIPE_TIMEOUT_MS: Long = 500
        private const val MAX_TRACKED_POINTERS = 32 // max per input system
        private const val UNTRACKED_POINTER = -1
        private const val MAX_FLING_TIME_MILLIS = 5000
        private const val SWIPE_NONE = 0
        private const val SWIPE_FROM_TOP = 1
        private const val SWIPE_FROM_BOTTOM = 2
        private const val SWIPE_FROM_RIGHT = 3
        private const val SWIPE_FROM_LEFT = 4
        private const val TRACKPAD_SWIPE_NONE = 0
        private const val TRACKPAD_SWIPE_FROM_TOP = 1
        private const val TRACKPAD_SWIPE_FROM_BOTTOM = 2
        private const val TRACKPAD_SWIPE_FROM_RIGHT = 3
        private const val TRACKPAD_SWIPE_FROM_LEFT = 4

        private fun <T> checkNull(name: String, arg: T?): T {
            requireNotNull(arg) { "$name must not be null" }
            return arg
        }
    }
}
