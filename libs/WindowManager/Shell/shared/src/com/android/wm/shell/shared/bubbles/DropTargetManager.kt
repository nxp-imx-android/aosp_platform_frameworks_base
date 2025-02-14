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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.ValueAnimator

/**
 * Manages animating drop targets in response to dragging bubble icons or bubble expanded views
 * across different drag zones.
 */
class DropTargetManager(
    context: Context,
    private val container: FrameLayout,
    private val isLayoutRtl: Boolean,
    private val dragZoneChangedListener: DragZoneChangedListener,
) {

    private var state: DragState? = null
    private val dropTargetView = View(context)
    private var animator: ValueAnimator? = null

    private companion object {
        const val ANIMATION_DURATION_MS = 250L
    }

    /** Must be called when a drag gesture is starting. */
    fun onDragStarted(draggedObject: DraggedObject, dragZones: List<DragZone>) {
        val state = DragState(dragZones, draggedObject)
        dragZoneChangedListener.onInitialDragZoneSet(state.initialDragZone)
        this.state = state
        animator?.cancel()
        setupDropTarget()
    }

    private fun setupDropTarget() {
        if (dropTargetView.parent != null) container.removeView(dropTargetView)
        container.addView(dropTargetView, 0)
        // TODO b/393173014: set elevation and background
        dropTargetView.alpha = 0f
        dropTargetView.scaleX = 1f
        dropTargetView.scaleY = 1f
        dropTargetView.translationX = 0f
        dropTargetView.translationY = 0f
        // the drop target is added with a width and height of 1 pixel. when it gets resized, we use
        // set its scale to the width and height of the bounds it should have to avoid layout passes
        dropTargetView.layoutParams = FrameLayout.LayoutParams(/* width= */ 1, /* height= */ 1)
    }

    /** Called when the user drags to a new location. */
    fun onDragUpdated(x: Int, y: Int) {
        val state = state ?: return
        val oldDragZone = state.currentDragZone
        val newDragZone = state.getMatchingDragZone(x = x, y = y)
        state.currentDragZone = newDragZone
        if (oldDragZone != newDragZone) {
            dragZoneChangedListener.onDragZoneChanged(from = oldDragZone, to = newDragZone)
            updateDropTarget()
        }
    }

    /** Called when the drag ended. */
    fun onDragEnded() {
        startFadeAnimation(from = dropTargetView.alpha, to = 0f) {
            container.removeView(dropTargetView)
        }
        state = null
    }

    private fun updateDropTarget() {
        val currentDragZone = state?.currentDragZone ?: return
        val dropTargetBounds = currentDragZone.dropTarget
        when {
            dropTargetBounds == null -> startFadeAnimation(from = dropTargetView.alpha, to = 0f)
            dropTargetView.alpha == 0f -> {
                dropTargetView.translationX = dropTargetBounds.exactCenterX()
                dropTargetView.translationY = dropTargetBounds.exactCenterY()
                dropTargetView.scaleX = dropTargetBounds.width().toFloat()
                dropTargetView.scaleY = dropTargetBounds.height().toFloat()
                startFadeAnimation(from = 0f, to = 1f)
            }
            else -> startMorphAnimation(dropTargetBounds)
        }
    }

    private fun startFadeAnimation(from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        animator?.cancel()
        val animator = ValueAnimator.ofFloat(from, to).setDuration(ANIMATION_DURATION_MS)
        animator.addUpdateListener { _ -> dropTargetView.alpha = animator.animatedValue as Float }
        if (onEnd != null) {
            animator.doOnEnd(onEnd)
        }
        this.animator = animator
        animator.start()
    }

    private fun startMorphAnimation(bounds: Rect) {
        animator?.cancel()
        val startAlpha = dropTargetView.alpha
        val startTx = dropTargetView.translationX
        val startTy = dropTargetView.translationY
        val startScaleX = dropTargetView.scaleX
        val startScaleY = dropTargetView.scaleY
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(ANIMATION_DURATION_MS)
        animator.addUpdateListener { _ ->
            val fraction = animator.animatedValue as Float
            dropTargetView.alpha = startAlpha + (1 - startAlpha) * fraction
            dropTargetView.translationX = startTx + (bounds.exactCenterX() - startTx) * fraction
            dropTargetView.translationY = startTy + (bounds.exactCenterY() - startTy) * fraction
            dropTargetView.scaleX =
                startScaleX + (bounds.width().toFloat() - startScaleX) * fraction
            dropTargetView.scaleY =
                startScaleY + (bounds.height().toFloat() - startScaleY) * fraction
        }
        this.animator = animator
        animator.start()
    }

    /** Stores the current drag state. */
    private inner class DragState(
        private val dragZones: List<DragZone>,
        draggedObject: DraggedObject
    ) {
        val initialDragZone =
            if (draggedObject.initialLocation.isOnLeft(isLayoutRtl)) {
                dragZones.filterIsInstance<DragZone.Bubble.Left>().first()
            } else {
                dragZones.filterIsInstance<DragZone.Bubble.Right>().first()
            }
        var currentDragZone: DragZone = initialDragZone

        fun getMatchingDragZone(x: Int, y: Int): DragZone {
            return dragZones.firstOrNull { it.contains(x, y) } ?: currentDragZone
        }
    }

    /** An interface to be notified when drag zones change. */
    interface DragZoneChangedListener {
        /** An initial drag zone was set. Called when a drag starts. */
        fun onInitialDragZoneSet(dragZone: DragZone)

        /** Called when the object was dragged to a different drag zone. */
        fun onDragZoneChanged(from: DragZone, to: DragZone)
    }

    private fun Animator.doOnEnd(onEnd: () -> Unit) {
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            }
        )
    }
}
