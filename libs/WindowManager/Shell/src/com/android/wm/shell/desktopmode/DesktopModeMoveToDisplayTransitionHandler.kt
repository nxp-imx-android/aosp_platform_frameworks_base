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

package com.android.wm.shell.desktopmode

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.IBinder
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.transition.Transitions
import kotlin.time.Duration.Companion.milliseconds

/**
 * Transition handler for moving a window to a different display.
 */
class DesktopModeMoveToDisplayTransitionHandler(
    private val animationTransaction: SurfaceControl.Transaction
) : Transitions.TransitionHandler {

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        val change = info.changes.find { it.startDisplayId != it.endDisplayId } ?: return false
        ValueAnimator.ofFloat(0f, 1f)
            .apply {
                duration = ANIM_DURATION.inWholeMilliseconds
                interpolator = Interpolators.LINEAR
                addUpdateListener { animation ->
                    animationTransaction
                        .setAlpha(change.leash, animation.animatedValue as Float)
                        .setFrameTimeline(Choreographer.getInstance().vsyncId)
                        .apply()
                }
                addListener(
                    object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) {
                            val endBounds = change.endAbsBounds
                            startTransaction
                                .setPosition(
                                    change.leash,
                                    endBounds.left.toFloat(),
                                    endBounds.top.toFloat(),
                                )
                                .setWindowCrop(change.leash, endBounds.width(), endBounds.height())
                                .apply()
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            finishTransaction.apply()
                            finishCallback.onTransitionFinished(null)
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            finishTransaction.apply()
                            finishCallback.onTransitionFinished(null)
                        }

                        override fun onAnimationRepeat(animation: Animator) = Unit
                    }
                )
            }
            .start()
        return true
    }

    private companion object {
        val ANIM_DURATION = 100.milliseconds
    }
}
