/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.pinnedlayer.phone

import android.app.TaskInfo
import android.app.WindowConfiguration
import android.content.Context
import android.graphics.Rect
import android.util.Slog
import android.util.TypedValue
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.DesktopState
import kotlin.math.max

/** A controller that tracks the available display area for the pinned layer. */
class PinnedLayerPresentationController(
    private val context: Context,
    private val displayController: DisplayController,
    private val desktopState: DesktopState,
) {

    private val entryBoundsPadding: Int

    init {
        entryBoundsPadding =
            context.resources.getDimensionPixelSize(R.dimen.pinned_window_init_padding)
    }

    /**
     * Calculates the entry destination bounds for a task window which is pinned.
     *
     * The size of the window is calculated based on the task's original bounds, adjusted to fit
     * within the defined constraints (min 220dp, max 70% of display dimensions). The aspect ratio
     * of the task is preserved.
     *
     * As a security mitigation -- to limit the app's abiltiy to position the pinned window -- the
     * task is moved into the right bottom corner of the display, everytime it's pinned.
     *
     * @param task The task for which to calculate the bounds.
     * @return A [Rect] with the calculated bounds, or an empty Rect if calculation is not possible.
     */
    fun getPinEntryDestinationBounds(task: TaskInfo): Rect? {
        val displayId = task.displayId
        val displayLayout = displayController.getDisplayLayout(displayId)
        val displayContext = displayController.getDisplayContext(displayId)
        if (displayLayout == null || displayContext == null) {
            Slog.w(TAG, "Cannot get bounds for display $displayId, layout or context not found")
            return null
        }

        val taskBounds = task.configuration.windowConfiguration.getBounds()
        if (taskBounds.width() <= 0 || taskBounds.height() <= 0) {
            Slog.w(TAG, "Cannot get bounds for task with empty or invalid bounds")
            return null
        }

        val w0 = taskBounds.width().toFloat()
        val h0 = taskBounds.height().toFloat()

        val minSizePx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                MIN_SIZE_DP,
                displayContext.resources.displayMetrics,
            )
        val maxWidth = displayLayout.width() * MAX_SIZE_RATIO
        val maxHeight = displayLayout.height() * MAX_SIZE_RATIO

        // Adjust the task size to fit within the constraints.
        val minScale = max(minSizePx / w0, minSizePx / h0)
        val maxScale = minOf(maxWidth / w0, maxHeight / h0)
        val scale =
            if (minScale > maxScale) {
                // Constraints conflict, prioritize max size
                // Should not happen, CDD requires min 440dp display for freeform
                maxScale
            } else {
                1.0f.coerceIn(minScale, maxScale)
            }
        val finalWidth = w0 * scale
        val finalHeight = h0 * scale

        // Calculate the bounds based on the final size, stable insets and padding.
        val stableInsets = displayLayout.stableInsets()
        val right = displayLayout.width() - stableInsets.right - entryBoundsPadding.toInt()
        val bottom = displayLayout.height() - stableInsets.bottom - entryBoundsPadding.toInt()
        val left = right - finalWidth.toInt()
        val top = bottom - finalHeight.toInt()

        return Rect(left, top, right, bottom)
    }

    /**
     * Checks if the task is in a state that is supported for pinning.
     *
     * @param task The task to check.
     * @return True if the task is supported, false otherwise.
     */
    fun isTaskSupportedForPinning(task: TaskInfo): Boolean {
        // Freeform ensures tasks is not in pip/split/fullscreen/bubble.
        val isFreeform = task.windowingMode == WindowConfiguration.WINDOWING_MODE_FREEFORM
        // Limit to pining only in same display and require desktop mode support.
        val displaySupportsDesktopMode =
            desktopState.isDesktopModeSupportedOnDisplay(task.displayId)
        return isFreeform && displaySupportsDesktopMode
    }

    private companion object {
        private const val TAG = "PinnedLayerDisplayAreaController"
        // min = 220dp, max = 70% of the display width and height
        private const val MIN_SIZE_DP = 220f
        private const val MAX_SIZE_RATIO = 0.7f
    }
}
