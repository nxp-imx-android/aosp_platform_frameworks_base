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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.SurfaceControl
import android.view.animation.Interpolator
import android.window.TransitionInfo
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.transition.Transitions

/** Animator for pinned layer transitions. */
class PinnedLayerAnimator {

    companion object {
        private const val PIN_ANIMATION_DURATION_MS = 300L
        private val PIN_ANIMATION_INTERPOLATOR: Interpolator = Interpolators.FAST_OUT_SLOW_IN

        fun createPinAnimator(
            change: TransitionInfo.Change,
            startTransaction: SurfaceControl.Transaction,
            finishTransaction: SurfaceControl.Transaction,
            finishCallback: Transitions.TransitionFinishCallback,
            sctFactory: () -> SurfaceControl.Transaction = {
                SurfaceControl.Transaction()
            }, // for testing
        ): Animator {
            val animator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = PIN_ANIMATION_DURATION_MS
                    interpolator = PIN_ANIMATION_INTERPOLATOR
                }

            val startBounds = Rect(change.startAbsBounds)
            val endBounds = Rect(change.endAbsBounds)

            fun applyTxWithBounds(
                tx: SurfaceControl.Transaction,
                left: Float,
                top: Float,
                width: Int,
                height: Int,
            ) {
                tx.setPosition(change.leash, left, top)
                tx.setWindowCrop(change.leash, width, height)
                tx.apply()
            }

            fun applyTxWithBounds(tx: SurfaceControl.Transaction, bounds: Rect) {
                applyTxWithBounds(
                    tx,
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.width(),
                    bounds.height(),
                )
            }

            animator.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        super.onAnimationStart(animation)
                        applyTxWithBounds(startTransaction, startBounds)
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        super.onAnimationEnd(animation)
                        applyTxWithBounds(finishTransaction, endBounds)
                        finishCallback.onTransitionFinished(null)
                    }
                }
            )

            animator.addUpdateListener { animation ->
                val transaction = sctFactory.invoke()
                val progress = animation.animatedFraction
                val s = startBounds
                val e = endBounds
                val left = s.left + (e.left - s.left) * progress
                val top = s.top + (e.top - s.top) * progress
                val width = s.width() + (e.width() - s.width()) * progress
                val height = s.height() + (e.height() - s.height()) * progress
                applyTxWithBounds(transaction, left, top, width.toInt(), height.toInt())
            }

            return animator
        }
    }
}
