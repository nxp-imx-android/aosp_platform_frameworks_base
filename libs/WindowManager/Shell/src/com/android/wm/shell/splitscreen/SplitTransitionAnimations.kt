/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.splitscreen

import android.animation.Animator
import android.graphics.Rect
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import com.android.wm.shell.animation.SizeChangeAnimation
import com.android.wm.shell.shared.TransitionUtil.isClosingMode
import com.android.wm.shell.shared.animation.Interpolators
import java.util.function.Consumer

class SplitTransitionAnimations {
    /**
     * Build animation for dismissing one of the stages and moving the other to fullscreen.
     *
     * <p>Normally this would be caused by one of the stages going away, for example due to back
     * navigation or force-stop.
     *
     * @return Built Animator if current change is for the expanding/closing surface, null otherwise
     */
    fun buildDismissAnimation(
        change: TransitionInfo.Change,
        t: SurfaceControl.Transaction,
        stageCoordinator: StageCoordinator,
        onFinish: Consumer<Animator>,
    ): Animator? {

        val expanding = change.mode == TRANSIT_CHANGE && change.snapshot != null
        val closing = isClosingMode(change.mode)

        if (closing) {
            val topOrLeftBounds = Rect()
            val bottomOrRightBounds = Rect()
            stageCoordinator.getStageBounds(topOrLeftBounds, bottomOrRightBounds)
            val otherBounds =
                if (change.startAbsBounds == topOrLeftBounds) bottomOrRightBounds
                else topOrLeftBounds

            return buildSizeChangeAnimation(
                change,
                t,
                change.startAbsBounds,
                calculateExitBounds(change.startAbsBounds, otherBounds, change.endAbsBounds),
                onFinish,
            )
        } else if (expanding) {
            return buildSizeChangeAnimation(
                change,
                t,
                change.startAbsBounds,
                change.endAbsBounds,
                onFinish,
            )
        }

        return null
    }

    /**
     * Calculates the "exit bounds" for a closing window in a split-screen dismiss animation. These
     * bounds represent the final size and position of the closing window as it animates off-screen,
     * shrinking towards the edge of the expanding window.
     *
     * @param startClosing The starting bounds of the window that is closing.
     * @param startExpanding The starting bounds of the window that is expanding.
     * @param endExpanding The final bounds of the window that is expanding.
     * @return The calculated bounds for the closing window at the end of the animation.
     */
    fun calculateExitBounds(startClosing: Rect, startExpanding: Rect, endExpanding: Rect): Rect {
        val result: Rect = Rect(startClosing)

        // left/right
        if (endExpanding.left < startExpanding.left) {
            result.right = result.left
        } else if (endExpanding.right > startExpanding.right) {
            result.left = result.right
        }

        // up/down
        if (endExpanding.top < startExpanding.top) {
            result.bottom = result.top
        } else if (endExpanding.bottom > startExpanding.bottom) {
            result.top = result.bottom
        }

        return result
    }

    private fun buildSizeChangeAnimation(
        change: TransitionInfo.Change,
        startT: SurfaceControl.Transaction,
        startBounds: Rect,
        endBounds: Rect,
        onFinish: Consumer<Animator>,
    ): Animator {
        val sca = SizeChangeAnimation(startBounds, endBounds)
        sca.initialize(change.leash, change.snapshot, startT)

        val va = sca.buildAnimator(change.leash, change.snapshot, onFinish)
        va.setDuration(SIZE_CHANGE_ANIMATION_DURATION)
        va.interpolator = Interpolators.EMPHASIZED

        return va
    }

    companion object {
        private const val SIZE_CHANGE_ANIMATION_DURATION = 500L
    }
}
