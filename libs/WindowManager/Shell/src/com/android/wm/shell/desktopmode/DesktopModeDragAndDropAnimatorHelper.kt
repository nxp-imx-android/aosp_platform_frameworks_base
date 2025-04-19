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
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo.Change
import androidx.core.util.Supplier
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import javax.inject.Inject

/**
 * Helper class for creating and managing animations related to drag and drop operations in Desktop
 * Mode. This class provides methods to create different types of animations, for example, covers
 * different animations for tab tearing.
 */
class DesktopModeDragAndDropAnimatorHelper
@Inject
constructor(val context: Context, val transactionSupplier: Supplier<Transaction>) {

    /**
     * Creates an animator for a given change, incorporating start and finish callbacks.
     *
     * This function is responsible for creating an animator that handles the visual changes defined
     * by the provided [Change] object. It leverages a transaction supplier to manage the transition
     * and provides callbacks to be executed when the animation starts and finishes.
     *
     * @param change The [Change] object describing the desired visual transition. It should contain
     *   information like the view that should be animated (leash) and the start/end values.
     * @param finishCallback A [TransitionFinishCallback] that will be invoked when the animation
     *   completes. It will inform the caller that the transition is finished.
     * @return An [Animator] instance configured to perform the change described by the `change`
     *   parameter.
     */
    fun createAnimator(change: Change, finishCallback: TransitionFinishCallback): Animator {
        val transaction = transactionSupplier.get()

        val animatorStartedCallback: () -> Unit = {
            transaction.show(change.leash)
            transaction.apply()
        }
        val animatorFinishedCallback: () -> Unit = { finishCallback.onTransitionFinished(null) }

        return createAlphaAnimator(change, animatorStartedCallback, animatorFinishedCallback)
    }

    private fun createAlphaAnimator(
        change: Change,
        onStart: () -> Unit,
        onFinish: () -> Unit,
    ): Animator {
        val transaction = transactionSupplier.get()

        val alphaAnimator = ValueAnimator()
        alphaAnimator.setFloatValues(0f, 1f)
        alphaAnimator.setDuration(FADE_IN_ANIMATION_DURATION)

        alphaAnimator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    onStart.invoke()
                }

                override fun onAnimationEnd(animation: Animator) {
                    onFinish.invoke()
                }
            }
        )
        alphaAnimator.addUpdateListener { animation: ValueAnimator ->
            transaction.setAlpha(change.leash, animation.animatedFraction)
            transaction.apply()
        }

        return alphaAnimator
    }

    companion object {
        const val FADE_IN_ANIMATION_DURATION = 300L
    }
}
