/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.TaskInfo
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.view.Gravity
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.policy.DesktopModeCompatUtils.applyLayoutGravity
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.DesktopTaskPosition.BottomLeft
import com.android.wm.shell.desktopmode.DesktopTaskPosition.BottomRight
import com.android.wm.shell.desktopmode.DesktopTaskPosition.Center
import com.android.wm.shell.desktopmode.DesktopTaskPosition.LeftSnapped
import com.android.wm.shell.desktopmode.DesktopTaskPosition.Maximized
import com.android.wm.shell.desktopmode.DesktopTaskPosition.RightSnapped
import com.android.wm.shell.desktopmode.DesktopTaskPosition.TopLeft
import com.android.wm.shell.desktopmode.DesktopTaskPosition.TopRight
import kotlin.math.abs

/** The position of a task window in desktop mode. */
sealed class DesktopTaskPosition {
    data object Center : DesktopTaskPosition() {
        private const val WINDOW_HEIGHT_PROPORTION = 0.375

        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point {
            val x = (frame.width() - window.width()) / 2 + frame.left
            // Position with more margin at the bottom.
            val y = (frame.height() - window.height()) * WINDOW_HEIGHT_PROPORTION + frame.top
            return Point(x, y.toInt())
        }

        override fun next(): DesktopTaskPosition = BottomRight
    }

    data object BottomRight : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.right - window.width(), frame.bottom - window.height())

        override fun next(): DesktopTaskPosition = TopLeft
    }

    data object TopLeft : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.left, frame.top)

        override fun next(): DesktopTaskPosition = BottomLeft
    }

    data object BottomLeft : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.left, frame.bottom - window.height())

        override fun next(): DesktopTaskPosition = TopRight
    }

    data object TopRight : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.right - window.width(), frame.top)

        override fun next(): DesktopTaskPosition = Center
    }

    data object Maximized : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.left, frame.top)

        override fun next(): DesktopTaskPosition = Center
    }

    data object LeftSnapped : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.left, frame.top)

        override fun next(): DesktopTaskPosition = Center
    }

    data object RightSnapped : DesktopTaskPosition() {
        override fun getTopLeftCoordinates(frame: Rect, window: Rect): Point =
            Point(frame.right - window.width(), frame.top)

        override fun next(): DesktopTaskPosition = Center
    }

    /**
     * Returns the top left coordinates for the window to be placed in the given DesktopTaskPosition
     * in the frame.
     */
    abstract fun getTopLeftCoordinates(frame: Rect, window: Rect): Point

    abstract fun next(): DesktopTaskPosition
}

fun applyLayoutGravityIfNeeded(taskInfo: TaskInfo, bounds: Rect, stableBounds: Rect): Boolean {
    var horizontalGravity = 0
    var verticalGravity = 0
    taskInfo.topActivityInfo?.windowLayout?.let {
        horizontalGravity = it.gravity.and(Gravity.HORIZONTAL_GRAVITY_MASK)
        verticalGravity = it.gravity.and(Gravity.VERTICAL_GRAVITY_MASK)
    }
    if (verticalGravity > 0 || horizontalGravity > 0) {
        applyLayoutGravity(verticalGravity, horizontalGravity, bounds, stableBounds)
        return true
    }
    return false
}

/** Returns the current DesktopTaskPosition for a given window in the frame. */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
fun Rect.getDesktopTaskPosition(bounds: Rect): DesktopTaskPosition {
    return when {
        Flags.enableRememberedBounds() && this == bounds -> Maximized
        Flags.enableRememberedBounds() &&
            left == bounds.left &&
            height() == bounds.height() &&
            top == bounds.top -> LeftSnapped
        Flags.enableRememberedBounds() &&
            right == bounds.right &&
            height() == bounds.height() &&
            top == bounds.top -> RightSnapped
        top == bounds.top && left == bounds.left && bottom != bounds.bottom -> TopLeft
        top == bounds.top && right == bounds.right && bottom != bounds.bottom -> TopRight
        bottom == bounds.bottom && left == bounds.left && top != bounds.top -> BottomLeft
        bottom == bounds.bottom && right == bounds.right && top != bounds.top -> BottomRight
        else -> Center
    }
}

internal fun cascadeWindow(
    res: Resources,
    frame: Rect,
    prev: Rect,
    dest: Rect,
    isRememberedBounds: Boolean,
) {
    if (Flags.enableRememberedBounds() && isRememberedBounds) {
        cascadeWindowForRememberedBounds(res, frame, prev, dest)
        return
    }
    val candidateBounds = Rect(dest)
    val lastPos = frame.getDesktopTaskPosition(prev)
    var destCoord = Center.getTopLeftCoordinates(frame, candidateBounds)
    candidateBounds.offsetTo(destCoord.x, destCoord.y)
    // If the default center position is not free or if last focused window is not at the
    // center, get the next cascading window position.
    if (!prevBoundsMovedAboveThreshold(res, prev, candidateBounds) || Center != lastPos) {
        val nextCascadingPos = lastPos.next()
        destCoord = nextCascadingPos.getTopLeftCoordinates(frame, dest)
    }
    dest.offsetTo(destCoord.x, destCoord.y)
}

fun cascadeWindowStepped(
    res: Resources,
    frame: Rect,
    prev: Rect,
    dest: Rect,
    isRememberedBounds: Boolean,
) {
    val offset = res.getDimensionPixelSize(R.dimen.desktop_mode_cascading_offset)

    if (Flags.enableRememberedBounds() && isRememberedBounds) {
        cascadeWindowSteppedForRememberedBounds(offset, frame, prev, dest)
        return
    }

    if (haveSameBoundsWithThreshold(offset, prev, dest)) {
        // TODO(b/410787173): Implement multi-step cascading and screen wrapping.
        cascadeOneStep(offset, dest)
    } else {
        // Default to center
        val centerPos = Center.getTopLeftCoordinates(frame, dest)
        dest.offsetTo(centerPos.x, centerPos.y)
    }
}

private fun cascadeWindowSteppedForRememberedBounds(
    offset: Int,
    frame: Rect,
    prev: Rect,
    dest: Rect,
) {
    val candidatePos = frame.getDesktopTaskPosition(dest)
    if (candidatePos == Maximized || candidatePos == LeftSnapped || candidatePos == RightSnapped) {
        // No need to cascade if the destination bounds is maximized or snapped.
        return
    }
    if (!haveSameBoundsWithThreshold(offset, prev, dest)) {
        // If the remembered bounds is different enough from the bounds of the focused window,
        // we can use the remembered bounds as is.
        return
    }

    if (
        dest.left + offset + dest.width() <= frame.right &&
            dest.top + offset + dest.height() <= frame.bottom
    ) {
        // Successfully resolves collision with one-step cascading preserving general size and
        // location (no wrapping)
        cascadeOneStep(offset, dest)
    }
}

fun cascadeWindowForRememberedBounds(res: Resources, frame: Rect, prev: Rect, dest: Rect) {
    val candidatePos = frame.getDesktopTaskPosition(dest)
    if (candidatePos == Maximized || candidatePos == LeftSnapped || candidatePos == RightSnapped) {
        // No need to cascade if the destination bounds is maximized or snapped.
        return
    }
    if (prevBoundsMovedAboveThreshold(res, prev, dest)) {
        // No need to cascade if the destination bounds have moved significantly.
        return
    }
    val lastPos = frame.getDesktopTaskPosition(prev)
    val nextCascadingPos = lastPos.next()
    val destCoord = nextCascadingPos.getTopLeftCoordinates(frame, dest)
    dest.offsetTo(destCoord.x, destCoord.y)
}

internal fun prevBoundsMovedAboveThreshold(res: Resources, prev: Rect, newBounds: Rect): Boolean {
    // This is the required minimum dp for a task to be touchable.
    val moveThresholdPx =
        res.getDimensionPixelSize(R.dimen.freeform_required_visible_empty_space_in_header)
    val leftFar = newBounds.left - prev.left > moveThresholdPx
    val topFar = newBounds.top - prev.top > moveThresholdPx
    val rightFar = prev.right - newBounds.right > moveThresholdPx
    val bottomFar = prev.bottom - newBounds.bottom > moveThresholdPx

    return leftFar || topFar || rightFar || bottomFar
}

private fun haveSameBoundsWithThreshold(cascadingOffset: Int, a: Rect, b: Rect): Boolean {
    // The threshold is set this way as this is especially useful for checking whether the bounds of
    // the new window is closer to the bounds of the previous window or the cascaded bounds.
    val thresholdPx = cascadingOffset / 2
    val leftClose = abs(b.left - a.left) < thresholdPx
    val topClose = abs(b.top - a.top) < thresholdPx
    val rightClose = abs(b.right - a.right) < thresholdPx
    val bottomClose = abs(b.bottom - a.bottom) < thresholdPx

    return leftClose && topClose && rightClose && bottomClose
}

private fun cascadeOneStep(offset: Int, bounds: Rect) {
    bounds.offset(offset, offset)
}
