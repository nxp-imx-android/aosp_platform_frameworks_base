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

package com.android.systemui.customization.clocks

import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.END
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.START
import androidx.constraintlayout.widget.ConstraintSet.TOP
import androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.customization.clocks.ContextUtil.getSafeStatusBarHeight
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.plugins.clocks.AodClockBurnInModel
import com.android.systemui.plugins.clocks.ClockFaceLayout
import com.android.systemui.plugins.clocks.ClockPreviewConfig
import com.android.systemui.plugins.clocks.ClockViewIds
import com.android.systemui.plugins.clocks.LockscreenElement
import com.android.systemui.plugins.clocks.LockscreenElementContext
import com.android.systemui.plugins.clocks.LockscreenElementFactory
import com.android.systemui.plugins.clocks.LockscreenElementKeys

/** A ClockFaceLayout that applies the default lockscreen layout to a single view */
open class DefaultClockFaceLayout(val view: View) : ClockFaceLayout {
    override val views = listOf(view)

    override val elements by lazy {
        if (view.id == ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE) {
            listOf(largeClockElement)
        } else {
            listOf(smallClockElement)
        }
    }

    private val smallClockElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.ClockSmall
            override val context = view.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                // TODO(b/432451019): Collect correctly. Possibly route in using the ViewModel
                // val topMargin = if(isShadeLayoutWide) {
                //    dimensionResource(SysuiR.dimen.keyguard_split_shade_top_margin) +
                //        systemBarUtils.getStatusBarHeaderHeightKeyguard()
                // } else {
                //    dimensionResource(SysuiR.dimen.keyguard_clock_top_margin)
                // }
                val topMargin = 0.dp

                // TODO(b/432451019): Placement/positional modifiers need an implementation
                clockView(
                    view = view,
                    modifier =
                        Modifier.height(dimensionResource(clocksR.dimen.small_clock_height))
                            .padding(
                                horizontal = dimensionResource(clocksR.dimen.clock_padding_start)
                            )
                            .padding(top = topMargin)
                            // .onTopPlacementChanged(onTopChanged)
                            .then(context.burnInModifier)
                            .onGloballyPositioned { coordinates ->
                                // onBottomChanged?.invoke(coordinates.boundsInWindow().bottom)
                            },
                )
            }
        }

    private val largeClockElement =
        object : LockscreenElement {
            override val key = LockscreenElementKeys.ClockLarge
            override val context = view.context

            @Composable
            override fun ContentScope.LockscreenElement(
                factory: LockscreenElementFactory,
                context: LockscreenElementContext,
            ) {
                // TODO(b/418824686): Migrate stepping animation to compose
                clockView(view, Modifier.wrapContentSize().then(context.burnInModifier))
            }
        }

    companion object {
        @Composable
        fun clockView(view: View?, modifier: Modifier = Modifier) {
            // TODO(b/432451019): This may be more performant wrapped in a Box. See b/430690566.
            AndroidView(
                factory = {
                    FrameLayout(it).apply {
                        // Clip nothing. The clock views at times render outside their bounds.
                        // Compose does not clip by default, so only this layer needs clipping
                        // to be explicitly disabled.
                        clipChildren = false
                        clipToPadding = false
                    }
                },
                update = { parent ->
                    view?.let {
                        parent.removeAllViews()
                        (view.parent as? ViewGroup)?.removeView(view)
                        parent.addView(view)
                    } ?: run { parent.removeAllViews() }
                },
                modifier = modifier,
            )
        }
    }

    override fun applyConstraints(constraints: ConstraintSet): ConstraintSet {
        if (views.size != 1) {
            throw IllegalArgumentException(
                "Should have only one container view when using DefaultClockFaceLayout"
            )
        }
        return constraints
    }

    override fun applyExternalDisplayPresentationConstraints(
        constraints: ConstraintSet
    ): ConstraintSet {
        return constraints.apply {
            constrainWidth(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, WRAP_CONTENT)
            constrainHeight(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, WRAP_CONTENT)

            connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, TOP, PARENT_ID, TOP)
            connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, BOTTOM, PARENT_ID, BOTTOM)
            connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, START, PARENT_ID, START)
            connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, END, PARENT_ID, END)
        }
    }

    override fun applyPreviewConstraints(
        clockPreviewConfig: ClockPreviewConfig,
        constraints: ConstraintSet,
    ): ConstraintSet {
        val res = view.context.resources
        return constraints.apply {
            constrainWidth(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, WRAP_CONTENT)
            constrainHeight(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, WRAP_CONTENT)
            constrainMaxHeight(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, 0)

            val largeClockTopMargin =
                if (com.android.systemui.shared.Flags.clockReactiveSmartspaceLayout()) {
                    view.context.getSafeStatusBarHeight() / 2 +
                        res.getDimensionPixelSize(clocksR.dimen.keyguard_smartspace_top_offset) +
                        res.getDimensionPixelSize(clocksR.dimen.enhanced_smartspace_height)
                } else {
                    view.context.getSafeStatusBarHeight() +
                        res.getDimensionPixelSize(clocksR.dimen.small_clock_padding_top) +
                        res.getDimensionPixelSize(clocksR.dimen.keyguard_smartspace_top_offset) +
                        res.getDimensionPixelSize(clocksR.dimen.date_weather_view_height) +
                        res.getDimensionPixelSize(clocksR.dimen.enhanced_smartspace_height)
                }

            connect(
                ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE,
                TOP,
                PARENT_ID,
                TOP,
                largeClockTopMargin,
            )
            connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, START, PARENT_ID, START)
            connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, END, PARENT_ID, END)

            clockPreviewConfig.udfpsTop?.let { udfpsTop ->
                connect(
                    ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE,
                    BOTTOM,
                    PARENT_ID,
                    BOTTOM,
                    (res.displayMetrics.heightPixels - udfpsTop).toInt(),
                )
            }
                ?: run {
                    // Copied calculation codes from applyConstraints in DefaultDeviceEntrySection
                    clockPreviewConfig.lockViewId?.let { lockViewId ->
                        connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE, BOTTOM, lockViewId, TOP)
                    }
                        ?: run {
                            val bottomPaddingPx =
                                res.getDimensionPixelSize(clocksR.dimen.lock_icon_margin_bottom)
                            val defaultDensity =
                                DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat() /
                                    DisplayMetrics.DENSITY_DEFAULT.toFloat()
                            val lockIconRadiusPx = (defaultDensity * 36).toInt()
                            val clockBottomMargin = bottomPaddingPx + 2 * lockIconRadiusPx
                            connect(
                                ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE,
                                BOTTOM,
                                PARENT_ID,
                                BOTTOM,
                                clockBottomMargin,
                            )
                        }
                }

            constrainWidth(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, WRAP_CONTENT)
            constrainHeight(
                ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL,
                res.getDimensionPixelSize(clocksR.dimen.small_clock_height),
            )
            connect(
                ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL,
                START,
                PARENT_ID,
                START,
                res.getDimensionPixelSize(clocksR.dimen.clock_padding_start) +
                    clockPreviewConfig.statusViewMarginHorizontal,
            )

            val smallClockTopMargin = clockPreviewConfig.getSmallClockTopPadding()
            connect(
                ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL,
                TOP,
                PARENT_ID,
                TOP,
                smallClockTopMargin,
            )
        }
    }

    override fun applyAodBurnIn(aodBurnInModel: AodClockBurnInModel) {
        // Default clock doesn't need detailed control of view
    }
}
