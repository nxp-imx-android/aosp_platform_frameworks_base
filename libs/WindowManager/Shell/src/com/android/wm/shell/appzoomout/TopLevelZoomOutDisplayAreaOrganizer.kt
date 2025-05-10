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
package com.android.wm.shell.appzoomout

import android.content.Context
import android.util.ArrayMap
import android.view.Choreographer
import android.view.Display
import android.view.SurfaceControl
import android.window.DisplayAreaInfo
import android.window.DisplayAreaOrganizer
import android.window.WindowContainerToken
import com.android.internal.policy.ScreenDecorationsUtils
import com.android.wm.shell.common.DisplayLayout
import java.util.concurrent.Executor
import kotlin.math.max

private const val SqueezeEffectMaxThicknessDp = 16
// Defines the amount the squeeze border overlaps the shrinking content on the shorter display edge.
// At full progress, the overlap is 4 dp on the shorter display edge. On the longer display edge, it
// will be more than 4 dp, depending on the display aspect ratio.
private const val SqueezeEffectOverlapShortEdgeThicknessDp = 4

/** Display area organizer that manages the top level zoom out UI and states.  */
class TopLevelZoomOutDisplayAreaOrganizer(
    displayLayout: DisplayLayout,
    private val context: Context,
    mainExecutor: Executor
) : DisplayAreaOrganizer(mainExecutor) {

    private val mDisplayAreaTokenMap: MutableMap<WindowContainerToken, SurfaceControl> = ArrayMap()
    private val mDisplayLayout = DisplayLayout()
    private var cornerRadius = 1f
    private var mProgress = 1f

    init {
        setDisplayLayout(displayLayout)
    }

    override fun onDisplayAreaAppeared(displayAreaInfo: DisplayAreaInfo, leash: SurfaceControl) {
        leash.setUnreleasedWarningCallSite("TopLevelZoomDisplayAreaOrganizer.onDisplayAreaAppeared")
        if (displayAreaInfo.displayId == Display.DEFAULT_DISPLAY) {
            mDisplayAreaTokenMap[displayAreaInfo.token] = leash
        }
    }

    override fun onDisplayAreaVanished(displayAreaInfo: DisplayAreaInfo) {
        val leash = mDisplayAreaTokenMap[displayAreaInfo.token]
        leash?.release()
        mDisplayAreaTokenMap.remove(displayAreaInfo.token)
    }

    /**
     * Registers the TopLevelZoomOutDisplayAreaOrganizer to manage the display area of
     * [DisplayAreaOrganizer.FEATURE_WINDOWED_MAGNIFICATION].
     */
    fun registerOrganizer() {
        val displayAreaInfos = registerOrganizer(FEATURE_WINDOWED_MAGNIFICATION)
        for (i in displayAreaInfos.indices) {
            val info = displayAreaInfos[i]
            onDisplayAreaAppeared(info.displayAreaInfo, info.leash)
        }
    }

    override fun unregisterOrganizer() {
        super.unregisterOrganizer()
        reset()
    }

    fun setProgress(progress: Float) {
        if (mProgress == progress) {
            return
        }

        mProgress = progress
        apply()
    }

    private fun apply() {
        val tx = SurfaceControl.Transaction()
        mDisplayAreaTokenMap.values.forEach { leash: SurfaceControl ->
            updateSurface(tx, leash, mProgress)
        }
        tx.apply()
    }

    private fun reset() {
        setProgress(1f)
    }

    private fun updateSurface(
        tx: SurfaceControl.Transaction,
        leash: SurfaceControl,
        progress: Float
    ) {
        if (progress == 0f) {
            // Reset when scale is set back to 0.
            tx
                .setCrop(leash, null)
                .setScale(leash, 1f, 1f)
                .setPosition(leash, 0f, 0f)
                .setCornerRadius(leash, 0f)
            return
        }
        // Get display dimensions once
        val displayWidth = mDisplayLayout.width()
        val displayHeight = mDisplayLayout.height()
        val displayWidthF = displayWidth.toFloat()
        val displayHeightF = displayHeight.toFloat()

        // Convert DP thickness values to pixels
        val maxThicknessPx = mDisplayLayout.dpToPx(SqueezeEffectMaxThicknessDp)
        val overlapShortEdgeThicknessPx = mDisplayLayout.dpToPx(SqueezeEffectOverlapShortEdgeThicknessDp)

        // Determine the longer edge of the display
        val longEdgePx = max(displayWidth, displayHeight) // Will be Int, but division with Float promotes

        // Calculate the potential for zooming based on thickness parameters
        // This represents how much the content "shrinks" due to the squeeze effect on both sides.
        val zoomPotentialPx = (maxThicknessPx - overlapShortEdgeThicknessPx) * 2f

        val zoomOutScale = 1f - (progress * zoomPotentialPx / longEdgePx)

        // Calculate the current thickness of the squeeze effect based on progress
        val squeezeThickness = maxThicknessPx * progress

        // Calculate the X and Y offsets needed to center the scaled content.
        // These values are also used to adjust the crop region.
        // (1f - zoomOutScale) is the percentage of size reduction.
        // Half of this reduction, applied to the width/height, gives the offset for centering.
        val positionXOffset = (1f - zoomOutScale) * displayWidthF * 0.5f
        val positionYOffset = (1f - zoomOutScale) * displayHeightF * 0.5f

        // Calculate crop values.
        // The squeezeThickness acts as an initial margin/inset.
        // This margin is then reduced by the positionOffset, because as the view scales down
        // and moves towards the center, less cropping is needed to achieve the same visual margin
        // relative to the scaled content.
        val horizontalCrop = squeezeThickness - positionXOffset
        val verticalCrop = squeezeThickness - positionYOffset

        // Calculate the right and bottom crop coordinates
        val cropRight = displayWidthF - horizontalCrop
        val cropBottom = displayHeightF - verticalCrop

        tx
            .setCrop(leash, horizontalCrop, verticalCrop, cropRight, cropBottom)
            .setCornerRadius(leash, cornerRadius * zoomOutScale)
            .setScale(leash, zoomOutScale, zoomOutScale)
            .setPosition(leash, positionXOffset, positionYOffset)
            .setFrameTimelineVsync(Choreographer.getInstance().vsyncId)
    }

    fun setDisplayLayout(displayLayout: DisplayLayout) {
        mDisplayLayout.set(displayLayout)
        cornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(context)
    }

    fun onRotateDisplay(context: Context, toRotation: Int) {
        if (mDisplayLayout.rotation() == toRotation) {
            return
        }
        mDisplayLayout.rotateTo(context.resources, toRotation)
    }
}
